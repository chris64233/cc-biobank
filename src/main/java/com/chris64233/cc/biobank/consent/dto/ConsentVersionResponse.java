package com.chris64233.cc.biobank.consent.dto;

import com.chris64233.cc.biobank.consent.ConsentVersion;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record ConsentVersionResponse(
        Long id,
        Long subjectId,
        String subjectCode,
        String version,
        List<String> allowedPurposes,
        Instant validFrom,
        Instant validUntil,
        Instant withdrawnAt,
        Instant createdAt) {

    public static ConsentVersionResponse from(ConsentVersion consent) {
        List<String> purposes = Arrays.stream(consent.getAllowedPurposes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return new ConsentVersionResponse(consent.getId(), consent.getSubject().getId(),
                consent.getSubject().getSubjectCode(), consent.getVersion(), purposes,
                consent.getValidFrom(), consent.getValidUntil(), consent.getWithdrawnAt(),
                consent.getCreatedAt());
    }
}
