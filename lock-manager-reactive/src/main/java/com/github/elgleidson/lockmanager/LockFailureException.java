package com.github.elgleidson.lockmanager;

public class LockFailureException extends RuntimeException {

  private LockFailureException(String message) {
    super(message);
  }

  private LockFailureException(String message, Throwable cause) {
    super(message, cause);
  }

  public static LockFailureException alreadyLocked(String uniqueIdentifier) {
    return new LockFailureException("Lock already acquired on '" + uniqueIdentifier + "'!");
  }

  public static LockFailureException other(String uniqueIdentifier, Throwable cause) {
    return new LockFailureException("Failed to acquire lock on '" + uniqueIdentifier + "'", cause);
  }
}
