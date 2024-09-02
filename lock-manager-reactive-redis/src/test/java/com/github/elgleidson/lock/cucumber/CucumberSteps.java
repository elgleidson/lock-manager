package com.github.elgleidson.lock.cucumber;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import redis.embedded.RedisServer;

@CucumberContextConfiguration
@SpringBootTest(classes = TestApplication.class, webEnvironment = WebEnvironment.NONE)
@Slf4j
public class CucumberSteps extends CucumberStepsBase {

  private static final RedisServer redisServer = RedisServer.builder().port(6379).build();

  @BeforeAll
  public static void beforeAll()  {
    redisServer.start();
  }

  @AfterAll
  public static void afterAll() {
    redisServer.stop();
  }

  public CucumberSteps(ReactiveLockManager lockManager) {
    super(lockManager);
  }

  @Override
  @Before
  public void before() {
    super.before();
  }

  @Override
  @ParameterType(DURATION_REGEX)
  public Duration duration(String duration) {
    return super.duration(duration);
  }

  @Override
  @Given("the lock expires in {duration}")
  public void givenLockExpiresIn(Duration duration) {
    super.givenLockExpiresIn(duration);
  }

  @Override
  @Given("the process takes {duration}")
  public void givenTheProcessTakes(Duration duration) {
    super.givenTheProcessTakes(duration);
  }

  @Override
  @Given("an existing record with id of {string}")
  public void givenAnExistingRecordWithIdOf(String id) {
    super.givenAnExistingRecordWithIdOf(id);
  }

  @Override
  @When("I call the update {int} time(s) concurrently with id {string}")
  public void callTheUpdateConcurrently(int concurrency, String id) {
    super.callTheUpdateConcurrently(concurrency, id);
  }

  @Override
  @When("I call the update {int} time(s) sequentially with id {string}")
  public void callTheUpdateSequentially(int times, String id) {
    super.callTheUpdateSequentially(times, id);
  }

  @Override
  @When("I call the lock update {int} time(s) concurrently with id {string}")
  public void callTheLockUpdateConcurrently(int concurrency, String id) {
    super.callTheLockUpdateConcurrently(concurrency, id);
  }

  @Override
  @When("I call the lock update {int} time(s) sequentially with id {string}")
  public void callTheLockUpdateSequentially(int times, String id) {
    super.callTheLockUpdateSequentially(times, id);
  }

  @Override
  @Then("the record with id {string} is updated {int} time(s)")
  public void thenTheRecordIsUpdated(String id, int expectedUpdates) {
    super.thenTheRecordIsUpdated(id, expectedUpdates);
  }
}