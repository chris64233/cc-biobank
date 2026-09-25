package com.chris64233.cc.biobank.issue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record IssueRequest(
        @NotBlank(message = "幂等键不能为空") String idempotencyKey,
        @NotEmpty(message = "领用明细不能为空") List<@Valid Item> items) {

    public record Item(
            @NotNull(message = "子样本 id 不能为空") Long aliquotId,
            @NotNull(message = "领用体积不能为空") BigDecimal volume) {
    }
}
