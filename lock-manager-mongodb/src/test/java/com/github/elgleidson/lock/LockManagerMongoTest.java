package com.github.elgleidson.lock;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.elgleidson.lock.LockManagerMongo.LockMongoEntity;
import com.mongodb.client.result.DeleteResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class LockManagerMongoTest {

  private static final Instant NOW = Instant.now();
  private static final Clock CLOCK = Clock.fixed(NOW, UTC);

  private static final String UNIQUE_IDENTIFIER = "my-unique-identifier";
  private static final Duration TTL = Duration.ofSeconds(30);

  private static final ZonedDateTime EXPIRES_AT = ZonedDateTime.ofInstant(NOW, UTC).plus(TTL);
  private static final String LOCK_ID = "some-mongodb-id";
  private static final Lock LOCK = new Lock(LOCK_ID, UNIQUE_IDENTIFIER, EXPIRES_AT);

  @Mock
  private MongoTemplate reactiveMongoTemplate;

  private LockManager lockManager;

  private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
  private Lock lockResult;
  private boolean unlockResult;

  @BeforeEach
  void setUp() {
    lockManager = new LockManagerMongo(reactiveMongoTemplate, CLOCK);
    var logger = (Logger) LoggerFactory.getLogger(lockManager.getClass());
    logger.addAppender(listAppender);
    listAppender.start();
  }

  @AfterEach
  void tearDown() {
    listAppender.stop();
  }

  @Test
  void lock() {
    givenMongoInsertedIsInvokedSuccessfully();
    whenILock();
    thenIExpectLock();
    thenMongoInsertIsInvoked();
  }

  @Test
  void lockAlreadyAcquired() {
    var exception = new DuplicateKeyException("test exception");
    givenMongoInsertThrowsAnException(exception);
    assertThatExceptionOfType(LockFailureException.class)
      .isThrownBy(() -> whenILock())
      .withMessage("Lock already acquired on 'my-unique-identifier'!");
    thenMongoInsertIsInvoked();
    thenTheLogsContains("[WARN] error lock(): lock already acquired on 'my-unique-identifier'!");
  }

  @Test
  void lockException() {
    var exception = new RuntimeException("test exception");
    givenMongoInsertThrowsAnException(exception);
    assertThatExceptionOfType(LockFailureException.class)
      .isThrownBy(() -> whenILock())
      .withMessage("Failed to acquire lock on 'my-unique-identifier'")
      .withCause(exception);
    thenMongoInsertIsInvoked();
    thenTheLogsContains("[ERROR] error lock(): message=test exception");
  }

  @Test
  void unlock() {
    givenMongoRemoveIsInvokedSuccessfully();
    whenIUnlock();
    thenIExpectUnlock(true);
    thenMongoRemoveIsInvoked();
  }

  @Test
  void unlockRecordNotFound() {
    givenMongoRemoveDoesNotFindAnyRecord();
    whenIUnlock();
    thenIExpectUnlock(false);
    thenMongoRemoveIsInvoked();
  }

  @Test
  void unlockException() {
    var exception = new RuntimeException("test exception");
    givenMongoTemplateRemoveThrowsAnException(exception);
    whenIUnlock();
    thenIExpectUnlock(false);
    thenMongoRemoveIsInvoked();
    thenTheLogsContains("[ERROR] error unlock(): message=test exception");
  }

  private void givenMongoInsertedIsInvokedSuccessfully() {
    var lockMongoEntity = new LockMongoEntity(LOCK_ID, UNIQUE_IDENTIFIER, EXPIRES_AT.toLocalDateTime());
    doReturn(lockMongoEntity)
      .when(reactiveMongoTemplate).insert(any(LockMongoEntity.class));
  }

  private void givenMongoInsertThrowsAnException(Throwable throwable) {
    doThrow(throwable)
      .when(reactiveMongoTemplate).insert(any(LockMongoEntity.class));
  }

  private void givenMongoRemoveIsInvokedSuccessfully() {
    doReturn(DeleteResult.acknowledged(1L))
      .when(reactiveMongoTemplate).remove(any(Query.class), any(Class.class));
  }

  private void givenMongoRemoveDoesNotFindAnyRecord() {
    doReturn(DeleteResult.acknowledged(0L))
      .when(reactiveMongoTemplate).remove(any(Query.class), any(Class.class));
  }

  private void givenMongoTemplateRemoveThrowsAnException(Throwable throwable) {
    doThrow(throwable)
      .when(reactiveMongoTemplate).remove(any(Query.class), any(Class.class));
  }

  private void whenILock() {
    lockResult = lockManager.lock(UNIQUE_IDENTIFIER, TTL);
  }

  private void whenIUnlock() {
    unlockResult = lockManager.unlock(LOCK);
  }

  private void thenIExpectLock() {
    assertThat(lockResult).isEqualTo(LOCK);
  }

  private void thenIExpectUnlock(boolean expected) {
    assertThat(unlockResult).isEqualTo(expected);
  }

  private void thenMongoInsertIsInvoked() {
    var expected = new LockMongoEntity(null, UNIQUE_IDENTIFIER, EXPIRES_AT.toLocalDateTime());
    verify(reactiveMongoTemplate).insert(expected);
  }

  private void thenMongoRemoveIsInvoked() {
    var expected = query(where("id").is(LOCK_ID).and("uniqueIdentifier").is(UNIQUE_IDENTIFIER)).limit(1);
    verify(reactiveMongoTemplate).remove(expected, LockMongoEntity.class);
  }

  private void thenTheLogsContains(String expectedErrorMessage) {
    assertThat(listAppender.list.stream().map(l -> l.toString())).contains(expectedErrorMessage);
  }

}