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

class DisposalApiTests extends AbstractApiTest {

    private record Frozen(SubjectSetup subject, long sampleId, List<Long> aliquotIds) {
    }

    private Frozen freezeTwoSamplesWithAliquots() throws Exception {
        SubjectSetup subject = setupSubject();
        long sample1 = receiveSample(subject, uniqueExternalId(), "100", "0");
        List<Long> a1 = createAliquots(sample1, "{\"volumes\":[30,20]}");
        long sample2 = receiveSample(subject, uniqueExternalId(), "80", "0");
        List<Long> a2 = createAliquots(sample2, "{\"volumes\":[40]}");
        mockMvc.perform(post("/api/subjects/{code}/withdrawals", subject.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalKey":"%s","consentVersionCode":null}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());
        return new Frozen(subject, sample1, List.of(a1.get(0), a1.get(1), a2.get(0)));
    }

    private String disposeBody(String key, String decision, Object... typeIdPairs) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < typeIdPairs.length; i += 2) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"targetType\":\"").append(typeIdPairs[i])
                    .append("\",\"targetId\":").append(typeIdPairs[i + 1]).append('}');
        }
        return """
                {"disposalKey":"%s","decision":"%s","items":[%s]}
                """.formatted(key, decision, items);
    }

    @Test
    void batchDestroyIsAtomicAndUpdatesLedger() throws Exception {
        Frozen frozen = freezeTwoSamplesWithAliquots();
        String key = UUID.randomUUID().toString();
        String body = disposeBody(key, "DESTROY",
                "ALIQUOT", frozen.aliquotIds().get(0),
                "ALIQUOT", frozen.aliquotIds().get(1),
                "SAMPLE", frozen.sampleId());

        MvcResult result = mockMvc.perform(
                        post("/api/subjects/{code}/disposals", frozen.subject().code())
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andReturn();
        JsonNode node = readTree(result);
        for (JsonNode item : node.get("items")) {
            assertThat(item.get("resultingStatus").asString()).isEqualTo("DESTROYED");
            assertThat(item.get("remainingVolume").decimalValue())
                    .isEqualByComparingTo("0.000");
        }

        mockMvc.perform(get("/api/aliquots/{id}", frozen.aliquotIds().get(0)))
                .andExpect(jsonPath("$.status").value("DESTROYED"))
                .andExpect(jsonPath("$.remainingVolume").value(0.0));
        mockMvc.perform(get("/api/samples/{id}", frozen.sampleId()))
                .andExpect(jsonPath("$.status").value("DESTROYED"))
                .andExpect(jsonPath("$.remainingVolume").value(0.0));

        // 幂等重放返回首次结果。
        MvcResult replay = mockMvc.perform(
                        post("/api/subjects/{code}/disposals", frozen.subject().code())
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(readTree(replay)).isEqualTo(node);
    }

    @Test
    void retainNoResearchKeepsVolumeButBlocksUse() throws Exception {
        Frozen frozen = freezeTwoSamplesWithAliquots();
        Long aliquotId = frozen.aliquotIds().get(2);
        mockMvc.perform(post("/api/subjects/{code}/disposals", frozen.subject().code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposeBody(UUID.randomUUID().toString(), "RETAIN_NO_RESEARCH",
                                "ALIQUOT", aliquotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].resultingStatus").value("RETAINED_NO_RESEARCH"))
                .andExpect(jsonPath("$.items[0].remainingVolume").value(40.0));

        mockMvc.perform(get("/api/aliquots/{id}", aliquotId))
                .andExpect(jsonPath("$.status").value("RETAINED_NO_RESEARCH"))
                .andExpect(jsonPath("$.remainingVolume").value(40.0));
    }

    @Test
    void returnDecisionZeroesLedger() throws Exception {
        Frozen frozen = freezeTwoSamplesWithAliquots();
        Long aliquotId = frozen.aliquotIds().get(0);
        mockMvc.perform(post("/api/subjects/{code}/disposals", frozen.subject().code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposeBody(UUID.randomUUID().toString(), "RETURN",
                                "ALIQUOT", aliquotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].resultingStatus").value("RETURNED"))
                .andExpect(jsonPath("$.items[0].remainingVolume").value(0.0));
    }

    @Test
    void disposeRejectsUnfrozenSampleAndRollsBackWholeBatch() throws Exception {
        Frozen frozen = freezeTwoSamplesWithAliquots();
        // 混入一个未冻结对象：另一受试者的在库分装。
        SubjectSetup other = setupSubject();
        long otherSample = receiveSample(other, uniqueExternalId(), "100", "0");
        long otherAliquot = createAliquots(otherSample, "{\"volumes\":[10]}").get(0);

        String body = disposeBody(UUID.randomUUID().toString(), "DESTROY",
                "ALIQUOT", frozen.aliquotIds().get(0),
                "ALIQUOT", otherAliquot);
        mockMvc.perform(post("/api/subjects/{code}/disposals", frozen.subject().code())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 批内第一个冻结分装也必须保持 FROZEN，不能只处置一部分。
        mockMvc.perform(get("/api/aliquots/{id}", frozen.aliquotIds().get(0)))
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.remainingVolume").value(30.0));

        // 直接处置未冻结对象同样被拒。
        mockMvc.perform(post("/api/subjects/{code}/disposals", other.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposeBody(UUID.randomUUID().toString(), "DESTROY",
                                "ALIQUOT", otherAliquot)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAMPLE_NOT_DISPOSABLE"));
    }

    @Test
    void depletedAliquotCannotBeDisposed() throws Exception {
        SubjectSetup subject = setupSubject();
        long sampleId = receiveSample(subject, uniqueExternalId(), "100", "0");
        Long aliquotId = createAliquots(sampleId, "{\"volumes\":[30]}").get(0);
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), subject, aliquotId, "30")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/subjects/{code}/withdrawals", subject.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalKey":"%s","consentVersionCode":null}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/subjects/{code}/disposals", subject.code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposeBody(UUID.randomUUID().toString(), "DESTROY",
                                "ALIQUOT", aliquotId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAMPLE_NOT_DISPOSABLE"));
    }

    @Test
    void alreadyDisposedAliquotCannotBeDisposedAgain() throws Exception {
        Frozen frozen = freezeTwoSamplesWithAliquots();
        Long aliquotId = frozen.aliquotIds().get(0);
        String firstBody = disposeBody(UUID.randomUUID().toString(), "DESTROY",
                "ALIQUOT", aliquotId);
        mockMvc.perform(post("/api/subjects/{code}/disposals", frozen.subject().code())
                        .contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/subjects/{code}/disposals", frozen.subject().code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposeBody(UUID.randomUUID().toString(), "RETURN",
                                "ALIQUOT", aliquotId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAMPLE_NOT_DISPOSABLE"));
    }

    @Test
    void disposalProgressListsEveryItem() throws Exception {
        Frozen frozen = freezeTwoSamplesWithAliquots();
        mockMvc.perform(post("/api/subjects/{code}/disposals", frozen.subject().code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposeBody(UUID.randomUUID().toString(), "DESTROY",
                                "ALIQUOT", frozen.aliquotIds().get(0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/subjects/{code}/disposals", frozen.subject().code())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposeBody(UUID.randomUUID().toString(), "RETURN",
                                "ALIQUOT", frozen.aliquotIds().get(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/subjects/{code}/disposals", frozen.subject().code()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].resultingStatus").value("DESTROYED"))
                .andExpect(jsonPath("$[1].resultingStatus").value("RETURNED"));
    }
}
