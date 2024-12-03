package com.github.elgleidson.lock;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class LockManagerCaffeineAutoConfiguration {

  @Bean
  @ConditionalOnClass(Caffeine.class)
  @ConditionalOnMissingBean(LockManagerCaffeine.class)
  public LockManagerCaffeine lockManagerCaffeine() {
    return new LockManagerCaffeine();
  }

}
