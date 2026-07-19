package com.splitsettle.dao;

import com.splitsettle.db.DBConnection;
import com.splitsettle.service.SettlementService;

import java.sql.*;
import java.util.List;

public class SettlementDAO {

    public void saveSettlementPlan(int groupId, List<SettlementService.Transaction> transactions) throws SQLException {
        String sql = "INSERT INTO settlements(group_id, from_user, to_user, amount) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (SettlementService.Transaction t : transactions) {
                ps.setInt(1, groupId);
                ps.setInt(2, t.fromUserId());
                ps.setInt(3, t.toUserId());
                ps.setBigDecimal(4, t.amount());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void markAsPaid(int settlementId) throws SQLException {
        String sql = "UPDATE settlements SET is_paid = TRUE WHERE id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, settlementId);
            ps.executeUpdate();
        }
    }
}
