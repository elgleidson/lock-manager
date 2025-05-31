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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Slf4j
public class LockManagerMongo implements LockManager {

  private final MongoTemplate mongoTemplate;
  private final Clock clock;

  public LockManagerMongo(MongoTemplate mongoTemplate) {
    this(mongoTemplate, Clock.systemUTC());
  }

  protected LockManagerMongo(MongoTemplate mongoTemplate, Clock clock) {
    this.mongoTemplate = mongoTemplate;
    this.clock = clock;
  }

  @Override
  public Lock lock(String uniqueIdentifier, Duration expiresIn) {
    try {
      log.debug("trying to acquire lock for {}, expiring in {}", uniqueIdentifier, expiresIn);
      var lockMongoEntity = createMongoEntity(uniqueIdentifier, expiresIn);
      var inserted = mongoTemplate.insert(lockMongoEntity);
      var lock = convertToLock(inserted);
      log.debug("locked={}", lock);
      return lock;
    } catch (Exception ex) {
      if (ex instanceof DuplicateKeyException) {
        // this is to track concurrent calls
        log.warn("error lock(): lock already acquired on '{}'!", uniqueIdentifier);
        throw LockFailureException.alreadyLocked(uniqueIdentifier);
      }
      log.error("error lock(): message={}", ex.getMessage());
      throw LockFailureException.other(uniqueIdentifier, ex);
    }
  }

  @Override
  public boolean unlock(Lock lock) {
    try {
      log.debug("trying to unlock {}", lock);
      // only unlocks if lock id and unique identifier match
      var query = query(where("id").is(lock.id()).and("uniqueIdentifier").is(lock.uniqueIdentifier())).limit(1);
      var removed = mongoTemplate.remove(query, LockMongoEntity.class);
      var unlocked = removed.getDeletedCount() > 0;
      log.debug("unlocked={}", unlocked);
      return unlocked;
    } catch (Exception ex) {
      // log the error, but returns successfully as the lock will expire (TTL)
      log.error("error unlock(): message={}", ex.getMessage());
      return false;
    }
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
