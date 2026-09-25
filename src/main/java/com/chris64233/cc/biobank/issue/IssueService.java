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
    private final ObjectMapper objectMapper;

    public IssueService(AliquotRepository aliquotRepository,
            IssueRecordRepository issueRecordRepository, SampleEventRepository eventRepository,
            ObjectMapper objectMapper) {
        this.aliquotRepository = aliquotRepository;
        this.issueRecordRepository = issueRecordRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IssueResponse issue(IssueRequest request) {
        Map<Long, BigDecimal> merged = mergeAndValidate(request);
        String requestHash = canonicalHash(request.idempotencyKey(), merged);

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

        List<Long> ids = new ArrayList<>(merged.keySet());
        Map<Long, Aliquot> locked = aliquotRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(Aliquot::getId, Function.identity()));
        for (Long id : ids) {
            if (!locked.containsKey(id)) {
                throw ApiException.notFound("子样本不存在: " + id);
            }
        }

        List<IssueResponse.Item> responseItems = new ArrayList<>();
        for (Long id : ids) {
            Aliquot aliquot = locked.get(id);
            BigDecimal volume = merged.get(id);
            if (aliquot.getStatus() == AliquotStatus.DEPLETED) {
                throw ApiException.insufficientStock("子样本已耗尽，不可继续领用: " + id);
            }
            if (aliquot.getRemainingVolume().compareTo(volume) < 0) {
                throw ApiException.insufficientStock(
                        "子样本 " + id + " 库存不足: 需要 " + volume + ", 剩余 "
                                + aliquot.getRemainingVolume());
            }
            aliquot.deduct(volume);
            eventRepository.save(new SampleEvent(EventType.ALIQUOT_ISSUED,
                    aliquot.getSample().getId(), aliquot.getId(), volume.negate(),
                    aliquot.getRemainingVolume(), "幂等键=" + request.idempotencyKey()));
            responseItems.add(new IssueResponse.Item(aliquot.getId(), volume,
                    aliquot.getRemainingVolume(), aliquot.getStatus()));
        }
        aliquotRepository.saveAll(locked.values());

        IssueResponse response =
                new IssueResponse(request.idempotencyKey(), responseItems, Instant.now());
        try {
            issueRecordRepository.saveAndFlush(new IssueRecord(request.idempotencyKey(),
                    requestHash, serialize(response)));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "幂等键并发冲突: " + request.idempotencyKey());
        }
        return response;
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

    private String canonicalHash(String key, Map<Long, BigDecimal> merged) {
        StringBuilder builder = new StringBuilder(key).append('|');
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
