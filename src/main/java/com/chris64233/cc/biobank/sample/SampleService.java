package com.chris64233.cc.biobank.sample;

import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.common.VolumeMath;
import com.chris64233.cc.biobank.consent.ConsentPolicy;
import com.chris64233.cc.biobank.consent.ConsentVersion;
import com.chris64233.cc.biobank.consent.Subject;
import com.chris64233.cc.biobank.event.EventType;
import com.chris64233.cc.biobank.event.SampleEvent;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.sample.dto.ReceiveSampleRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleService {

    private final SampleRepository sampleRepository;
    private final SampleEventRepository eventRepository;
    private final ConsentPolicy consentPolicy;

    public SampleService(SampleRepository sampleRepository, SampleEventRepository eventRepository,
            ConsentPolicy consentPolicy) {
        this.sampleRepository = sampleRepository;
        this.eventRepository = eventRepository;
        this.consentPolicy = consentPolicy;
    }

    @Transactional
    public Sample receive(ReceiveSampleRequest request) {
        BigDecimal initialVolume = VolumeMath.normalize(request.initialVolume());
        BigDecimal reservedVolume = VolumeMath.normalize(request.reservedVolume());

        if (!VolumeMath.isPositive(initialVolume)) {
            throw ApiException.validation("初始体积必须大于零");
        }
        if (VolumeMath.isNegative(reservedVolume)) {
            throw ApiException.validation("保留体积不得为负");
        }
        if (reservedVolume.compareTo(initialVolume) >= 0) {
            throw ApiException.validation("保留体积必须小于初始体积");
        }
        if (sampleRepository.existsByExternalId(request.externalId())) {
            throw new ApiException(ErrorCode.DUPLICATE_EXTERNAL_ID,
                    "外部样本号已存在: " + request.externalId());
        }

        Subject subject = consentPolicy.requireSubjectByCode(request.subjectCode());
        // 接收即保藏，必须存在支持研究保藏、当前有效的同意版本；锁行校验，与撤回互斥。
        ConsentVersion consent = consentPolicy.requireValidConsent(subject.getId(),
                request.consentVersion(), ReceiveSampleRequest.STORAGE_PURPOSE);

        Instant receivedAt = request.receivedAt() != null ? request.receivedAt() : Instant.now();
        Sample sample = new Sample(request.externalId(), subject.getId(), consent.getId(),
                request.sampleType(), initialVolume, reservedVolume,
                request.storageLocation(), receivedAt);
        try {
            sample = sampleRepository.saveAndFlush(sample);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.DUPLICATE_EXTERNAL_ID,
                    "外部样本号已存在: " + request.externalId());
        }

        eventRepository.save(new SampleEvent(EventType.SAMPLE_RECEIVED, sample.getId(), null,
                initialVolume, sample.getRemainingVolume(),
                "外部样本号=" + sample.getExternalId()
                        + ";受试者=" + subject.getId()
                        + ";同意版本=" + consent.getVersion()));
        return sample;
    }

    @Transactional(readOnly = true)
    public Sample getById(Long id) {
        return sampleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("样本不存在: " + id));
    }
}
