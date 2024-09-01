package com.github.elgleidson.lock;

import java.time.Duration;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public interface ReactiveLockManager {

  default <T> Mono<T> wrap(String uniqueIdentifier, Duration expiresIn, Supplier<Mono<T>> monoSupplier) {
    return wrap(uniqueIdentifier, expiresIn, true, monoSupplier);
  }

  default <T> Mono<T> wrap(String uniqueIdentifier, Duration expiresIn, boolean onErrorUnlock, Supplier<Mono<T>> monoSupplier) {
    return lock(uniqueIdentifier, expiresIn)
      .flatMap(lock -> Mono.defer(monoSupplier)
        .flatMap(t -> safeUnlock(lock).thenReturn(t))
        // ? in case we're working with Mono<Void> or an empty Mono is returned by the supplier.
        .switchIfEmpty(Mono.defer(() -> safeUnlock(lock).then(Mono.empty())))
        .onErrorResume(throwable -> onErrorUnlock ? safeUnlock(lock).then(Mono.error(throwable)) : Mono.error(throwable))
      );
  }

  private Mono<Boolean> safeUnlock(Lock lock) {
    return unlock(lock).onErrorReturn(false);
  }

  Mono<Lock> lock(String uniqueIdentifier, Duration expiresIn);

  /**
   * Releases the lock.
   * <p>This method should never throw an exception. In case of any exception, just log it and return false instead.</p>
   * <p>You should unlock only when the lock ID and unique identifier match.</p>
   * @param lock
   * @return whether the lock was released.
   */
  Mono<Boolean> unlock(Lock lock);

}
