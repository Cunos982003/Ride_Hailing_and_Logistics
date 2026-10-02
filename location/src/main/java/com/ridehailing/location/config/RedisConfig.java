package com.ridehailing.location.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.time.Duration;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

  @Bean(destroyMethod = "shutdown")
  public ClientResources clientResources() {
    return DefaultClientResources.builder()
        .threadFactoryProvider(poolName -> new DefaultThreadFactory(poolName, true))
        .build();
  }

  @Bean
  public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
    return builder ->
        builder.clientOptions(
            ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(
                    SocketOptions.builder().connectTimeout(Duration.ofMillis(500)).build())
                .build());
  }
}
