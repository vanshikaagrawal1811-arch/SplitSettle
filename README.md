# SplitSettle — Debt-Simplification Engine for Group Expenses

A group expense tracker for roommates, trips, and shared events. The core
problem is not splitting bills but minimizing settlement friction: given a
group with arbitrary many-to-many debts (A owes B, B owes C, C owes A), the
engine computes the minimum number of transactions required to settle every
balance to zero, using a greedy max-heap matching algorithm.

---

## Demo

A walkthrough of a typical session, in order.

### 1. Start the App

Choose to load an existing group or create a new one. Data persists across
runs via SQLite.

![Startup](docs/screenshots/startup.png)

### 2. Log an Expense

Enter the payer, description, amount, and participants.

![Add Expense](docs/screenshots/add-expense.png)

### 3. View Balances

Per-person net position, computed from all logged expenses.

![Balances](docs/screenshots/balances.png)

### 4. View the Settlement Plan

The minimized set of payments produced by the debt-simplification algorithm.

![Settlement Plan](docs/screenshots/settlement-plan.png)

---

## Stack

- Java 17
- JDBC
- SQLite (embedded)
- Command-line interface (Scanner-based)
- JUnit 5 for algorithm tests

---

## Setup

1. Requires **Java 17+** and **Maven**. SQLite is embedded, so no separate
   database server or credentials are needed.

2. Run:

   ```bash
   mvn compile exec:java -Dexec.mainClass=com.splitsettle.Main
   ```

   **PowerShell:**

   ```powershell
   mvn compile "exec:java" "-Dexec.mainClass=com.splitsettle.Main"
   ```

3. On first run, the application creates `splitsettle.db` in the project
   directory and initializes the schema automatically.

4. Run the tests:

   ```bash
   mvn test
   ```

The database file location can be overridden with the
`SPLITSETTLE_DB_PATH` environment variable (default: `splitsettle.db`).

---

## Usage

1. Choose to load an existing group or create a new one.
2. Create a group with a unique name and unique member names (letters only).
3. Log an expense by entering the payer, description, amount, and participants (defaults to all members if left blank).
4. View balances to see each member's net position (owed / owes).
5. View the settlement plan to generate the minimized set of payments and optionally save it.
6. View all logged expenses or the current group member list. Members can be added after group creation.

Invalid input (unknown user IDs, non-numeric or non-positive amounts,
duplicate or non-letter names) is rejected with an error message and the
user is prompted again.

---

## Inspecting the Data

Open the SQLite database:

```sql
sqlite3 splitsettle.db
.tables
SELECT * FROM users;
SELECT * FROM expenses;
```

`group_members` stores raw `group_id` / `user_id` mappings. The bundled
`group_membership` view resolves these IDs into readable names.

```sql
SELECT *
FROM group_membership
WHERE group_name = 'Goa Trip';
```

---

## Design Notes

- **Algorithm:** Greedy heap-based matching (max-heap of creditors and
  min-heap of debtors). Guarantees at most **n − 1** transactions for
  **n** people and runs in **O(n log n)**.

- **Rounding:** Equal splits use `BigDecimal` with remainder distribution so
  shares always sum exactly to the original amount.

- **Schema:** `users`, `groups`, and a `group_members` join table model the
  many-to-many relationship between people and groups.
  `group_membership` is a read-only view for convenience.

- **Persistence:** SQLite, chosen for a single-user CLI tool to avoid server
  setup and credential management.

- **Schema Initialization:** `SchemaInitializer` executes
  `CREATE TABLE IF NOT EXISTS` statements on every startup, creating the
  schema only if it does not already exist.

- **Data Access:** Raw JDBC with explicit transaction boundaries
  (`commit` / `rollback`) around multi-table operations such as expense
  creation and group creation.

- **Error Handling:** A `SplitSettleException` hierarchy
  (`UnbalancedSharesException`, `UnbalancedLedgerException`,
  `InvalidExpenseException`, and `UnknownUserException`) separates expected
  domain-rule violations from unexpected failures such as `SQLException`.

- **Use Case:** The netting pattern applied here—minimizing transactions
  across many bilateral debts—is the same principle used at larger scale in
  payment platforms and interbank settlement systems.
