Feature: Lock manager

  Background:
    Given an existing record with id of "123"

  Scenario: Update - single call
    When I call the update 1 time sequentially with id "123"
    Then the record with id "123" is updated 1 time

  Scenario: Update - multiple calls sequentially
    When I call the update 5 times sequentially with id "123"
    # it updates the record 5 times because this method is NOT locking the record
    Then the record with id "123" is updated 5 times

  Scenario: Update - multiple calls concurrently
    When I call the update 5 times concurrently with id "123"
    # it updates the record 5 times because this method is NOT locking the record
    Then the record with id "123" is updated 5 times

  Scenario: Lock Update - single call
    When I call the lock update 1 time sequentially with id "123"
    Then the record with id "123" is updated 1 time

  Scenario: Lock Update - multiple calls sequentially
    When I call the lock update 5 times sequentially with id "123"
    # it updates the record 5 times because even though this method is locking the record, the calls are made sequentially,
    # which unlocks the record at the end of every call, making the next call to acquire a lock and update the record again.
    Then the record with id "123" is updated 5 times

  Scenario: Lock Update - multiple calls concurrently
    When I call the lock update 5 times concurrently with id "123"
    # for testing purposes:
    # CucumberSteps is handling the LockingFailedException thrown by the LockManager and returning LOCKED
    Then the record with id "123" is updated 1 time