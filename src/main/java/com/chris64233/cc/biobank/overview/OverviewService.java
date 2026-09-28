package com.chris64233.cc.biobank.overview;

import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.dto.AliquotResponse;
import com.chris64233.cc.biobank.disposal.DisposalRecord;
import com.chris64233.cc.biobank.disposal.DisposalRecordRepository;
import com.chris64233.cc.biobank.disposal.dto.DisposalResponse;
import com.chris64233.cc.biobank.issue.IssueRecord;
import com.chris64233.cc.biobank.issue.IssueRecordRepository;
import com.chris64233.cc.biobank.issue.dto.IssueResponse;
import com.chris64233.cc.biobank.overview.dto.SubjectOverviewResponse;
import com.chris64233.cc.biobank.sample.Sample;
import com.chris64233.cc.biobank.sample.SampleRepository;
import com.chris64233.cc.biobank.sample.dto.SampleResponse;
import com.chris64233.cc.biobank.subject.Subject;
import com.chris64233.cc.biobank.subject.SubjectService;
import com.chris64233.cc.biobank.subject.dto.ConsentResponse;
import com.chris64233.cc.biobank.withdrawal.WithdrawalRecord;
import com.chris64233.cc.biobank.withdrawal.WithdrawalRecordRepository;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawalResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class OverviewService {

    private final SubjectService subjectService;
    private final SampleRepository sampleRepository;
    private final AliquotRepository aliquotRepository;
    private final WithdrawalRecordRepository withdrawalRecordRepository;
    private final IssueRecordRepository issueRecordRepository;
    private final DisposalRecordRepository disposalRecordRepository;
    private final ObjectMapper objectMapper;

    public OverviewService(SubjectService subjectService, SampleRepository sampleRepository,
            AliquotRepository aliquotRepository,
            WithdrawalRecordRepository withdrawalRecordRepository,
            IssueRecordRepository issueRecordRepository,
            DisposalRecordRepository disposalRecordRepository, ObjectMapper objectMapper) {
        this.subjectService = subjectService;
        this.sampleRepository = sampleRepository;
        this.aliquotRepository = aliquotRepository;
        this.withdrawalRecordRepository = withdrawalRecordRepository;
        this.issueRecordRepository = issueRecordRepository;
        this.disposalRecordRepository = disposalRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SubjectOverviewResponse overview(String subjectCode) {
        Subject subject = subjectService.getByCode(subjectCode);

        List<ConsentResponse> consents = subjectService.listConsents(subject.getId()).stream()
                .map(ConsentResponse::from)
                .toList();

        List<SubjectOverviewResponse.SampleLineage> samples = new ArrayList<>();
        for (Sample sample : sampleRepository.findBySubjectIdOrderByIdAsc(subject.getId())) {
            List<AliquotResponse> aliquots =
                    aliquotRepository.findBySampleIdOrderByIdAsc(sample.getId()).stream()
                            .map(AliquotResponse::from)
                            .toList();
            samples.add(new SubjectOverviewResponse.SampleLineage(
                    SampleResponse.from(sample), aliquots));
        }

        List<WithdrawalResponse> withdrawals = withdrawalRecordRepository
                .findBySubjectCodeOrderByIdAsc(subjectCode).stream()
                .map(this::toWithdrawalResponse)
                .toList();

        List<IssueResponse> issues = issueRecordRepository
                .findBySubjectCodeOrderByIdAsc(subjectCode).stream()
                .map(this::toIssueResponse)
                .toList();

        List<DisposalResponse> disposals = disposalRecordRepository
                .findBySubjectCodeOrderByIdAsc(subjectCode).stream()
                .map(this::toDisposalResponse)
                .toList();

        return new SubjectOverviewResponse(subjectCode, consents, samples, withdrawals, issues,
                disposals);
    }

    private WithdrawalResponse toWithdrawalResponse(WithdrawalRecord record) {
        try {
            return objectMapper.readValue(record.getResponseBody(), WithdrawalResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("撤回记录反序列化失败: " + record.getWithdrawalKey(),
                    ex);
        }
    }

    private IssueResponse toIssueResponse(IssueRecord record) {
        try {
            return objectMapper.readValue(record.getResponseBody(), IssueResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("领用记录反序列化失败: " + record.getIdempotencyKey(),
                    ex);
        }
    }

    private DisposalResponse toDisposalResponse(DisposalRecord record) {
        try {
            return objectMapper.readValue(record.getResponseBody(), DisposalResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("处置记录反序列化失败: " + record.getDisposalKey(),
                    ex);
        }
    }
}
