package com.saritha.fashions.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TaxUtils {
    public static BigDecimal calcPercent(BigDecimal base, BigDecimal percent) {
        if (base == null || percent == null) {
            throw new IllegalArgumentException("Tax base and percent cannot be null");
        }
        return base.multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public static String formatAmount(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String formatAmount(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

}
