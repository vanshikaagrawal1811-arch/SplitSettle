package com.splitsettle;

import com.splitsettle.dao.ExpenseDAO;
import com.splitsettle.dao.GroupDAO;
import com.splitsettle.dao.SettlementDAO;
import com.splitsettle.dao.UserDAO;
import com.splitsettle.db.SchemaInitializer;
import com.splitsettle.exception.SplitSettleException;
import com.splitsettle.exception.UnknownUserException;
import com.splitsettle.model.Expense;
import com.splitsettle.model.ExpenseShare;
import com.splitsettle.model.Group;
import com.splitsettle.model.User;
import com.splitsettle.service.SettlementService;
import com.splitsettle.service.SplitCalculator;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static final String CUR = "Rs. ";
    private static final String ARROW = "->";
    private static final String DIVIDER = "-----------------------------------------";

    private static final Scanner scanner = new Scanner(System.in);
    private static final UserDAO userDAO = new UserDAO();
    private static final GroupDAO groupDAO = new GroupDAO();
    private static final ExpenseDAO expenseDAO = new ExpenseDAO();
    private static final SettlementDAO settlementDAO = new SettlementDAO();
    private static final SettlementService settlementService = new SettlementService();

    private static Group activeGroup;

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        printBanner();

        try {
            SchemaInitializer.ensureSchema();
        } catch (SQLException e) {
            System.out.println("[!] Couldn't set up the local database: " + e.getMessage());
            return;
        }

        try {
            chooseGroup();
        } catch (NoSuchElementException e) {
            System.out.println("\nGoodbye - see you next settle-up!");
            return;
        } catch (SQLException e) {
            System.out.println("[!] Couldn't set up the group (database error): " + e.getMessage());
            return;
        }

        boolean running = true;
        while (running) {
            printMenu();
            String choice;
            try {
                choice = scanner.nextLine().trim();
            } catch (NoSuchElementException e) {
                break;
            }
            try {
                switch (choice) {
                    case "1" -> addExpense();
                    case "2" -> addMember();
                    case "3" -> viewBalances();
                    case "4" -> viewSettlementPlan();
                    case "5" -> viewAllExpenses();
                    case "6" -> viewMembers();
                    case "7" -> running = confirmExit();
                    default -> System.out.println("Please enter a number from 1-7.");
                }
            } catch (SplitSettleException e) {
                System.out.println("[!] " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("[!] Database error: " + e.getMessage());
            }
        }
        System.out.println("\nGoodbye - see you next settle-up!");
    }

    private static void printBanner() {
        System.out.println("=================================");
        System.out.println("        SplitSettle CLI");
        System.out.println("=================================");
        System.out.println("Track group expenses and get the minimum set");
        System.out.println("of payments needed to settle everyone up.");
        System.out.println("(data is stored locally in splitsettle.db)\n");
    }

    private static void chooseGroup() throws SQLException {
        List<GroupDAO.GroupSummary> existing = groupDAO.findAll();

        if (existing.isEmpty()) {
            System.out.println("No groups found yet - let's create your first one.\n");
            createNewGroup();
            return;
        }

        System.out.println("Welcome back! Found " + existing.size()
                + " existing group(s) in splitsettle.db.\n");
        System.out.println("1. Load an existing group");
        System.out.println("2. Create a new group");
        System.out.print("> ");
        String choice = scanner.nextLine().trim();

        if (choice.equals("1")) {
            loadExistingGroup(existing);
        } else {
            createNewGroup();
        }
    }

    private static boolean loadExistingGroup(List<GroupDAO.GroupSummary> existing) throws SQLException {
        System.out.println("\nExisting groups:");
        System.out.println(DIVIDER);
        for (GroupDAO.GroupSummary g : existing) {
            System.out.printf("  #%-4d %-20s (%d members)%n", g.id(), g.name(), g.memberCount());
        }
        System.out.println(DIVIDER);

        while (true) {
            System.out.print("Load which group? (e.g. " + existing.get(0).id() + "): ");
            String input = scanner.nextLine().trim();
            int groupId;
            try {
                groupId = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid group number - try again.");
                continue;
            }

            Group group = groupDAO.findById(groupId);
            if (group == null) {
                System.out.println("No group with # " + groupId + " - try again.");
                continue;
            }

            activeGroup = group;
            System.out.println("\n[OK] Loaded '" + activeGroup.getName() + "' with "
                    + activeGroup.getMembers().size() + " members:");
            activeGroup.getMembers().forEach(m ->
                    System.out.println("  #" + m.getId() + "  " + m.getName()));
            return true;
        }
    }

    private static void createNewGroup() throws SQLException {
        String groupName = readUniqueGroupName("Name this group (e.g. 'Goa Trip'): ");

        System.out.println("\nNow let's add the people in your group.\n");
        List<User> members = new ArrayList<>();
        Set<String> namesUsed = new HashSet<>();

        while (true) {
            System.out.print("Member name (or press Enter when done): ");
            String name = scanner.nextLine().trim();

            if (name.isEmpty()) {
                if (members.size() >= 2) break;
                System.out.println("You need at least 2 people to form a group - keep going.");
                continue;
            }

            if (!isValidPersonName(name)) {
                System.out.println("  [!] Names can only contain letters - try again.\n");
                continue;
            }

            if (namesUsed.contains(name.toLowerCase())) {
                System.out.println("  [!] " + name + " is already in this group - each member needs a unique name. Try again.\n");
                continue;
            }

            User user = userDAO.create(name);
            members.add(user);
            namesUsed.add(name.toLowerCase());
            System.out.println("  [OK] Added " + user.getName() + "\n");
        }

        activeGroup = groupDAO.create(groupName, members);

        System.out.println("\n[OK] Group '" + activeGroup.getName() + "' is ready with " + members.size() + " members:");
        members.forEach(m -> System.out.println("  #" + m.getId() + "  " + m.getName()));
    }

    private static String readUniqueGroupName(String prompt) throws SQLException {
        while (true) {
            String name = readNonEmpty(prompt);
            if (groupDAO.nameExists(name)) {
                System.out.println("  [!] A group named \"" + name + "\" already exists - group names must be unique. Try again.\n");
                continue;
            }
            return name;
        }
    }

    private static boolean isValidPersonName(String name) {
        return name.matches("[A-Za-z]+");
    }

    private static void printMenu() {
        System.out.println("\n" + DIVIDER);
        System.out.println(activeGroup.getName() + "  (" + activeGroup.getMembers().size() + " members)");
        System.out.println(DIVIDER);
        System.out.println("1. Add an expense");
        System.out.println("2. Add a member to this group");
        System.out.println("3. View balances");
        System.out.println("4. View settlement plan (who pays whom)");
        System.out.println("5. View all expenses");
        System.out.println("6. View group members");
        System.out.println("7. Exit");
        System.out.print("> ");
    }

    private static void addExpense() throws SQLException {
        List<User> members = activeGroup.getMembers();

        System.out.println("\nGroup members:");
        members.forEach(u -> System.out.println("  #" + u.getId() + "  " + u.getName()));

        int paidBy = readValidUserId("\nWho paid? (e.g. " + members.get(0).getId() + "): ", members);
        String description = readNonEmpty("What was it for? (e.g. Dinner): ");
        BigDecimal amount = readPositiveAmount("Total amount: ");
        List<Integer> participantIds = readParticipantIds(
                "Split among which #s? (comma-separated, Enter = everyone): ", members);

        List<ExpenseShare> shares = SplitCalculator.splitEqually(amount, participantIds);
        Expense expense = new Expense(activeGroup.getId(), paidBy, description, amount, shares);
        expenseDAO.create(expense);

        String payerName = nameOf(paidBy, members);
        BigDecimal perHead = amount.divide(BigDecimal.valueOf(participantIds.size()), 2, java.math.RoundingMode.HALF_UP);
        System.out.println("\n[OK] " + payerName + " paid " + money(amount) + " for \"" + description
                + "\", split " + participantIds.size() + " ways (" + money(perHead) + " each).");
    }

    private static void addMember() throws SQLException {
        System.out.println();
        String name;
        while (true) {
            name = readNonEmpty("New member's name: ");
            if (!isValidPersonName(name)) {
                System.out.println("  [!] Names can only contain letters - try again.");
                continue;
            }
            final String candidate = name;
            boolean alreadyInGroup = activeGroup.getMembers().stream()
                    .anyMatch(m -> m.getName().equalsIgnoreCase(candidate));
            if (alreadyInGroup) {
                System.out.println("  [!] " + name + " is already in " + activeGroup.getName() + " - names must be unique within a group. Try again.");
                continue;
            }
            break;
        }

        User user = userDAO.create(name);
        groupDAO.addMember(activeGroup.getId(), user.getId());
        activeGroup.addMember(user);
        System.out.println("  [OK] Added " + user.getName() + " to " + activeGroup.getName() + ".");
        System.out.println("  Note: this only affects expenses logged from now on -- past expenses are unchanged.");
    }

    private static void viewBalances() throws SQLException {
        Map<Integer, BigDecimal> balances = getBalances();
        List<User> members = activeGroup.getMembers();

        List<User> sorted = new ArrayList<>(members);
        sorted.sort((a, b) -> {
            BigDecimal balA = balances.getOrDefault(a.getId(), BigDecimal.ZERO);
            BigDecimal balB = balances.getOrDefault(b.getId(), BigDecimal.ZERO);
            return balB.compareTo(balA);
        });

        System.out.println("\nBalances (+ owed money, - owes money):");
        System.out.println(DIVIDER);
        BigDecimal totalPositive = BigDecimal.ZERO;
        for (User u : sorted) {
            BigDecimal bal = balances.getOrDefault(u.getId(), BigDecimal.ZERO);
            String status = bal.compareTo(BigDecimal.ZERO) == 0 ? "settled up"
                    : bal.compareTo(BigDecimal.ZERO) > 0 ? "is owed" : "owes";
            System.out.printf("  %-12s %-10s %s%n", u.getName(), status, money(bal.abs()));
            if (bal.compareTo(BigDecimal.ZERO) > 0) totalPositive = totalPositive.add(bal);
        }
        System.out.println(DIVIDER);
        System.out.println("  Total in motion: " + money(totalPositive));
    }

    private static void viewSettlementPlan() throws SQLException {
        Map<Integer, BigDecimal> balances = getBalances();
        List<SettlementService.Transaction> plan = settlementService.simplifyDebts(balances);

        if (plan.isEmpty()) {
            System.out.println("\n[OK] Everyone is already settled up - no payments needed!");
            return;
        }

        System.out.println("\nSettlement plan - " + plan.size()
                + " payment(s) needed to settle everyone up:");
        System.out.println(DIVIDER);
        int step = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (SettlementService.Transaction t : plan) {
            String from = nameOf(t.fromUserId(), activeGroup.getMembers());
            String to = nameOf(t.toUserId(), activeGroup.getMembers());
            System.out.printf("  %d. %-10s %s %-10s %s%n", step++, from, ARROW, to, money(t.amount()));
            total = total.add(t.amount());
        }
        System.out.println(DIVIDER);
        System.out.println("  Total moved: " + money(total));

        System.out.print("\nSave this plan to the settlements table? (y/n): ");
        String confirm = scanner.nextLine().trim();
        if (confirm.equalsIgnoreCase("y")) {
            settlementDAO.saveSettlementPlan(activeGroup.getId(), plan);
            System.out.println("[OK] Saved.");
        } else {
            System.out.println("Not saved.");
        }
    }

    private static void viewAllExpenses() throws SQLException {
        List<Expense> expenses = expenseDAO.getAllForGroup(activeGroup.getId());

        if (expenses.isEmpty()) {
            System.out.println("\nNo expenses logged yet.");
            return;
        }

        System.out.println("\nAll expenses:");
        System.out.println(DIVIDER);
        BigDecimal total = BigDecimal.ZERO;
        int i = 1;
        for (Expense e : expenses) {
            String payer = nameOf(e.getPaidBy(), activeGroup.getMembers());
            System.out.printf("  %d. %-10s paid %-10s for \"%s\" (%d people)%n",
                    i++, payer, money(e.getAmount()), e.getDescription(), e.getShares().size());
            total = total.add(e.getAmount());
        }
        System.out.println(DIVIDER);
        System.out.println("  Total spent by group: " + money(total));
    }

    private static void viewMembers() {
        List<User> members = activeGroup.getMembers();
        System.out.println("\nGroup members (" + members.size() + "):");
        System.out.println(DIVIDER);
        for (User u : members) {
            System.out.printf("  #%-4d %s%n", u.getId(), u.getName());
        }
        System.out.println(DIVIDER);
    }

    private static boolean confirmExit() {
        System.out.print("Are you sure you want to exit? (y/n): ");
        String confirm = scanner.nextLine().trim();
        return !confirm.equalsIgnoreCase("y");
    }

    private static Map<Integer, BigDecimal> getBalances() throws SQLException {
        Map<Integer, BigDecimal> paid = expenseDAO.getTotalPaidPerUser(activeGroup.getId());
        Map<Integer, BigDecimal> owed = expenseDAO.getTotalOwedPerUser(activeGroup.getId());
        List<Integer> memberIds = groupDAO.getMemberIds(activeGroup.getId());
        return settlementService.computeBalances(memberIds, paid, owed);
    }

    private static String nameOf(int userId, List<User> members) {
        return members.stream()
                .filter(u -> u.getId() == userId)
                .map(User::getName)
                .findFirst()
                .orElse("User #" + userId);
    }

    private static String money(BigDecimal amount) {
        return CUR + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) return input;
            System.out.println("This can't be blank - try again.");
        }
    }

    private static BigDecimal readPositiveAmount(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                BigDecimal amount = new BigDecimal(input);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println("Amount must be greater than zero - try again.");
                    continue;
                }
                return amount;
            } catch (NumberFormatException e) {
                System.out.println("That's not a valid number (e.g. 250.50) - try again.");
            }
        }
    }

    private static int readValidUserId(String prompt, List<User> members) {
        Set<Integer> validIds = members.stream().map(User::getId).collect(Collectors.toSet());
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int id = Integer.parseInt(input);
                if (!validIds.contains(id)) {
                    System.out.println("No member with # " + id + " in this group - try again.");
                    continue;
                }
                return id;
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number - try again.");
            }
        }
    }

    private static List<Integer> readParticipantIds(String prompt, List<User> members) {
        Set<Integer> validIds = members.stream().map(User::getId).collect(Collectors.toSet());
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                return members.stream().map(User::getId).collect(Collectors.toList());
            }

            try {
                List<Integer> ids = Arrays.stream(input.split(","))
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());

                for (Integer id : ids) {
                    if (!validIds.contains(id)) {
                        throw new UnknownUserException(id);
                    }
                }
                return ids;
            } catch (NumberFormatException e) {
                System.out.println("Please enter numbers separated by commas (e.g. 1,2,3) - try again.");
            }
        }
    }
}
