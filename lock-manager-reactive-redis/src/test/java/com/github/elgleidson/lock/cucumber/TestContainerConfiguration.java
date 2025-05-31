package com.github.elgleidson.lock.cucumber;

import com.github.elgleidson.lock.TestApplication;
import com.redis.testcontainers.RedisContainer;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

@CucumberContextConfiguration
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TestContainerConfiguration {

  @ServiceConnection
  static final RedisContainer redis = new RedisContainer("redis:7.4.2-alpine");

}
