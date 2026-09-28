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

class SubjectQueryApiTests extends AbstractApiTest {

    @Test
    void subjectLineageAggregatesSamplesAliquotsAndEvents() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30,20]}");
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(0), "10")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withdrawalNo\":\"%s\",\"subjectCode\":\"%s\"}"
                                .formatted("WD-" + UUID.randomUUID(), code)))
                .andExpect(status().isCreated());
        String dispositionBody = """
                {"dispositionNo":"%s","subjectCode":"%s","action":"DESTROY",
                 "items":[{"targetType":"ALIQUOT","targetId":%d}]}
                """.formatted("DP-" + UUID.randomUUID(), code, ids.get(0));
        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody))
                .andExpect(status().isCreated());

        // 受试者 id 通过样本响应获得。
        MvcResult sampleResult = mockMvc.perform(get("/api/samples/{id}", sampleId))
                .andExpect(status().isOk()).andReturn();
        long subjectId = readTree(sampleResult).get("subjectId").asLong();

        MvcResult lineageResult = mockMvc.perform(get("/api/subjects/{id}/lineage", subjectId))
                .andExpect(status().isOk()).andReturn();
        JsonNode lineage = readTree(lineageResult);
        assertThat(lineage.get("subject").get("subjectCode").asString()).isEqualTo(code);
        assertThat(lineage.get("consents")).hasSize(1);
        assertThat(lineage.get("samples")).hasSize(1);
        assertThat(lineage.get("aliquots")).hasSize(2);
        JsonNode events = lineage.get("events");
        assertThat(events.get(0).get("eventType").asString()).isEqualTo("SAMPLE_RECEIVED");
        java.util.List<String> eventTypes = new java.util.ArrayList<>();
        for (JsonNode event : events) {
            eventTypes.add(event.get("eventType").asString());
        }
        assertThat(eventTypes).contains("ALIQUOT_ISSUED", "SAMPLE_FROZEN", "ALIQUOT_FROZEN",
                "ALIQUOT_DISPOSED");

        mockMvc.perform(get("/api/subjects/{id}/freezes", subjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].frozenSampleIds.length()").value(1))
                .andExpect(jsonPath("$[0].frozenAliquotIds.length()").value(2));

        mockMvc.perform(get("/api/subjects/{id}/issues", subjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].consentSnapshot.version").value("v1"))
                .andExpect(jsonPath("$[0].purpose").value(PURPOSE_USE));

        mockMvc.perform(get("/api/subjects/{id}/dispositions", subjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("DESTROY"))
                .andExpect(jsonPath("$[0].items.length()").value(1));
    }

    @Test
    void queryUnknownSubjectReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/subjects/{id}/lineage", 999999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/subjects/{id}/issues", 999999999L))
                .andExpect(status().isNotFound());
    }
}
