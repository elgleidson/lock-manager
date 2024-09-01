package com.github.elgleidson.lock;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class ReactiveLockManagerInMemory implements ReactiveLockManager {

  private final Map<String, String> locks = new ConcurrentHashMap<>();

  private final Clock clock;
  private final UUIDWrapper uuidWrapper;

  public ReactiveLockManagerInMemory() {
    this(Clock.systemUTC(), new UUIDWrapper());
  }

  public ReactiveLockManagerInMemory(Clock clock, UUIDWrapper uuidWrapper) {
    log.warn("*** THIS LOCK MANAGER ONLY WORKS FOR A SINGLE INSTANCE! ***");
    log.warn("Consider using another implementation!");
    this.clock = clock;
    this.uuidWrapper = uuidWrapper;
  }

  @Override
  public Mono<Lock> lock(String uniqueIdentifier, Duration expiresIn) {
    return Mono.fromSupplier(() -> createLock(uniqueIdentifier, expiresIn))
      .flatMap(lock -> {
        var id = locks.putIfAbsent(uniqueIdentifier, lock.id());
        if (id != null) {
          log.warn("error lock(): lock already acquired on '{}'!", uniqueIdentifier);
          return Mono.error(LockFailureException.alreadyLocked(uniqueIdentifier));
        }
        return Mono.just(lock);
      })
      .onErrorMap(throwable -> {
        if (throwable instanceof LockFailureException) {
          return throwable;
        }
        log.error("error lock(): message={}", throwable.getMessage());
        return LockFailureException.other(uniqueIdentifier, throwable);
      })
      .doFirst(() -> log.debug("trying to acquire lock for {}, expiring in {}", uniqueIdentifier, expiresIn))
      .doOnSuccess(lock -> log.debug("locked={}", lock));
  }

  @Override
  public Mono<Boolean> unlock(Lock lock) {
    // only unlocks if the lock id matches as uniqueIdentifier is the cache key
    return Mono.fromSupplier(() -> locks.remove(lock.uniqueIdentifier(), lock.id()))
      .flatMap(removed -> {
        // if the value is different it means either the lock has already expired or it was released and other process has acquired the lock on the same unique identifier
        // in this case, does not unlock it as it needs to be unlocked by the process that has acquired the lock, or it will expire automatically
        // log it for tracking purposes!
        if (!Boolean.TRUE.equals(removed)) {
          log.warn("unlock(): another process has acquired the lock on '{}'", lock.uniqueIdentifier());
        }
        return Mono.just(removed);
      })
      .onErrorResume(throwable -> {
        // log the error, but returns successfully as the lock will expire (TTL)
        log.error("error unlock(): message={}", throwable.getMessage());
        return Mono.just(false);
      })
      .doFirst(() -> log.debug("trying to unlock {}", lock))
      .doOnSuccess(unlocked -> log.debug("unlocked={}", unlocked));
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
