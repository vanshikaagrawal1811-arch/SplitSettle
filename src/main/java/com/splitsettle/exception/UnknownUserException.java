package com.splitsettle.exception;

/** Thrown when a user id entered doesn't belong to the active group. */
public class UnknownUserException extends SplitSettleException {
    public UnknownUserException(int userId) {
        super("User #" + userId + " is not a member of this group.");
    }
}
