package com.github.elgleidson.lockmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

@ExtendWith(MockitoExtension.class)
class ReactiveLockManagerTest {

  private static final String UNIQUE_IDENTIFIER = "my-unique-identifier";
  private static final Duration TTL = Duration.ofSeconds(5);
  private static final ZonedDateTime NOW = ZonedDateTime.now();
  private static final Lock LOCK = new Lock("id", UNIQUE_IDENTIFIER, NOW.plus(TTL));

  private static final Object OBJECT = "my object";

  @Spy
  private ReactiveLockManager lockManager;

  private Supplier<Mono<Object>> monoSupplier;
  private PublisherProbe<Object> publisherProbe;
  private PublisherProbe<Lock> publisherProbeLock;
  private PublisherProbe<Long> publisherProbeUnlock;
  private Mono<Object> wrapResult;

  @Test
  void wrap() {
    initPublishers();
    givenAMonoSupplier();
    givenACallToLock();
    givenACallToUnlock();
    whenIWrap();
    thenIExpectWrapResult();
    thenTheMonoIsCalled();
    thenLockIsInvoked();
    thenUnlockIsInvoked();
  }

  @Test
  void wrapWithError() {
    var exception = new RuntimeException("test");
    initPublishers(exception);
    givenAMonoSupplier();
    givenACallToLock();
    whenIWrap();
    thenIExpectWrapException(exception);
    thenTheMonoIsCalled();
    thenLockIsInvoked();
    thenUnlockIsNotInvoked();
  }

  private void initPublishers() {
    publisherProbeLock = PublisherProbe.of(Mono.just(LOCK));
    publisherProbeUnlock = PublisherProbe.of(Mono.just(1L));
    publisherProbe = PublisherProbe.of(Mono.just(OBJECT));
  }

  private void initPublishers(Throwable throwable) {
    publisherProbeLock = PublisherProbe.of(Mono.just(LOCK));
    publisherProbeUnlock = PublisherProbe.of(Mono.just(1L));
    publisherProbe = PublisherProbe.of(Mono.error(throwable));
  }

  private void givenAMonoSupplier() {
    monoSupplier = publisherProbe::mono;
  }

  private void givenACallToLock() {
    // lock method that requires implementation as there is no default one
    doReturn(publisherProbeLock.mono()).when(lockManager).lock(anyString(), any(Duration.class));
  }

  private void givenACallToUnlock() {
    // unlock method that requires implementation as there is no default one
    doReturn(publisherProbeUnlock.mono()).when(lockManager).unlock(any(Lock.class));
  }

  private void whenIWrap() {
    wrapResult = lockManager.wrap(UNIQUE_IDENTIFIER, TTL, monoSupplier);
  }

  private void thenIExpectWrapResult() {
    StepVerifier.create(wrapResult).expectNext(OBJECT).verifyComplete();
  }

  private void thenIExpectWrapException(Throwable expectedThrowable) {
    StepVerifier.create(wrapResult).verifyErrorSatisfies(throwable -> assertThat(throwable).isEqualTo(expectedThrowable));
  }

  private void thenLockIsInvoked() {
    verify(lockManager).lock(UNIQUE_IDENTIFIER, TTL);
    publisherProbeLock.assertWasRequested();
    publisherProbeLock.assertWasSubscribed();
  }

  private void thenUnlockIsInvoked() {
    verify(lockManager).unlock(LOCK);
    publisherProbeUnlock.assertWasRequested();
    publisherProbeUnlock.assertWasSubscribed();
  }

  private void thenUnlockIsNotInvoked() {
    verify(lockManager, never()).unlock(any(Lock.class));
    publisherProbeUnlock.assertWasNotRequested();
    publisherProbeUnlock.assertWasNotSubscribed();
  }

  private void thenTheMonoIsCalled() {
    publisherProbe.assertWasRequested();
    publisherProbe.assertWasSubscribed();
  }

}