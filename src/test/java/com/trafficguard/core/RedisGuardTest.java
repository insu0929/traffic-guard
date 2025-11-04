package com.trafficguard.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisGuardTest {

    @Autowired
    private RedisGuard redisGuard;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Redis에서 FLUSHALL이 비활성화되어 있을 수 있으므로
        // 테스트별로 고유한 키를 사용하여 충돌을 방지
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 생성된 키들을 정리
        try {
            String[] patterns = {
                "tb:test:first-request:*",
                "tb:test:exceed-burst:*",
                "tb:test:token-refill:*",
                "tb:test:independent1:*",
                "tb:test:independent2:*"
            };

            for (String pattern : patterns) {
                try {
                    redisTemplate.execute((RedisCallback<Void>) connection -> {
                        connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match(pattern)
                            .count(100)
                            .build())
                        .forEachRemaining(key -> connection.del(key));
                        return null;
                    });
                } catch (Exception e) {
                    // 키 삭제 실패는 무시
                }
            }
        } catch (Exception e) {
            // 정리 실패는 무시
        }
    }

    @Test
    void testTokenBucket_FirstRequest_ShouldAllow() {
        // Given
        String bucketKey = "test:first-request:" + System.currentTimeMillis();
        double ratePerSec = 10.0;
        int burst = 5;
        long ttlMillis = 60000;

        // When
        RedisGuard.RateDecision decision = redisGuard.tokenBucketAllow(bucketKey, ratePerSec, burst, ttlMillis);

        // Then
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remainingTokens()).isEqualTo(4.0); // burst - 1
        assertThat(decision.retryAfterMs()).isEqualTo(0);
    }

    @Test
    void testTokenBucket_ExceedBurst_ShouldReject() {
        // Given
        String bucketKey = "test:exceed-burst:" + System.currentTimeMillis();
        double ratePerSec = 1.0;
        int burst = 2;
        long ttlMillis = 60000;

        // When - burst만큼 요청 (2번)
        for (int i = 0; i < 2; i++) {
            RedisGuard.RateDecision decision = redisGuard.tokenBucketAllow(bucketKey, ratePerSec, burst, ttlMillis);
            assertThat(decision.allowed()).isTrue();
        }

        // 3번째 요청은 거부되어야 함
        RedisGuard.RateDecision decision = redisGuard.tokenBucketAllow(bucketKey, ratePerSec, burst, ttlMillis);

        // Then
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterMs()).isGreaterThan(0);
    }

    @Test
    void testTokenBucket_TokenRefill_ShouldAllow() throws InterruptedException {
        // Given
        String bucketKey = "test:token-refill:" + System.currentTimeMillis();
        double ratePerSec = 10.0;
        int burst = 5;
        long ttlMillis = 60000;

        // When - 모든 토큰 소진
        for (int i = 0; i < 5; i++) {
            redisGuard.tokenBucketAllow(bucketKey, ratePerSec, burst, ttlMillis);
        }

        // 토큰이 모두 소진되었는지 확인
        RedisGuard.RateDecision decision = redisGuard.tokenBucketAllow(bucketKey, ratePerSec, burst, ttlMillis);
        assertThat(decision.allowed()).isFalse();

        // 150ms 대기 (10 tokens/sec이므로 100ms에 1개 토큰 충전)
        Thread.sleep(150);

        // Then - 토큰이 충전되어 요청이 허용되어야 함
        decision = redisGuard.tokenBucketAllow(bucketKey, ratePerSec, burst, ttlMillis);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void testTokenBucket_DifferentKeys_Independent() {
        // Given
        long timestamp = System.currentTimeMillis();
        String bucketKey1 = "test:independent1:" + timestamp;
        String bucketKey2 = "test:independent2:" + timestamp;
        double ratePerSec = 10.0;
        int burst = 2;
        long ttlMillis = 60000;

        // When - 첫 번째 버킷을 모두 소진
        for (int i = 0; i < 2; i++) {
            redisGuard.tokenBucketAllow(bucketKey1, ratePerSec, burst, ttlMillis);
        }

        // Then - 두 번째 버킷은 독립적으로 동작해야 함
        RedisGuard.RateDecision decision1 = redisGuard.tokenBucketAllow(bucketKey1, ratePerSec, burst, ttlMillis);
        RedisGuard.RateDecision decision2 = redisGuard.tokenBucketAllow(bucketKey2, ratePerSec, burst, ttlMillis);

        assertThat(decision1.allowed()).isFalse();
        assertThat(decision2.allowed()).isTrue();
    }
}


