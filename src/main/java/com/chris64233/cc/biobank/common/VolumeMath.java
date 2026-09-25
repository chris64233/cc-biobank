package com.chris64233.cc.biobank.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 体积统一使用 BigDecimal，固定 3 位小数精度，HALF_UP 舍入。
 */
public final class VolumeMath {

    public static final int SCALE = 3;

    private VolumeMath() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }
}
