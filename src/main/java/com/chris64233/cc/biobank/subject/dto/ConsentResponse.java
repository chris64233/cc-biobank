package com.chris64233.cc.biobank.subject.dto;

import com.chris64233.cc.biobank.subject.Consent;
import java.time.Instant;
import java.util.SortedSet;
import java.util.TreeSet;

public record ConsentResponse(
        Long id,
        String subjectCode,
        String versionCode,
        SortedSet<String> allowedPurposes,
        Instant validFrom,
        Instant validUntil,
        boolean withdrawn,
        Instant withdrawnAt,
        Instant createdAt) {

    public static ConsentResponse from(Consent consent) {
        return new ConsentResponse(consent.getId(), consent.getSubject().getSubjectCode(),
                consent.getVersionCode(), new TreeSet<>(consent.getAllowedPurposes()),
                consent.getValidFrom(), consent.getValidUntil(), consent.isWithdrawn(),
                consent.getWithdrawnAt(), consent.getCreatedAt());
    }
}
