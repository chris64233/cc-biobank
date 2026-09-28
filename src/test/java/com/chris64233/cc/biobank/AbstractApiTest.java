package com.chris64233.cc.biobank;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    protected static final String PURPOSE_RESEARCH = "RESEARCH_GENOMICS";
    protected static final String PURPOSE_OTHER = "CLINICAL_TRIAL_X";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** 已登记受试者与其一个开放有效的同意版本（允许 RESEARCH_GENOMICS 用途）。 */
    protected record SubjectSetup(String code, String consentVersion) {
    }

    protected String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    protected String uniqueExternalId() {
        return uniqueCode("EXT");
    }

    protected void createSubject(String code) throws Exception {
        mockMvc.perform(post("/api/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectCode\":\"%s\"}".formatted(code)))
                .andExpect(status().isCreated());
    }

    protected SubjectSetup setupSubject() throws Exception {
        String code = uniqueCode("SUBJ");
        String version = "v1";
        createSubject(code);
        registerConsent(code, version, Set.of(PURPOSE_RESEARCH), null);
        return new SubjectSetup(code, version);
    }

    protected void registerConsent(String code, String version, Set<String> purposes,
            Instant validUntil) throws Exception {
        String until = validUntil != null ? "\"" + validUntil + "\"" : "null";
        // 登记已过期版本属于历史数据补录：生效时间取截止时间前 30 天。
        Instant validFrom = validUntil != null
                ? validUntil.minus(30, ChronoUnit.DAYS)
                : Instant.now().minus(1, ChronoUnit.DAYS);
        String body = """
                {"versionCode":"%s","allowedPurposes":[%s],
                 "validFrom":"%s","validUntil":%s}
                """.formatted(version,
                purposes.stream().map(p -> "\"" + p + "\"").reduce((a, b) -> a + "," + b)
                        .orElse(""),
                validFrom, until);
        mockMvc.perform(post("/api/subjects/{code}/consents", code)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    protected long receiveSample(SubjectSetup subject, String externalId, String initialVolume,
            String reservedVolume) throws Exception {
        String body = """
                {"subjectCode":"%s","consentVersionCode":"%s","externalId":"%s",
                 "sampleType":"BLOOD","initialVolume":%s,
                 "reservedVolume":%s,"storageLocation":"FRIDGE-A1"}
                """.formatted(subject.code(), subject.consentVersion(), externalId,
                initialVolume, reservedVolume);
        MvcResult result = mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readTree(result).get("id").asLong();
    }

    /** 便捷 3 参接收：自动建立受试者与默认有效同意，同时把受试者记录到 lastSubject。 */
    protected SubjectSetup lastSubject;

    protected long receiveSample(String externalId, String initialVolume, String reservedVolume)
            throws Exception {
        lastSubject = setupSubject();
        return receiveSample(lastSubject, externalId, initialVolume, reservedVolume);
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

    /** 便捷 3 参领用：使用最近一次 receiveSample 建立的受试者与默认用途。 */
    protected String issueBody(String key, long aliquotId, String volume) {
        return issueBody(key, lastSubject, aliquotId, volume, PURPOSE_RESEARCH);
    }

    protected String issueBody(String key, SubjectSetup subject, long aliquotId, String volume,
            String purpose) {
        return """
                {"idempotencyKey":"%s","subjectCode":"%s","consentVersionCode":"%s",
                 "purpose":"%s","items":[{"aliquotId":%d,"volume":%s}]}
                """.formatted(key, subject.code(), subject.consentVersion(), purpose,
                aliquotId, volume);
    }

    protected String issueBody(String key, SubjectSetup subject, long aliquotId, String volume) {
        return issueBody(key, subject, aliquotId, volume, PURPOSE_RESEARCH);
    }

    protected JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected BigDecimal decimal(JsonNode node, String field) {
        return node.get(field).decimalValue();
    }
}
