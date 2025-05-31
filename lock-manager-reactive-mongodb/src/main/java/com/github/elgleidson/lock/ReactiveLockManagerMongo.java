package com.github.elgleidson.lock;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import reactor.core.publisher.Mono;

@Slf4j
public class ReactiveLockManagerMongo implements ReactiveLockManager {

  private final ReactiveMongoTemplate reactiveMongoTemplate;
  private final Clock clock;

  public ReactiveLockManagerMongo(ReactiveMongoTemplate reactiveMongoTemplate) {
    this(reactiveMongoTemplate, Clock.systemUTC());
  }

  protected ReactiveLockManagerMongo(ReactiveMongoTemplate reactiveMongoTemplate, Clock clock) {
    this.reactiveMongoTemplate = reactiveMongoTemplate;
    this.clock = clock;
  }

  @Override
  public Mono<Lock> lock(String uniqueIdentifier, Duration expiresIn) {
    return Mono.fromSupplier(() -> createMongoEntity(uniqueIdentifier, expiresIn))
      .flatMap(reactiveMongoTemplate::insert)
      .map(this::convertToLock)
      .onErrorMap(throwable -> {
        if (throwable instanceof DuplicateKeyException) {
          // this is to track concurrent calls
          log.warn("error lock(): lock already acquired on '{}'!", uniqueIdentifier);
          return LockFailureException.alreadyLocked(uniqueIdentifier);
        }
        log.error("error lock(): message={}", throwable.getMessage());
        return LockFailureException.other(uniqueIdentifier, throwable);
      })
      .doFirst(() -> log.debug("trying to acquire lock for {}, expiring in {}", uniqueIdentifier, expiresIn))
      .doOnSuccess(lock -> log.debug("locked={}", lock));
  }

  @Override
  public Mono<Boolean> unlock(Lock lock) {
    // only unlocks if lock id and unique identifier match
    var query = query(where("id").is(lock.id()).and("uniqueIdentifier").is(lock.uniqueIdentifier())).limit(1);
    return reactiveMongoTemplate.remove(query, LockMongoEntity.class)
      .map(deleteResult -> deleteResult.getDeletedCount() > 0)
      .defaultIfEmpty(false)
      .onErrorResume(throwable -> {
        // log the error, but returns successfully as the lock will expire (TTL)
        log.error("error unlock(): message={}", throwable.getMessage());
        return Mono.just(false);
      })
      .doFirst(() -> log.debug("trying to unlock {}", lock))
      .doOnSuccess(unlocked -> log.debug("unlocked={}", unlocked));
  }

  private Lock convertToLock(LockMongoEntity lockMongoEntity) {
    return new Lock(lockMongoEntity.id(), lockMongoEntity.uniqueIdentifier(), lockMongoEntity.expiresAt().atZone(ZoneOffset.UTC));
  }

  private LockMongoEntity createMongoEntity(String uniqueIdentifier, Duration expiresIn) {
    var expiresAt = LocalDateTime.now(clock).plus(expiresIn);
    return new LockMongoEntity(null, uniqueIdentifier, expiresAt);
  }

  @Document("locks")
  record LockMongoEntity(
    @Id
    String id,
    @Indexed(unique = true, name = "uniqueIdentifier")
    String uniqueIdentifier,
    @Indexed(expireAfter = "0s", name = "expiresAt")
    LocalDateTime expiresAt
  ) {
  }
}
