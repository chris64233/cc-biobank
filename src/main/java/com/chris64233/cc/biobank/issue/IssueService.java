package com.chris64233.cc.biobank.issue;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.common.VolumeMath;
import com.chris64233.cc.biobank.consent.ConsentPolicy;
import com.chris64233.cc.biobank.consent.ConsentVersion;
import com.chris64233.cc.biobank.consent.Subject;
import com.chris64233.cc.biobank.event.EventType;
import com.chris64233.cc.biobank.event.SampleEvent;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.issue.dto.IssueRequest;
import com.chris64233.cc.biobank.issue.dto.IssueResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class IssueService {

    private final AliquotRepository aliquotRepository;
    private final IssueRecordRepository issueRecordRepository;
    private final SampleEventRepository eventRepository;
    private final ConsentPolicy consentPolicy;
    private final ObjectMapper objectMapper;

    public IssueService(AliquotRepository aliquotRepository,
            IssueRecordRepository issueRecordRepository, SampleEventRepository eventRepository,
            ConsentPolicy consentPolicy, ObjectMapper objectMapper) {
        this.aliquotRepository = aliquotRepository;
        this.issueRecordRepository = issueRecordRepository;
        this.eventRepository = eventRepository;
        this.consentPolicy = consentPolicy;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IssueResponse issue(IssueRequest request) {
        Map<Long, BigDecimal> merged = mergeAndValidate(request);
        String requestHash = canonicalHash(request, merged);

        Optional<IssueRecord> existing =
                issueRecordRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            IssueRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "幂等键已使用且请求内容不同: " + request.idempotencyKey());
            }
            return deserialize(record.getResponseBody());
        }

        // 加锁顺序：受试者 → 同意版本 → 分装，与撤回事务完全一致，二者在受试者行上串行化，
        // 并发结果只能是"完整领用"或"完整冻结"，不会交错。
        Subject subject = consentPolicy.requireSubjectByCode(request.subjectCode());
        ConsentVersion consent = consentPolicy.requireValidConsent(subject.getId(),
                request.consentVersion(), request.purpose());

        List<Long> ids = new ArrayList<>(merged.keySet());
        Map<Long, Aliquot> locked = aliquotRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(Aliquot::getId, Function.identity()));

        List<IssueResponse.Item> responseItems = new ArrayList<>();
        for (Long id : ids) {
            Aliquot aliquot = locked.get(id);
            if (aliquot == null) {
                throw ApiException.notFound("子样本不存在: " + id);
            }
            if (!aliquot.getSubjectId().equals(subject.getId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "领用分装不属于受试者 " + request.subjectCode() + ": " + id);
            }
            BigDecimal volume = merged.get(id);
            switch (aliquot.getStatus()) {
                case FROZEN -> throw new ApiException(ErrorCode.SAMPLE_FROZEN,
                        "子样本已因同意撤回冻结，不可领用: " + id);
                case DESTROYED, RETURNED, RETAINED -> throw new ApiException(
                        ErrorCode.ALREADY_DISPOSED,
                        "子样本已处置(" + aliquot.getStatus() + ")，不可领用: " + id);
                case DEPLETED -> throw ApiException.insufficientStock(
                        "子样本已耗尽，不可继续领用: " + id);
                case AVAILABLE -> {
                    if (aliquot.getRemainingVolume().compareTo(volume) < 0) {
                        throw ApiException.insufficientStock(
                                "子样本 " + id + " 库存不足: 需要 " + volume + ", 剩余 "
                                        + aliquot.getRemainingVolume());
                    }
                }
            }
            aliquot.deduct(volume);
            eventRepository.save(new SampleEvent(EventType.ALIQUOT_ISSUED,
                    aliquot.getSample().getId(), aliquot.getId(), volume.negate(),
                    aliquot.getRemainingVolume(),
                    "幂等键=" + request.idempotencyKey()
                            + ";用途=" + request.purpose()
                            + ";同意版本=" + consent.getVersion()));
            responseItems.add(new IssueResponse.Item(aliquot.getId(), volume,
                    aliquot.getRemainingVolume(), aliquot.getStatus()));
        }
        aliquotRepository.saveAll(locked.values());

        IssueResponse response = new IssueResponse(request.idempotencyKey(), subject.getId(),
                request.purpose(), snapshot(consent, request.purpose()), responseItems,
                Instant.now());
        try {
            issueRecordRepository.saveAndFlush(new IssueRecord(request.idempotencyKey(),
                    subject.getId(), request.purpose(), consent.getId(), requestHash,
                    serialize(response)));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "幂等键并发冲突: " + request.idempotencyKey());
        }
        return response;
    }

    private IssueResponse.ConsentSnapshot snapshot(ConsentVersion consent, String purpose) {
        List<String> purposes = Arrays.stream(consent.getAllowedPurposes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new IssueResponse.ConsentSnapshot(consent.getId(), consent.getVersion(), purpose,
                purposes, consent.getValidFrom(), consent.getValidUntil(),
                consent.getWithdrawnAt(), Instant.now());
    }

    private Map<Long, BigDecimal> mergeAndValidate(IssueRequest request) {
        Map<Long, BigDecimal> merged = new TreeMap<>();
        for (IssueRequest.Item item : request.items()) {
            BigDecimal volume = VolumeMath.normalize(item.volume());
            if (!VolumeMath.isPositive(volume)) {
                throw ApiException.validation("领用体积必须大于零: 子样本 " + item.aliquotId());
            }
            merged.merge(item.aliquotId(), volume, BigDecimal::add);
        }
        return merged;
    }

    private String canonicalHash(IssueRequest request, Map<Long, BigDecimal> merged) {
        StringBuilder builder = new StringBuilder(request.idempotencyKey())
                .append('|').append(request.subjectCode())
                .append('|').append(request.purpose())
                .append('|').append(request.consentVersion()).append('|');
        merged.forEach((id, volume) ->
                builder.append(id).append('=').append(volume.toPlainString()).append(';'));
        return builder.toString();
    }

    private String serialize(IssueResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new IllegalStateException("领用结果序列化失败", ex);
        }
    }

    private IssueResponse deserialize(String body) {
        try {
            return objectMapper.readValue(body, IssueResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("幂等记录反序列化失败", ex);
        }
    }
}
