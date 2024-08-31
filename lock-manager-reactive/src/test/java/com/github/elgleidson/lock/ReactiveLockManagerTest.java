package com.github.elgleidson.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
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
  private PublisherProbe<Boolean> publisherProbeUnlock;
  private Mono<Object> wrapResult;

  @BeforeEach
  void setUp() {
    publisherProbe = PublisherProbe.of(Mono.just(OBJECT));
    publisherProbeLock = PublisherProbe.of(Mono.just(LOCK));
    publisherProbeUnlock = PublisherProbe.of(Mono.just(true));
  }

  @Test
  void wrap() {
    givenAMonoSupplier();
    givenACallToLock();
    givenACallToUnlock();
    whenIWrap();
    thenIExpectWrapResult();
    thenLockIsInvoked();
    thenTheMonoIsCalled();
    thenUnlockIsInvoked();
  }

  @Test
  void wrapWithErrorFromLock() {
    var exception = new RuntimeException("test");
    givenAMonoSupplier();
    givenACallToLock(exception);
    whenIWrap();
    thenIExpectWrapException(exception);
    thenLockIsInvoked();
    thenTheMonoIsNotCalled();
    thenUnlockIsNotInvoked();
  }

  @Test
  void wrapWithErrorFromSupplier() {
    var exception = new RuntimeException("test");
    givenAMonoSupplier(exception);
    givenACallToLock();
    givenACallToUnlock();
    whenIWrap();
    thenIExpectWrapException(exception);
    thenLockIsInvoked();
    thenTheMonoIsCalled();
    thenUnlockIsInvoked();
  }

  @Test
  void wrapWithErrorFromSupplierNotUnlock() {
    var exception = new RuntimeException("test");
    givenAMonoSupplier(exception);
    givenACallToLock();
    whenIWrap(false);
    thenIExpectWrapException(exception);
    thenLockIsInvoked();
    thenTheMonoIsCalled();
    thenUnlockIsNotInvoked();
  }

  @Test
  void wrapWithErrorFromUnlock() {
    var exception = new RuntimeException("test");
    givenAMonoSupplier();
    givenACallToLock();
    givenACallToUnlock(exception);
    whenIWrap();
    thenIExpectWrapResult();
    thenLockIsInvoked();
    thenTheMonoIsCalled();
    thenUnlockIsInvoked();
  }

  private void givenAMonoSupplier() {
    monoSupplier = publisherProbe::mono;
  }

  private void givenAMonoSupplier(Throwable throwable) {
    publisherProbe = PublisherProbe.of(Mono.error(throwable));
    monoSupplier = publisherProbe::mono;
  }

  private void givenACallToLock() {
    // lock method that requires implementation as there is no default one
    doReturn(publisherProbeLock.mono()).when(lockManager).lock(anyString(), any(Duration.class));
  }

  private void givenACallToLock(Throwable throwable) {
    // lock method that requires implementation as there is no default one
    publisherProbeLock = PublisherProbe.of(Mono.error(throwable));
    doReturn(publisherProbeLock.mono()).when(lockManager).lock(anyString(), any(Duration.class));
  }

  private void givenACallToUnlock() {
    // unlock method that requires implementation as there is no default one
    doReturn(publisherProbeUnlock.mono()).when(lockManager).unlock(any(Lock.class));
  }

  private void givenACallToUnlock(Throwable throwable) {
    // unlock method that requires implementation as there is no default one
    publisherProbeUnlock = PublisherProbe.of(Mono.error(throwable));
    doReturn(publisherProbeUnlock.mono()).when(lockManager).unlock(any(Lock.class));
  }

  private void whenIWrap() {
    wrapResult = lockManager.wrap(UNIQUE_IDENTIFIER, TTL, monoSupplier);
  }

  private void whenIWrap(boolean onErrorUnlock) {
    wrapResult = lockManager.wrap(UNIQUE_IDENTIFIER, TTL, onErrorUnlock, monoSupplier);
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

  private void thenTheMonoIsNotCalled() {
    publisherProbe.assertWasNotRequested();
    publisherProbe.assertWasNotSubscribed();
  }

}