package com.github.elgleidson.lock;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

@Slf4j
public class ReactiveLockManagerRedis implements ReactiveLockManager {

  protected static final String KEYSPACE = "lock:";

  private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
  private final Clock clock;
  private final UUIDWrapper uuidWrapper;

  @Autowired
  public ReactiveLockManagerRedis(ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
    this(reactiveStringRedisTemplate, Clock.systemUTC(), new UUIDWrapper());
  }

  public ReactiveLockManagerRedis(ReactiveStringRedisTemplate reactiveStringRedisTemplate, Clock clock, UUIDWrapper uuidWrapper) {
    this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
    this.clock = clock;
    this.uuidWrapper = uuidWrapper;
  }

  @Override
  public Mono<Lock> lock(String uniqueIdentifier, Duration expiresIn) {
    return Mono.fromSupplier(() -> createLock(uniqueIdentifier, expiresIn))
      .flatMap(lock -> reactiveStringRedisTemplate.opsForValue()
        .setIfAbsent(lockKey(uniqueIdentifier), lock.id(), expiresIn)
        .onErrorMap(throwable -> {
          // severity 1 alert as we should be able to acquire a lock
          log.error("error lock(): message={}", throwable.getMessage());
          return LockFailureException.other(uniqueIdentifier, throwable);
        })
        .flatMap(inserted -> {
          if (!Boolean.TRUE.equals(inserted)) {
            // this is to track concurrent calls
            log.warn("error lock(): lock already acquired on '{}'!", uniqueIdentifier);
            return Mono.error(LockFailureException.alreadyLocked(uniqueIdentifier));
          }
          return Mono.just(lock);
        })
      );
  }

  @Override
  public Mono<Long> unlock(Lock lock) {
    // only unlocks if the lock id matches as uniqueIdentifier is the cache key
    var lockKey = lockKey(lock.uniqueIdentifier());
    return reactiveStringRedisTemplate.opsForValue().get(lockKey)
      .flatMap(value -> {
        // if the value is different it means either the lock has already expired or it was released and other process has acquired the lock on the same unique identifier
        // in this case, does not unlock it as it needs to be unlocked by the process that has acquired the lock, or it will expire automatically
        // log it for tracking purposes!
        if (!lock.id().equals(value)) {
          log.warn("unlock(): another process has acquired the lock on '{}'", lock.uniqueIdentifier());
          return Mono.just(0L);
        }
        return reactiveStringRedisTemplate.delete(lockKey);
      })
      .defaultIfEmpty(0L)
      .onErrorResume(throwable -> {
        // log the error, but returns successfully as the lock will expire (TTL)
        log.error("error unlock(): message={}", throwable.getMessage());
        return Mono.just(0L);
      });
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
