package com.splitsettle.service;

import com.splitsettle.exception.UnbalancedLedgerException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettlementServiceTest {

    private final SettlementService service = new SettlementService();

    @Test
    void perfectlyMatchedPairNeedsOneTransaction() {
        Map<Integer, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1, new BigDecimal("0.00"));
        balances.put(2, new BigDecimal("50.00"));
        balances.put(3, new BigDecimal("-50.00"));

        List<SettlementService.Transaction> plan = service.simplifyDebts(balances);

        assertEquals(1, plan.size());
        assertEquals(3, plan.get(0).fromUserId());
        assertEquals(2, plan.get(0).toUserId());
        assertEquals(0, new BigDecimal("50.00").compareTo(plan.get(0).amount()));
    }

    @Test
    void fourPersonGroupNeedsOnlyTwoTransactions() {
        Map<Integer, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1, new BigDecimal("300.00"));
        balances.put(2, new BigDecimal("-100.00"));
        balances.put(3, new BigDecimal("-200.00"));
        balances.put(4, new BigDecimal("0.00"));

        List<SettlementService.Transaction> plan = service.simplifyDebts(balances);

        assertEquals(2, plan.size());
        BigDecimal totalSettled = plan.stream().map(SettlementService.Transaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("300.00").compareTo(totalSettled));
    }

    @Test
    void unbalancedLedgerThrowsInsteadOfSilentlyMisSettling() {
        Map<Integer, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1, new BigDecimal("100.00"));
        balances.put(2, new BigDecimal("-50.00"));

        assertThrows(UnbalancedLedgerException.class, () -> service.simplifyDebts(balances));
    }

    @Test
    void allZeroBalancesProduceNoTransactions() {
        Map<Integer, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1, BigDecimal.ZERO);
        balances.put(2, BigDecimal.ZERO);

        assertTrue(service.simplifyDebts(balances).isEmpty());
    }

    @Test
    void tinyRoundingDustIsNotTreatedAsRealDebt() {
        Map<Integer, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1, new BigDecimal("0.001"));
        balances.put(2, new BigDecimal("-0.001"));

        assertTrue(service.simplifyDebts(balances).isEmpty());
    }
}
