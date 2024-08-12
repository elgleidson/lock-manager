package com.github.elgleidson.lock;

import java.time.Duration;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public interface ReactiveLockManager {

  default <T> Mono<T> wrap(String uniqueIdentifier, Duration expiresIn, Supplier<Mono<T>> monoSupplier) {
    return lock(uniqueIdentifier, expiresIn)
      .flatMap(lock -> Mono.defer(monoSupplier)
      .doOnSuccess(t -> unlock(lock).subscribe()));
  }

  Mono<Lock> lock(String uniqueIdentifier, Duration expiresIn);

  Mono<Long> unlock(Lock lock);

}
