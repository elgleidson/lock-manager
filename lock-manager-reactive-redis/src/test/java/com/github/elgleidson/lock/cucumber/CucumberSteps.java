package com.github.elgleidson.lock.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.elgleidson.lock.LockFailureException;
import com.github.elgleidson.lock.ReactiveLockManager;
import com.github.elgleidson.lock.TestApplication;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import redis.embedded.RedisServer;

@CucumberContextConfiguration
@SpringBootTest(classes = TestApplication.class, webEnvironment = WebEnvironment.NONE)
@Slf4j
public class CucumberSteps {

  private static final RedisServer redisServer = RedisServer.builder().port(6379).build();

  private final Map<String, AtomicInteger> db = new ConcurrentHashMap<>();

  @Autowired
  private ReactiveLockManager lockManager;

  private Duration ttl;
  private Duration delay;

  private Flux<Boolean> responses;

  @BeforeAll
  public static void beforeAll()  {
    redisServer.start();
  }

  @AfterAll
  public static void afterAll() {
    redisServer.stop();
  }

  @Before
  public void before() {
    db.clear();
  }

  @ParameterType("(\\d+(s|ms))")
  public Duration duration(String duration) {
    var amount = duration.replaceFirst("(ms|s)", "");
    var timeUnit = duration.replaceFirst(amount, "");
    return timeUnit.equals("ms")
      ? Duration.ofMillis(Long.parseLong(amount))
      : Duration.ofSeconds(Long.parseLong(amount));
  }

  @Given("the lock expires in {duration}")
  public void givenLockExpiresIn(Duration duration) {
    this.ttl = duration;
  }

  @Given("the process takes {duration}")
  public void givenTheProcessTakes(Duration duration) {
    this.delay = duration;
  }

  @Given("an existing record with id of {string}")
  public void givenAnExistingRecordWithIdOf(String id) {
    db.put(id, new AtomicInteger(0));
  }

  @When("I call the update {int} time(s) concurrently with id {string}")
  public void callTheUpdateConcurrently(int concurrency, String id) {
    callUpdateConcurrently(concurrency, id, this::update);
  }

  @When("I call the update {int} time(s) sequentially with id {string}")
  public void callTheUpdateSequentially(int times, String id) {
    callUpdateSequentially(times, id, this::update);
  }

  @When("I call the lock update {int} time(s) concurrently with id {string}")
  public void callTheLockUpdateConcurrently(int concurrency, String id) {
    callUpdateConcurrently(concurrency, id, this::updateLock);
  }

  @When("I call the lock update {int} time(s) sequentially with id {string}")
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
      .delayElement(delay) // to simulate processing
      .map(AtomicInteger::incrementAndGet)
      .doOnNext(updates -> log.info("exec={}: updated id={}, updates={}", exec, id, updates))
      .thenReturn(true)
      .doFirst(() -> log.info("exec={}: updating id={}", exec, id));
  }

  private Mono<Boolean> updateLock(int exec, String id) {
    return lockManager.wrap(id, ttl, true, () -> update(exec, id))
      .onErrorResume(LockFailureException.class, e -> {
        log.error("exec={}: id={}, locked", exec, id);
        return Mono.just(false);
      });
  }

  @Then("the record with id {string} is updated {int} time(s)")
  public void thenTheRecordIsUpdated(String id, int expectedUpdates) {
    StepVerifier.create(responses.then()).verifyComplete();
    var updates = db.get(id).get();
    assertThat(updates).isEqualTo(expectedUpdates);
  }
}