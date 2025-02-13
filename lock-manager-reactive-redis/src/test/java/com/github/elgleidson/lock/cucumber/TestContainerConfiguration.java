package com.github.elgleidson.lock.cucumber;

import com.github.elgleidson.lock.TestApplication;
import com.redis.testcontainers.RedisContainer;
import io.cucumber.java.BeforeAll;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@CucumberContextConfiguration
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
public class TestContainerConfiguration {

  @Container
  @ServiceConnection
  private static final RedisContainer redis = new RedisContainer("redis:7.4.2-alpine");

  @BeforeAll
  public static void beforeAll() {
    redis.start();
  }

}
