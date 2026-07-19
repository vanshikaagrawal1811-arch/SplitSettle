package com.splitsettle.model;

import java.math.BigDecimal;
import java.util.List;

public class Expense {
    private int id;
    private final int groupId;
    private final int paidBy;
    private final String description;
    private final BigDecimal amount;
    private final List<ExpenseShare> shares;

    public Expense(int groupId, int paidBy, String description, BigDecimal amount, List<ExpenseShare> shares) {
        this.groupId = groupId;
        this.paidBy = paidBy;
        this.description = description;
        this.amount = amount;
        this.shares = shares;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getGroupId() { return groupId; }
    public int getPaidBy() { return paidBy; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public List<ExpenseShare> getShares() { return shares; }
}
