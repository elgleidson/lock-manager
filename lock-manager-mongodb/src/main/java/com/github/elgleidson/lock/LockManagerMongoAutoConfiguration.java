package com.github.elgleidson.lock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

@AutoConfiguration(after = MongoDataAutoConfiguration.class)
public class LockManagerMongoAutoConfiguration {

  @Bean
  @ConditionalOnBean(MongoTemplate.class)
  @ConditionalOnMissingBean(LockManagerMongo.class)
  @ConditionalOnProperty(prefix = "spring.data.mongodb", name = "auto-index-creation", havingValue = "true")
  public LockManagerMongo lockManagerMongo(MongoTemplate mongoTemplate) {
    return new LockManagerMongo(mongoTemplate);
  }

}
