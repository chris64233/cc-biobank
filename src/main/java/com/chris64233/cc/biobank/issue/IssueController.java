package com.chris64233.cc.biobank.issue;

import com.chris64233.cc.biobank.issue.dto.IssueRequest;
import com.chris64233.cc.biobank.issue.dto.IssueResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping
    public IssueResponse issue(@Valid @RequestBody IssueRequest request) {
        return issueService.issue(request);
    }
}
