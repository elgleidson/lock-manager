package com.github.elgleidson.lock.cucumber;

import com.github.elgleidson.lock.LockFailureException;
import com.github.elgleidson.lock.ReactiveLockManager;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/tests")
@Slf4j
@AllArgsConstructor
public class LockTestController {

  // testing purposes, to be used by the cucumber test class to clean the context + check results
  protected final Map<String, Integer> db = new ConcurrentHashMap<>();
  private final ReactiveLockManager lockManager;

  @PostMapping("/{id}")
  public Mono<ResponseEntity<Integer>> get(@PathVariable("id") String id) {
    return Mono.just(db.get(id))
      .map(ResponseEntity::ok)
      .doFirst(() -> log.info("enter get(): id={}", id))
      .doOnSuccess(responseEntity -> log.info("exit get(): response={}", responseEntity));
  }

  @PutMapping("/{id}")
  public Mono<ResponseEntity<Void>> update(@PathVariable("id") String id) {
    return updateRecord(id)
        .thenReturn(ResponseEntity.noContent().<Void>build())
        .doFirst(() -> log.info("enter update(): id={}", id))
        .doOnSuccess(responseEntity -> log.info("exit update(): response={}", responseEntity));
  }

  @PutMapping("/{id}/lock")
  public Mono<ResponseEntity<Void>> lockUpdate(@PathVariable("id") String id) {
    return lockManager.wrap(id, Duration.ofSeconds(30), () -> updateRecord(id))
        .thenReturn(ResponseEntity.noContent().<Void>build())
        .onErrorResume(LockFailureException.class, e -> Mono.just(ResponseEntity.status(HttpStatus.LOCKED).build()))
        .doFirst(() -> log.info("enter update(): id={}", id))
        .doOnSuccess(responseEntity -> log.info("exit update(): response={}", responseEntity));
  }

  private Mono<Integer> updateRecord(String id) {
    return Mono.just(db.compute(id, (s, updates) -> updates == null ? 0 : updates + 1))
        .delayElement(Duration.ofMillis(250)) // to simulate processing
        .doFirst(() -> log.info("enter updateRecord(): id={}", id))
        .doOnSuccess(result -> log.info("exit updateRecord(): result={}", result));
  }
}