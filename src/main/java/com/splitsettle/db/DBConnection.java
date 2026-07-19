package com.splitsettle.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite is embedded -- no server, no username/password. The whole database
 * is just a file on disk, created automatically the first time it's opened.
 *
 * Override the file location with SPLITSETTLE_DB_PATH if you ever want it
 * somewhere other than the project folder (e.g. a different name per run).
 */
public final class DBConnection {

    private static final String DB_PATH =
            System.getenv().getOrDefault("SPLITSETTLE_DB_PATH", "splitsettle.db");
    private static final String URL = "jdbc:sqlite:" + DB_PATH;

    private DBConnection() {
    }

    public static Connection get() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        // SQLite has foreign key enforcement OFF by default per-connection --
        // turn it on so ON DELETE CASCADE and the FK constraints in the
        // schema actually do something.
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }
}
