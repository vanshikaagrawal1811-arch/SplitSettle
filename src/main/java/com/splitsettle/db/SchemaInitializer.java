package com.splitsettle.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates all tables on startup if they don't already exist. Since SQLite is
 * just a file, this means the app is fully self-setting-up: no separate
 * "run this schema file" step for the user to remember, ever.
 *
 * Safe to call every time the app starts -- CREATE TABLE IF NOT EXISTS means
 * existing data is left untouched on subsequent runs.
 */
public final class SchemaInitializer {

    private SchemaInitializer() {
    }

    public static void ensureSchema() throws SQLException {
        String sql = readBundledSchema();
        try (Connection conn = DBConnection.get();
             Statement stmt = conn.createStatement()) {
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    private static String readBundledSchema() {
        try (InputStream in = SchemaInitializer.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("schema.sql not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't read bundled schema.sql", e);
        }
    }
}
