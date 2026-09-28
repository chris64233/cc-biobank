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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LineageApiTests extends AbstractApiTest {

    @Test
    void lineageContainsOrderedEventsAndCurrentVolumes() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "10");
        List<Long> ids = createAliquots(sampleId,
                "{\"volumes\":[30,20],\"lossVolume\":5}");
        String issueBody = """
                {"idempotencyKey":"%s","subjectCode":"%s","purpose":"RESEARCH_USE",
                 "consentVersion":"v1",
                 "items":[{"aliquotId":%d,"volume":12}]}
                """.formatted(UUID.randomUUID(), code, ids.get(0));
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/samples/{id}/lineage", sampleId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode lineage = readTree(result);

        assertThat(decimal(lineage.get("sample"), "remainingVolume"))
                .isEqualByComparingTo("45.000");
        assertThat(lineage.get("aliquots")).hasSize(2);

        JsonNode events = lineage.get("events");
        assertThat(events).hasSize(5);
        assertThat(events.get(0).get("eventType").asString()).isEqualTo("SAMPLE_RECEIVED");
        assertThat(events.get(1).get("eventType").asString()).isEqualTo("ALIQUOT_CREATED");
        assertThat(events.get(2).get("eventType").asString()).isEqualTo("ALIQUOT_CREATED");
        assertThat(events.get(3).get("eventType").asString()).isEqualTo("ALIQUOT_LOSS");
        assertThat(events.get(4).get("eventType").asString()).isEqualTo("ALIQUOT_ISSUED");
        for (int i = 1; i < events.size(); i++) {
            assertThat(events.get(i).get("id").asLong())
                    .isGreaterThan(events.get(i - 1).get("id").asLong());
        }
        assertThat(events.get(4).get("aliquotId").asLong()).isEqualTo(ids.get(0));
        assertThat(events.get(4).get("volumeChange").decimalValue())
                .isEqualByComparingTo("-12.000");
        assertThat(events.get(4).get("resultingVolume").decimalValue())
                .isEqualByComparingTo("18.000");
    }

    @Test
    void failedIssueWritesNoEvents() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");

        MvcResult before = mockMvc.perform(get("/api/samples/{id}/events", sampleId))
                .andExpect(status().isOk())
                .andReturn();
        int eventCount = readTree(before).size();

        String issueBody = """
                {"idempotencyKey":"%s","subjectCode":"%s","purpose":"RESEARCH_USE",
                 "consentVersion":"v1",
                 "items":[{"aliquotId":%d,"volume":99}]}
                """.formatted(UUID.randomUUID(), code, ids.get(0));
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody))
                .andExpect(status().isUnprocessableEntity());

        MvcResult after = mockMvc.perform(get("/api/samples/{id}/events", sampleId))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(readTree(after).size()).isEqualTo(eventCount);
    }
}
