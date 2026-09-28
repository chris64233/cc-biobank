package com.chris64233.cc.biobank.subject;

import com.chris64233.cc.biobank.consent.dto.SubjectLineageResponse;
import com.chris64233.cc.biobank.disposition.dto.DispositionResponse;
import com.chris64233.cc.biobank.issue.dto.IssueResponse;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawalResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subjects/{subjectId}")
public class SubjectQueryController {

    private final SubjectQueryService subjectQueryService;

    public SubjectQueryController(SubjectQueryService subjectQueryService) {
        this.subjectQueryService = subjectQueryService;
    }

    /** 受试者样本谱系：同意版本、原始样本、后代分装、全部事件。 */
    @GetMapping("/lineage")
    public SubjectLineageResponse lineage(@PathVariable Long subjectId) {
        return subjectQueryService.lineage(subjectId);
    }

    /** 撤回与冻结范围历史（按时间顺序）。 */
    @GetMapping("/freezes")
    public List<WithdrawalResponse> freezes(@PathVariable Long subjectId) {
        return subjectQueryService.freezes(subjectId);
    }

    /** 历史领用（含领用当时的同意快照）。 */
    @GetMapping("/issues")
    public List<IssueResponse> issues(@PathVariable Long subjectId) {
        return subjectQueryService.issues(subjectId);
    }

    /** 处置进度（每一批处置的对象、动作与处置后台账）。 */
    @GetMapping("/dispositions")
    public List<DispositionResponse> dispositions(@PathVariable Long subjectId) {
        return subjectQueryService.dispositions(subjectId);
    }
}
