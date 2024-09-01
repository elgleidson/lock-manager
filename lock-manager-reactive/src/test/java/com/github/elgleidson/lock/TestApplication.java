package com.github.elgleidson.lock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TestApplication {

  public static void main(String[] args) {
    SpringApplication.run(TestApplication.class, args);
  }

  @Bean
  public ReactiveLockManagerInMemory reactiveLockManagerInMemory() {
    return new ReactiveLockManagerInMemory();
  }

}
