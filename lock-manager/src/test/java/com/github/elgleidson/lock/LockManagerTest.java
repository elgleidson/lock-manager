package com.github.elgleidson.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
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

@ExtendWith(MockitoExtension.class)
class LockManagerTest {

  private static final String UNIQUE_IDENTIFIER = "my-unique-identifier";
  private static final Duration TTL = Duration.ofSeconds(5);
  private static final ZonedDateTime NOW = ZonedDateTime.now();
  private static final Lock LOCK = new Lock("id", UNIQUE_IDENTIFIER, NOW.plus(TTL));

  private static final Object OBJECT = "my object";

  @Spy
  private LockManager lockManager;

  private Supplier<Object> supplier;
  private Object wrapResult;

  @Test
  void wrap() {
    givenASupplier();
    givenACallToLock();
    givenACallToUnlock();
    whenIWrap();
    thenIExpectWrapResult();
    thenSupplierIsCalled();
    thenLockIsInvoked();
    thenUnlockIsInvoked();
  }

  @Test
  void wrapWithError() {
    var exception = new RuntimeException("test");
    givenASupplier(exception);
    givenACallToLock();
    givenACallToUnlock();
    assertThatException().isThrownBy(() -> whenIWrap()).isEqualTo(exception);
    thenSupplierIsCalled();
    thenLockIsInvoked();
    thenUnlockIsInvoked();
  }

  @Test
  void wrapWithErrorNotUnlock() {
    var exception = new RuntimeException("test");
    givenASupplier(exception);
    givenACallToLock();
    assertThatException().isThrownBy(() -> whenIWrap(false)).isEqualTo(exception);
    thenSupplierIsCalled();
    thenLockIsInvoked();
    thenUnlockIsNotInvoked();
  }


  private void givenASupplier() {
    supplier = mock(Supplier.class);
    doReturn(OBJECT).when(supplier).get();
  }

  private void givenASupplier(Throwable throwable) {
    supplier = mock(Supplier.class);
    doThrow(throwable).when(supplier).get();
  }

  private void givenACallToLock() {
    // lock method that requires implementation as there is no default one
    doReturn(LOCK).when(lockManager).lock(anyString(), any(Duration.class));
  }

  private void givenACallToUnlock() {
    // unlock method that requires implementation as there is no default one
    doReturn(true).when(lockManager).unlock(any(Lock.class));
  }

  private void whenIWrap() {
    wrapResult = lockManager.wrap(UNIQUE_IDENTIFIER, TTL, supplier);
  }

  private void whenIWrap(boolean onErrorUnlock) {
    wrapResult = lockManager.wrap(UNIQUE_IDENTIFIER, TTL, onErrorUnlock, supplier);
  }

  private void thenIExpectWrapResult() {
    assertThat(wrapResult).isEqualTo(OBJECT);
  }

  private void thenLockIsInvoked() {
    verify(lockManager).lock(UNIQUE_IDENTIFIER, TTL);
  }

  private void thenUnlockIsInvoked() {
    verify(lockManager).unlock(LOCK);
  }

  private void thenUnlockIsNotInvoked() {
    verify(lockManager, never()).unlock(any(Lock.class));
  }

  private void thenSupplierIsCalled() {
    verify(supplier).get();
  }

}