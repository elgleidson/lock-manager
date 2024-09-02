# Simple Lock Manager

A simple implementations of a lock manager:
- `lock-manager-*` (for non-reactive implementations)
- `lock-manager-reactive-*` (for reactive implementations)


## Examples:

### Lock manager (non-reactive)

`pom.xml`:
```xml
    <dependency>
      <groupId>com.github.elgleidson</groupId>
      <artifactId>lock-manager-redis</artifactId>
    </dependency>
```
Or if you want to use MongoDB:
```xml
    <dependency>
      <groupId>com.github.elgleidson</groupId>
      <artifactId>lock-manager-mongodb</artifactId>
    </dependency>
```

`MyService`:
```java
@Service
@RequiredArgsConstructor
public class MyService {

  private final MyRepository myRepository;
  private final LockManager lockManager;

  public MyResult doSomething() {
    try {
      return lockManager.wrap("my-unique-identifier", Duration.ofSeconds(30), () -> {
        return myRepository.doSomething();
      });
    } catch (LockFailureException e) {
      // handle lock failure
    }
  }
}
```
Or if you want lock and unlock manually:
```java
  public MyResult doSomething() {
    try {
      var lock = lockManager.lock("my-unique-identifier", Duration.ofSeconds(30));
      var myResult = myRepository.doSomething();
      lockManager.unlock(lock);
      return myResult;
    } catch (LockFailureException e) {
      // handle lock failure
    } 
  }
```

### Lock manager (reactive)

`pom.xml`:
```xml
    <dependency>
      <groupId>com.github.elgleidson</groupId>
      <artifactId>lock-manager-reactive-redis</artifactId>
    </dependency>
```
Or if you want to use MongoDB:
```xml
    <dependency>
      <groupId>com.github.elgleidson</groupId>
      <artifactId>lock-manager-reactive-mongodb</artifactId>
    </dependency>
```

`MyService`:
```java
@Service
@RequiredArgsConstructor
public class MyReactiveService {

  private final MyReactiveRepository myRepository;
  private final ReactiveLockManager lockManager;

  public Mono<MyResult> doSomethingWrap() {
    return lockManager.wrap("my-unique-identifier", Duration.ofSeconds(30), () -> {
        return myRepository.doSomething();
      })
      .onErrorResume(LockFailureException.class, lockFailureException -> {
        // handle lock failure
      });
  }
}
```
Or if you want lock and unlock manually:
```java
  public Mono<MyResult> doSomething() {
    return lockManager.lock("my-unique-identifier", Duration.ofSeconds(30))
      .flatMap(lock -> myRepository.doSomething()
        .flatMap(myResult -> lockManager.unlock(lock).thenReturn(myResult))
      )
      .onErrorResume(LockFailureException.class, lockFailureException -> {
        // handle lock failure
      });
  }
```
