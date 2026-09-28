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
        SubjectSetup subject = setupSubject();
        String externalId = uniqueExternalId();
        String body = """
                {"subjectCode":"%s","consentVersionCode":"%s","externalId":"%s",
                 "sampleType":"PLASMA","initialVolume":100,
                 "reservedVolume":10,"storageLocation":"FRIDGE-A1"}
                """.formatted(subject.code(), subject.consentVersion(), externalId);
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectCode").value(subject.code()))
                .andExpect(jsonPath("$.consentVersionCode").value(subject.consentVersion()))
                .andExpect(jsonPath("$.externalId").value(externalId))
                .andExpect(jsonPath("$.sampleType").value("PLASMA"))
                .andExpect(jsonPath("$.initialVolume").value(100.0))
                .andExpect(jsonPath("$.reservedVolume").value(10.0))
                .andExpect(jsonPath("$.remainingVolume").value(100.0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.storageLocation").value("FRIDGE-A1"))
                .andExpect(jsonPath("$.receivedAt", notNullValue()));
    }

    @Test
    void receiveRejectsNonPositiveInitialVolume() throws Exception {
        SubjectSetup subject = setupSubject();
        String body = """
                {"subjectCode":"%s","consentVersionCode":"%s","externalId":"%s",
                 "sampleType":"PLASMA","initialVolume":0,
                 "reservedVolume":0,"storageLocation":"FRIDGE-A1"}
                """.formatted(subject.code(), subject.consentVersion(), uniqueExternalId());
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receiveRejectsNegativeReservedVolume() throws Exception {
        SubjectSetup subject = setupSubject();
        String body = """
                {"subjectCode":"%s","consentVersionCode":"%s","externalId":"%s",
                 "sampleType":"PLASMA","initialVolume":100,
                 "reservedVolume":-1,"storageLocation":"FRIDGE-A1"}
                """.formatted(subject.code(), subject.consentVersion(), uniqueExternalId());
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receiveRejectsReservedVolumeNotLessThanInitial() throws Exception {
        SubjectSetup subject = setupSubject();
        String body = """
                {"subjectCode":"%s","consentVersionCode":"%s","externalId":"%s",
                 "sampleType":"PLASMA","initialVolume":100,
                 "reservedVolume":100,"storageLocation":"FRIDGE-A1"}
                """.formatted(subject.code(), subject.consentVersion(), uniqueExternalId());
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receiveRejectsDuplicateExternalId() throws Exception {
        SubjectSetup subject = setupSubject();
        String externalId = uniqueExternalId();
        receiveSample(subject, externalId, "100", "10");
        String body = """
                {"subjectCode":"%s","consentVersionCode":"%s","externalId":"%s",
                 "sampleType":"PLASMA","initialVolume":50,
                 "reservedVolume":5,"storageLocation":"FRIDGE-B2"}
                """.formatted(subject.code(), subject.consentVersion(), externalId);
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
                .andExpect(jsonPath("$.details.subjectCode", notNullValue()))
                .andExpect(jsonPath("$.details.consentVersionCode", notNullValue()))
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
