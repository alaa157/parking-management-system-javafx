package com.parking.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Centralized currency rounding policy for the in-memory payment flow. */
public final class Money {
    private Money() { }

    public static double round(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static double percentage(double amount, double rate) {
        return round(BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(rate)).doubleValue());
    }

    public static boolean same(double left, double right) {
        return Math.abs(left - right) < 0.005;
    }
}
