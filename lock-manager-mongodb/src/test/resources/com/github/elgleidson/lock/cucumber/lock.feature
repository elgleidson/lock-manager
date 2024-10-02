Feature: Lock manager

  Background:
    Given the lock expires in 30s
    Given the process takes 150ms

  Scenario: Update - single call
    Given an existing record with id of "123"
    When I call the update 1 time sequentially with id "123"
    Then the record with id "123" is updated 1 time

  Scenario: Update - multiple calls sequentially
    Given an existing record with id of "123-ms"
    When I call the update 3 times sequentially with id "123-ms"
    Then the record with id "123-ms" is updated 3 times

  Scenario: Update - multiple calls concurrently
    Given an existing record with id of "123-mc"
    When I call the update 3 times concurrently with id "123-mc"
    # it updates the record 3 times because this method is NOT locking the record
    Then the record with id "123-mc" is updated 3 times

  Scenario: Lock Update - single call
    Given an existing record with id of "123-lock"
    When I call the lock update 1 time sequentially with id "123-lock"
    Then the record with id "123-lock" is updated 1 time

  Scenario: Lock Update - multiple calls sequentially
    Given an existing record with id of "123-lock-ms"
    When I call the lock update 3 times sequentially with id "123-lock-ms"
    # it updates the record 3 times because even though this method is locking the record, the calls are made sequentially,
    # which unlocks the record at the end of every call, making the next call to acquire a lock and update the record again.
    Then the record with id "123-lock-ms" is updated 3 times

  Scenario: Lock Update - multiple calls concurrently
    Given an existing record with id of "123-lock-mc"
    When I call the lock update 3 times concurrently with id "123-lock-mc"
    Then the record with id "123-lock-mc" is updated 1 time

  Scenario: Can acquire lock after unlock
    Given an existing record with id of "123-unlocked"
    When I try to lock the record with id of "123-unlocked"
    Then the lock is acquired
    Given I unlock
    When I try to lock the record with id of "123-unlocked"
    Then the lock is acquired

  Scenario: Cannot acquire lock during expiration window
    Given an existing record with id of "123-not-expired"
    Given the lock expires in 1s
    When I try to lock the record with id of "123-not-expired"
    Then the lock is acquired
    Given I wait 500ms
    When I try to lock the record with id of "123-not-expired"
    Then the lock is not acquired

#  commented because it doesn't work with embedded mongo
#  Scenario: Can acquire lock after expiration window
#    Given an existing record with id of "123-expired"
#    Given the lock expires in 1s
#    When I try to lock the record with id of "123-expired"
#    Then the lock is acquired
#    Given I wait 1s
#    When I try to lock the record with id of "123-expired"
#    Then the lock is acquired