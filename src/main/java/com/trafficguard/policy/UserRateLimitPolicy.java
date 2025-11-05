package com.trafficguard.policy;

import lombok.AllArgsConstructor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.trafficguard.exception.RateLimitExceededException;
import com.trafficguard.annotation.UserRateLimit;
import com.trafficguard.core.JoinPointContext;
import com.trafficguard.core.RateLimitHeaderSupport;
import com.trafficguard.core.RedisGuard;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

@Slf4j
@Component
@Order(20)
@AllArgsConstructor
public class UserRateLimitPolicy implements GuardPolicy {
    private final RedisGuard redisGuard;
    private final RateLimitHeaderSupport rateLimitHeaderSupport;

    @Override
    public boolean supports(Method method) {
        return AnnotationUtils.findAnnotation(method, UserRateLimit.class) != null;
    }

    @Override
    public void before(Method method, JoinPointContext joinPointContext) {

        UserRateLimit ann = AnnotationUtils.findAnnotation(method, UserRateLimit.class);
        if (ann == null) {
            log.error("UserRateLimitPolicy - no annotation found");
            return;
        }

        // 시간 단위를 초 단위로 변환 (소수점 포함)
        double ratePerSecond = ann.timeUnit().getRatePerSecond(ann.rate());

        // timeUnit에 따라 TTL을 동적으로 계산
        long ttlMillis = calculateTtlMillis(ann.timeUnit(), ann.ttlMillis());

        String bucketKey = "user:"+joinPointContext.getResourceKey()+":"+joinPointContext.getUserId();
        log.debug("UserRateLimitPolicy - bucketKey: {}, ratePerSecond: {}, burst: {}, ttlMillis: {}",
                bucketKey, ratePerSecond, ann.burst(), ttlMillis);

        RedisGuard.RateDecision d = redisGuard.tokenBucketAllow(bucketKey, ratePerSecond, ann.burst(), ttlMillis);
        
        log.debug("UserRateLimitPolicy - redisGuard.tokenBucketAllow() completed, result: allowed={}, tokens={}, retry={}", 
                d.allowed(), d.remainingTokens(), d.retryAfterMs());

        if (ann.emitHeaders()) {
            rateLimitHeaderSupport.writeHeaders(ann.rate(), d.remainingTokens(), d.retryAfterMs());
        }

        if (!d.allowed()) {
            log.warn("Rate Limit Exceeded: " + bucketKey + ":" + ratePerSecond + ":" + d.remainingTokens() + ":" + d.retryAfterMs());

            throw new RateLimitExceededException("[USER_RATE_LIMIT] " + "userId: " + joinPointContext.getUserId() + " resource:" + joinPointContext.getResourceKey());
        }

        log.debug("UserRateLimitPolicy - rate limit check passed");
    }

    @Override
    public void after(Method method, JoinPointContext joinPointContext) {

    }

    /**
     * timeUnit에 따라 적절한 TTL을 계산합니다.
     * 기본 TTL이 timeUnit보다 짧으면 timeUnit의 2배로 설정합니다.
     */
    private long calculateTtlMillis(UserRateLimit.TimeUnit timeUnit, long defaultTtlMillis) {
        long timeUnitMillis = timeUnit.getSeconds() * 1000L;
        
        // 기본 TTL이 timeUnit보다 짧으면 timeUnit의 2배로 설정
        if (defaultTtlMillis < timeUnitMillis) {
            return timeUnitMillis * 2;
        }
        
        return defaultTtlMillis;
    }
}



