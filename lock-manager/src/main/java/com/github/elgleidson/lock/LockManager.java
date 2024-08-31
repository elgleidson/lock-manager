package com.github.elgleidson.lock;

import java.time.Duration;
import java.util.function.Supplier;

public interface LockManager {

  default <T> T wrap(String uniqueIdentifier, Duration expiresIn, Supplier<T> supplier) {
    return wrap(uniqueIdentifier, expiresIn, true, supplier);
  }

  default <T> T wrap(String uniqueIdentifier, Duration expiresIn, boolean onErrorUnlock, Supplier<T> supplier) {
    var lock = lock(uniqueIdentifier, expiresIn);
    T result;
    try {
      result = supplier.get();
    } catch (Exception ex) {
      if (onErrorUnlock) {
        unlock(lock);
      }
      throw ex;
    }
    unlock(lock);
    return result;
  }

  Lock lock(String uniqueIdentifier, Duration expiresIn);

  boolean unlock(Lock lock);

}
