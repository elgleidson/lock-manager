Feature: Lock manager

  Background:
    Given an existing record with id of "123"
    Given the lock expires in 30s
    Given the process takes 150ms

  Scenario: Update - single call
    When I call the update 1 time sequentially with id "123"
    Then the record with id "123" is updated 1 time

  Scenario: Update - multiple calls sequentially
    When I call the update 3 times sequentially with id "123"
    Then the record with id "123" is updated 3 times

  Scenario: Update - multiple calls concurrently
    When I call the update 3 times concurrently with id "123"
    # it updates the record 3 times because this method is NOT locking the record
    Then the record with id "123" is updated 3 times

  Scenario: Lock Update - single call
    When I call the lock update 1 time sequentially with id "123"
    Then the record with id "123" is updated 1 time

  Scenario: Lock Update - multiple calls sequentially
    When I call the lock update 3 times sequentially with id "123"
    # it updates the record 3 times because even though this method is locking the record, the calls are made sequentially,
    # which unlocks the record at the end of every call, making the next call to acquire a lock and update the record again.
    Then the record with id "123" is updated 3 times

  Scenario: Lock Update - multiple calls concurrently
    When I call the lock update 3 times concurrently with id "123"
    Then the record with id "123" is updated 1 time