package com.github.elgleidson.lock.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.elgleidson.lock.LockFailureException;
import com.github.elgleidson.lock.ReactiveLockManager;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@Slf4j
@RequiredArgsConstructor
public abstract class CucumberStepsBase {

  private static final Duration TTL = Duration.ofSeconds(30);
  private static final Duration DELAY = Duration.ofMillis(150);

  private final Map<String, AtomicInteger> db = new ConcurrentHashMap<>();

  private final ReactiveLockManager lockManager;

  private Flux<Boolean> responses;

  public void before() {
    db.clear();
  }

  public void givenAnExistingRecordWithIdOf(String id) {
    db.put(id, new AtomicInteger(0));
  }

  public void callTheUpdateConcurrently(int concurrency, String id) {
    callUpdateConcurrently(concurrency, id, this::update);
  }

  public void callTheUpdateSequentially(int times, String id) {
    callUpdateSequentially(times, id, this::update);
  }

  public void callTheLockUpdateConcurrently(int concurrency, String id) {
    callUpdateConcurrently(concurrency, id, this::updateLock);
  }

  public void callTheLockUpdateSequentially(int times, String id) {
    callUpdateSequentially(times, id, this::updateLock);
  }

  private void callUpdateSequentially(int times, String id, BiFunction<Integer, String, Mono<Boolean>> function) {
    responses = Flux.range(1, times)
      .concatMap(i -> function.apply(i, id)
        .doFirst(() -> log.info("sequential exec={}: start", i))
        .doOnSuccess(unused -> log.info("sequential exec={}: end", i))
      )
      .subscribeOn(Schedulers.single());
  }

  @SneakyThrows
  private void callUpdateConcurrently(int concurrency, String id, BiFunction<Integer, String, Mono<Boolean>> function) {
    responses = Flux.range(1, concurrency)
      .parallel(concurrency)
      .runOn(Schedulers.newParallel("parallel", concurrency))
      .flatMap(i -> function.apply(i, id)
        .doFirst(() -> log.info("parallel exec={}: start", i))
        .doOnSuccess(unused -> log.info("parallel exec={}: end", i))
      )
      .sequential()
      .subscribeOn(Schedulers.single());
  }

  private Mono<Boolean> update(int exec, String id) {
    return Mono.just(db.get(id))
      .delayElement(DELAY) // to simulate processing
      .map(AtomicInteger::incrementAndGet)
      .doOnNext(updates -> log.info("exec={}: updated id={}, updates={}", exec, id, updates))
      .thenReturn(true)
      .doFirst(() -> log.info("exec={}: updating id={}", exec, id));
  }

  private Mono<Boolean> updateLock(int exec, String id) {
    return lockManager.wrap(id, TTL, true, () -> update(exec, id))
      .onErrorResume(LockFailureException.class, e -> {
        log.error("exec={}: id={}, locked", exec, id);
        return Mono.just(false);
      });
  }

  public void thenTheRecordIsUpdated(String id, int expectedUpdates) {
    StepVerifier.create(responses.then()).verifyComplete();
    var updates = db.get(id).get();
    assertThat(updates).isEqualTo(expectedUpdates);
  }
}