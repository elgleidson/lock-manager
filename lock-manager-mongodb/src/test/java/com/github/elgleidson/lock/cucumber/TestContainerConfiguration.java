package com.github.elgleidson.lock.cucumber;

import com.github.elgleidson.lock.TestApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;

@CucumberContextConfiguration
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TestContainerConfiguration {

  @ServiceConnection
  static final MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0.16");

}
