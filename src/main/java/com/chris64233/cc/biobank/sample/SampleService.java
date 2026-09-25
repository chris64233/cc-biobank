package com.chris64233.cc.biobank.sample;

import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.common.VolumeMath;
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

    public SampleService(SampleRepository sampleRepository, SampleEventRepository eventRepository) {
        this.sampleRepository = sampleRepository;
        this.eventRepository = eventRepository;
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

        Instant receivedAt = request.receivedAt() != null ? request.receivedAt() : Instant.now();
        Sample sample = new Sample(request.externalId(), request.sampleType(), initialVolume,
                reservedVolume, request.storageLocation(), receivedAt);
        try {
            sample = sampleRepository.saveAndFlush(sample);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.DUPLICATE_EXTERNAL_ID,
                    "外部样本号已存在: " + request.externalId());
        }

        eventRepository.save(new SampleEvent(EventType.SAMPLE_RECEIVED, sample.getId(), null,
                initialVolume, sample.getRemainingVolume(),
                "外部样本号=" + sample.getExternalId()));
        return sample;
    }

    @Transactional(readOnly = true)
    public Sample getById(Long id) {
        return sampleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("样本不存在: " + id));
    }
}
