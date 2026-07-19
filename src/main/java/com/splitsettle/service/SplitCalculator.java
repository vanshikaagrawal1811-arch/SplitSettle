package com.splitsettle.service;

import com.splitsettle.exception.InvalidExpenseException;
import com.splitsettle.model.ExpenseShare;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits an amount evenly across users WITHOUT losing money to rounding.
 *
 * The loophole: splitting ₹100 three ways with plain division gives
 * 33.33 + 33.33 + 33.33 = 99.99 — one paisa vanishes. Over many expenses this
 * drift adds up and balances stop summing to exactly zero, which makes the
 * settlement algorithm produce a leftover "phantom" debt that never clears.
 *
 * Fix: round down for everyone, then hand the leftover paise to the first N
 * people one unit each, so shares always sum EXACTLY to the original amount.
 */
public final class SplitCalculator {

    private SplitCalculator() {
    }

    public static List<ExpenseShare> splitEqually(BigDecimal amount, List<Integer> userIds) {
        if (userIds.isEmpty()) {
            throw new InvalidExpenseException("Cannot split an expense with zero participants");
        }

        int n = userIds.size();
        BigDecimal base = amount.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);

        BigDecimal distributed = base.multiply(BigDecimal.valueOf(n));
        BigDecimal remainder = amount.subtract(distributed);
        int remainderUnits = remainder.movePointRight(2).intValueExact();

        List<ExpenseShare> shares = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BigDecimal share = base;
            if (i < remainderUnits) {
                share = share.add(new BigDecimal("0.01"));
            }
            shares.add(new ExpenseShare(userIds.get(i), share));
        }
        return shares;
    }
}
