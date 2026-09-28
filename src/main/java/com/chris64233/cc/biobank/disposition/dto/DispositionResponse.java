package com.chris64233.cc.biobank.disposition.dto;

import com.chris64233.cc.biobank.disposition.DispositionAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 批量处置结果。仅列出本次实际处置的对象及其处置后体积台账：
 * DESTROY/RETURN 将在库剩余量清零，RETAIN 保留原体积但禁止研究使用。
 */
public record DispositionResponse(
        String dispositionNo,
        Long subjectId,
        DispositionAction action,
        List<Item> items,
        Instant disposedAt) {

    public record Item(
            String targetType,
            Long targetId,
            String previousStatus,
            String resultingStatus,
            BigDecimal volumeBefore,
            BigDecimal volumeAfter) {
    }
}
