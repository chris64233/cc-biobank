package com.chris64233.cc.biobank.withdrawal;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.consent.ConsentPolicy;
import com.chris64233.cc.biobank.consent.ConsentVersion;
import com.chris64233.cc.biobank.consent.ConsentVersionRepository;
import com.chris64233.cc.biobank.consent.Subject;
import com.chris64233.cc.biobank.event.EventType;
import com.chris64233.cc.biobank.event.SampleEvent;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.sample.Sample;
import com.chris64233.cc.biobank.sample.SampleRepository;
import com.chris64233.cc.biobank.sample.SampleStatus;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawConsentRequest;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawalResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class WithdrawalService {

    private final ConsentPolicy consentPolicy;
    private final ConsentVersionRepository consentVersionRepository;
    private final SampleRepository sampleRepository;
    private final AliquotRepository aliquotRepository;
    private final SampleEventRepository eventRepository;
    private final WithdrawalRecordRepository withdrawalRecordRepository;
    private final ObjectMapper objectMapper;

    public WithdrawalService(ConsentPolicy consentPolicy,
            ConsentVersionRepository consentVersionRepository, SampleRepository sampleRepository,
            AliquotRepository aliquotRepository, SampleEventRepository eventRepository,
            WithdrawalRecordRepository withdrawalRecordRepository, ObjectMapper objectMapper) {
        this.consentPolicy = consentPolicy;
        this.consentVersionRepository = consentVersionRepository;
        this.sampleRepository = sampleRepository;
        this.aliquotRepository = aliquotRepository;
        this.eventRepository = eventRepository;
        this.withdrawalRecordRepository = withdrawalRecordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 一次性撤回受试者全部同意并冻结其全部在库可用样本。
     *
     * <p>加锁顺序 受试者 → 同意版本 → 原始样本 → 分装，与领用时的锁序一致；
     * 并发领用在受试者行上等待，故结果只能是完整领用或完整冻结之一。
     * 历史领用记录与已耗尽/已处置样本不改写。
     */
    @Transactional
    public WithdrawalResponse withdraw(WithdrawConsentRequest request) {
        Optional<WithdrawalRecord> existing =
                withdrawalRecordRepository.findByWithdrawalNo(request.withdrawalNo());
        if (existing.isPresent()) {
            return deserialize(existing.get().getResponseBody());
        }

        Subject subject = consentPolicy.requireSubjectByCode(request.subjectCode());
        // 锁定受试者：此后任何新领用都无法进入。
        consentPolicy.lockSubject(subject.getId());

        Instant now = Instant.now();
        List<ConsentVersion> consents =
                consentVersionRepository.findBySubjectIdForUpdate(subject.getId());
        List<Long> consentIds = consents.stream().map(ConsentVersion::getId).toList();
        for (ConsentVersion consent : consents) {
            consent.withdraw(now);
        }
        consentVersionRepository.saveAll(consents);

        // 锁定并冻结全部在库可用的原始样本。
        List<Sample> samples = sampleRepository.findBySubjectIdForUpdate(subject.getId());
        List<Long> frozenSampleIds = new java.util.ArrayList<>();
        for (Sample sample : samples) {
            if (sample.getStatus() == SampleStatus.AVAILABLE) {
                sample.freeze(now);
                frozenSampleIds.add(sample.getId());
                eventRepository.save(new SampleEvent(EventType.SAMPLE_FROZEN, sample.getId(),
                        null, BigDecimal.ZERO, sample.getRemainingVolume(),
                        "同意撤回，撤回号=" + request.withdrawalNo()));
            }
        }
        sampleRepository.saveAll(samples);

        // 锁定并冻结全部在库可用的后代分装；已耗尽的不再有在库物质，保持 DEPLETED。
        List<Aliquot> aliquots = aliquotRepository.findBySubjectIdForUpdate(subject.getId());
        List<Long> frozenAliquotIds = new java.util.ArrayList<>();
        for (Aliquot aliquot : aliquots) {
            if (aliquot.getStatus() == AliquotStatus.AVAILABLE) {
                aliquot.freeze(now);
                frozenAliquotIds.add(aliquot.getId());
                eventRepository.save(new SampleEvent(EventType.ALIQUOT_FROZEN,
                        aliquot.getSample().getId(), aliquot.getId(), BigDecimal.ZERO,
                        aliquot.getRemainingVolume(),
                        "同意撤回，撤回号=" + request.withdrawalNo()));
            }
        }
        aliquotRepository.saveAll(aliquots);

        WithdrawalResponse response = new WithdrawalResponse(request.withdrawalNo(),
                subject.getId(), consentIds, frozenSampleIds, frozenAliquotIds, now);
        try {
            withdrawalRecordRepository.saveAndFlush(
                    new WithdrawalRecord(request.withdrawalNo(), subject.getId(),
                            serialize(response)));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "撤回号并发冲突: " + request.withdrawalNo());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<WithdrawalRecord> listBySubject(Long subjectId) {
        return withdrawalRecordRepository.findBySubjectIdOrderByIdAsc(subjectId);
    }

    private String serialize(WithdrawalResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new IllegalStateException("撤回结果序列化失败", ex);
        }
    }

    private WithdrawalResponse deserialize(String body) {
        try {
            return objectMapper.readValue(body, WithdrawalResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("撤回幂等记录反序列化失败", ex);
        }
    }
}
