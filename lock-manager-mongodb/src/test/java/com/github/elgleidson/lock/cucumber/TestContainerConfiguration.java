package com.github.elgleidson.lock.cucumber;

import com.github.elgleidson.lock.TestApplication;
import io.cucumber.java.BeforeAll;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@CucumberContextConfiguration
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
public class TestContainerConfiguration {

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0.16");

  @BeforeAll
  public static void beforeAll() {
    mongodb.start();
  }

}
