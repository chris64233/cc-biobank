package com.chris64233.cc.biobank.disposal.dto;

import com.chris64233.cc.biobank.disposal.DisposalTargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 对一批已冻结对象（原始样本或分装）执行同一种处置决定。整批在一个事务内原子完成，
 * 任一对象不满足条件则全部回滚，不会只处置一部分。
 */
public record DisposeRequest(
        @NotBlank(message = "处置号不能为空") String disposalKey,
        @NotNull(message = "处置决定不能为空")
        com.chris64233.cc.biobank.disposal.DisposalDecision decision,
        @NotEmpty(message = "处置对象列表不能为空") List<@Valid Item> items,
        String note) {

    public record Item(
            @NotNull(message = "对象类型不能为空") DisposalTargetType targetType,
            @NotNull(message = "对象 id 不能为空") Long targetId) {
    }
}
