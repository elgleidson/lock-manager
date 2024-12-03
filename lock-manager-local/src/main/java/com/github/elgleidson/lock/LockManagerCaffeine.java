package com.github.elgleidson.lock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LockManagerCaffeine implements LockManager {

  private final Cache<String, Lock> locks;
  private final Clock clock;
  private final UUIDWrapper uuidWrapper;

  public LockManagerCaffeine() {
    this(Clock.systemUTC(), new UUIDWrapper());
  }

  protected LockManagerCaffeine(Clock clock, UUIDWrapper uuidWrapper) {
    this(Caffeine.newBuilder().expireAfter(new LockExpiry(clock)).build(), clock, uuidWrapper);
  }

  protected LockManagerCaffeine(Cache<String, Lock> cache, Clock clock, UUIDWrapper uuidWrapper) {
    log.warn("*** THIS LOCK MANAGER IS NOT SUITABLE FOR MULTI INSTANCES SCENARIOS. PLEASE USE ANOTHER IMPLEMENTATION! ***");
    this.locks = cache;
    this.clock = clock;
    this.uuidWrapper = uuidWrapper;
  }

  @Override
  public synchronized Lock lock(String uniqueIdentifier, Duration expiresIn) {
    try {
      var locked = locks.getIfPresent(uniqueIdentifier);
      if (locked != null) {
        // this is to track concurrent calls
        log.warn("error lock(): lock already acquired on '{}'!", uniqueIdentifier);
        throw LockFailureException.alreadyLocked(uniqueIdentifier);
      }
      var lock = createLock(uniqueIdentifier, expiresIn);
      locks.put(uniqueIdentifier, lock);
      log.debug("locked={}", lock);
      return lock;
    } catch (Exception ex) {
      if (ex instanceof LockFailureException) {
        throw ex;
      }
      log.error("error lock(): message={}", ex.getMessage());
      throw LockFailureException.other(uniqueIdentifier, ex);
    }
  }

  @Override
  public synchronized boolean unlock(Lock lock) {
    try {
      // only unlocks if the lock id matches as uniqueIdentifier is the cache key
      var locked = locks.getIfPresent(lock.uniqueIdentifier());
      // if the value is different it means either the lock has already expired or it was released and other process has acquired the lock on the same unique identifier
      // in this case, does not unlock it as it needs to be unlocked by the process that has acquired the lock, or it will expire automatically
      // log it for tracking purposes!
      if (locked == null) {
        return false;
      }
      if (!lock.id().equals(locked.id())) {
        log.warn("unlock(): another process has acquired the lock on '{}'", lock.uniqueIdentifier());
        return false;
      }
      locks.invalidate(lock.uniqueIdentifier());
      var unlocked = true;
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

  private static class LockExpiry implements Expiry<String, Lock> {

    private final Clock clock;

    public LockExpiry(Clock clock) {
      this.clock = clock;
    }

    @Override
    public long expireAfterCreate(String uniqueIdentifier, Lock lock, long currentTime) {
      return Duration.between(Instant.now(clock), lock.expiresAt()).toNanos();
    }

    @Override
    public long expireAfterUpdate(String uniqueIdentifier, Lock lock, long currentTime, long currentDuration) {
      return currentDuration;
    }

    @Override
    public long expireAfterRead(String uniqueIdentifier, Lock lock, long currentTime, long currentDuration) {
      return currentDuration;
    }
  }
}
