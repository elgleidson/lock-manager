package com.github.elgleidson.lock;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
public class LockManagerRedis implements LockManager {

  protected static final String KEYSPACE = "lock:";

  private final StringRedisTemplate stringRedisTemplate;
  private final Clock clock;
  private final UUIDWrapper uuidWrapper;

  public LockManagerRedis(StringRedisTemplate stringRedisTemplate) {
    this(stringRedisTemplate, Clock.systemUTC(), new UUIDWrapper());
  }

  protected LockManagerRedis(StringRedisTemplate stringRedisTemplate, Clock clock, UUIDWrapper uuidWrapper) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.clock = clock;
    this.uuidWrapper = uuidWrapper;
  }

  @Override
  public Lock lock(String uniqueIdentifier, Duration expiresIn) {
    try {
      var lock = createLock(uniqueIdentifier, expiresIn);
      var inserted = stringRedisTemplate.opsForValue().setIfAbsent(lockKey(uniqueIdentifier), lock.id(), expiresIn);
      if (!Boolean.TRUE.equals(inserted)) {
        // this is to track concurrent calls
        log.warn("error lock(): lock already acquired on '{}'!", uniqueIdentifier);
        throw LockFailureException.alreadyLocked(uniqueIdentifier);
      }
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
      // only unlocks if the lock id matches as uniqueIdentifier is the cache key
      var lockKey = lockKey(lock.uniqueIdentifier());
      var value = stringRedisTemplate.opsForValue().get(lockKey);
      // if the value is different it means either the lock has already expired or it was released and other process has acquired the lock on the same unique identifier
      // in this case, does not unlock it as it needs to be unlocked by the process that has acquired the lock, or it will expire automatically
      // log it for tracking purposes!
      if (!lock.id().equals(value)) {
        log.warn("unlock(): another process has acquired the lock on '{}'", lock.uniqueIdentifier());
        return false;
      }
      var unlocked = stringRedisTemplate.delete(lockKey);
      log.debug("unlocked={}", unlocked);
      return Boolean.TRUE.equals(unlocked);
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

  private String lockKey(String uniqueIdentifier) {
    return KEYSPACE + uniqueIdentifier;
  }

  protected static class UUIDWrapper {

    protected UUID randomUUID() {
      return UUID.randomUUID();
    }
  }

}
