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

class DispositionApiTests extends AbstractApiTest {

    private String dispositionBody(String dispositionNo, String code, String action,
            String itemsJson) {
        return """
                {"dispositionNo":"%s","subjectCode":"%s","action":"%s","items":%s}
                """.formatted(dispositionNo, code, action, itemsJson);
    }

    private String aliquotItem(long id) {
        return "{\"targetType\":\"ALIQUOT\",\"targetId\":" + id + "}";
    }

    private String sampleItem(long id) {
        return "{\"targetType\":\"SAMPLE\",\"targetId\":" + id + "}";
    }

    private String freeze(String code) throws Exception {
        String withdrawalNo = "WD-" + UUID.randomUUID();
        String body = """
                {"withdrawalNo":"%s","subjectCode":"%s"}
                """.formatted(withdrawalNo, code);
        mockMvc.perform(post("/api/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        return withdrawalNo;
    }

    @Test
    void destroyClearsVolumeAndMarksDestroyed() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30,20]}");
        freeze(code);

        String dispositionNo = "DP-" + UUID.randomUUID();
        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody(dispositionNo, code, "DESTROY",
                                "[" + aliquotItem(ids.get(0)) + "," + aliquotItem(ids.get(1))
                                        + "]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.action").value("DESTROY"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].resultingStatus").value("DESTROYED"))
                .andExpect(jsonPath("$.items[0].volumeAfter").value(0.0))
                .andExpect(jsonPath("$.items[1].resultingStatus").value("DESTROYED"));

        mockMvc.perform(get("/api/aliquots/{id}", ids.get(0)))
                .andExpect(jsonPath("$.status").value("DESTROYED"))
                .andExpect(jsonPath("$.remainingVolume").value(0.0));
    }

    @Test
    void retainKeepsVolumeButBlocksResearchUse() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        freeze(code);

        String dispositionNo = "DP-" + UUID.randomUUID();
        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody(dispositionNo, code, "RETAIN",
                                "[" + sampleItem(sampleId) + "," + aliquotItem(ids.get(0))
                                        + "]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].resultingStatus").value("RETAINED"))
                .andExpect(jsonPath("$.items[0].volumeAfter").value(70.0))
                .andExpect(jsonPath("$.items[1].resultingStatus").value("RETAINED"))
                .andExpect(jsonPath("$.items[1].volumeAfter").value(30.0));

        // 保留在库：体积不变，但撤回后同意失效，任何研究领用都被拒绝。
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(0), "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_NOT_VALID"));
    }

    @Test
    void returnSampleAndAliquot() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        freeze(code);

        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody("DP-" + UUID.randomUUID(), code, "RETURN",
                                "[" + sampleItem(sampleId) + "," + aliquotItem(ids.get(0))
                                        + "]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].resultingStatus").value("RETURNED"))
                .andExpect(jsonPath("$.items[0].volumeAfter").value(0.0))
                .andExpect(jsonPath("$.items[1].resultingStatus").value("RETURNED"));
    }

    @Test
    void dispositionIsAtomicWhenOneTargetInvalid() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30,20,10]}");
        freeze(code);
        // 先合法销毁第一条。
        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody("DP-" + UUID.randomUUID(), code, "DESTROY",
                                "[" + aliquotItem(ids.get(0)) + "]")))
                .andExpect(status().isCreated());

        // 批处理混入一条已处置对象：整体失败，其余对象保持 FROZEN。
        MvcResult failure = mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody("DP-" + UUID.randomUUID(), code, "DESTROY",
                                "[" + aliquotItem(ids.get(1)) + ","
                                        + aliquotItem(ids.get(0)) + "]")))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(readTree(failure).get("code").asString())
                .isIn("SAMPLE_FROZEN", "ALREADY_DISPOSED");

        mockMvc.perform(get("/api/aliquots/{id}", ids.get(1)))
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.remainingVolume").value(20.0));
        mockMvc.perform(get("/api/aliquots/{id}", ids.get(2)))
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.remainingVolume").value(10.0));
    }

    @Test
    void cannotDisposeBeforeFreeze() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");

        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody("DP-" + UUID.randomUUID(), code, "DESTROY",
                                "[" + aliquotItem(ids.get(0)) + "]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAMPLE_FROZEN"));
    }

    @Test
    void cannotDisposeTwice() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        freeze(code);

        String body = dispositionBody("DP-" + UUID.randomUUID(), code, "DESTROY",
                "[" + aliquotItem(ids.get(0)) + "]");
        MvcResult first = mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        MvcResult replay = mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        assertThat(readTree(replay)).isEqualTo(readTree(first));

        // 换新处置号再次处置同一对象被拒。
        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody("DP-" + UUID.randomUUID(), code, "DESTROY",
                                "[" + aliquotItem(ids.get(0)) + "]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_DISPOSED"));
    }

    @Test
    void rejectInvalidAction() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        freeze(code);

        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody("DP-" + UUID.randomUUID(), code, "INCINERATE",
                                "[" + aliquotItem(ids.get(0)) + "]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectDisposingAnotherSubjectsAliquot() throws Exception {
        String codeA = createSubjectWithConsent();
        long sampleA = receiveSample(uniqueExternalId(), codeA, CONSENT_V1, "100", "0");
        List<Long> idsA = createAliquots(sampleA, "{\"volumes\":[30]}");
        freeze(codeA);

        String codeB = createSubjectWithConsent();
        long sampleB = receiveSample(uniqueExternalId(), codeB, CONSENT_V1, "100", "0");
        createAliquots(sampleB, "{\"volumes\":[30]}");
        freeze(codeB);

        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody("DP-" + UUID.randomUUID(), codeB, "DESTROY",
                                "[" + aliquotItem(idsA.get(0)) + "]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void depletedAliquotCannotBeDisposed() throws Exception {
        String code = createSubjectWithConsent();
        long sampleId = receiveSample(uniqueExternalId(), code, CONSENT_V1, "100", "0");
        List<Long> ids = createAliquots(sampleId, "{\"volumes\":[30]}");
        mockMvc.perform(post("/api/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(UUID.randomUUID().toString(), code, PURPOSE_USE,
                                CONSENT_V1, ids.get(0), "30")))
                .andExpect(status().isOk());
        freeze(code);

        // 耗尽的分装未冻结也无在库物质，不允许处置。
        mockMvc.perform(post("/api/dispositions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispositionBody("DP-" + UUID.randomUUID(), code, "DESTROY",
                                "[" + aliquotItem(ids.get(0)) + "]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAMPLE_FROZEN"));
    }
}
