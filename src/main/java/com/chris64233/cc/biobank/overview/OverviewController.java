package com.chris64233.cc.biobank.overview;

import com.chris64233.cc.biobank.overview.dto.SubjectOverviewResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subjects/{subjectCode}")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/overview")
    public SubjectOverviewResponse overview(@PathVariable String subjectCode) {
        return overviewService.overview(subjectCode);
    }
}
