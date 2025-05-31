package com.github.elgleidson.lock;

import static com.github.elgleidson.lock.LockManagerRedis.KEYSPACE;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LockManagerRedisTest {

  private static final Instant NOW = Instant.now();
  private static final Clock CLOCK = Clock.fixed(NOW, UTC);

  private static final String UNIQUE_IDENTIFIER = "my-unique-identifier";
  private static final Duration TTL = Duration.ofSeconds(30);

  private static final ZonedDateTime EXPIRES_AT = ZonedDateTime.ofInstant(NOW, UTC).plus(TTL);
  private static final UUID LOCK_ID = UUID.randomUUID();
  private static final Lock LOCK = new Lock(LOCK_ID.toString(), UNIQUE_IDENTIFIER, EXPIRES_AT);

  @Mock
  private StringRedisTemplate reactiveRedisTemplate;
  @Mock
  private ValueOperations<String, String> reactiveValueOperations;

  private LockManager lockManager;

  private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
  private Lock lockResult;
  private boolean unlockResult;

  @BeforeEach
  void setUp() {
    lockManager = new LockManagerRedis(reactiveRedisTemplate, CLOCK, () -> LOCK_ID);

    lenient().when(reactiveRedisTemplate.opsForValue()).thenReturn(reactiveValueOperations);

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
    givenRedisTemplateInsertIsInvokedSuccessfully();
    whenILock();
    thenIExpectLock();
    thenRedisTemplateInsertIsInvoked();
  }

  @Test
  void lockAlreadyLocked() {
    givenRedisTemplateInsertIsInvokedSuccessfully(false);
    assertThatExceptionOfType(LockFailureException.class)
      .isThrownBy(this::whenILock)
      .withMessage("Lock already acquired on 'my-unique-identifier'!");
    thenRedisTemplateInsertIsInvoked();
    thenTheLogsContains("[WARN] error lock(): lock already acquired on 'my-unique-identifier'!");
  }

  @Test
  void lockException() {
    var exception = new RuntimeException("test exception");
    givenRedisTemplateInsertThrowsAnException(exception);
    assertThatExceptionOfType(LockFailureException.class)
      .isThrownBy(this::whenILock)
      .withMessage("Failed to acquire lock on 'my-unique-identifier'")
      .withCause(exception);
    thenRedisTemplateInsertIsInvoked();
    thenTheLogsContains("[ERROR] error lock(): message=test exception");
  }

  @Test
  void unlock() {
    givenRedisTemplateGetIsInvokedSuccessfully();
    givenRedisTemplateDeleteIsInvokedSuccessfully();
    whenIUnlock();
    thenIExpectUnlock(true);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsInvoked();
  }

  @Test
  void unlockNotFoundGet() {
    givenRedisTemplateGetDoesNotFindAnyRecord();
    whenIUnlock();
    thenIExpectUnlock(false);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsNotInvoked();
  }

  @Test
  void unlockNotFoundDelete() {
    givenRedisTemplateGetIsInvokedSuccessfully();
    givenRedisTemplateDeleteDoesNotFindAnyRecord();
    whenIUnlock();
    thenIExpectUnlock(false);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsInvoked();
  }

  @Test
  void unlockDifferentValue() {
    givenRedisTemplateGetIsInvokedSuccessfully("different-value");
    whenIUnlock();
    thenIExpectUnlock(false);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsNotInvoked();
    thenTheLogsContains("[WARN] unlock(): another process has acquired the lock on 'my-unique-identifier'");
  }

  @Test
  void unlockExceptionGet() {
    var exception = new RuntimeException("test exception");
    givenRedisTemplateGetThrowsAnException(exception);
    whenIUnlock();
    thenIExpectUnlock(false);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsNotInvoked();
    thenTheLogsContains("[ERROR] error unlock(): message=test exception");
  }

  @Test
  void unlockExceptionDelete() {
    var exception = new RuntimeException("test exception");
    givenRedisTemplateGetIsInvokedSuccessfully();
    givenRedisTemplateDeleteThrowsAnException(exception);
    whenIUnlock();
    thenIExpectUnlock(false);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsInvoked();
    thenTheLogsContains("[ERROR] error unlock(): message=test exception");
  }

  private void givenRedisTemplateInsertIsInvokedSuccessfully() {
    doReturn(true)
      .when(reactiveValueOperations).setIfAbsent(anyString(), anyString(), any(Duration.class));
  }

  private void givenRedisTemplateInsertIsInvokedSuccessfully(boolean inserted) {
    doReturn(inserted)
      .when(reactiveValueOperations).setIfAbsent(anyString(), anyString(), any(Duration.class));
  }

  private void givenRedisTemplateInsertThrowsAnException(Throwable throwable) {
    doThrow(throwable)
      .when(reactiveValueOperations).setIfAbsent(anyString(), anyString(), any(Duration.class));
  }

  private void givenRedisTemplateGetIsInvokedSuccessfully() {
    doReturn(LOCK_ID.toString())
      .when(reactiveValueOperations).get(anyString());
  }

  private void givenRedisTemplateGetIsInvokedSuccessfully(String expectedValue) {
    doReturn(expectedValue)
      .when(reactiveValueOperations).get(anyString());
  }

  private void givenRedisTemplateGetDoesNotFindAnyRecord() {
    doReturn(null)
      .when(reactiveValueOperations).get(anyString());
  }

  private void givenRedisTemplateGetThrowsAnException(Throwable throwable) {
    doThrow(throwable)
      .when(reactiveValueOperations).get(anyString());
  }

  private void givenRedisTemplateDeleteIsInvokedSuccessfully() {
    doReturn(true)
      .when(reactiveRedisTemplate).delete(anyString());
  }

  private void givenRedisTemplateDeleteDoesNotFindAnyRecord() {
    doReturn(false)
      .when(reactiveRedisTemplate).delete(anyString());
  }

  private void givenRedisTemplateDeleteThrowsAnException(Throwable throwable) {
    doThrow(throwable)
      .when(reactiveRedisTemplate).delete(anyString());
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

  private void thenIExpectUnlock(Boolean expected) {
    assertThat(unlockResult).isEqualTo(expected);
  }

  private void thenRedisTemplateInsertIsInvoked() {
    verify(reactiveValueOperations).setIfAbsent(KEYSPACE + UNIQUE_IDENTIFIER, LOCK_ID.toString(), TTL);
  }

  private void thenRedisTemplateGetIsInvoked() {
    verify(reactiveValueOperations).get(KEYSPACE + UNIQUE_IDENTIFIER);
  }

  private void thenRedisTemplateDeleteIsInvoked() {
    verify(reactiveRedisTemplate).delete(KEYSPACE + UNIQUE_IDENTIFIER);
  }

  private void thenRedisTemplateDeleteIsNotInvoked() {
    verify(reactiveRedisTemplate, never()).delete(anyString());
  }

  private void thenTheLogsContains(String expectedErrorMessage) {
    assertThat(listAppender.list.stream().map(Object::toString)).contains(expectedErrorMessage);
  }

}