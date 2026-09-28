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

    private String withdrawBody(String withdrawalNo, String subjectCode) {
        return """
                {"withdrawalNo":"%s","subjectCode":"%s","reason":"subject withdrew"}
                """.formatted(withdrawalNo, subjectCode);
    }

    private long setupSampleWithThreeAliquots(String code) throws Exception {
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "0");
        return sampleId;
    }

    @Test
    void withdrawFreezesAllInStockSamplesAndAliquotsButKeepsHistory() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = setupSampleWithThreeAliquots(code);
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30,20,10]}");

        // 撤回首条分装发生过 5ml 历史领用（剩余 25，仍在库）。
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(0), "5")))
                .andExpect(status().isOk());
        // 第三条分装已耗尽。
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(2), "10")))
                .andExpect(status().isOk());

        String withdrawalNo = "WD-" + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody(withdrawalNo, code)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode withdrawal = readTree(result);

        // 原始样本与仍有在库物质的两条分装被冻结；耗尽的分装不冻结。
        assertThat(withdrawal.get("frozenSampleIds")).hasSize(1);
        assertThat(withdrawal.get("frozenSampleIds").get(0).asLong()).isEqualTo(sampleId);
        List<Long> frozenAliquotIds = objectMapper.readerForListOf(Long.class)
                .readValue(withdrawal.get("frozenAliquotIds"));
        assertThat(frozenAliquotIds).containsExactly(ids.get(0), ids.get(1));

        mockMvc.perform(get("/api/samples/{id}", sampleId))
                .andExpect(jsonPath("$.status").value("FROZEN"));
        mockMvc.perform(get("/api/aliquots/{id}", ids.get(0)))
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.remainingVolume").value(25.0));
        mockMvc.perform(get("/api/aliquots/{id}", ids.get(1)))
                .andExpect(jsonPath("$.status").value("FROZEN"));
        mockMvc.perform(get("/api/aliquots/{id}", ids.get(2)))
                .andExpect(jsonPath("$.status").value("DEPLETED"));

        // 历史领用未被改写。
        mockMvc.perform(get("/api/subjects/{id}/issues",
                        withdrawal.get("subjectId").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // 冻结后：新领用（同意已撤回，先返回 CONSENT_NOT_VALID）、新分装、新接收全部被拒绝。
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(1), "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_NOT_VALID"));
        mockMvc.perform(post("/api/samples/{id}/aliquots", sampleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volumes\":[5]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAMPLE_FROZEN"));
        String newSampleBody = """
                {"externalId":"%s","subjectCode":"%s","consentVersion":"v1",
                 "sampleType":"BLOOD","initialVolume":10,"reservedVolume":0,
                 "storageLocation":"X"}
                """.formatted(uniqueExternalId(), code);
        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newSampleBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_NOT_VALID"));
    }

    @Test
    void withdrawalIsIdempotent() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = setupSampleWithThreeAliquots(code);
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");

        String withdrawalNo = "WD-" + UUID.randomUUID();
        String body = withdrawBody(withdrawalNo, code);
        MvcResult first = mockMvc.perform(post("/api/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        MvcResult replay = mockMvc.perform(post("/api/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        assertThat(readTree(replay)).isEqualTo(readTree(first));

        // 样本事件：接收 + 分装创建 + 样本冻结 + 分装冻结（分装事件同样挂在样本维度）。
        mockMvc.perform(get("/api/samples/{id}/events", sampleId))
                .andExpect(jsonPath("$.length()").value(4));
        mockMvc.perform(get("/api/aliquots/{id}/events", ids.get(0)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void withdrawUnknownSubjectReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawBody("WD-1", "SUBJ-NOBODY")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
