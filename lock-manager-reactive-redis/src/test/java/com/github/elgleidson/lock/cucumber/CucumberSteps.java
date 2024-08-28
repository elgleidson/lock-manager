package com.github.elgleidson.lock.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.elgleidson.lock.TestApplication;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.reactive.server.WebTestClient;
import redis.embedded.RedisServer;

@CucumberContextConfiguration
@SpringBootTest(classes = TestApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@Slf4j
public class CucumberSteps {

  private static final RedisServer redisServer = RedisServer.builder().port(6379).build();

  private static final String UPDATE_URL = "/tests/{id}";
  private static final String LOCK_UPDATE_URL = "/tests/{id}/lock";

  @Autowired
  private WebTestClient webTestClient;
  @Autowired
  private LockTestController lockTestController;
  private List<HttpStatusCode> responses;

  @BeforeAll
  public static void beforeAll()  {
    redisServer.start();
  }

  @AfterAll
  public static void afterAll() {
    redisServer.stop();
  }

  @Before
  public void thenDeleteTheCollection() {
    log.warn("@Before");
    lockTestController.db.clear();
  }

  @Given("an existing record with id of {string}")
  public void givenAnExistingRecordWithIdOf(String id) {
    lockTestController.db.put(id, 0);
  }

  @When("I call the update endpoint {int} time(s) concurrently with id {string}")
  public void iCallTheUpdateEndpointConcurrently(int concurrency, String id) {
    callUpdateEndpointConcurrently(UPDATE_URL, concurrency, id);
  }

  @When("I call the update endpoint {int} time(s) sequentially with id {string}")
  public void iCallTheUpdateEndpointSequentially(int times, String id) {
    callUpdateEndpointSequentially(UPDATE_URL, times, id);
  }

  @When("I call the lock update endpoint {int} time(s) concurrently with id {string}")
  public void iCallTheLockUpdateEndpointConcurrently(int concurrency, String id) {
    callUpdateEndpointConcurrently(LOCK_UPDATE_URL, concurrency, id);
  }

  @When("I call the lock update endpoint {int} time(s) sequentially with id {string}")
  public void iCallTheLockUpdateEndpointSequentially(int times, String id) {
    callUpdateEndpointSequentially(LOCK_UPDATE_URL, times, id);
  }

  private void callUpdateEndpointSequentially(String url, int times, String id) {
    responses = IntStream.rangeClosed(1, times).mapToObj(i -> callApi(url, id)).toList();
  }

  @SneakyThrows
  private void callUpdateEndpointConcurrently(String url, int concurrency, String id) {
    try (var executorService = Executors.newFixedThreadPool(concurrency)) {
      var callable = toCallable(url, id);
      var calls = Collections.nCopies(concurrency, callable);
      responses = executorService.invokeAll(calls).stream().map(Future::resultNow).toList();
    }
  }

  private Callable<HttpStatusCode> toCallable(String url, String id) {
    return () -> callApi(url, id);
  }

  private HttpStatusCode callApi(String url, String id) {
    return webTestClient.put()
        .uri(url, id)
        .exchange()
        .returnResult(Void.class)
        .getStatus();
  }

  @Then("the responses code are")
  public void thenTheResponsesCodeAre(DataTable dataTable) {
    var expected = dataTable.rows(1).asMap(Integer.class, Long.class);
    var actual = responses.stream().map(HttpStatusCode::value).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    assertThat(actual).isEqualTo(expected);
  }

  @Then("the record with id {string} is updated {int} time(s)")
  public void thenTheRecordIsUpdated(String id, int expectedUpdates) {
    var updates = lockTestController.db.get(id);
    assertThat(updates).isEqualTo(expectedUpdates);
  }
}