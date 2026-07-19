package com.splitsettle.dao;

import com.splitsettle.db.DBConnection;
import com.splitsettle.model.Group;
import com.splitsettle.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {

    public Group create(String name, List<User> members) throws SQLException {
        try (Connection conn = DBConnection.get()) {
            conn.setAutoCommit(false);
            try {
                int groupId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO groups(name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        groupId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO group_members(group_id, user_id) VALUES (?, ?)")) {
                    for (User u : members) {
                        ps.setInt(1, groupId);
                        ps.setInt(2, u.getId());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();

                Group g = new Group(groupId, name);
                members.forEach(g::addMember);
                return g;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public List<Integer> getMemberIds(int groupId) throws SQLException {
        String sql = "SELECT user_id FROM group_members WHERE group_id = ?";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            List<Integer> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt("user_id"));
            }
            return ids;
        }
    }

    /** Case-insensitive check so "Goa Trip" and "goa trip" are treated as the same name. */
    public boolean nameExists(String name) throws SQLException {
        String sql = "SELECT 1 FROM groups WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Lists every group on file, with a member count, ordered most-recently-created first. */
    public List<GroupSummary> findAll() throws SQLException {
        String sql = """
                SELECT g.id, g.name, COUNT(gm.user_id) as member_count
                FROM groups g
                LEFT JOIN group_members gm ON gm.group_id = g.id
                GROUP BY g.id, g.name
                ORDER BY g.id DESC
                """;
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<GroupSummary> groups = new ArrayList<>();
            while (rs.next()) {
                groups.add(new GroupSummary(rs.getInt("id"), rs.getString("name"), rs.getInt("member_count")));
            }
            return groups;
        }
    }

    /** Loads a group with its members fully populated, or null if no such group exists. */
    public Group findById(int groupId) throws SQLException {
        String groupSql = "SELECT id, name FROM groups WHERE id = ?";
        try (Connection conn = DBConnection.get()) {
            String name;
            try (PreparedStatement ps = conn.prepareStatement(groupSql)) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    name = rs.getString("name");
                }
            }

            Group group = new Group(groupId, name);
            String membersSql = """
                    SELECT u.id, u.name
                    FROM users u
                    JOIN group_members gm ON gm.user_id = u.id
                    WHERE gm.group_id = ?
                    ORDER BY u.id
                    """;
            try (PreparedStatement ps = conn.prepareStatement(membersSql)) {
                ps.setInt(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        group.addMember(new User(rs.getInt("id"), rs.getString("name")));
                    }
                }
            }
            return group;
        }
    }

    /** Adds an existing user to an existing group. No-op (silently ignored) if already a member. */
    public void addMember(int groupId, int userId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO group_members(group_id, user_id) VALUES (?, ?)";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /** Lightweight summary row used for the "pick a group" list -- avoids loading every member. */
    public record GroupSummary(int id, String name, int memberCount) {}
}
