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
        safeUnlock(lock);
      }
      throw ex;
    }
    safeUnlock(lock);
    return result;
  }

  private boolean safeUnlock(Lock lock) {
    try {
      return unlock(lock);
    } catch (Exception e) {
      return false;
    }
  }

  Lock lock(String uniqueIdentifier, Duration expiresIn);

  /**
   * Releases the lock.
   * <p>This method should never throw an exception. In case of any exception, just log it and return false instead.</p>
   * <p>You should unlock only when the lock ID and unique identifier match.</p>
   * @param lock
   * @return whether the lock was released.
   */
  boolean unlock(Lock lock);

}
