package com.chris64233.cc.biobank;

import java.util.List;
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

class IssueApiTests extends AbstractApiTest {

    @Test
    void issueFromMultipleAliquots() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30,20]}");
        String body = """
                {"idempotencyKey":"%s","subjectCode":"%s","purpose":"%s","consentVersion":"v1",
                 "items":[
                  {"aliquotId":%d,"volume":10},{"aliquotId":%d,"volume":5}]}
                """.formatted(UUID.randomUUID(), code, PURPOSE_USE, ids.get(0), ids.get(1));
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].remainingVolume").value(20.0))
                .andExpect(jsonPath("$.items[1].remainingVolume").value(15.0))
                .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.consentSnapshot.version").value("v1"))
                .andExpect(jsonPath("$.consentSnapshot.purpose").value(PURPOSE_USE));
    }

    @Test
    void replaySameKeyAndContentReturnsOriginalResult() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        String key = UUID.randomUUID().toString();
        String body = issueBody(key, code, PURPOSE_USE, CONSENT_V1, ids.get(0), "10");

        MvcResult first = mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult replay = mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstNode = readTree(first);
        JsonNode replayNode = readTree(replay);
        assertThat(replayNode).isEqualTo(firstNode);

        mockMvc.perform(get("/api/aliquots/{id}", ids.get(0)))
                .andExpect(jsonPath("$.remainingVolume").value(20.0));
    }

    @Test
    void sameKeyWithDifferentContentReturnsConflict() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(key, code, PURPOSE_USE, CONSENT_V1, ids.get(0), "10")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(key, code, PURPOSE_USE, CONSENT_V1, ids.get(0), "5")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void issueInsufficientStockLeavesNoTrace() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(0), "31")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mockMvc.perform(get("/api/aliquots/{id}", ids.get(0)))
                .andExpect(jsonPath("$.remainingVolume").value(30.0))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mockMvc.perform(get("/api/aliquots/{id}/events", ids.get(0)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void depletedAliquotCannotBeIssuedAgain() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(0), "30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].remainingVolume").value(0.0))
                .andExpect(jsonPath("$.items[0].status").value("DEPLETED"));

        mockMvc.perform(get("/api/aliquots/{id}", ids.get(0)))
                .andExpect(jsonPath("$.status").value("DEPLETED"));

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(0), "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void issueMissingAliquotReturnsNotFound() throws Exception {
        String code = createSubjectWithConsent();
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, 999999999L, "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void issueRejectsNonPositiveVolume() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(0), "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void issueRejectsEmptyItems() throws Exception {
        String body = """
                {"idempotencyKey":"k","subjectCode":"x","purpose":"p",
                 "consentVersion":"v1","items":[]}
                """;
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void issueRejectsPurposeMismatch() throws Exception {
        // 同意仅允许研究保藏，不允许本次领用用途。
        String code = createSubjectWithConsent(CONSENT_V1, "[\"" + PURPOSE_STORAGE + "\"]");
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(0), "10")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_NOT_VALID"));
    }

    @Test
    void issueAcrossSubjectsRejected() throws Exception {
        String codeA = createSubjectWithConsent();
        long sampleA = receiveSample(uniqueExternalId(), codeA, CONSENT_V1, "100", "10");
        List<Long> idsA = createAliquots(sampleA, "{\"volumes\":[30]}");
        String codeB = createSubjectWithConsent();

        // 用受试者 B 的同意去领用受试者 A 的分装。
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), codeB, PURPOSE_USE,
                                CONSENT_V1, idsA.get(0), "10")))
                .andExpect(status().isBadRequest());
    }
}
