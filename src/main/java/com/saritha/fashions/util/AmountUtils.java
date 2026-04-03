package com.saritha.fashions.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class AmountUtils {

    private static final int DECIMAL_PLACES = 0;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public static double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(DECIMAL_PLACES, ROUNDING_MODE)
                .doubleValue();
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(DECIMAL_PLACES, ROUNDING_MODE);
    }

    public static double calculateTax(double taxableAmount, double taxRate) {
        BigDecimal taxable = BigDecimal.valueOf(taxableAmount);
        BigDecimal rate = BigDecimal.valueOf(taxRate);
        BigDecimal taxAmount = taxable.multiply(rate).divide(BigDecimal.valueOf(100));
        return round(taxAmount.doubleValue());
    }

    public static int roundQuantity(double quantity) {
        // Round to nearest integer: 0.5 or above rounds up, below 0.5 rounds down
        return (int) Math.round(quantity);
    }

    // Additional helper method for clarity
    public static double roundToWholeNumber(double value) {
        return Math.round(value);
    }
}