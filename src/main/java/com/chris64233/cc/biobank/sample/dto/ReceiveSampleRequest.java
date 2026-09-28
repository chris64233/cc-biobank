package com.chris64233.cc.biobank.sample.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 样本接收。
 * subjectCode 标识受试者；consentVersion 为接收当时实际采用的同意版本，
 * 必须在有效期内、未撤回且允许 RESEARCH_STORAGE（研究保藏）用途。
 */
public record ReceiveSampleRequest(
        @NotBlank(message = "外部样本号不能为空") String externalId,
        @NotBlank(message = "受试者编号不能为空") String subjectCode,
        @NotBlank(message = "同意版本不能为空") String consentVersion,
        @NotBlank(message = "样本类型不能为空") String sampleType,
        @NotNull(message = "初始体积不能为空") BigDecimal initialVolume,
        @NotNull(message = "保留体积不能为空") BigDecimal reservedVolume,
        @NotBlank(message = "保存位置不能为空") String storageLocation,
        Instant receivedAt) {

    /** 样本入库保藏所需的同意用途。 */
    public static final String STORAGE_PURPOSE = "RESEARCH_STORAGE";
}
