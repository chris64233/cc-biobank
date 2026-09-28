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

class SubjectOverviewApiTests extends AbstractApiTest {

    @Test
    void overviewAggregatesLineageFreezeIssuesAndDisposals() throws Exception {
        SubjectSetup subject = setupSubject();
        long sampleId = receiveSample(subject, uniqueExternalId(), "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30,20]}");

        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), subject, ids.get(0), "10")))
                .andExpect(status().isOk());

        String withdrawalKey = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/subjects/{code}/withdrawals", subject.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalKey":"%s","consentVersionCode":null}
                                """.formatted(withdrawalKey)))
                .andExpect(status().isCreated());

        String disposalKey = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/subjects/{code}/disposals", subject.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disposalKey":"%s","decision":"DESTROY",
                                 "items":[{"targetType":"ALIQUOT","targetId":%d}]}
                                """.formatted(disposalKey, ids.get(1))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/subjects/{code}/overview", subject.code()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode overview = readTree(result);

        assertThat(overview.get("subjectCode").asString()).isEqualTo(subject.code());
        assertThat(overview.get("consents")).hasSize(1);
        assertThat(overview.get("consents").get(0).get("withdrawn").asBoolean()).isTrue();

        JsonNode lineage = overview.get("samples");
        assertThat(lineage).hasSize(1);
        assertThat(lineage.get(0).get("sample").get("status").asString()).isEqualTo("FROZEN");
        assertThat(lineage.get(0).get("aliquots")).hasSize(2);

        JsonNode withdrawals = overview.get("withdrawals");
        assertThat(withdrawals).hasSize(1);
        assertThat(withdrawals.get(0).get("withdrawalKey").asString()).isEqualTo(withdrawalKey);
        // 冻结范围：原始样本 + 仍在库的第二只分装；第一只已部分领用但仍在库也被冻结。
        assertThat(withdrawals.get(0).get("frozenSamples")).hasSize(1);
        assertThat(withdrawals.get(0).get("frozenAliquots")).hasSize(2);

        JsonNode issues = overview.get("issues");
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).get("consentVersionCode").asString()).isEqualTo("v1");
        assertThat(issues.get(0).get("purpose").asString()).isEqualTo(PURPOSE_RESEARCH);

        JsonNode disposals = overview.get("disposals");
        assertThat(disposals).hasSize(1);
        assertThat(disposals.get(0).get("disposalKey").asString()).isEqualTo(disposalKey);
        assertThat(disposals.get(0).get("items").get(0).get("resultingStatus").asString())
                .isEqualTo("DESTROYED");
    }

    @Test
    void overviewOfUnknownSubjectReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/subjects/{code}/overview", "NO-SUCH-SUBJECT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
