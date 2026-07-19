package com.splitsettle.dao;

import com.splitsettle.db.DBConnection;
import com.splitsettle.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    public User create(String name) throws SQLException {
        String sql = "INSERT INTO users(name) VALUES (?)";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new User(keys.getInt(1), name);
            }
        }
    }

    public List<User> findByIds(List<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return new ArrayList<>();
        String placeholders = String.join(",", ids.stream().map(i -> "?").toArray(String[]::new));
        String sql = "SELECT id, name FROM users WHERE id IN (" + placeholders + ")";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setInt(i + 1, ids.get(i));
            }
            List<User> users = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(new User(rs.getInt("id"), rs.getString("name")));
                }
            }
            return users;
        }
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT id, name FROM users ORDER BY id";
        try (Connection conn = DBConnection.get();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(new User(rs.getInt("id"), rs.getString("name")));
            }
            return users;
        }
    }
}
