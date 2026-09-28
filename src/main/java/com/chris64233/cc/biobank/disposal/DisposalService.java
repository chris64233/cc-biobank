package com.chris64233.cc.biobank.disposal;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.disposal.dto.DisposeRequest;
import com.chris64233.cc.biobank.disposal.dto.DisposalResponse;
import com.chris64233.cc.biobank.event.EventType;
import com.chris64233.cc.biobank.event.SampleEvent;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.sample.Sample;
import com.chris64233.cc.biobank.sample.SampleRepository;
import com.chris64233.cc.biobank.sample.SampleStatus;
import com.chris64233.cc.biobank.subject.Subject;
import com.chris64233.cc.biobank.subject.SubjectRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DisposalService {

    private final SampleRepository sampleRepository;
    private final AliquotRepository aliquotRepository;
    private final DisposalRecordRepository disposalRecordRepository;
    private final DisposalItemRepository disposalItemRepository;
    private final SubjectRepository subjectRepository;
    private final SampleEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public DisposalService(SampleRepository sampleRepository,
            AliquotRepository aliquotRepository,
            DisposalRecordRepository disposalRecordRepository,
            DisposalItemRepository disposalItemRepository, SubjectRepository subjectRepository,
            SampleEventRepository eventRepository, ObjectMapper objectMapper) {
        this.sampleRepository = sampleRepository;
        this.aliquotRepository = aliquotRepository;
        this.disposalRecordRepository = disposalRecordRepository;
        this.disposalItemRepository = disposalItemRepository;
        this.subjectRepository = subjectRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量处置在单个事务中完成：先锁原始样本、再锁分装（与撤回/领用的锁序一致），
     * 逐只校验全部为 FROZEN 且属于同一受试者，全部通过后统一更新状态与体积台账，
     * 写事件与处置明细。任一校验失败整体回滚，绝不留下部分处置。
     */
    @Transactional
    public DisposalResponse dispose(String subjectCode, DisposeRequest request) {
        Optional<DisposalRecord> existing =
                disposalRecordRepository.findByDisposalKey(request.disposalKey());
        if (existing.isPresent()) {
            DisposalRecord record = existing.get();
            if (!record.getSubjectCode().equals(subjectCode)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "处置号已用于其他受试者: " + request.disposalKey());
            }
            return deserialize(record.getResponseBody());
        }

        Subject subject = subjectRepository.findBySubjectCode(subjectCode)
                .orElseThrow(() -> ApiException.notFound("受试者不存在: " + subjectCode));

        TreeSet<Long> sampleIds = new TreeSet<>();
        TreeSet<Long> aliquotIds = new TreeSet<>();
        for (DisposeRequest.Item item : request.items()) {
            (item.targetType() == DisposalTargetType.SAMPLE ? sampleIds : aliquotIds)
                    .add(item.targetId());
        }
        List<Long> orderedSampleIds = List.copyOf(sampleIds);
        List<Long> orderedAliquotIds = List.copyOf(aliquotIds);

        var lockedSamples = sampleRepository.findAllByIdForUpdate(orderedSampleIds).stream()
                .collect(Collectors.toMap(Sample::getId, Function.identity()));
        for (Long id : orderedSampleIds) {
            if (!lockedSamples.containsKey(id)) {
                throw ApiException.notFound("样本不存在: " + id);
            }
        }
        var lockedAliquots = aliquotRepository.findAllByIdForUpdate(orderedAliquotIds).stream()
                .collect(Collectors.toMap(Aliquot::getId, Function.identity()));
        for (Long id : orderedAliquotIds) {
            if (!lockedAliquots.containsKey(id)) {
                throw ApiException.notFound("子样本不存在: " + id);
            }
        }

        // 预检：归属一致且必须为 FROZEN。耗尽、在库可用、已销毁/返还/保留禁用一律拒绝，
        // 杜绝处置未冻结对象或重复处置。
        for (Long id : orderedSampleIds) {
            Sample sample = lockedSamples.get(id);
            if (!sample.getSubject().getId().equals(subject.getId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "样本不属于该受试者: " + id + " / " + subject.getSubjectCode());
            }
            if (sample.getStatus() != SampleStatus.FROZEN) {
                throw new ApiException(ErrorCode.SAMPLE_NOT_DISPOSABLE,
                        "样本当前状态不可处置: " + id + " / " + sample.getStatus());
            }
        }
        for (Long id : orderedAliquotIds) {
            Aliquot aliquot = lockedAliquots.get(id);
            if (!aliquot.getSample().getSubject().getId().equals(subject.getId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "子样本不属于该受试者: " + id + " / " + subject.getSubjectCode());
            }
            if (aliquot.getStatus() != AliquotStatus.FROZEN) {
                throw new ApiException(ErrorCode.SAMPLE_NOT_DISPOSABLE,
                        "子样本当前状态不可处置: " + id + " / " + aliquot.getStatus());
            }
        }

        AliquotStatus targetStatus = switch (request.decision()) {
            case DESTROY -> AliquotStatus.DESTROYED;
            case RETURN -> AliquotStatus.RETURNED;
            case RETAIN_NO_RESEARCH -> AliquotStatus.RETAINED_NO_RESEARCH;
        };
        SampleStatus sampleTarget = switch (request.decision()) {
            case DESTROY -> SampleStatus.DESTROYED;
            case RETURN -> SampleStatus.RETURNED;
            case RETAIN_NO_RESEARCH -> SampleStatus.RETAINED_NO_RESEARCH;
        };
        Instant now = Instant.now();
        String detailPrefix = "处置号=" + request.disposalKey() + ", 决定=" + request.decision();

        List<DisposalResponse.Item> responseItems = new ArrayList<>();
        for (Long id : orderedSampleIds) {
            Sample sample = lockedSamples.get(id);
            BigDecimal before = sample.getRemainingVolume();
            sample.dispose(sampleTarget);
            eventRepository.save(new SampleEvent(EventType.SAMPLE_DISPOSED, sample.getId(), null,
                    sample.getRemainingVolume().subtract(before), sample.getRemainingVolume(),
                    detailPrefix));
            disposalItemRepository.save(new DisposalItem(request.disposalKey(), subjectCode,
                    DisposalTargetType.SAMPLE, id, request.decision(), targetStatus,
                    sample.getRemainingVolume(), now));
            responseItems.add(new DisposalResponse.Item(DisposalTargetType.SAMPLE, id, before,
                    sample.getRemainingVolume(), targetStatus));
        }
        for (Long id : orderedAliquotIds) {
            Aliquot aliquot = lockedAliquots.get(id);
            BigDecimal before = aliquot.getRemainingVolume();
            aliquot.dispose(targetStatus);
            eventRepository.save(new SampleEvent(EventType.ALIQUOT_DISPOSED,
                    aliquot.getSample().getId(), aliquot.getId(),
                    aliquot.getRemainingVolume().subtract(before),
                    aliquot.getRemainingVolume(), detailPrefix));
            disposalItemRepository.save(new DisposalItem(request.disposalKey(), subjectCode,
                    DisposalTargetType.ALIQUOT, id, request.decision(), targetStatus,
                    aliquot.getRemainingVolume(), now));
            responseItems.add(new DisposalResponse.Item(DisposalTargetType.ALIQUOT, id, before,
                    aliquot.getRemainingVolume(), targetStatus));
        }
        sampleRepository.saveAll(lockedSamples.values());
        aliquotRepository.saveAll(lockedAliquots.values());

        DisposalResponse response = new DisposalResponse(request.disposalKey(), subjectCode,
                request.decision(), responseItems, now);
        try {
            disposalRecordRepository.saveAndFlush(
                    new DisposalRecord(request.disposalKey(), subjectCode, serialize(response)));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "处置号并发冲突: " + request.disposalKey());
        }
        return response;
    }

    private String serialize(DisposalResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new IllegalStateException("处置结果序列化失败", ex);
        }
    }

    private DisposalResponse deserialize(String body) {
        try {
            return objectMapper.readValue(body, DisposalResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("处置幂等记录反序列化失败", ex);
        }
    }
}
