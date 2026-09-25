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

    private String issueBody(String key, long aliquotId, String volume) {
        return """
                {"idempotencyKey":"%s","items":[{"aliquotId":%d,"volume":%s}]}
                """.formatted(key, aliquotId, volume);
    }

    @Test
    void issueFromMultipleAliquots() throws Exception {
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30,20]}");
        String body = """
                {"idempotencyKey":"%s","items":[
                  {"aliquotId":%d,"volume":10},{"aliquotId":%d,"volume":5}]}
                """.formatted(UUID.randomUUID(), ids.get(0), ids.get(1));
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].remainingVolume").value(20.0))
                .andExpect(jsonPath("$.items[1].remainingVolume").value(15.0))
                .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"));
    }

    @Test
    void replaySameKeyAndContentReturnsOriginalResult() throws Exception {
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        String key = UUID.randomUUID().toString();
        String body = issueBody(key, ids.get(0), "10");

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
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(key, ids.get(0), "10")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(key, ids.get(0), "5")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void issueInsufficientStockLeavesNoTrace() throws Exception {
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), ids.get(0), "31")))
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
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), ids.get(0), "30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].remainingVolume").value(0.0))
                .andExpect(jsonPath("$.items[0].status").value("DEPLETED"));

        mockMvc.perform(get("/api/aliquots/{id}", ids.get(0)))
                .andExpect(jsonPath("$.status").value("DEPLETED"));

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), ids.get(0), "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void issueMissingAliquotReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), 999999999L, "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void issueRejectsNonPositiveVolume() throws Exception {
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), ids.get(0), "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void issueRejectsEmptyItems() throws Exception {
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"k\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
