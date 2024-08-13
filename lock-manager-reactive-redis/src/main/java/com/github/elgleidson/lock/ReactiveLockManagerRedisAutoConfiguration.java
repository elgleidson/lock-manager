package com.github.elgleidson.lock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@AutoConfiguration(after = RedisReactiveAutoConfiguration.class)
public class ReactiveLockManagerRedisAutoConfiguration {

  @Bean
  @ConditionalOnBean(ReactiveStringRedisTemplate.class)
  @ConditionalOnMissingBean(ReactiveLockManagerRedis.class)
  public ReactiveLockManagerRedis reactiveLockManagerRedis(ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
    return new ReactiveLockManagerRedis(reactiveStringRedisTemplate);
  }

}
