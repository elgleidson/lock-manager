package com.github.elgleidson.lock;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReactiveLockManagerInMemoryTest {

  private static final Instant NOW = Instant.now();
  private static final Clock CLOCK = Clock.fixed(NOW, UTC);

  private static final String UNIQUE_IDENTIFIER = "my-unique-identifier";
  private static final Duration TTL = Duration.ofSeconds(30);

  private static final ZonedDateTime EXPIRES_AT = ZonedDateTime.ofInstant(NOW, UTC).plus(TTL);
  private static final UUID LOCK_ID = UUID.randomUUID();
  private static final Lock LOCK = new Lock(LOCK_ID.toString(), UNIQUE_IDENTIFIER, EXPIRES_AT);
  private static final Lock ANOTHER_LOCK = new Lock("another-lock-id", UNIQUE_IDENTIFIER, EXPIRES_AT);

  @Mock
  private ReactiveLockManagerInMemory.UUIDWrapper uuidWrapper;

  private ReactiveLockManager lockManager;

  private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
  private Mono<Lock> lockResult;
  private Mono<Boolean> unlockResult;

  @BeforeEach
  void setUp() {
    lockManager = new ReactiveLockManagerInMemory(CLOCK, uuidWrapper);

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
    whenILock();
    thenIExpectLock();
  }

  @Test
  void lockAlreadyLocked() {
    whenILock();
    thenIExpectLock();
    whenILock();
    thenIExpectLockFailureException();
    thenTheLogsContains("[WARN] error lock(): lock already acquired on 'my-unique-identifier'!");
  }

  @Test
  void unlock() {
    whenILock();
    thenIExpectLock();
    whenIUnlock();
    thenIExpectUnlock(true);
  }

  @Test
  void unlockNotFound() {
    whenIUnlock();
    thenIExpectUnlock(false);
  }

  @Test
  void unlockDifferentValue() {
    whenILock();
    whenIUnlock(ANOTHER_LOCK);
    thenIExpectUnlock(false);
    thenTheLogsContains("[WARN] unlock(): another process has acquired the lock on 'my-unique-identifier'");
  }

  private void whenILock() {
    lockResult = lockManager.lock(UNIQUE_IDENTIFIER, TTL);
  }

  private void whenIUnlock() {
    whenIUnlock(LOCK);
  }

  private void whenIUnlock(Lock lock) {
    unlockResult = lockManager.unlock(lock);
  }

  private void thenIExpectLock() {
    StepVerifier.create(lockResult).expectNext(LOCK).verifyComplete();
  }

  private void thenIExpectUnlock(Boolean expected) {
    StepVerifier.create(unlockResult).expectNext(expected).verifyComplete();
  }

  private void thenIExpectLockFailureException() {
    StepVerifier.create(lockResult).verifyErrorSatisfies(throwable -> assertThat(throwable)
      .isInstanceOf(LockFailureException.class)
      .hasMessage("Lock already acquired on 'my-unique-identifier'!")
    );
  }

  private void thenTheLogsContains(String expectedErrorMessage) {
    assertThat(listAppender.list.stream().map(l -> l.toString())).contains(expectedErrorMessage);
  }

}