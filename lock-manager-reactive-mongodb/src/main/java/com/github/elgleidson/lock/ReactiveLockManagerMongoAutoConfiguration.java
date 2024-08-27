package com.github.elgleidson.lock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

@AutoConfiguration(after = MongoReactiveDataAutoConfiguration.class)
public class ReactiveLockManagerMongoAutoConfiguration {

  @Bean
  @ConditionalOnBean(ReactiveMongoTemplate.class)
  @ConditionalOnMissingBean(ReactiveLockManagerMongo.class)
  @ConditionalOnProperty(prefix = "spring.data.mongodb", name = "auto-index-creation", havingValue = "true")
  public ReactiveLockManagerMongo reactiveLockManagerMongo(ReactiveMongoTemplate reactiveMongoTemplate) {
    return new ReactiveLockManagerMongo(reactiveMongoTemplate);
  }

}
