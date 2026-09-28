package com.chris64233.cc.biobank.issue;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.common.VolumeMath;
import com.chris64233.cc.biobank.event.EventType;
import com.chris64233.cc.biobank.event.SampleEvent;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.issue.dto.IssueRequest;
import com.chris64233.cc.biobank.issue.dto.IssueResponse;
import com.chris64233.cc.biobank.subject.Consent;
import com.chris64233.cc.biobank.subject.ConsentRepository;
import com.chris64233.cc.biobank.subject.Subject;
import com.chris64233.cc.biobank.subject.SubjectRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    private final SubjectRepository subjectRepository;
    private final ConsentRepository consentRepository;
    private final ObjectMapper objectMapper;

    public IssueService(AliquotRepository aliquotRepository,
            IssueRecordRepository issueRecordRepository, SampleEventRepository eventRepository,
            SubjectRepository subjectRepository, ConsentRepository consentRepository,
            ObjectMapper objectMapper) {
        this.aliquotRepository = aliquotRepository;
        this.issueRecordRepository = issueRecordRepository;
        this.eventRepository = eventRepository;
        this.subjectRepository = subjectRepository;
        this.consentRepository = consentRepository;
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
            // 历史领用原样返回：同意事后撤回/过期不影响已完成的领用。
            return deserialize(record.getResponseBody());
        }

        Subject subject = subjectRepository.findBySubjectCode(request.subjectCode())
                .orElseThrow(() -> ApiException.notFound(
                        "受试者不存在: " + request.subjectCode()));

        // 按 aliquot id 升序加行锁，与撤回事务的加锁顺序保持一致。
        List<Long> ids = new ArrayList<>(merged.keySet());
        Map<Long, Aliquot> locked = aliquotRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(Aliquot::getId, Function.identity()));
        for (Long id : ids) {
            if (!locked.containsKey(id)) {
                throw ApiException.notFound("子样本不存在: " + id);
            }
        }

        // 逐只做归属预检：领用对象必须全部属于请求声明的受试者。
        for (Long id : ids) {
            Aliquot aliquot = locked.get(id);
            if (!aliquot.getSample().getSubject().getId().equals(subject.getId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "子样本不属于该受试者: " + id + " / " + subject.getSubjectCode());
            }
        }

        // 同意在加锁之后、状态/库存校验之前检查：撤回与领用并发时，先拿到资源的一方
        // 才能完成；撤回一旦生效，新领用一律以 CONSENT_INVALID 被拒绝。
        Consent consent = consentRepository
                .findBySubjectIdAndVersionCodeForUpdate(subject.getId(),
                        request.consentVersionCode())
                .orElseThrow(() -> new ApiException(ErrorCode.CONSENT_INVALID,
                        "同意版本不存在: " + subject.getSubjectCode() + "/"
                                + request.consentVersionCode()));
        Instant now = Instant.now();
        if (!consent.supports(request.purpose(), now)) {
            throw new ApiException(ErrorCode.CONSENT_INVALID, describeInvalidConsent(consent, now));
        }

        // 同意通过后再做库存与状态预检：任一项不满足整体回滚，绝不形成部分领用。
        for (Long id : ids) {
            Aliquot aliquot = locked.get(id);
            BigDecimal volume = merged.get(id);
            if (aliquot.getStatus() == AliquotStatus.DEPLETED) {
                throw ApiException.insufficientStock("子样本已耗尽，不可继续领用: " + id);
            }
            if (aliquot.getStatus() != AliquotStatus.AVAILABLE) {
                throw new ApiException(ErrorCode.SAMPLE_NOT_DISPOSABLE,
                        "子样本当前状态不可领用: " + id + " / " + aliquot.getStatus());
            }
            if (aliquot.getRemainingVolume().compareTo(volume) < 0) {
                throw ApiException.insufficientStock(
                        "子样本 " + id + " 库存不足: 需要 " + volume + ", 剩余 "
                                + aliquot.getRemainingVolume());
            }
        }

        List<IssueResponse.Item> responseItems = new ArrayList<>();
        for (Long id : ids) {
            Aliquot aliquot = locked.get(id);
            BigDecimal volume = merged.get(id);
            aliquot.deduct(volume);
            eventRepository.save(new SampleEvent(EventType.ALIQUOT_ISSUED,
                    aliquot.getSample().getId(), aliquot.getId(), volume.negate(),
                    aliquot.getRemainingVolume(),
                    "幂等键=" + request.idempotencyKey()
                            + ", 同意版本=" + consent.getVersionCode()
                            + ", 用途=" + request.purpose()));
            responseItems.add(new IssueResponse.Item(aliquot.getId(), volume,
                    aliquot.getRemainingVolume(), aliquot.getStatus()));
        }
        aliquotRepository.saveAll(locked.values());

        IssueResponse response = new IssueResponse(request.idempotencyKey(),
                subject.getSubjectCode(), consent.getVersionCode(), request.purpose(),
                responseItems, Instant.now());
        try {
            issueRecordRepository.saveAndFlush(new IssueRecord(request.idempotencyKey(),
                    subject.getSubjectCode(), consent.getVersionCode(), request.purpose(),
                    requestHash, serialize(response)));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "幂等键并发冲突: " + request.idempotencyKey());
        }
        return response;
    }

    private String describeInvalidConsent(Consent consent, Instant now) {
        String ref = consent.getSubject().getSubjectCode() + "/" + consent.getVersionCode();
        if (consent.isWithdrawn()) {
            return "同意已撤回，不能支持新的领用: " + ref;
        }
        if (now.isBefore(consent.getValidFrom())) {
            return "同意尚未生效: " + ref;
        }
        if (consent.getValidUntil() != null && !now.isBefore(consent.getValidUntil())) {
            return "同意已过期: " + ref;
        }
        return "同意用途不匹配，未授权该研究用途: " + ref;
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
                .append('|').append(request.consentVersionCode())
                .append('|').append(request.purpose()).append('|');
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
