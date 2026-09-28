package com.chris64233.cc.biobank.withdrawal;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.event.EventType;
import com.chris64233.cc.biobank.event.SampleEvent;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.sample.Sample;
import com.chris64233.cc.biobank.sample.SampleRepository;
import com.chris64233.cc.biobank.sample.SampleStatus;
import com.chris64233.cc.biobank.subject.Consent;
import com.chris64233.cc.biobank.subject.ConsentRepository;
import com.chris64233.cc.biobank.subject.Subject;
import com.chris64233.cc.biobank.subject.SubjectRepository;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawConsentRequest;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawalResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class WithdrawalService {

    private final SubjectRepository subjectRepository;
    private final ConsentRepository consentRepository;
    private final SampleRepository sampleRepository;
    private final AliquotRepository aliquotRepository;
    private final WithdrawalRecordRepository withdrawalRecordRepository;
    private final SampleEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public WithdrawalService(SubjectRepository subjectRepository,
            ConsentRepository consentRepository, SampleRepository sampleRepository,
            AliquotRepository aliquotRepository,
            WithdrawalRecordRepository withdrawalRecordRepository,
            SampleEventRepository eventRepository, ObjectMapper objectMapper) {
        this.subjectRepository = subjectRepository;
        this.consentRepository = consentRepository;
        this.sampleRepository = sampleRepository;
        this.aliquotRepository = aliquotRepository;
        this.withdrawalRecordRepository = withdrawalRecordRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WithdrawalResponse withdraw(String subjectCode, WithdrawConsentRequest request) {
        Optional<WithdrawalRecord> existing =
                withdrawalRecordRepository.findByWithdrawalKey(request.withdrawalKey());
        if (existing.isPresent()) {
            WithdrawalRecord record = existing.get();
            if (!record.getSubjectCode().equals(subjectCode)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "撤回号已用于其他受试者: " + request.withdrawalKey());
            }
            return deserialize(record.getResponseBody());
        }

        Subject subject = subjectRepository.findBySubjectCode(subjectCode)
                .orElseThrow(() -> ApiException.notFound("受试者不存在: " + subjectCode));

        // 先锁原始样本（id 升序），与分装事务互斥，保证撤回覆盖同刻新建的分装链路。
        List<Sample> samples = sampleRepository.findBySubjectIdForUpdate(subject.getId());

        // 再锁当前在库可用分装（aliquot id 升序）。领用事务也按 aliquot id 升序加锁，
        // 两边锁序一致：并发时要么领用先拿锁并完整完成（扣减+幂等记录），
        // 要么撤回先拿锁并将该分装冻结，绝不会出现半领用半冻结。
        List<Aliquot> available = aliquotRepository.findBySubjectIdAndStatusForUpdate(
                subject.getId(), AliquotStatus.AVAILABLE);

        Instant now = Instant.now();
        List<Consent> consents = consentRepository.findBySubjectIdForUpdate(subject.getId());
        if (request.consentVersionCode() != null && !request.consentVersionCode().isBlank()
                && consents.stream().noneMatch(c ->
                        c.getVersionCode().equals(request.consentVersionCode()))) {
            throw ApiException.notFound("同意版本不存在: " + subjectCode + "/"
                    + request.consentVersionCode());
        }
        List<String> withdrawnVersions = new ArrayList<>();
        for (Consent consent : consents) {
            boolean target = request.consentVersionCode() == null
                    || request.consentVersionCode().isBlank()
                    || consent.getVersionCode().equals(request.consentVersionCode());
            if (target && !consent.isWithdrawn()) {
                consent.markWithdrawn(now);
                withdrawnVersions.add(consent.getVersionCode());
            }
        }

        List<WithdrawalResponse.FrozenSample> frozenSamples = new ArrayList<>();
        for (Sample sample : samples) {
            if (sample.getStatus() == SampleStatus.ACTIVE) {
                sample.freeze();
                eventRepository.save(new SampleEvent(EventType.SAMPLE_FROZEN, sample.getId(), null,
                        BigDecimal.ZERO, sample.getRemainingVolume(),
                        "撤回号=" + request.withdrawalKey()));
                frozenSamples.add(new WithdrawalResponse.FrozenSample(sample.getId(),
                        sample.getExternalId(), sample.getRemainingVolume()));
            }
        }

        List<WithdrawalResponse.FrozenAliquot> frozenAliquots = new ArrayList<>();
        for (Aliquot aliquot : available) {
            aliquot.freeze();
            eventRepository.save(new SampleEvent(EventType.ALIQUOT_FROZEN,
                    aliquot.getSample().getId(), aliquot.getId(), BigDecimal.ZERO,
                    aliquot.getRemainingVolume(), "撤回号=" + request.withdrawalKey()));
            frozenAliquots.add(new WithdrawalResponse.FrozenAliquot(aliquot.getId(),
                    aliquot.getSample().getId(), aliquot.getRemainingVolume()));
        }

        WithdrawalResponse response = new WithdrawalResponse(request.withdrawalKey(), subjectCode,
                withdrawnVersions, frozenSamples, frozenAliquots, now);
        try {
            withdrawalRecordRepository.saveAndFlush(
                    new WithdrawalRecord(request.withdrawalKey(), subjectCode,
                            serialize(response)));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "撤回号并发冲突: " + request.withdrawalKey());
        }
        return response;
    }

    private String serialize(WithdrawalResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new IllegalStateException("撤回结果序列化失败", ex);
        }
    }

    private WithdrawalResponse deserialize(String body) {
        return parseResponse(body);
    }

    public WithdrawalResponse parseResponse(String body) {
        try {
            return objectMapper.readValue(body, WithdrawalResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("撤回幂等记录反序列化失败", ex);
        }
    }
}
