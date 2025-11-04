package com.trafficguard.config;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Configuration
public class RedisConfig {
    @Bean
    @ConfigurationProperties(prefix = "spring.redis.properties")
    public RedisProperties redisProperties() {
        return new RedisProperties();
    }

    /**
     * redis connection 설정
     */
    @Bean
    public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redisson) {
        return new RedissonConnectionFactory(redisson);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnBean(RedisProperties.class)
    public RedissonClient redisson(RedisProperties redisProperties) throws IOException {
        Config config = new Config();
        config.useMasterSlaveServers()
                .setMasterAddress(redisProperties.getMasterHost())
                .addSlaveAddress(redisProperties.getSlaveHost())
                .setReadMode(ReadMode.SLAVE);

        return Redisson.create(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedissonConnectionFactory redissonConnectionFactory) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(redissonConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedissonConnectionFactory redissonConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redissonConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }

    @Data
    public static class RedisProperties {
        private String masterHost;
        private String slaveHost;
    }

    /**
     * Token Bucket 스크립트를 외부 파일에서 로드
     * KEYS[1]=bucket(tokens), KEYS[2]=ts
     * ARGV[1]=ratePerSec, ARGV[2]=burst, ARGV[3]=nowMs, ARGV[4]=ttlMs
     * return {allowed(0/1), tokens(float), retryAfterMs(int)}
     */
    @Bean
    public DefaultRedisScript<List<Object>> tokenBucketScript() {
        return RedisScriptLoader.loadTokenBucketScript();
    }
}


