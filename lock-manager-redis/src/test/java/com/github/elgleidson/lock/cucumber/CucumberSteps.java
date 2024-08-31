package com.github.elgleidson.lock.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.elgleidson.lock.LockFailureException;
import com.github.elgleidson.lock.LockManager;
import com.github.elgleidson.lock.TestApplication;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import redis.embedded.RedisServer;

@CucumberContextConfiguration
@SpringBootTest(classes = TestApplication.class, webEnvironment = WebEnvironment.NONE)
@Slf4j
public class CucumberSteps {

  private static final RedisServer redisServer = RedisServer.builder().port(6379).build();

  private static final Duration TTL = Duration.ofSeconds(30);
  private static final Duration DELAY = Duration.ofMillis(150);

  enum Status {
    UPDATED, LOCKED
  }

  private final Map<String, AtomicInteger> db = new ConcurrentHashMap<>();

  @Autowired
  private LockManager lockManager;

  private List<Status> responses;

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

  @Given("an existing record with id of {string}")
  public void givenAnExistingRecordWithIdOf(String id) {
    db.put(id, new AtomicInteger(0));
  }

  @When("I call the update {int} time(s) concurrently with id {string}")
  public void iCallTheUpdateConcurrently(int concurrency, String id) {
    responses = callUpdateConcurrently(concurrency, id, this::update);
  }

  @When("I call the update {int} time(s) sequentially with id {string}")
  public void iCallTheUpdateSequentially(int times, String id) {
    responses = callUpdateSequentially(times, id, this::update);
  }

  @When("I call the lock update {int} time(s) concurrently with id {string}")
  public void iCallTheLockUpdateConcurrently(int concurrency, String id) {
    responses = callUpdateConcurrently(concurrency, id, this::updateLock);
  }

  @When("I call the lock update {int} time(s) sequentially with id {string}")
  public void iCallTheLockUpdateSequentially(int times, String id) {
    responses = callUpdateSequentially(times, id, this::updateLock);
  }

  private List<Status> callUpdateSequentially(int times, String id, BiFunction<Integer, String, Status> function) {
    return IntStream.range(1, times+1).boxed().sequential().map(i -> {
      log.info("sequential exec={}: start", i);
      var status = function.apply(i, id);
      log.info("sequential exec={}: end", i);
      return status;
    }).toList();
  }

  private List<Status> callUpdateConcurrently(int concurrency, String id, BiFunction<Integer, String, Status> function) {
    return IntStream.range(1, concurrency+1).boxed().parallel().map(i -> {
      log.info("parallel exec={}: start", i);
      var status = function.apply(i, id);
      log.info("parallel exec={}: end", i);
      return status;
    }).toList();
  }

  @SneakyThrows
  private Status update(int exec, String id) {
    log.info("exec={}: updating id={}", exec, id);
    Thread.sleep(DELAY);
    var updates = db.get(id);
    updates.incrementAndGet();
    var status = Status.UPDATED;
    log.info("exec={}: updated id={}, status={}", exec, id, status);
    return status;
  }

  private Status updateLock(int exec, String id) {
    try {
      return lockManager.wrap(id, TTL, () -> update(exec, id));
    } catch (LockFailureException e) {
      log.error("exec={}: id={}, status={}", exec, id, Status.LOCKED);
      return Status.LOCKED;
    }
  }

  @Then("the responses code are")
  public void thenTheResponsesCodeAre(DataTable dataTable) {
    var expected = dataTable.rows(1).asMap(String.class, Long.class);
    var actual = responses.stream().map(Enum::name).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    assertThat(actual).isEqualTo(expected);
  }

  @Then("the record with id {string} is updated {int} time(s)")
  public void thenTheRecordIsUpdated(String id, int expectedUpdates) {
    var updates = db.get(id).get();
    assertThat(updates).isEqualTo(expectedUpdates);
  }
}