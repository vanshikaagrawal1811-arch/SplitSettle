package com.splitsettle.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DBConnection {

    private static final String DB_PATH =
            System.getenv().getOrDefault("SPLITSETTLE_DB_PATH", "splitsettle.db");
    private static final String URL = "jdbc:sqlite:" + DB_PATH;

    private DBConnection() {
    }

    public static Connection get() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }
}
