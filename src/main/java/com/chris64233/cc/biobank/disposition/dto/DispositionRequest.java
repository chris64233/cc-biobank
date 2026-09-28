package com.chris64233.cc.biobank.disposition.dto;

import com.chris64233.cc.biobank.disposition.DispositionAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 对撤回冻结的样本执行批量处置。
 * targetType 为 SAMPLE 或 ALIQUOT；同一批只能处理同一受试者的冻结对象。
 * dispositionNo 为业务处置号，必须幂等。
 */
public record DispositionRequest(
        @NotBlank(message = "处置号不能为空") String dispositionNo,
        @NotBlank(message = "受试者编号不能为空") String subjectCode,
        @NotBlank(message = "处置动作不能为空") String action,
        @NotEmpty(message = "处置明细不能为空") List<@Valid Item> items) {

    public DispositionAction resolvedAction() {
        try {
            return DispositionAction.valueOf(action);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record Item(
            @NotBlank(message = "对象类型不能为空") String targetType,
            @jakarta.validation.constraints.NotNull(message = "对象 id 不能为空") Long targetId) {

        public boolean isSample() {
            return "SAMPLE".equalsIgnoreCase(targetType);
        }

        public boolean isAliquot() {
            return "ALIQUOT".equalsIgnoreCase(targetType);
        }
    }
}
