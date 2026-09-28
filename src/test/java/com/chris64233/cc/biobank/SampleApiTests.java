package com.chris64233.cc.biobank;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SampleApiTests extends AbstractApiTest {

    private String receiveBody(String externalId, String subjectCode, String version) {
        return """
                {"externalId":"%s","subjectCode":"%s","consentVersion":"%s",
                 "sampleType":"PLASMA","initialVolume":100,
                 "reservedVolume":10,"storageLocation":"FRIDGE-A1"}
                """.formatted(externalId, subjectCode, version);
    }

    @Test
    void receiveSuccess() throws Exception {
        String code = createSubjectWithConsent();
        String externalId = uniqueExternalId();
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiveBody(externalId, code, CONSENT_V1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalId").value(externalId))
                .andExpect(jsonPath("$.sampleType").value("PLASMA"))
                .andExpect(jsonPath("$.initialVolume").value(100.0))
                .andExpect(jsonPath("$.reservedVolume").value(10.0))
                .andExpect(jsonPath("$.remainingVolume").value(100.0))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.storageLocation").value("FRIDGE-A1"))
                .andExpect(jsonPath("$.consentVersionId", notNullValue()))
                .andExpect(jsonPath("$.subjectId", notNullValue()))
                .andExpect(jsonPath("$.receivedAt", notNullValue()));
    }

    @Test
    void receiveRejectsNonPositiveInitialVolume() throws Exception {
        String code = createSubjectWithConsent();
        String body = """
                {"externalId":"%s","subjectCode":"%s","consentVersion":"v1",
                 "sampleType":"PLASMA","initialVolume":0,
                 "reservedVolume":0,"storageLocation":"FRIDGE-A1"}
                """.formatted(uniqueExternalId(), code);
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receiveRejectsNegativeReservedVolume() throws Exception {
        String code = createSubjectWithConsent();
        String body = """
                {"externalId":"%s","subjectCode":"%s","consentVersion":"v1",
                 "sampleType":"PLASMA","initialVolume":100,
                 "reservedVolume":-1,"storageLocation":"FRIDGE-A1"}
                """.formatted(uniqueExternalId(), code);
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receiveRejectsReservedVolumeNotLessThanInitial() throws Exception {
        String code = createSubjectWithConsent();
        String body = """
                {"externalId":"%s","subjectCode":"%s","consentVersion":"v1",
                 "sampleType":"PLASMA","initialVolume":100,
                 "reservedVolume":100,"storageLocation":"FRIDGE-A1"}
                """.formatted(uniqueExternalId(), code);
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receiveRejectsDuplicateExternalId() throws Exception {
        String code = createSubjectWithConsent();
        String externalId = uniqueExternalId();
        receiveSample(externalId, code, CONSENT_V1, "100", "10");
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiveBody(externalId, code, CONSENT_V1)))
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

    @Test
    void receiveWithUnknownConsentVersionRejected() throws Exception {
        String code = createSubjectWithConsent();
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiveBody(uniqueExternalId(), code, "nope")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_NOT_VALID"));
    }

    @Test
    void receiveRejectsWhenStoragePurposeNotConsented() throws Exception {
        String code = createSubjectWithConsent("v9", "[\"OTHER_PURPOSE\"]");
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiveBody(uniqueExternalId(), code, "v9")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_NOT_VALID"));
    }
}
