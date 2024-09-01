package com.github.elgleidson.lock.cucumber;

import com.github.elgleidson.lock.ReactiveLockManager;
import com.github.elgleidson.lock.TestApplication;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@CucumberContextConfiguration
@SpringBootTest(classes = TestApplication.class, webEnvironment = WebEnvironment.NONE, properties = {
  "de.flapdoodle.mongodb.embedded.version=6.3.2",
  "spring.data.mongodb.auto-index-creation=true"
})
@Slf4j
public class CucumberSteps extends CucumberStepsBase {

  public CucumberSteps(ReactiveLockManager lockManager) {
    super(lockManager);
  }

  @Before
  public void before() {
    super.before();
  }

  @Given("an existing record with id of {string}")
  public void givenAnExistingRecordWithIdOf(String id) {
    super.givenAnExistingRecordWithIdOf(id);
  }

  @When("I call the update {int} time(s) concurrently with id {string}")
  public void callTheUpdateConcurrently(int concurrency, String id) {
    super.callTheUpdateConcurrently(concurrency, id);
  }

  @When("I call the update {int} time(s) sequentially with id {string}")
  public void callTheUpdateSequentially(int times, String id) {
    super.callTheUpdateSequentially(times, id);
  }

  @When("I call the lock update {int} time(s) concurrently with id {string}")
  public void callTheLockUpdateConcurrently(int concurrency, String id) {
    super.callTheLockUpdateConcurrently(concurrency, id);
  }

  @When("I call the lock update {int} time(s) sequentially with id {string}")
  public void callTheLockUpdateSequentially(int times, String id) {
    super.callTheLockUpdateSequentially(times, id);
  }

  @Then("the record with id {string} is updated {int} time(s)")
  public void thenTheRecordIsUpdated(String id, int expectedUpdates) {
    super.thenTheRecordIsUpdated(id, expectedUpdates);
  }
}