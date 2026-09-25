package com.chris64233.cc.biobank.aliquot.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreateAliquotsResponse(
        Long sampleId,
        List<AliquotResponse> aliquots,
        BigDecimal lossVolume,
        BigDecimal sampleRemainingVolume) {
}
