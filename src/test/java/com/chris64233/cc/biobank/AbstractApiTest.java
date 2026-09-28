package com.chris64233.cc.biobank;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractApiTest {

    protected static final String CONSENT_V1 = "v1";
    protected static final String PURPOSE_STORAGE = "RESEARCH_STORAGE";
    protected static final String PURPOSE_USE = "RESEARCH_USE";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String uniqueExternalId() {
        return "EXT-" + UUID.randomUUID();
    }

    /** 登记受试者并登记 v1 同意（允许研究保藏与研究使用），返回受试者编号。 */
    protected String createSubjectWithConsent() throws Exception {
        return createSubjectWithConsent(CONSENT_V1, "["
                + "\"" + PURPOSE_STORAGE + "\",\"" + PURPOSE_USE + "\"]");
    }

    protected String createSubjectWithConsent(String version, String purposesJson)
            throws Exception {
        String code = "SUBJ-" + UUID.randomUUID();
        mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectCode\":\"%s\"}".formatted(code)))
                .andExpect(status().isCreated());
        String consentBody = """
                {"subjectCode":"%s","version":"%s","allowedPurposes":%s}
                """.formatted(code, version, purposesJson);
        mockMvc.perform(post("/api/subjects/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consentBody))
                .andExpect(status().isCreated());
        return code;
    }

    protected long receiveSample(String externalId, String subjectCode, String version,
            String initialVolume, String reservedVolume) throws Exception {
        String body = """
                {"externalId":"%s","subjectCode":"%s","consentVersion":"%s",
                 "sampleType":"BLOOD","initialVolume":%s,
                 "reservedVolume":%s,"storageLocation":"FRIDGE-A1"}
                """.formatted(externalId, subjectCode, version, initialVolume, reservedVolume);
        MvcResult result = mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readTree(result).get("id").asLong();
    }

    /** 便捷方法：新建受试者与 v1 同意后接收一个样本。 */
    protected long receiveSample(String externalId, String initialVolume, String reservedVolume)
            throws Exception {
        String code = createSubjectWithConsent();
        return receiveSample(externalId, code, CONSENT_V1, initialVolume, reservedVolume);
    }

    protected List<Long> createAliquots(long sampleId, String requestBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/samples/{id}/aliquots", sampleId)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : readTree(result).get("aliquots")) {
            ids.add(node.get("id").asLong());
        }
        return ids;
    }

    protected String issueBody(String key, String subjectCode, String purpose, String version,
            long aliquotId, String volume) {
        return """
                {"idempotencyKey":"%s","subjectCode":"%s","purpose":"%s",
                 "consentVersion":"%s","items":[{"aliquotId":%d,"volume":%s}]}
                """.formatted(key, subjectCode, purpose, version, aliquotId, volume);
    }

    protected JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected BigDecimal decimal(JsonNode node, String field) {
        return node.get(field).decimalValue();
    }
}
