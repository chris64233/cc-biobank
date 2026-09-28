package com.chris64233.cc.biobank.disposition;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.consent.ConsentPolicy;
import com.chris64233.cc.biobank.consent.Subject;
import com.chris64233.cc.biobank.disposition.dto.DispositionRequest;
import com.chris64233.cc.biobank.disposition.dto.DispositionResponse;
import com.chris64233.cc.biobank.event.EventType;
import com.chris64233.cc.biobank.event.SampleEvent;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.sample.Sample;
import com.chris64233.cc.biobank.sample.SampleRepository;
import com.chris64233.cc.biobank.sample.SampleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DispositionService {

    private final ConsentPolicy consentPolicy;
    private final SampleRepository sampleRepository;
    private final AliquotRepository aliquotRepository;
    private final SampleEventRepository eventRepository;
    private final DispositionRecordRepository dispositionRecordRepository;
    private final ObjectMapper objectMapper;

    public DispositionService(ConsentPolicy consentPolicy, SampleRepository sampleRepository,
            AliquotRepository aliquotRepository, SampleEventRepository eventRepository,
            DispositionRecordRepository dispositionRecordRepository, ObjectMapper objectMapper) {
        this.consentPolicy = consentPolicy;
        this.sampleRepository = sampleRepository;
        this.aliquotRepository = aliquotRepository;
        this.eventRepository = eventRepository;
        this.dispositionRecordRepository = dispositionRecordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量处置冻结样本。所有对象必须属于同一受试者且当前为 FROZEN；
     * 全部状态与体积台账在同一事务内原子更新，任一对象不合法整体回滚，不会只处置一部分。
     */
    @Transactional
    public DispositionResponse dispose(DispositionRequest request) {
        Optional<DispositionRecord> existing =
                dispositionRecordRepository.findByDispositionNo(request.dispositionNo());
        if (existing.isPresent()) {
            return deserialize(existing.get().getResponseBody());
        }

        DispositionAction action = request.resolvedAction();
        if (action == null) {
            throw ApiException.validation(
                    "处置动作无效，必须为 DESTROY/RETURN/RETAIN: " + request.action());
        }

        Subject subject = consentPolicy.requireSubjectByCode(request.subjectCode());
        consentPolicy.lockSubject(subject.getId());

        List<DispositionResponse.Item> items = new ArrayList<>();
        List<Sample> changedSamples = new ArrayList<>();
        List<Aliquot> changedAliquots = new ArrayList<>();
        List<Long> sampleIdsToLock = new ArrayList<>();
        List<Long> aliquotIdsToLock = new ArrayList<>();
        for (DispositionRequest.Item item : request.items()) {
            if (item.isSample()) {
                sampleIdsToLock.add(item.targetId());
            } else if (item.isAliquot()) {
                aliquotIdsToLock.add(item.targetId());
            } else {
                throw ApiException.validation(
                        "对象类型无效，必须为 SAMPLE 或 ALIQUOT: " + item.targetType());
            }
        }
        if (sampleIdsToLock.stream().distinct().count() != sampleIdsToLock.size()
                || aliquotIdsToLock.stream().distinct().count() != aliquotIdsToLock.size()) {
            throw ApiException.validation("处置明细存在重复对象");
        }
        // 统一按 id 升序加锁，避免跨请求以不同顺序持锁造成死锁。
        java.util.Collections.sort(sampleIdsToLock);
        java.util.Collections.sort(aliquotIdsToLock);

        // 先对涉及的原始样本和分装加悲观锁（均按 id 排序），与撤回/领用串行，保证台账原子更新。
        for (Long sampleId : sampleIdsToLock) {
            Sample sample = sampleRepository.findByIdForUpdate(sampleId)
                    .orElseThrow(() -> ApiException.notFound("样本不存在: " + sampleId));
            requireFrozenSample(sample, subject.getId());
            BigDecimal before = sample.getRemainingVolume();
            SampleStatus target = mapSampleStatus(action);
            boolean clearVolume = action != DispositionAction.RETAIN;
            sample.dispose(target, clearVolume);
            changedSamples.add(sample);
            eventRepository.save(new SampleEvent(EventType.SAMPLE_DISPOSED, sample.getId(), null,
                    clearVolume ? before.negate() : BigDecimal.ZERO, sample.getRemainingVolume(),
                    "处置=" + action + "，处置号=" + request.dispositionNo()));
            items.add(new DispositionResponse.Item("SAMPLE", sample.getId(),
                    SampleStatus.FROZEN.name(), target.name(), before,
                    sample.getRemainingVolume()));
        }

        Map<Long, Aliquot> lockedAliquots = new TreeMap<>();
        if (!aliquotIdsToLock.isEmpty()) {
            aliquotRepository.findAllByIdForUpdate(aliquotIdsToLock)
                    .forEach(a -> lockedAliquots.put(a.getId(), a));
        }
        for (Long aliquotId : aliquotIdsToLock) {
            Aliquot aliquot = lockedAliquots.get(aliquotId);
            if (aliquot == null) {
                throw ApiException.notFound("子样本不存在: " + aliquotId);
            }
            requireFrozenAliquot(aliquot, subject.getId());
            BigDecimal before = aliquot.getRemainingVolume();
            AliquotStatus target = mapAliquotStatus(action);
            boolean clearVolume = action != DispositionAction.RETAIN;
            aliquot.dispose(target, clearVolume);
            changedAliquots.add(aliquot);
            eventRepository.save(new SampleEvent(EventType.ALIQUOT_DISPOSED,
                    aliquot.getSample().getId(), aliquot.getId(),
                    clearVolume ? before.negate() : BigDecimal.ZERO,
                    aliquot.getRemainingVolume(),
                    "处置=" + action + "，处置号=" + request.dispositionNo()));
            items.add(new DispositionResponse.Item("ALIQUOT", aliquot.getId(),
                    AliquotStatus.FROZEN.name(), target.name(), before,
                    aliquot.getRemainingVolume()));
        }

        sampleRepository.saveAll(changedSamples);
        aliquotRepository.saveAll(changedAliquots);

        DispositionResponse response = new DispositionResponse(request.dispositionNo(),
                subject.getId(), action, items, Instant.now());
        try {
            dispositionRecordRepository.saveAndFlush(new DispositionRecord(
                    request.dispositionNo(), subject.getId(), action.name(), serialize(response)));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "处置号并发冲突: " + request.dispositionNo());
        }
        return response;
    }

    private void requireFrozenSample(Sample sample, Long subjectId) {
        if (!sample.getSubjectId().equals(subjectId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "处置样本不属于该受试者: " + sample.getId());
        }
        switch (sample.getStatus()) {
            case FROZEN -> {
            }
            case AVAILABLE -> throw new ApiException(ErrorCode.SAMPLE_FROZEN,
                    "样本未冻结，不可处置: " + sample.getId());
            case DESTROYED, RETURNED, RETAINED -> throw new ApiException(ErrorCode.ALREADY_DISPOSED,
                    "样本已处置(" + sample.getStatus() + ")，不得重复处置: " + sample.getId());
        }
    }

    private void requireFrozenAliquot(Aliquot aliquot, Long subjectId) {
        if (!aliquot.getSubjectId().equals(subjectId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "处置子样本不属于该受试者: " + aliquot.getId());
        }
        switch (aliquot.getStatus()) {
            case FROZEN -> {
            }
            case AVAILABLE, DEPLETED -> throw new ApiException(ErrorCode.SAMPLE_FROZEN,
                    "子样本当前状态 " + aliquot.getStatus() + "，不可处置: " + aliquot.getId());
            case DESTROYED, RETURNED, RETAINED -> throw new ApiException(ErrorCode.ALREADY_DISPOSED,
                    "子样本已处置(" + aliquot.getStatus() + ")，不得重复处置: "
                            + aliquot.getId());
        }
    }

    private SampleStatus mapSampleStatus(DispositionAction action) {
        return switch (action) {
            case DESTROY -> SampleStatus.DESTROYED;
            case RETURN -> SampleStatus.RETURNED;
            case RETAIN -> SampleStatus.RETAINED;
        };
    }

    private AliquotStatus mapAliquotStatus(DispositionAction action) {
        return switch (action) {
            case DESTROY -> AliquotStatus.DESTROYED;
            case RETURN -> AliquotStatus.RETURNED;
            case RETAIN -> AliquotStatus.RETAINED;
        };
    }

    @Transactional(readOnly = true)
    public List<DispositionRecord> listBySubject(Long subjectId) {
        return dispositionRecordRepository.findBySubjectIdOrderByIdAsc(subjectId);
    }

    private String serialize(DispositionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new IllegalStateException("处置结果序列化失败", ex);
        }
    }

    private DispositionResponse deserialize(String body) {
        try {
            return objectMapper.readValue(body, DispositionResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("处置幂等记录反序列化失败", ex);
        }
    }
}
