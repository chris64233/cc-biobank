package com.chris64233.cc.biobank.overview.dto;

import com.chris64233.cc.biobank.aliquot.dto.AliquotResponse;
import com.chris64233.cc.biobank.disposal.dto.DisposalResponse;
import com.chris64233.cc.biobank.issue.dto.IssueResponse;
import com.chris64233.cc.biobank.sample.dto.SampleResponse;
import com.chris64233.cc.biobank.subject.dto.ConsentResponse;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawalResponse;
import java.util.List;

/**
 * 受试者维度总览：同意版本、样本谱系、撤回冻结范围、历史领用与处置进度一屏聚合。
 */
public record SubjectOverviewResponse(
        String subjectCode,
        List<ConsentResponse> consents,
        List<SampleLineage> samples,
        List<WithdrawalResponse> withdrawals,
        List<IssueResponse> issues,
        List<DisposalResponse> disposals) {

    public record SampleLineage(
            SampleResponse sample,
            List<AliquotResponse> aliquots) {
    }
}
