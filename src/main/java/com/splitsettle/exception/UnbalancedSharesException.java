package com.splitsettle.exception;

import java.math.BigDecimal;

/** Thrown when an expense's shares don't sum to its total amount. */
public class UnbalancedSharesException extends SplitSettleException {
    public UnbalancedSharesException(BigDecimal sharesSum, BigDecimal expenseAmount) {
        super("Shares (" + sharesSum + ") do not sum to expense amount (" + expenseAmount + ")");
    }
}
