-- SQLite schema for SplitSettle.
-- Load with:  sqlite3 splitsettle.db < sql/schema.sql
-- (or let the app create it automatically on first run -- see below)

PRAGMA foreign_keys = OFF;

DROP TABLE IF EXISTS settlements;
DROP TABLE IF EXISTS expense_shares;
DROP TABLE IF EXISTS expenses;
DROP TABLE IF EXISTS group_members;
DROP TABLE IF EXISTS groups;
DROP TABLE IF EXISTS users;

PRAGMA foreign_keys = ON;

CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

CREATE TABLE groups (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE group_members (
    group_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    PRIMARY KEY (group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- One expense = one person paid a total amount, split across the group.
CREATE TABLE expenses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL,
    paid_by INTEGER NOT NULL,
    description TEXT,
    amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY (paid_by) REFERENCES users(id)
);

-- How the expense splits across members. sum(share_amount) must equal expenses.amount.
CREATE TABLE expense_shares (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    expense_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    share_amount DECIMAL(12,2) NOT NULL CHECK (share_amount >= 0),
    FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- A settlement is a "simplified" payment instruction produced by the algorithm.
CREATE TABLE settlements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL,
    from_user INTEGER NOT NULL,
    to_user INTEGER NOT NULL,
    amount DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    is_paid BOOLEAN DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES groups(id),
    FOREIGN KEY (from_user) REFERENCES users(id),
    FOREIGN KEY (to_user) REFERENCES users(id)
);
