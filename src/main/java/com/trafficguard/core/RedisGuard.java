package com.trafficguard.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class RedisGuard {

    public static class RateDecision {
        private final boolean allowed;
        private final double remainingTokens;
        private final long retryAfterMs;

        public RateDecision(boolean allowed, double remainingTokens, long retryAfterMs) {
            this.allowed = allowed; this.remainingTokens = remainingTokens; this.retryAfterMs = retryAfterMs;
        }

        public boolean allowed() {
            return allowed;
        }

        public double remainingTokens() {
            return remainingTokens;
        }

        public long retryAfterMs() {
            return retryAfterMs;
        }
    }

    private final StringRedisTemplate rt;
    private final DefaultRedisScript<List<Object>> tokenBucket;

    public RedisGuard(StringRedisTemplate rt, DefaultRedisScript<List<Object>> tokenBucket) {
        this.rt = rt; this.tokenBucket = tokenBucket;
    }

    public RateDecision tokenBucketAllow(String bucketKey, double ratePerSec, int burst, long ttlMillis) {
        List<String> keys = Arrays.asList("tb:"+bucketKey+":tokens", "tb:"+bucketKey+":ts");
        long now = System.currentTimeMillis();

        log.info("RedisGuard.tokenBucketAllow - bucketKey: {}, ratePerSec: {}, burst: {}, ttlMillis: {}, now: {}",
                bucketKey, ratePerSec, burst, ttlMillis, now);
        log.info("RedisGuard.tokenBucketAllow - keys: {}", keys);

        try {
            List<Object> res = rt.execute(tokenBucket, keys,
                    String.valueOf(ratePerSec),
                    String.valueOf(burst),
                    String.valueOf(now),
                    String.valueOf(ttlMillis)
            );
            
            log.info("RedisGuard.tokenBucketAllow - Redis result: {}", res);
            
            if (res.size() < 3) {
                log.warn("RedisGuard.tokenBucketAllow - Invalid Redis result: {}", res);
                // Redis 결과가 유효하지 않을 때도 요청 허용 (fail-open 방식)
                return new RateDecision(true, burst, 0);
            }
            
            int allowed = Integer.parseInt(String.valueOf(res.get(0)));
            double tokens = Double.parseDouble(String.valueOf(res.get(1)));
            long retry = Long.parseLong(String.valueOf(res.get(2)));
            
            RateDecision decision = new RateDecision(allowed == 1, tokens, retry);
            log.info("RedisGuard.tokenBucketAllow - Final decision: allowed={}, tokens={}, retry={}",
                    decision.allowed(), decision.remainingTokens(), decision.retryAfterMs());
            
            return decision;

        } catch (DataAccessException e) {
            log.error("레디스 오류: 임시로 ratelimit 해제: {}", e.getMessage(), e);
            return new RateDecision(true, burst, 0);
        } catch (Exception e) {
            log.error("RedisGuard.tokenBucketAllow - Unexpected error: {}", e.getMessage(), e);
            // 예상치 못한 오류 시에도 요청 허용 (fail-open 방식)
            log.error("오류 발생: 임시로 ratelimit 해제: {}", e.getMessage(), e);
            return new RateDecision(true, burst, 0);
        }
    }
}



