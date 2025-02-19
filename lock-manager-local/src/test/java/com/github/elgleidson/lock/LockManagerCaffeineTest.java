package com.github.elgleidson.lock;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.benmanes.caffeine.cache.Cache;
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

@ExtendWith(MockitoExtension.class)
class LockManagerCaffeineTest {

  private static final Instant NOW = Instant.now();
  private static final Clock CLOCK = Clock.fixed(NOW, UTC);

  private static final String UNIQUE_IDENTIFIER = "my-unique-identifier";
  private static final Duration TTL = Duration.ofSeconds(30);

  private static final ZonedDateTime EXPIRES_AT = ZonedDateTime.ofInstant(NOW, UTC).plus(TTL);
  private static final UUID LOCK_ID = UUID.randomUUID();
  private static final Lock LOCK = new Lock(LOCK_ID.toString(), UNIQUE_IDENTIFIER, EXPIRES_AT);

  @Mock
  private Cache<String, Lock> caffeineCache;

  private LockManager lockManager;

  private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
  private Lock lockResult;
  private boolean unlockResult;

  @BeforeEach
  void setUp() {
    lockManager = new LockManagerCaffeine(caffeineCache, CLOCK, () -> LOCK_ID);

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
    givenCaffeineCacheGetIfPresentReturnsNull();
    givenCaffeineCachePutIsInvokedSuccessfully();
    whenILock();
    thenIExpectLock();
    thenCaffeineCacheGetIfPresentIsInvoked();
    thenCaffeineCachePutIsInvoked();
  }

  @Test
  void lockAlreadyLocked() {
    givenCaffeineCacheGetIfPresentReturnsALock();
    assertThatExceptionOfType(LockFailureException.class)
      .isThrownBy(() -> whenILock())
      .withMessage("Lock already acquired on 'my-unique-identifier'!");
    thenCaffeineCacheGetIfPresentIsInvoked();
    thenCaffeineCachePutIsNotInvoked();
    thenTheLogsContains("[WARN] error lock(): lock already acquired on 'my-unique-identifier'!");
  }

  @Test
  void lockException() {
    var exception = new RuntimeException("test exception");
    givenCaffeineCachePutThrowsAnException(exception);
    assertThatExceptionOfType(LockFailureException.class)
      .isThrownBy(() -> whenILock())
      .withMessage("Failed to acquire lock on 'my-unique-identifier'")
      .withCause(exception);
    thenCaffeineCachePutIsInvoked();
    thenTheLogsContains("[ERROR] error lock(): message=test exception");
  }

  @Test
  void unlock() {
    givenCaffeineCacheGetIfPresentReturnsALock();
    givenCaffeineCacheInvalidateInInvokedSuccessfully();
    whenIUnlock();
    thenIExpectUnlock(true);
    thenCaffeineCacheGetIfPresentIsInvoked();
    thenCaffeineCacheInvalidateIsInvoked();
  }

  @Test
  void unlockNotFound() {
    givenCaffeineCacheGetIfPresentReturnsNull();
    whenIUnlock();
    thenIExpectUnlock(false);
    thenCaffeineCacheGetIfPresentIsInvoked();
    thenCaffeineCacheInvalidateIsNotInvoked();
  }

  @Test
  void unlockDifferentValue() {
    givenCaffeineCacheGetIfPresentReturnsALock("different-value");
    whenIUnlock();
    thenIExpectUnlock(false);
    thenCaffeineCacheGetIfPresentIsInvoked();
    thenCaffeineCacheInvalidateIsNotInvoked();
    thenTheLogsContains("[WARN] unlock(): another process has acquired the lock on 'my-unique-identifier'");
  }

  @Test
  void unlockException() {
    var exception = new RuntimeException("test exception");
    givenCaffeineCacheGetIfPresentReturnsALock();
    givenCaffeineCacheInvalidateThrowsAnException(exception);
    whenIUnlock();
    thenIExpectUnlock(false);
    thenCaffeineCacheGetIfPresentIsInvoked();
    thenCaffeineCacheInvalidateIsInvoked();
    thenTheLogsContains("[ERROR] error unlock(): message=test exception");
  }

  private void givenCaffeineCacheGetIfPresentReturnsNull() {
    doReturn(null)
      .when(caffeineCache).getIfPresent(anyString());
  }

  private void givenCaffeineCacheGetIfPresentReturnsALock() {
    doReturn(LOCK)
      .when(caffeineCache).getIfPresent(anyString());
  }

  private void givenCaffeineCacheGetIfPresentReturnsALock(String id) {
    var lock = new Lock(id, UNIQUE_IDENTIFIER, EXPIRES_AT);
    doReturn(lock)
      .when(caffeineCache).getIfPresent(anyString());
  }

  private void givenCaffeineCachePutIsInvokedSuccessfully() {
    doNothing()
      .when(caffeineCache).put(anyString(), any());
  }

  private void givenCaffeineCachePutThrowsAnException(Throwable throwable) {
    doThrow(throwable)
      .when(caffeineCache).put(anyString(), any());
  }

  private void givenCaffeineCacheInvalidateInInvokedSuccessfully() {
    doNothing()
      .when(caffeineCache).invalidate(anyString());
  }

  private void givenCaffeineCacheInvalidateThrowsAnException(Throwable throwable) {
    doThrow(throwable)
      .when(caffeineCache).invalidate(anyString());
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

  private void thenCaffeineCacheGetIfPresentIsInvoked() {
    verify(caffeineCache).getIfPresent(UNIQUE_IDENTIFIER);
  }

  private void thenCaffeineCachePutIsInvoked() {
    verify(caffeineCache).put(UNIQUE_IDENTIFIER, LOCK);
  }

  private void thenCaffeineCachePutIsNotInvoked() {
    verify(caffeineCache, never()).put(anyString(), any());
  }

  private void thenCaffeineCacheInvalidateIsInvoked() {
    verify(caffeineCache).invalidate(UNIQUE_IDENTIFIER);
  }


  private void thenCaffeineCacheInvalidateIsNotInvoked() {
    verify(caffeineCache, never()).invalidate(anyString());
  }

  private void thenTheLogsContains(String expectedErrorMessage) {
    assertThat(listAppender.list.stream().map(l -> l.toString())).contains(expectedErrorMessage);
  }
}