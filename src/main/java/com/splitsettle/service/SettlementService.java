package com.splitsettle.service;

import com.splitsettle.exception.UnbalancedLedgerException;

import java.math.BigDecimal;
import java.util.*;

public class SettlementService {

    public record Transaction(int fromUserId, int toUserId, BigDecimal amount) {
    }

    /**
     * Given each user's net balance (positive = is owed money, negative = owes money),
     * returns the minimum-ish set of transactions that zeroes everyone out.
     *
     * Algorithm: greedy max-heap matching.
     *   1. Split users into creditors (balance > 0) and debtors (balance < 0).
     *   2. Repeatedly take the biggest creditor and biggest debtor.
     *   3. Settle min(|creditor balance|, |debtor balance|) between them.
     *   4. Push whichever side still has a nonzero balance back into its heap.
     *   5. Repeat until both heaps are empty.
     *
     * NOT guaranteed globally optimal — true minimum-transaction settlement is
     * NP-hard in general (reduces to a subset-sum/partition problem). Greedy is
     * the standard practical approximation; it's provably at most (n-1)
     * transactions for n participants, and usually does better.
     *
     * BigDecimal (not double) avoids floating point misclassifying a balance
     * as slightly nonzero. Anything with |balance| < 0.005 is treated as
     * settled to absorb rounding dust from SplitCalculator.
     */
    public List<Transaction> simplifyDebts(Map<Integer, BigDecimal> balances) {
        BigDecimal epsilon = new BigDecimal("0.005");

        PriorityQueue<Map.Entry<Integer, BigDecimal>> creditors =
                new PriorityQueue<>((a, b) -> b.getValue().compareTo(a.getValue())); // max-heap
        PriorityQueue<Map.Entry<Integer, BigDecimal>> debtors =
                new PriorityQueue<>((a, b) -> a.getValue().compareTo(b.getValue())); // min-heap

        for (Map.Entry<Integer, BigDecimal> entry : balances.entrySet()) {
            BigDecimal bal = entry.getValue();
            if (bal.compareTo(epsilon) > 0) {
                creditors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), bal));
            } else if (bal.negate().compareTo(epsilon) > 0) {
                debtors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), bal));
            }
        }

        BigDecimal totalCredit = balances.values().stream()
                .filter(b -> b.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDebt = balances.values().stream()
                .filter(b -> b.compareTo(BigDecimal.ZERO) < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .negate();
        if (totalCredit.subtract(totalDebt).abs().compareTo(epsilon) > 0) {
            throw new UnbalancedLedgerException(totalCredit, totalDebt);
        }

        List<Transaction> transactions = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Map.Entry<Integer, BigDecimal> topCreditor = creditors.poll();
            Map.Entry<Integer, BigDecimal> topDebtor = debtors.poll();

            BigDecimal owed = topDebtor.getValue().negate();
            BigDecimal settled = owed.min(topCreditor.getValue());

            transactions.add(new Transaction(topDebtor.getKey(), topCreditor.getKey(), settled));

            BigDecimal creditorRemaining = topCreditor.getValue().subtract(settled);
            BigDecimal debtorRemaining = owed.subtract(settled).negate();

            if (creditorRemaining.compareTo(epsilon) > 0) {
                creditors.add(new AbstractMap.SimpleEntry<>(topCreditor.getKey(), creditorRemaining));
            }
            if (debtorRemaining.negate().compareTo(epsilon) > 0) {
                debtors.add(new AbstractMap.SimpleEntry<>(topDebtor.getKey(), debtorRemaining));
            }
        }

        return transactions;
    }

    public Map<Integer, BigDecimal> computeBalances(
            List<Integer> allUserIds,
            Map<Integer, BigDecimal> totalPaidPerUser,
            Map<Integer, BigDecimal> totalOwedPerUser) {

        Map<Integer, BigDecimal> balances = new HashMap<>();
        for (Integer userId : allUserIds) {
            BigDecimal paid = totalPaidPerUser.getOrDefault(userId, BigDecimal.ZERO);
            BigDecimal owed = totalOwedPerUser.getOrDefault(userId, BigDecimal.ZERO);
            balances.put(userId, paid.subtract(owed));
        }
        return balances;
    }
}
