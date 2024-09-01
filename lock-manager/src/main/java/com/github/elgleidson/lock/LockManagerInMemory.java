package com.github.elgleidson.lock;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LockManagerInMemory implements LockManager {

  private final Map<String, String> locks = new ConcurrentHashMap<>();
  private final Clock clock;
  private final UUIDWrapper uuidWrapper;

  public LockManagerInMemory() {
    this(Clock.systemUTC(), new UUIDWrapper());
  }

  public LockManagerInMemory(Clock clock, UUIDWrapper uuidWrapper) {
    log.warn("*** THIS LOCK MANAGER ONLY WORKS FOR A SINGLE INSTANCE! ***");
    log.warn("Consider using another implementation!");
    this.clock = clock;
    this.uuidWrapper = uuidWrapper;
  }

  @Override
  public Lock lock(String uniqueIdentifier, Duration expiresIn) {
    try {
      log.debug("trying to acquire lock for {}, expiring in {}", uniqueIdentifier, expiresIn);
      var lock = createLock(uniqueIdentifier, expiresIn);
      var id = locks.putIfAbsent(uniqueIdentifier, lock.id());
      if (id != null) {
        log.warn("error lock(): lock already acquired on '{}'!", uniqueIdentifier);
        throw LockFailureException.alreadyLocked(uniqueIdentifier);
      }
      log.debug("locked={}", lock);
      return lock;
    } catch (LockFailureException lockFailureException) {
      throw lockFailureException;
    } catch (Exception ex) {
      log.error("error lock(): message={}", ex.getMessage());
      throw LockFailureException.other(uniqueIdentifier, ex);
    }
  }

  @Override
  public boolean unlock(Lock lock) {
    try {
      log.debug("trying to unlock {}", lock);
      // only unlocks if the lock id matches as uniqueIdentifier is the cache key
      // if the value is different it means either the lock has already expired or it was released and other process has acquired the lock on the same unique identifier
      // in this case, does not unlock it as it needs to be unlocked by the process that has acquired the lock, or it will expire automatically
      // log it for tracking purposes!
      var unlocked = locks.remove(lock.uniqueIdentifier(), lock.id());
      if (!unlocked && locks.containsKey(lock.uniqueIdentifier())) {
        log.warn("unlock(): another process has acquired the lock on '{}'", lock.uniqueIdentifier());
      }
      log.debug("unlocked={}", unlocked);
      return unlocked;
    } catch (Exception ex) {
      // log the error, but returns successfully as the lock will expire (TTL)
      log.error("error unlock(): message={}", ex.getMessage());
      return false;
    }
  }

  private Lock createLock(String uniqueIdentifier, Duration expiresIn) {
    var id = uuidWrapper.randomUUID().toString();
    var expiresAt = ZonedDateTime.now(clock).plus(expiresIn);
    return new Lock(id, uniqueIdentifier, expiresAt);
  }

  protected static class UUIDWrapper {

    protected UUID randomUUID() {
      return UUID.randomUUID();
    }
  }
}
