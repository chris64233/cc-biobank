package com.chris64233.cc.biobank;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsentApiTests extends AbstractApiTest {

    private String registerSubject() throws Exception {
        String code = "SUBJ-" + UUID.randomUUID();
        String body = "{\"subjectCode\":\"%s\"}".formatted(code);
        mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return code;
    }

    @Test
    void duplicateConsentVersionConflicts() throws Exception {
        String code = createSubjectWithConsent();
        String body = """
                {"subjectCode":"%s","version":"v1",
                 "allowedPurposes":["RESEARCH_STORAGE"]}
                """.formatted(code);
        mockMvc.perform(post("/api/subjects/consents")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CONSENT"));
    }

    @Test
    void expiredConsentCannotSupportNewIssue() throws Exception {
        String code = registerSubject();
        String validFrom = Instant.now().minus(10, ChronoUnit.DAYS).toString();
        String validUntil = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        String consentBody = """
                {"subjectCode":"%s","version":"v-old",
                 "allowedPurposes":["RESEARCH_STORAGE","RESEARCH_USE"],
                 "validFrom":"%s","validUntil":"%s"}
                """.formatted(code, validFrom, validUntil);
        mockMvc.perform(post("/api/subjects/consents")
                        .contentType(MediaType.APPLICATION_JSON).content(consentBody))
                .andExpect(status().isCreated());

        // 过期同意不能支持新的接收。
        String sampleBody = """
                {"externalId":"%s","subjectCode":"%s","consentVersion":"v-old",
                 "sampleType":"BLOOD","initialVolume":10,"reservedVolume":0,
                 "storageLocation":"X"}
                """.formatted(uniqueExternalId(), code);
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(sampleBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_NOT_VALID"));
    }

    @Test
    void futureConsentCannotSupportReceive() throws Exception {
        String code = registerSubject();
        String validFrom = Instant.now().plus(10, ChronoUnit.DAYS).toString();
        String consentBody = """
                {"subjectCode":"%s","version":"v-future",
                 "allowedPurposes":["RESEARCH_STORAGE"],
                 "validFrom":"%s"}
                """.formatted(code, validFrom);
        mockMvc.perform(post("/api/subjects/consents")
                        .contentType(MediaType.APPLICATION_JSON).content(consentBody))
                .andExpect(status().isCreated());

        String sampleBody = """
                {"externalId":"%s","subjectCode":"%s",
                 "consentVersion":"v-future","sampleType":"BLOOD",
                 "initialVolume":10,"reservedVolume":0,"storageLocation":"X"}
                """.formatted(uniqueExternalId(), code);
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(sampleBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_NOT_VALID"));
    }

    @Test
    void registerConsentForUnknownSubjectReturnsNotFound() throws Exception {
        String body = """
                {"subjectCode":"SUBJ-NONE","version":"v1",
                 "allowedPurposes":["RESEARCH_STORAGE"]}
                """;
        mockMvc.perform(post("/api/subjects/consents")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
