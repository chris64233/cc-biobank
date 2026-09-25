package com.chris64233.cc.biobank.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 体积统一使用 {@link BigDecimal}，固定 6 位小数精度。
 * 禁止使用 double/float 参与任何体积计算（金额式浮点计算禁止）。
 */
public final class Volumes {

    /** 固定小数位数。 */
    public static final int SCALE = 6;

    private Volumes() {
    }

    /** 规范化到固定精度；输入精度超过固定位数时直接拒绝（不做静默截断）。 */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException(
                    "volume precision exceeds " + SCALE + " decimal places: " + value);
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** 等分除法：余数保留在母样本中，子样本量向下取整到固定精度。 */
    public static BigDecimal divideFloor(BigDecimal total, int parts) {
        return total.divide(BigDecimal.valueOf(parts), SCALE, RoundingMode.FLOOR);
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
