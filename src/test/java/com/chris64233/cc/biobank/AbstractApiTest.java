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

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String uniqueExternalId() {
        return "EXT-" + UUID.randomUUID();
    }

    protected long receiveSample(String externalId, String initialVolume, String reservedVolume)
            throws Exception {
        String body = """
                {"externalId":"%s","sampleType":"BLOOD","initialVolume":%s,
                 "reservedVolume":%s,"storageLocation":"FRIDGE-A1"}
                """.formatted(externalId, initialVolume, reservedVolume);
        MvcResult result = mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return readTree(result).get("id").asLong();
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

    protected JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected BigDecimal decimal(JsonNode node, String field) {
        return node.get(field).decimalValue();
    }
}
