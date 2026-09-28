package com.chris64233.cc.biobank.consent;

import com.chris64233.cc.biobank.consent.dto.ConsentVersionResponse;
import com.chris64233.cc.biobank.consent.dto.CreateSubjectRequest;
import com.chris64233.cc.biobank.consent.dto.RegisterConsentRequest;
import com.chris64233.cc.biobank.consent.dto.SubjectResponse;
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

    private final ConsentService consentService;

    public SubjectController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> create(
            @Valid @RequestBody CreateSubjectRequest request) {
        Subject subject = consentService.createSubject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SubjectResponse.from(subject));
    }

    @GetMapping("/{id}")
    public SubjectResponse get(@PathVariable Long id) {
        return SubjectResponse.from(consentService.getSubject(id));
    }

    @GetMapping("/{id}/consents")
    public List<ConsentVersionResponse> consents(@PathVariable Long id) {
        return consentService.listConsents(id);
    }

    @PostMapping("/consents")
    public ResponseEntity<ConsentVersionResponse> registerConsent(
            @Valid @RequestBody RegisterConsentRequest request) {
        ConsentVersion consent = consentService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConsentVersionResponse.from(consent));
    }
}
