package com.github.elgleidson.lock.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.elgleidson.lock.LockFailureException;
import com.github.elgleidson.lock.LockManager;
import com.github.elgleidson.lock.TestApplication;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import java.time.Duration;
import java.util.Map;
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
  "de.flapdoodle.mongodb.embedded.version=6.3.2",
  "spring.data.mongodb.auto-index-creation=true"
})
@Slf4j
public class CucumberSteps {

  private static final Duration TTL = Duration.ofSeconds(30);
  private static final Duration DELAY = Duration.ofMillis(150);

  private final Map<String, AtomicInteger> db = new ConcurrentHashMap<>();

  @Autowired
  private LockManager lockManager;

  @Before
  public void before() {
    db.clear();
  }

  @Given("an existing record with id of {string}")
  public void givenAnExistingRecordWithIdOf(String id) {
    db.put(id, new AtomicInteger(0));
  }

  @When("I call the update {int} time(s) concurrently with id {string}")
  public void iCallTheUpdateConcurrently(int concurrency, String id) {
    callUpdateConcurrently(concurrency, id, this::update);
  }

  @When("I call the update {int} time(s) sequentially with id {string}")
  public void iCallTheUpdateSequentially(int times, String id) {
    callUpdateSequentially(times, id, this::update);
  }

  @When("I call the lock update {int} time(s) concurrently with id {string}")
  public void iCallTheLockUpdateConcurrently(int concurrency, String id) {
    callUpdateConcurrently(concurrency, id, this::updateLock);
  }

  @When("I call the lock update {int} time(s) sequentially with id {string}")
  public void iCallTheLockUpdateSequentially(int times, String id) {
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
    Thread.sleep(DELAY); // to simulate processing
    var updates = db.get(id).incrementAndGet();
    log.info("exec={}: updated id={}, updates={}", exec, id, updates);
    return true;
  }

  private boolean updateLock(int exec, String id) {
    try {
      return lockManager.wrap(id, TTL, () -> update(exec, id));
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
}