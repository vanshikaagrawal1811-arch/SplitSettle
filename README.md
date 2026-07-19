# SplitSettle — Debt-Simplification Engine for Group Expenses

A group expense tracker (roommates, trips, shared events) where the core
problem isn't splitting bills — it's minimizing settlement friction. Given a
group with arbitrary many-to-many debts (A owes B, B owes C, C owes A), the
engine uses a greedy max-heap matching algorithm to compute the minimum
number of transactions needed to settle every balance to zero.

## Stack
- Java 17 + JDBC (SQLite) — no ORM, no framework, no server to install
- Command-line interface (Scanner-based)
- JUnit 5 for algorithm tests

## Setup
1. Nothing to install beyond Java and Maven — SQLite is embedded, so there's
   no separate database server, no user/password, no service to start.
2. Run: `mvn compile exec:java -Dexec.mainClass=com.splitsettle.Main`
   (in PowerShell, quote each argument:
   `mvn compile "exec:java" "-Dexec.mainClass=com.splitsettle.Main"`)
3. On first run, the app automatically creates `splitsettle.db` in the
   project folder and sets up all tables. Nothing else to configure.
4. Run tests: `mvn test`

Optional: override where the database file lives with the
`SPLITSETTLE_DB_PATH` environment variable (defaults to `splitsettle.db`
in the current directory).

## How it works
1. On startup, choose to load an existing group from `splitsettle.db` or
   create a new one. New groups get a unique name and a list of members
   (letters-only names, no duplicates within the same group).
2. Add expenses through the menu: who paid, what it was for, the amount, and
   who it's split among (defaults to everyone if left blank). You can also
   add a member to the active group at any time.
3. View live balances — plain-language output like "Bob owes Rs. 150.00".
4. View the settlement plan — the minimized set of "X -> Y" payments, with a
   confirmation step before it's saved.
5. View all logged expenses, or the full group member list, at any time.

Bad input (invalid user IDs, non-numeric amounts, zero/negative amounts,
duplicate or non-letter names) is caught and re-prompted inline instead of
crashing the session.

## Inspecting the data directly
Since it's just a file, you can open it anytime with the `sqlite3` CLI
(even while the app isn't running):
```
sqlite3 splitsettle.db
.tables
SELECT * FROM users;
SELECT * FROM expenses;
```

## Design decisions worth knowing
- **Algorithm**: greedy heap-based matching (max-heap of creditors, min-heap
  of debtors), not a globally-optimal min-transaction search — that's
  NP-hard in general (equivalent to a subset-sum/partition problem). Greedy
  guarantees at most n-1 transactions for n people, and does better whenever
  debts partially cancel out. Runs in O(n log n).
- **Rounding**: equal splits use BigDecimal with a remainder-distribution
  scheme so shares always sum exactly to the original amount — no lost paise.
- **SQLite over a client-server DB**: this is a single-user CLI tool, so an
  embedded, file-based database removes an entire class of setup friction
  (installing a server, managing credentials, keeping a service running)
  with no real downside at this scale.
- **Self-initializing schema**: `SchemaInitializer` runs bundled
  `CREATE TABLE IF NOT EXISTS` statements on every startup, so there's no
  separate "load the schema" step for a new user to remember — the app sets
  itself up the first time it runs, and leaves existing data untouched on
  every run after that.
- **No ORM**: raw JDBC, to directly control transaction boundaries around
  multi-table writes (expense + shares, group + members) using explicit
  commit/rollback. Generated IDs are retrieved via
  `Statement.RETURN_GENERATED_KEYS`.
- **Custom exceptions**: a `SplitSettleException` hierarchy
  (`UnbalancedSharesException`, `UnbalancedLedgerException`,
  `InvalidExpenseException`, `UnknownUserException`) distinguishes expected
  domain-rule violations from unexpected failures like `SQLException`,
  letting the CLI show a clean message and keep running instead of crashing.

## Known limitations (deliberately out of scope)
- SQLite's per-file write-locking means it isn't built for many concurrent
  writers — a non-issue for a single-user CLI tool, but a real constraint
  if this were ever turned into a multi-user API.
- No concurrency protection on settlements (e.g. optimistic locking) —
  acceptable at this scale.
- Greedy settlement isn't provably optimal — true minimum-transaction
  settlement is NP-hard.

## Use case
Built for group expense settling among friends — the same netting pattern
(minimizing transactions across many bilateral debts) shows up at larger
scale in payment platforms and interbank settlement systems, just applied
here at friend-group scale.
