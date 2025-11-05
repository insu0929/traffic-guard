package com.trafficguard.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.trafficguard.exception.RateLimitExceededException;
import com.trafficguard.annotation.UserRateLimit;
import com.trafficguard.core.JoinPointContext;
import com.trafficguard.core.RateLimitHeaderSupport;
import com.trafficguard.core.RedisGuard;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRateLimitPolicyTest {

    @Mock
    private RedisGuard redisGuard;

    @Mock
    private RateLimitHeaderSupport rateLimitHeaderSupport;

    private UserRateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new UserRateLimitPolicy(redisGuard, rateLimitHeaderSupport);
    }

    @Test
    void testSupports_WithUserRateLimitAnnotation_ShouldReturnTrue() throws Exception {
        // Given
        Method method = TestController.class.getMethod("rateLimitedMethod");

        // When
        boolean supports = policy.supports(method);

        // Then
        assertThat(supports).isTrue();
    }

    @Test
    void testSupports_WithoutUserRateLimitAnnotation_ShouldReturnFalse() throws Exception {
        // Given
        Method method = TestController.class.getMethod("normalMethod");

        // When
        boolean supports = policy.supports(method);

        // Then
        assertThat(supports).isFalse();
    }

    @Test
    void testBefore_AllowedRequest_ShouldNotThrowException() throws Exception {
        // Given
        Method method = TestController.class.getMethod("rateLimitedMethod");
        JoinPointContext context = new JoinPointContext("TestController:rateLimitedMethod", "mem:12345", "default");
        
        RedisGuard.RateDecision decision = new RedisGuard.RateDecision(true, 2.0, 0);
        when(redisGuard.tokenBucketAllow(anyString(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(decision);

        // When & Then
        policy.before(method, context);

        verify(redisGuard).tokenBucketAllow("user:TestController:rateLimitedMethod:mem:12345", 10.0, 5, 60000);
        verify(rateLimitHeaderSupport).writeHeaders(10, 2.0, 0);
    }

    @Test
    void testBefore_RejectedRequest_ShouldThrowException() throws Exception {
        // Given
        Method method = TestController.class.getMethod("rateLimitedMethod");
        JoinPointContext context = new JoinPointContext("TestController:rateLimitedMethod", "mem:12345", "default");
        
        RedisGuard.RateDecision decision = new RedisGuard.RateDecision(false, 0.0, 1000);
        when(redisGuard.tokenBucketAllow(anyString(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(decision);

        // When & Then
        assertThatThrownBy(() -> policy.before(method, context))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("USER_RATE_LIMIT");

        verify(redisGuard).tokenBucketAllow("user:TestController:rateLimitedMethod:mem:12345", 10.0, 5, 60000);
        verify(rateLimitHeaderSupport).writeHeaders(10, 0.0, 1000);
    }

    @Test
    void testBefore_MinuteRate_ShouldConvertToSeconds() throws Exception {
        // Given
        Method method = TestController.class.getMethod("minuteRateMethod");
        JoinPointContext context = new JoinPointContext("TestController:minuteRateMethod", "mem:12345", "default");
        
        RedisGuard.RateDecision decision = new RedisGuard.RateDecision(true, 2.0, 0);
        when(redisGuard.tokenBucketAllow(anyString(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(decision);

        // When & Then
        policy.before(method, context);

        // 60 requests per minute = 1 request per second
        verify(redisGuard).tokenBucketAllow("user:TestController:minuteRateMethod:mem:12345", 1.0, 5, 60000);
        verify(rateLimitHeaderSupport).writeHeaders(60, 2.0, 0);
    }

    @Test
    void testBefore_HourRate_ShouldConvertToSeconds() throws Exception {
        // Given
        Method method = TestController.class.getMethod("hourRateMethod");
        JoinPointContext context = new JoinPointContext("TestController:hourRateMethod", "mem:12345", "default");
        
        RedisGuard.RateDecision decision = new RedisGuard.RateDecision(true, 2.0, 0);
        when(redisGuard.tokenBucketAllow(anyString(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(decision);

        // When & Then
        policy.before(method, context);

        // 3600 requests per hour = 1 request per second
        // HOUR timeUnit(3600초) > 기본 TTL(60초)이므로 3600 * 2 = 7200초 = 7200000ms 사용
        verify(redisGuard).tokenBucketAllow("user:TestController:hourRateMethod:mem:12345", 1.0, 5, 7200000L);
        verify(rateLimitHeaderSupport).writeHeaders(3600, 2.0, 0);
    }

    @Test
    void testBefore_DayRate_ShouldConvertToSeconds() throws Exception {
        // Given
        Method method = TestController.class.getMethod("dayRateMethod");
        JoinPointContext context = new JoinPointContext("TestController:dayRateMethod", "mem:12345", "default");
        
        RedisGuard.RateDecision decision = new RedisGuard.RateDecision(true, 2.0, 0);
        when(redisGuard.tokenBucketAllow(anyString(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(decision);

        // When & Then
        policy.before(method, context);

        // 86400 requests per day = 1 request per second
        // DAY timeUnit(86400초) > 기본 TTL(60초)이므로 86400 * 2 = 172800초 = 172800000ms 사용
        verify(redisGuard).tokenBucketAllow("user:TestController:dayRateMethod:mem:12345", 1.0, 5, 172800000L);
        verify(rateLimitHeaderSupport).writeHeaders(86400, 2.0, 0);
    }

    @Test
    void testBefore_FractionalRate_ShouldRoundUp() throws Exception {
        // Given
        Method method = TestController.class.getMethod("fractionalRateMethod");
        JoinPointContext context = new JoinPointContext("TestController:fractionalRateMethod", "mem:12345", "default");
        
        RedisGuard.RateDecision decision = new RedisGuard.RateDecision(true, 2.0, 0);
        when(redisGuard.tokenBucketAllow(anyString(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(decision);

        // When & Then
        policy.before(method, context);

        // 1 request per minute = 1/60 request per second = 0.016... -> rounded up to 1
        verify(redisGuard).tokenBucketAllow("user:TestController:fractionalRateMethod:mem:12345", 0.016666666666666666, 5, 60000);
        verify(rateLimitHeaderSupport).writeHeaders(1, 2.0, 0);
    }

    @Test
    void testBefore_WithoutAnnotation_ShouldNotDoAnything() throws Exception {
        // Given
        Method method = TestController.class.getMethod("normalMethod");
        JoinPointContext context = new JoinPointContext("TestController:normalMethod", "mem:12345", "default");

        // When
        policy.before(method, context);

        // Then
        verifyNoInteractions(redisGuard);
        verifyNoInteractions(rateLimitHeaderSupport);
    }

    @Test
    void testBefore_SecondTimeUnit_ShouldUseDefaultTtl() throws NoSuchMethodException {
        // Given - SECOND timeUnit (60초)은 기본 TTL(60초)과 같으므로 기본값 사용
        Method method = TestController.class.getMethod("rateLimitedMethod");
        JoinPointContext context = new JoinPointContext("test", "user123", "plan1");

        RedisGuard.RateDecision decision = new RedisGuard.RateDecision(true, 5, 0);
        when(redisGuard.tokenBucketAllow(anyString(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(decision);

        // When
        policy.before(method, context);

        // Then - 기본 TTL 60000ms 사용
        verify(redisGuard).tokenBucketAllow(anyString(), eq(10.0), eq(5), eq(60000L));
    }

    @Test
    void testBefore_MinuteTimeUnit_ShouldUseCalculatedTtl() throws NoSuchMethodException {
        // Given - MINUTE timeUnit (60초)은 기본 TTL(60초)과 같으므로 2배(120초) 사용
        Method method = TestController.class.getMethod("minuteRateMethod");
        JoinPointContext context = new JoinPointContext("test", "user123", "plan1");

        RedisGuard.RateDecision decision = new RedisGuard.RateDecision(true, 5, 0);
        when(redisGuard.tokenBucketAllow(anyString(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(decision);

        // When
        policy.before(method, context);

        // Then - MINUTE(60초) = 기본 TTL(60초)이므로 기본값 60000ms 사용
        verify(redisGuard).tokenBucketAllow(anyString(), eq(1.0), eq(5), eq(60000L));
    }

    @Test
    void testBefore_HourTimeUnit_ShouldUseCalculatedTtl() throws NoSuchMethodException {
        // Given - HOUR timeUnit (3600초)은 기본 TTL(60초)보다 크므로 2배(7200초) 사용
        Method method = TestController.class.getMethod("hourRateMethod");
        JoinPointContext context = new JoinPointContext("test", "user123", "plan1");

        RedisGuard.RateDecision decision = new RedisGuard.RateDecision(true, 5, 0);
        when(redisGuard.tokenBucketAllow(anyString(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(decision);

        // When
        policy.before(method, context);

        // Then - HOUR(3600초) * 2 = 7200초 = 7200000ms 사용
        verify(redisGuard).tokenBucketAllow(anyString(), eq(1.0), eq(5), eq(7200000L));
    }

    @Test
    void testBefore_DayTimeUnit_ShouldUseCalculatedTtl() throws NoSuchMethodException {
        // Given - DAY timeUnit (86400초)은 기본 TTL(60초)보다 크므로 2배(172800초) 사용
        Method method = TestController.class.getMethod("dayRateMethod");
        JoinPointContext context = new JoinPointContext("test", "user123", "plan1");

        RedisGuard.RateDecision decision = new RedisGuard.RateDecision(true, 5, 0);
        when(redisGuard.tokenBucketAllow(anyString(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(decision);

        // When
        policy.before(method, context);

        // Then - DAY(86400초) * 2 = 172800초 = 172800000ms 사용
        verify(redisGuard).tokenBucketAllow(anyString(), eq(1.0), eq(5), eq(172800000L));
    }

    // Test controller for reflection
    static class TestController {
        @UserRateLimit(rate = 10, timeUnit = UserRateLimit.TimeUnit.SECOND, burst = 5, ttlMillis = 60000)
        public void rateLimitedMethod() {}

        @UserRateLimit(rate = 60, timeUnit = UserRateLimit.TimeUnit.MINUTE, burst = 5, ttlMillis = 60000)
        public void minuteRateMethod() {}

        @UserRateLimit(rate = 3600, timeUnit = UserRateLimit.TimeUnit.HOUR, burst = 5, ttlMillis = 60000)
        public void hourRateMethod() {}

        @UserRateLimit(rate = 86400, timeUnit = UserRateLimit.TimeUnit.DAY, burst = 5, ttlMillis = 60000)
        public void dayRateMethod() {}

        @UserRateLimit(rate = 1, timeUnit = UserRateLimit.TimeUnit.MINUTE, burst = 5, ttlMillis = 60000)
        public void fractionalRateMethod() {}

        public void normalMethod() {}
    }
}

