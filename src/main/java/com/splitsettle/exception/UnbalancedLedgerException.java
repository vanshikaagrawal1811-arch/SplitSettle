package com.splitsettle.exception;

import java.math.BigDecimal;

/** Thrown when total credit and total debt across a group's balances don't match. */
public class UnbalancedLedgerException extends SplitSettleException {
    public UnbalancedLedgerException(BigDecimal totalCredit, BigDecimal totalDebt) {
        super("Ledger does not balance: total credit=" + totalCredit + " total debt=" + totalDebt
                + ". An expense was recorded with shares that don't sum to its amount.");
    }
}
