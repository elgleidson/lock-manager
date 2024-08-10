package com.github.elgleidson.lockmanager;

import static com.github.elgleidson.lockmanager.ReactiveLockManagerRedis.KEYSPACE;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReactiveLockManagerRedisTest {

  private static final Instant NOW = Instant.now();
  private static final Clock CLOCK = Clock.fixed(NOW, UTC);

  private static final String UNIQUE_IDENTIFIER = "my-unique-identifier";
  private static final Duration TTL = Duration.ofSeconds(30);

  private static final ZonedDateTime EXPIRES_AT = ZonedDateTime.ofInstant(NOW, UTC).plus(TTL);
  private static final UUID LOCK_ID = UUID.randomUUID();
  private static final Lock LOCK = new Lock(LOCK_ID.toString(), UNIQUE_IDENTIFIER, EXPIRES_AT);

  @Mock
  private ReactiveStringRedisTemplate reactiveRedisTemplate;
  @Mock
  private ReactiveValueOperations<String, String> reactiveValueOperations;
  @Mock
  private ReactiveLockManagerRedis.UUIDWrapper uuidWrapper;

  private ReactiveLockManager lockManager;

  private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
  private Mono<Lock> lockResult;
  private Mono<Long> unlockResult;

  @BeforeEach
  void setUp() {
    lockManager = new ReactiveLockManagerRedis(reactiveRedisTemplate, CLOCK, uuidWrapper);

    lenient().when(reactiveRedisTemplate.opsForValue()).thenReturn(reactiveValueOperations);
    lenient().doReturn(LOCK_ID).when(uuidWrapper).randomUUID();

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
    whenILock();
    thenIExpectLockFailureException();
    thenRedisTemplateInsertIsInvoked();
    thenTheLogsContains("[WARN] error lock(): lock already acquired on 'my-unique-identifier'!");
  }

  @Test
  void lockException() {
    var exception = new RuntimeException("test exception");
    givenRedisTemplateInsertThrowsAnException(exception);
    whenILock();
    thenIExpectLockFailureException(exception);
    thenRedisTemplateInsertIsInvoked();
    thenTheLogsContains("[ERROR] error lock(): message=test exception");
  }

  @Test
  void unlock() {
    givenRedisTemplateGetIsInvokedSuccessfully();
    givenRedisTemplateDeleteIsInvokedSuccessfully();
    whenIUnlock();
    thenIExpectUnlock(1L);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsInvoked();
  }

  @Test
  void unlockNotFoundGet() {
    givenRedisTemplateGetDoesNotFindAnyRecord();
    whenIUnlock();
    thenIExpectUnlock(0L);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsNotInvoked();
  }

  @Test
  void unlockNotFoundDelete() {
    givenRedisTemplateGetIsInvokedSuccessfully();
    givenRedisTemplateDeleteDoesNotFindAnyRecord();
    whenIUnlock();
    thenIExpectUnlock(0L);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsInvoked();
  }

  @Test
  void unlockDifferentValue() {
    givenRedisTemplateGetIsInvokedSuccessfully("different-value");
    whenIUnlock();
    thenIExpectUnlock(0L);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsNotInvoked();
    thenTheLogsContains("[WARN] unlock(): another process has acquired the lock on 'my-unique-identifier'");
  }

  @Test
  void unlockExceptionGet() {
    var exception = new RuntimeException("test exception");
    givenRedisTemplateGetThrowsAnException(exception);
    whenIUnlock();
    thenIExpectUnlock(0L);
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
    thenIExpectUnlock(0L);
    thenRedisTemplateGetIsInvoked();
    thenRedisTemplateDeleteIsInvoked();
    thenTheLogsContains("[ERROR] error unlock(): message=test exception");
  }

  private void givenRedisTemplateInsertIsInvokedSuccessfully() {
    doReturn(Mono.just(true))
      .when(reactiveValueOperations).setIfAbsent(anyString(), anyString(), any(Duration.class));
  }

  private void givenRedisTemplateInsertIsInvokedSuccessfully(boolean inserted) {
    doReturn(Mono.just(inserted))
      .when(reactiveValueOperations).setIfAbsent(anyString(), anyString(), any(Duration.class));
  }

  private void givenRedisTemplateInsertThrowsAnException(Throwable throwable) {
    doReturn(Mono.error(throwable))
      .when(reactiveValueOperations).setIfAbsent(anyString(), anyString(), any(Duration.class));
  }

  private void givenRedisTemplateGetIsInvokedSuccessfully() {
    doReturn(Mono.just(LOCK_ID.toString()))
      .when(reactiveValueOperations).get(anyString());
  }

  private void givenRedisTemplateGetIsInvokedSuccessfully(String expectedValue) {
    doReturn(Mono.just(expectedValue))
      .when(reactiveValueOperations).get(anyString());
  }

  private void givenRedisTemplateGetDoesNotFindAnyRecord() {
    doReturn(Mono.empty())
      .when(reactiveValueOperations).get(anyString());
  }

  private void givenRedisTemplateGetThrowsAnException(Throwable throwable) {
    doReturn(Mono.error(throwable))
      .when(reactiveValueOperations).get(anyString());
  }

  private void givenRedisTemplateDeleteIsInvokedSuccessfully() {
    doReturn(Mono.just(1L))
      .when(reactiveRedisTemplate).delete(anyString());
  }

  private void givenRedisTemplateDeleteDoesNotFindAnyRecord() {
    doReturn(Mono.just(0L))
      .when(reactiveRedisTemplate).delete(anyString());
  }

  private void givenRedisTemplateDeleteThrowsAnException(Throwable throwable) {
    doReturn(Mono.error(throwable))
      .when(reactiveRedisTemplate).delete(anyString());
  }

  private void whenILock() {
    lockResult = lockManager.lock(UNIQUE_IDENTIFIER, TTL);
  }

  private void whenIUnlock() {
    unlockResult = lockManager.unlock(LOCK);
  }

  private void thenIExpectLock() {
    StepVerifier.create(lockResult).expectNext(LOCK).verifyComplete();
  }

  private void thenIExpectUnlock(Long expected) {
    StepVerifier.create(unlockResult).expectNext(expected).verifyComplete();
  }

  private void thenIExpectLockFailureException() {
    StepVerifier.create(lockResult).verifyErrorSatisfies(throwable -> assertThat(throwable)
      .isInstanceOf(LockFailureException.class)
      .hasMessage("Lock already acquired on 'my-unique-identifier'!")
    );
  }

  private void thenIExpectLockFailureException(Throwable exceptedCause) {
    StepVerifier.create(lockResult).verifyErrorSatisfies(throwable -> assertThat(throwable)
      .isInstanceOf(LockFailureException.class)
      .hasMessage("Failed to acquire lock on 'my-unique-identifier'")
      .hasCause(exceptedCause)
    );
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
    assertThat(listAppender.list.stream().map(l -> l.toString())).contains(expectedErrorMessage);
  }

}