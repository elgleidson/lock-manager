package com.github.elgleidson.lock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = RedisAutoConfiguration.class)
public class LockManagerRedisAutoConfiguration {

  @Bean
  @ConditionalOnBean(StringRedisTemplate.class)
  @ConditionalOnMissingBean(LockManagerRedis.class)
  public LockManagerRedis lockManagerRedis(StringRedisTemplate stringRedisTemplate) {
    return new LockManagerRedis(stringRedisTemplate);
  }

}
