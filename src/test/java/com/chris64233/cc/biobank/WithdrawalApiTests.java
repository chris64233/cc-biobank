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

class WithdrawalApiTests extends AbstractApiTest {

    private String withdrawBody(String key, String version) {
        String versionJson = version != null ? "\"" + version + "\"" : "null";
        return """
                {"withdrawalKey":"%s","consentVersionCode":%s,"reason":"participant request"}
                """.formatted(key, versionJson);
    }

    @Test
    void withdrawalFreezesAllInStockSamplesAndAliquotsOnce() throws Exception {
        SubjectSetup subject = setupSubject();
        long sample1 = receiveSample(subject, uniqueExternalId(), "100", "0");
        List<Long> a1 = createAliquots(sample1, "{\"volumes\":[30,20]}");
        long sample2 = receiveSample(subject, uniqueExternalId(), "80", "0");
        List<Long> a2 = createAliquots(sample2, "{\"volumes\":[40]}");

        String key = UUID.randomUUID().toString();
        MvcResult first = mockMvc.perform(post("/api/subjects/{code}/withdrawals", subject.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody(key, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withdrawnConsentVersions[0]").value("v1"))
                .andReturn();
        JsonNode node = readTree(first);
        assertThat(node.get("frozenSamples")).hasSize(2);
        assertThat(node.get("frozenAliquots")).hasSize(3);

        mockMvc.perform(get("/api/samples/{id}", sample1))
                .andExpect(jsonPath("$.status").value("FROZEN"));
        mockMvc.perform(get("/api/samples/{id}", sample2))
                .andExpect(jsonPath("$.status").value("FROZEN"));
        for (Long aliquotId : a1) {
            mockMvc.perform(get("/api/aliquots/{id}", aliquotId))
                    .andExpect(jsonPath("$.status").value("FROZEN"));
        }
        for (Long aliquotId : a2) {
            mockMvc.perform(get("/api/aliquots/{id}", aliquotId))
                    .andExpect(jsonPath("$.status").value("FROZEN"));
        }

        // 幂等重放：返回首次结果，冻结范围不扩大。
        MvcResult replay = mockMvc.perform(post("/api/subjects/{code}/withdrawals", subject.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody(key, null)))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(readTree(replay)).isEqualTo(node);

        // 撤回后不能再领用、也不能再分装。
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), subject, a1.get(0), "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_INVALID"));
        mockMvc.perform(post("/api/samples/{id}/aliquots", sample1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volumes\":[10]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAMPLE_NOT_DISPOSABLE"));
    }

    @Test
    void withdrawalDoesNotFreezeDepletedOrHistoricalIssued() throws Exception {
        SubjectSetup subject = setupSubject();
        long sampleId = receiveSample(subject, uniqueExternalId(), "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30,20]}");

        // 完整耗尽第一只（历史领用）。
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), subject, ids.get(0), "30")))
                .andExpect(status().isOk());
        // 部分领用第二只。
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), subject, ids.get(1), "5")))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(
                        post("/api/subjects/{code}/withdrawals", subject.code())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(withdrawBody(UUID.randomUUID().toString(), null)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = readTree(result);
        // 耗尽的分装不在冻结范围；部分领用且仍在库的被冻结。
        assertThat(node.get("frozenAliquots")).hasSize(1);
        assertThat(node.get("frozenAliquots").get(0).get("aliquotId").asLong())
                .isEqualTo(ids.get(1));

        mockMvc.perform(get("/api/aliquots/{id}", ids.get(0)))
                .andExpect(jsonPath("$.status").value("DEPLETED"));
        mockMvc.perform(get("/api/aliquots/{id}", ids.get(1)))
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.remainingVolume").value(15.0));
    }

    @Test
    void withdrawalKeyRejectedForDifferentSubject() throws Exception {
        SubjectSetup s1 = setupSubject();
        SubjectSetup s2 = setupSubject();
        String key = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/subjects/{code}/withdrawals", s1.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody(key, null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/subjects/{code}/withdrawals", s2.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody(key, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void withdrawalOfUnknownSubjectReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/subjects/{code}/withdrawals", "NO-SUCH-SUBJECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody(UUID.randomUUID().toString(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
