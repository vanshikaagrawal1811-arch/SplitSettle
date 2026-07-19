package com.splitsettle.dao;

import com.splitsettle.db.DBConnection;
import com.splitsettle.exception.UnbalancedSharesException;
import com.splitsettle.model.Expense;
import com.splitsettle.model.ExpenseShare;

import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class ExpenseDAO {

    /** Inserts an expense and its shares atomically. Rejects it if shares don't sum to amount. */
    public Expense create(Expense expense) throws SQLException {
        BigDecimal sumShares = expense.getShares().stream()
                .map(ExpenseShare::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumShares.compareTo(expense.getAmount()) != 0) {
            throw new UnbalancedSharesException(sumShares, expense.getAmount());
        }

        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            try {
                int expenseId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO expenses(group_id, paid_by, description, amount) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, expense.getGroupId());
                    ps.setInt(2, expense.getPaidBy());
                    ps.setString(3, expense.getDescription());
                    ps.setBigDecimal(4, expense.getAmount());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        expenseId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO expense_shares(expense_id, user_id, share_amount) VALUES (?, ?, ?)")) {
                    for (ExpenseShare share : expense.getShares()) {
                        ps.setInt(1, expenseId);
                        ps.setInt(2, share.getUserId());
                        ps.setBigDecimal(3, share.getShareAmount());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
                expense.setId(expenseId);
                return expense;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public Map<Integer, BigDecimal> getTotalPaidPerUser(int groupId) throws SQLException {
        String sql = "SELECT paid_by, SUM(amount) as total FROM expenses WHERE group_id = ? GROUP BY paid_by";
        return sumQuery(sql, groupId, "paid_by");
    }

    public Map<Integer, BigDecimal> getTotalOwedPerUser(int groupId) throws SQLException {
        String sql = """
                SELECT es.user_id, SUM(es.share_amount) as total
                FROM expense_shares es
                JOIN expenses e ON e.id = es.expense_id
                WHERE e.group_id = ?
                GROUP BY es.user_id
                """;
        return sumQuery(sql, groupId, "user_id");
    }

    /** Returns every expense logged for a group, each with its shares populated, ordered by insertion. */
    public java.util.List<Expense> getAllForGroup(int groupId) throws SQLException {
        java.util.List<Expense> expenses = new java.util.ArrayList<>();

        try (Connection conn = DBConnection.get()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, group_id, paid_by, description, amount FROM expenses WHERE group_id = ? ORDER BY id")) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int expenseId = rs.getInt("id");
                        java.util.List<ExpenseShare> shares = getSharesForExpense(conn, expenseId);
                        Expense expense = new Expense(
                                rs.getInt("group_id"),
                                rs.getInt("paid_by"),
                                rs.getString("description"),
                                rs.getBigDecimal("amount"),
                                shares);
                        expense.setId(expenseId);
                        expenses.add(expense);
                    }
                }
            }
        }
        return expenses;
    }

    private java.util.List<ExpenseShare> getSharesForExpense(Connection conn, int expenseId) throws SQLException {
        java.util.List<ExpenseShare> shares = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT user_id, share_amount FROM expense_shares WHERE expense_id = ?")) {
            ps.setInt(1, expenseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shares.add(new ExpenseShare(rs.getInt("user_id"), rs.getBigDecimal("share_amount")));
                }
            }
        }
        return shares;
    }

    private Map<Integer, BigDecimal> sumQuery(String sql, int groupId, String keyColumn) throws SQLException {
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            Map<Integer, BigDecimal> result = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt(keyColumn), rs.getBigDecimal("total"));
                }
            }
            return result;
        }
    }
}
