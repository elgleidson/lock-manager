package com.github.elgleidson.lock;

import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@AutoConfiguration(after = MongoReactiveDataAutoConfiguration.class)
public class ReactiveLockManagerMongoAutoConfiguration {

  @Bean
  @ConditionalOnBean(ReactiveMongoTemplate.class)
  @ConditionalOnMissingBean(ReactiveLockManagerMongo.class)
  public ReactiveLockManagerMongo reactiveLockManagerMongo(ReactiveMongoTemplate reactiveMongoTemplate) {
    ensureIndex(reactiveMongoTemplate);
    return new ReactiveLockManagerMongo(reactiveMongoTemplate);
  }

  private void ensureIndex(ReactiveMongoTemplate reactiveMongoTemplate) {
    var indexOps = reactiveMongoTemplate.indexOps(ReactiveLockManagerMongo.LockMongoEntity.class);
    // the index definitions need to match to what's in the LockMongoEntity's annotations
    indexOps.ensureIndex(new Index().on("uniqueIdentifier", Sort.Direction.ASC).unique().named("uniqueIdentifier"))
      .then(indexOps.ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO).named("expiresAt")))
      .subscribe();
  }

}
