package com.chris64233.cc.biobank.aliquot;

import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.common.VolumeMath;
import com.chris64233.cc.biobank.aliquot.dto.AliquotResponse;
import com.chris64233.cc.biobank.aliquot.dto.CreateAliquotsRequest;
import com.chris64233.cc.biobank.aliquot.dto.CreateAliquotsResponse;
import com.chris64233.cc.biobank.event.EventType;
import com.chris64233.cc.biobank.event.SampleEvent;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.sample.Sample;
import com.chris64233.cc.biobank.sample.SampleRepository;
import com.chris64233.cc.biobank.sample.SampleStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AliquotService {

    private final SampleRepository sampleRepository;
    private final AliquotRepository aliquotRepository;
    private final SampleEventRepository eventRepository;

    public AliquotService(SampleRepository sampleRepository, AliquotRepository aliquotRepository,
            SampleEventRepository eventRepository) {
        this.sampleRepository = sampleRepository;
        this.aliquotRepository = aliquotRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * 分装与母样本扣减在同一事务中完成，任一子项校验失败则整体回滚。
     */
    @Transactional
    public CreateAliquotsResponse createAliquots(Long sampleId, CreateAliquotsRequest request) {
        Sample sample = sampleRepository.findByIdForUpdate(sampleId)
                .orElseThrow(() -> ApiException.notFound("样本不存在: " + sampleId));

        if (sample.getStatus() != SampleStatus.AVAILABLE) {
            throw new ApiException(ErrorCode.SAMPLE_FROZEN,
                    "样本当前状态为 " + sample.getStatus() + "，不可分装: " + sampleId);
        }

        List<BigDecimal> volumes = resolveVolumes(request);
        BigDecimal loss = VolumeMath.normalize(
                request.lossVolume() != null ? request.lossVolume() : BigDecimal.ZERO);
        if (VolumeMath.isNegative(loss)) {
            throw ApiException.validation("损耗体积不得为负");
        }

        BigDecimal total = loss;
        for (BigDecimal volume : volumes) {
            total = total.add(volume);
        }

        BigDecimal available = sample.getRemainingVolume().subtract(sample.getReservedVolume());
        if (total.compareTo(available) > 0) {
            throw ApiException.insufficientStock(
                    "子样本体积与损耗之和超过母样本可用体积: 需要 " + total + ", 可用 " + available);
        }

        List<AliquotResponse> created = new ArrayList<>();
        for (BigDecimal volume : volumes) {
            Aliquot aliquot = aliquotRepository.save(new Aliquot(sample, volume));
            sample.deduct(volume);
            eventRepository.save(new SampleEvent(EventType.ALIQUOT_CREATED, sample.getId(),
                    aliquot.getId(), volume.negate(), sample.getRemainingVolume(),
                    "分装子样本 #" + aliquot.getId()));
            created.add(AliquotResponse.from(aliquot));
        }
        if (loss.signum() > 0) {
            sample.deduct(loss);
            eventRepository.save(new SampleEvent(EventType.ALIQUOT_LOSS, sample.getId(), null,
                    loss.negate(), sample.getRemainingVolume(), "分装损耗"));
        }
        sampleRepository.save(sample);

        return new CreateAliquotsResponse(sample.getId(), created, loss,
                sample.getRemainingVolume());
    }

    private List<BigDecimal> resolveVolumes(CreateAliquotsRequest request) {
        boolean equalMode = request.count() != null || request.volumePerAliquot() != null;
        boolean listMode = request.volumes() != null && !request.volumes().isEmpty();
        if (equalMode == listMode) {
            throw ApiException.validation("必须且只能使用一种分装模式: 等分(count + volumePerAliquot) 或指定体积(volumes)");
        }

        List<BigDecimal> volumes = new ArrayList<>();
        if (equalMode) {
            if (request.count() == null || request.volumePerAliquot() == null) {
                throw ApiException.validation("等分模式需要同时提供 count 和 volumePerAliquot");
            }
            if (request.count() < 1) {
                throw ApiException.validation("分装数量必须大于等于 1");
            }
            BigDecimal each = VolumeMath.normalize(request.volumePerAliquot());
            if (!VolumeMath.isPositive(each)) {
                throw ApiException.validation("每份子样本体积必须大于零");
            }
            for (int i = 0; i < request.count(); i++) {
                volumes.add(each);
            }
        } else {
            for (int i = 0; i < request.volumes().size(); i++) {
                BigDecimal volume = VolumeMath.normalize(request.volumes().get(i));
                if (!VolumeMath.isPositive(volume)) {
                    throw ApiException.validation("第 " + (i + 1) + " 个子样本体积必须大于零");
                }
                volumes.add(volume);
            }
        }
        return volumes;
    }

    @Transactional(readOnly = true)
    public Aliquot getById(Long id) {
        return aliquotRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("子样本不存在: " + id));
    }
}
