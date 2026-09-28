package com.chris64233.cc.biobank.sample.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record ReceiveSampleRequest(
        @NotBlank(message = "受试者编号不能为空") String subjectCode,
        @NotBlank(message = "同意版本号不能为空") String consentVersionCode,
        @NotBlank(message = "外部样本号不能为空") String externalId,
        @NotBlank(message = "样本类型不能为空") String sampleType,
        @NotNull(message = "初始体积不能为空") BigDecimal initialVolume,
        @NotNull(message = "保留体积不能为空") BigDecimal reservedVolume,
        @NotBlank(message = "保存位置不能为空") String storageLocation,
        Instant receivedAt) {
}
