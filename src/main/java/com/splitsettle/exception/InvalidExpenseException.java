package com.splitsettle.exception;

/** Thrown when an expense request itself is malformed (e.g. no participants). */
public class InvalidExpenseException extends SplitSettleException {
    public InvalidExpenseException(String message) {
        super(message);
    }
}
