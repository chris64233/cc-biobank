package com.chris64233.cc.biobank;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AliquotApiTests extends AbstractApiTest {

    @Test
    void createEqualSplitAliquotsWithLoss() throws Exception {
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        MvcResult result = mockMvc.perform(post("/api/samples/{id}/aliquots", sampleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":3,\"volumePerAliquot\":20,\"lossVolume\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aliquots.length()").value(3))
                .andExpect(jsonPath("$.lossVolume").value(5.0))
                .andExpect(jsonPath("$.sampleRemainingVolume").value(35.0))
                .andReturn();
        JsonNode aliquots = readTree(result).get("aliquots");
        for (JsonNode aliquot : aliquots) {
            assertThat(aliquot.get("initialVolume").decimalValue())
                    .isEqualByComparingTo("20.000");
            assertThat(aliquot.get("status").asString()).isEqualTo("AVAILABLE");
        }
    }

    @Test
    void createExplicitVolumeAliquots() throws Exception {
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[10,15.5]}");
        assertThat(ids).hasSize(2);
        mockMvc.perform(get("/api/samples/{id}", sampleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingVolume").value(74.5));
    }

    @Test
    void createRejectsExceedingAvailableVolume() throws Exception {
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        mockMvc.perform(post("/api/samples/{id}/aliquots", sampleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volumes\":[80,10],\"lossVolume\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        mockMvc.perform(get("/api/samples/{id}", sampleId))
                .andExpect(jsonPath("$.remainingVolume").value(100.0));
        mockMvc.perform(get("/api/samples/{id}/lineage", sampleId))
                .andExpect(jsonPath("$.aliquots.length()").value(0))
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    void createRollsBackWhenAnyItemInvalid() throws Exception {
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        mockMvc.perform(post("/api/samples/{id}/aliquots", sampleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volumes\":[10,-5]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/samples/{id}/lineage", sampleId))
                .andExpect(jsonPath("$.aliquots.length()").value(0))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.sample.remainingVolume").value(100.0));
    }

    @Test
    void createRejectsAmbiguousModes() throws Exception {
        long sampleId = receiveSample(uniqueExternalId(), "100", "10");
        mockMvc.perform(post("/api/samples/{id}/aliquots", sampleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":2,\"volumePerAliquot\":10,\"volumes\":[5]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createOnMissingSampleReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/samples/{id}/aliquots", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1,\"volumePerAliquot\":10}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
