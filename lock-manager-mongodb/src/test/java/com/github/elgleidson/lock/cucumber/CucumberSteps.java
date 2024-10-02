package com.github.elgleidson.lock.cucumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.elgleidson.lock.Lock;
import com.github.elgleidson.lock.LockFailureException;
import com.github.elgleidson.lock.LockManager;
import com.github.elgleidson.lock.TestApplication;
import io.cucumber.java.Before;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@CucumberContextConfiguration
@SpringBootTest(classes = TestApplication.class, webEnvironment = WebEnvironment.NONE, properties = {
  "de.flapdoodle.mongodb.embedded.version=7.0.12",
  "spring.data.mongodb.auto-index-creation=true"
})
@Slf4j
public class CucumberSteps {

  private final Map<String, AtomicInteger> db = new ConcurrentHashMap<>();

  @Autowired
  private LockManager lockManager;

  private Duration ttl;
  private Duration delay;
  private Optional<Lock> lockResult;

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

  @Given("I try to lock the record with id of {string}")
  public void givenILockRecordWithIdOf(String id) {
    try {
      lockResult = Optional.of(lockManager.lock(id, ttl));
    } catch (LockFailureException e) {
      lockResult = Optional.empty();
    }
  }

  @Given("I unlock")
  public void givenIUnlock() {
    var lock = lockResult.get();
    lockManager.unlock(lock);
  }

  @Given("I wait {duration}")
  public void givenIWait(Duration duration) {
    await().during(duration).until(() -> true);
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

  private void callUpdateSequentially(int times, String id, BiFunction<Integer, String, Boolean> function) {
    IntStream.range(1, times+1).boxed().sequential().forEach(i -> {
      log.info("sequential exec={}: start", i);
      function.apply(i, id);
      log.info("sequential exec={}: end", i);
    });
  }

  private void callUpdateConcurrently(int concurrency, String id, BiFunction<Integer, String, Boolean> function) {
    IntStream.range(1, concurrency+1).boxed().parallel().forEach(i -> {
      log.info("parallel exec={}: start", i);
      function.apply(i, id);
      log.info("parallel exec={}: end", i);
    });
  }

  @SneakyThrows
  private boolean update(int exec, String id) {
    log.info("exec={}: updating id={}", exec, id);
    Thread.sleep(delay); // to simulate processing
    var updates = db.get(id).incrementAndGet();
    log.info("exec={}: updated id={}, updates={}", exec, id, updates);
    return true;
  }

  private boolean updateLock(int exec, String id) {
    try {
      return lockManager.wrap(id, ttl, () -> update(exec, id));
    } catch (LockFailureException e) {
      log.error("exec={}: id={}, locked", exec, id);
      return false;
    }
  }

  @Then("the record with id {string} is updated {int} time(s)")
  public void thenTheRecordIsUpdated(String id, int expectedUpdates) {
    var updates = db.get(id).get();
    assertThat(updates).isEqualTo(expectedUpdates);
  }

  @Then("the lock is acquired")
  public void thenTheLockIsAcquired() {
    assertThat(lockResult).isPresent();
  }

  @Then("the lock is not acquired")
  public void thenTheLockIsNotAcquired() {
    assertThat(lockResult).isNotPresent();
  }
}