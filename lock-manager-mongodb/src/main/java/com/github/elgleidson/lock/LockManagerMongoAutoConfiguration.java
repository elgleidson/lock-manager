package com.github.elgleidson.lock;

import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@AutoConfiguration(after = MongoDataAutoConfiguration.class)
public class LockManagerMongoAutoConfiguration {

  @Bean
  @ConditionalOnBean(MongoTemplate.class)
  @ConditionalOnMissingBean(LockManagerMongo.class)
  public LockManagerMongo lockManagerMongo(MongoTemplate mongoTemplate) {
    ensureIndex(mongoTemplate);
    return new LockManagerMongo(mongoTemplate);
  }

  private void ensureIndex(MongoTemplate mongoTemplate) {
    var indexOps = mongoTemplate.indexOps(LockManagerMongo.LockMongoEntity.class);
    // the index definitions need to match to what's in the LockMongoEntity's annotations
    indexOps.ensureIndex(new Index().on("uniqueIdentifier", Sort.Direction.ASC).unique().named("uniqueIdentifier"));
    indexOps.ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO).named("expiresAt"));
  }

}
