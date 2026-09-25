package com.chris64233.cc.biobank;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SampleApiTests extends AbstractApiTest {

    @Test
    void receiveSuccess() throws Exception {
        String externalId = uniqueExternalId();
        String body = """
                {"externalId":"%s","sampleType":"PLASMA","initialVolume":100,
                 "reservedVolume":10,"storageLocation":"FRIDGE-A1"}
                """.formatted(externalId);
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalId").value(externalId))
                .andExpect(jsonPath("$.sampleType").value("PLASMA"))
                .andExpect(jsonPath("$.initialVolume").value(100.0))
                .andExpect(jsonPath("$.reservedVolume").value(10.0))
                .andExpect(jsonPath("$.remainingVolume").value(100.0))
                .andExpect(jsonPath("$.storageLocation").value("FRIDGE-A1"))
                .andExpect(jsonPath("$.receivedAt", notNullValue()));
    }

    @Test
    void receiveRejectsNonPositiveInitialVolume() throws Exception {
        String body = """
                {"externalId":"%s","sampleType":"PLASMA","initialVolume":0,
                 "reservedVolume":0,"storageLocation":"FRIDGE-A1"}
                """.formatted(uniqueExternalId());
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receiveRejectsNegativeReservedVolume() throws Exception {
        String body = """
                {"externalId":"%s","sampleType":"PLASMA","initialVolume":100,
                 "reservedVolume":-1,"storageLocation":"FRIDGE-A1"}
                """.formatted(uniqueExternalId());
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receiveRejectsReservedVolumeNotLessThanInitial() throws Exception {
        String body = """
                {"externalId":"%s","sampleType":"PLASMA","initialVolume":100,
                 "reservedVolume":100,"storageLocation":"FRIDGE-A1"}
                """.formatted(uniqueExternalId());
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receiveRejectsDuplicateExternalId() throws Exception {
        String externalId = uniqueExternalId();
        receiveSample(externalId, "100", "10");
        String body = """
                {"externalId":"%s","sampleType":"PLASMA","initialVolume":50,
                 "reservedVolume":5,"storageLocation":"FRIDGE-B2"}
                """.formatted(externalId);
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_ID"));
    }

    @Test
    void receiveRejectsMissingRequiredField() throws Exception {
        String body = """
                {"sampleType":"PLASMA","initialVolume":100,"reservedVolume":10}
                """;
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.externalId", notNullValue()))
                .andExpect(jsonPath("$.details.storageLocation", notNullValue()));
    }

    @Test
    void getMissingSampleReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/samples/{id}", 999999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
