package com.chris64233.cc.biobank;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsentIssueApiTests extends AbstractApiTest {

    private long oneAliquot(SubjectSetup subject) throws Exception {
        long sampleId = receiveSample(subject, uniqueExternalId(), "100", "0");
        return createAliquots(sampleId, "{\"volumes\":[50]}").get(0);
    }

    @Test
    void issueStoresConsentSnapshot() throws Exception {
        SubjectSetup subject = setupSubject();
        long aliquotId = oneAliquot(subject);

        MvcResult result = mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), subject, aliquotId, "10")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = readTree(result);
        assertThat(node.get("consentVersionCode").asString()).isEqualTo("v1");
        assertThat(node.get("purpose").asString()).isEqualTo(PURPOSE_RESEARCH);
        assertThat(node.get("subjectCode").asString()).isEqualTo(subject.code());

        mockMvc.perform(get("/api/aliquots/{id}", aliquotId))
                .andExpect(jsonPath("$.consentVersionCode").value("v1"))
                .andExpect(jsonPath("$.subjectCode").value(subject.code()));
    }

    @Test
    void issueWithUnknownConsentVersionRejected() throws Exception {
        SubjectSetup subject = setupSubject();
        long aliquotId = oneAliquot(subject);
        String body = """
                {"idempotencyKey":"%s","subjectCode":"%s","consentVersionCode":"v999",
                 "purpose":"%s","items":[{"aliquotId":%d,"volume":1}]}
                """.formatted(UUID.randomUUID(), subject.code(), PURPOSE_RESEARCH, aliquotId);
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_INVALID"));

        mockMvc.perform(get("/api/aliquots/{id}", aliquotId))
                .andExpect(jsonPath("$.remainingVolume").value(50.0));
    }

    @Test
    void issueWithPurposeMismatchRejected() throws Exception {
        SubjectSetup subject = setupSubject();
        long aliquotId = oneAliquot(subject);
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), subject, aliquotId, "10",
                                PURPOSE_OTHER)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_INVALID"));

        mockMvc.perform(get("/api/aliquots/{id}", aliquotId))
                .andExpect(jsonPath("$.remainingVolume").value(50.0))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void issueWithExpiredConsentRejected() throws Exception {
        SubjectSetup subject = setupSubject();
        long aliquotId = oneAliquot(subject);
        registerConsent(subject.code(), "v2-expired", Set.of(PURPOSE_RESEARCH),
                Instant.now().minus(1, ChronoUnit.HOURS));

        String body = """
                {"idempotencyKey":"%s","subjectCode":"%s","consentVersionCode":"v2-expired",
                 "purpose":"%s","items":[{"aliquotId":%d,"volume":1}]}
                """.formatted(UUID.randomUUID(), subject.code(), PURPOSE_RESEARCH, aliquotId);
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_INVALID"));
    }

    @Test
    void receiveWithExpiredConsentRejected() throws Exception {
        String code = uniqueCode("SUBJ");
        createSubject(code);
        registerConsent(code, "v-old", Set.of(PURPOSE_RESEARCH),
                Instant.now().minus(1, ChronoUnit.HOURS));
        String body = """
                {"subjectCode":"%s","consentVersionCode":"v-old","externalId":"%s",
                 "sampleType":"BLOOD","initialVolume":100,"reservedVolume":0,
                 "storageLocation":"FRIDGE-A1"}
                """.formatted(code, uniqueExternalId());
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_INVALID"));
    }

    @Test
    void issueFromAnotherSubjectsAliquotRejected() throws Exception {
        SubjectSetup owner = setupSubject();
        SubjectSetup other = setupSubject();
        long aliquotId = oneAliquot(owner);

        String body = """
                {"idempotencyKey":"%s","subjectCode":"%s","consentVersionCode":"%s",
                 "purpose":"%s","items":[{"aliquotId":%d,"volume":1}]}
                """.formatted(UUID.randomUUID(), other.code(), other.consentVersion(),
                PURPOSE_RESEARCH, aliquotId);
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void completedIssueSurvivesLaterConsentWithdrawal() throws Exception {
        SubjectSetup subject = setupSubject();
        long aliquotId = oneAliquot(subject);
        String key = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(key, subject, aliquotId, "10")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/subjects/{code}/withdrawals", subject.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalKey":"%s","consentVersionCode":null}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());

        // 历史领用按幂等键重放仍返回首次完整结果，不被撤回改写。
        MvcResult replay = mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(key, subject, aliquotId, "10")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = readTree(replay);
        assertThat(node.get("items").get(0).get("issuedVolume").decimalValue())
                .isEqualByComparingTo("10.000");
        assertThat(node.get("consentVersionCode").asString()).isEqualTo("v1");

        // 撤回后新领用被拒绝。
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), subject, aliquotId, "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_INVALID"));
    }
}
