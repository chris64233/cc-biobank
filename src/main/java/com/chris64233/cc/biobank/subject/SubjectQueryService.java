package com.chris64233.cc.biobank.subject;

import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.dto.AliquotResponse;
import com.chris64233.cc.biobank.consent.ConsentService;
import com.chris64233.cc.biobank.consent.ConsentVersionRepository;
import com.chris64233.cc.biobank.consent.Subject;
import com.chris64233.cc.biobank.consent.dto.ConsentVersionResponse;
import com.chris64233.cc.biobank.consent.dto.SubjectLineageResponse;
import com.chris64233.cc.biobank.consent.dto.SubjectResponse;
import com.chris64233.cc.biobank.disposition.DispositionRecordRepository;
import com.chris64233.cc.biobank.disposition.dto.DispositionResponse;
import com.chris64233.cc.biobank.event.EventResponse;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.issue.IssueRecordRepository;
import com.chris64233.cc.biobank.issue.dto.IssueResponse;
import com.chris64233.cc.biobank.sample.SampleRepository;
import com.chris64233.cc.biobank.sample.dto.SampleResponse;
import com.chris64233.cc.biobank.withdrawal.WithdrawalRecordRepository;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawalResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 受试者维度只读查询：样本谱系、撤回冻结范围、历史领用、处置进度。
 * 领用/撤回/处置返回的均为业务发生当时保存的响应快照。
 */
@Service
public class SubjectQueryService {

    private final ConsentService consentService;
    private final ConsentVersionRepository consentVersionRepository;
    private final SampleRepository sampleRepository;
    private final AliquotRepository aliquotRepository;
    private final SampleEventRepository eventRepository;
    private final IssueRecordRepository issueRecordRepository;
    private final WithdrawalRecordRepository withdrawalRecordRepository;
    private final DispositionRecordRepository dispositionRecordRepository;
    private final ObjectMapper objectMapper;

    public SubjectQueryService(ConsentService consentService,
            ConsentVersionRepository consentVersionRepository, SampleRepository sampleRepository,
            AliquotRepository aliquotRepository, SampleEventRepository eventRepository,
            IssueRecordRepository issueRecordRepository,
            WithdrawalRecordRepository withdrawalRecordRepository,
            DispositionRecordRepository dispositionRecordRepository, ObjectMapper objectMapper) {
        this.consentService = consentService;
        this.consentVersionRepository = consentVersionRepository;
        this.sampleRepository = sampleRepository;
        this.aliquotRepository = aliquotRepository;
        this.eventRepository = eventRepository;
        this.issueRecordRepository = issueRecordRepository;
        this.withdrawalRecordRepository = withdrawalRecordRepository;
        this.dispositionRecordRepository = dispositionRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SubjectLineageResponse lineage(Long subjectId) {
        Subject subject = consentService.getSubject(subjectId);
        List<ConsentVersionResponse> consents =
                consentVersionRepository.findBySubjectIdOrderByIdAsc(subjectId).stream()
                        .map(ConsentVersionResponse::from)
                        .toList();
        List<SampleResponse> samples =
                sampleRepository.findBySubjectIdOrderByIdAsc(subjectId).stream()
                        .map(SampleResponse::from)
                        .toList();
        List<AliquotResponse> aliquots =
                aliquotRepository.findBySubjectIdOrderByIdAsc(subjectId).stream()
                        .map(AliquotResponse::from)
                        .toList();
        List<Long> sampleIds = samples.stream().map(SampleResponse::id).toList();
        List<EventResponse> events = sampleIds.isEmpty() ? List.of()
                : eventRepository.findBySampleIdInOrderByIdAsc(sampleIds).stream()
                        .map(EventResponse::from)
                        .toList();
        return new SubjectLineageResponse(SubjectResponse.from(subject), consents, samples,
                aliquots, events);
    }

    @Transactional(readOnly = true)
    public List<WithdrawalResponse> freezes(Long subjectId) {
        consentService.getSubject(subjectId);
        return withdrawalRecordRepository.findBySubjectIdOrderByIdAsc(subjectId).stream()
                .map(r -> read(r.getResponseBody(), WithdrawalResponse.class))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IssueResponse> issues(Long subjectId) {
        consentService.getSubject(subjectId);
        return issueRecordRepository.findBySubjectIdOrderByIdAsc(subjectId).stream()
                .map(r -> read(r.getResponseBody(), IssueResponse.class))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DispositionResponse> dispositions(Long subjectId) {
        consentService.getSubject(subjectId);
        return dispositionRecordRepository.findBySubjectIdOrderByIdAsc(subjectId).stream()
                .map(r -> read(r.getResponseBody(), DispositionResponse.class))
                .toList();
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception ex) {
            throw new IllegalStateException("历史记录反序列化失败", ex);
        }
    }
}
