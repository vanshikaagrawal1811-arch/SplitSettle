package com.splitsettle.exception;

/**
 * Base type for all domain-rule violations in SplitSettle (bad splits, broken
 * ledgers, invalid input). Unchecked, since these represent programming/data
 * errors the caller should either prevent upstream or handle explicitly.
 *
 * Catching this one type at the CLI boundary is enough to handle any
 * domain-specific failure with a clean message, while letting unrelated
 * failures (like SQLException) still surface as unexpected errors.
 */
public class SplitSettleException extends RuntimeException {
    public SplitSettleException(String message) {
        super(message);
    }
}
