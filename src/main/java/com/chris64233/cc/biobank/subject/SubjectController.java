package com.chris64233.cc.biobank.subject;

import com.chris64233.cc.biobank.subject.dto.ConsentResponse;
import com.chris64233.cc.biobank.subject.dto.CreateSubjectRequest;
import com.chris64233.cc.biobank.subject.dto.RegisterConsentRequest;
import com.chris64233.cc.biobank.subject.dto.SubjectResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody CreateSubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.create(request));
    }

    @GetMapping("/{subjectCode}")
    public SubjectResponse get(@PathVariable String subjectCode) {
        return SubjectResponse.from(subjectService.getByCode(subjectCode));
    }

    @PostMapping("/{subjectCode}/consents")
    public ResponseEntity<ConsentResponse> registerConsent(@PathVariable String subjectCode,
            @Valid @RequestBody RegisterConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subjectService.registerConsent(subjectCode, request));
    }

    @GetMapping("/{subjectCode}/consents")
    public List<ConsentResponse> listConsents(@PathVariable String subjectCode) {
        Subject subject = subjectService.getByCode(subjectCode);
        return subjectService.listConsents(subject.getId()).stream()
                .map(ConsentResponse::from)
                .toList();
    }
}
