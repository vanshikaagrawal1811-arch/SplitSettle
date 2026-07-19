package com.splitsettle.model;

import java.math.BigDecimal;

public class ExpenseShare {
    private final int userId;
    private final BigDecimal shareAmount;

    public ExpenseShare(int userId, BigDecimal shareAmount) {
        this.userId = userId;
        this.shareAmount = shareAmount;
    }

    public int getUserId() { return userId; }
    public BigDecimal getShareAmount() { return shareAmount; }
}
