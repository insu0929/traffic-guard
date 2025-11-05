package com.trafficguard.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserRateLimitAnnotationTest {

    @Test
    void testTimeUnit_Seconds() {
        // Given
        UserRateLimit.TimeUnit timeUnit = UserRateLimit.TimeUnit.SECOND;

        // When & Then
        assertThat(timeUnit.getSeconds()).isEqualTo(1);
        assertThat(timeUnit.getRatePerSecond(10)).isEqualTo(10.0);
    }

    @Test
    void testTimeUnit_Minutes() {
        // Given
        UserRateLimit.TimeUnit timeUnit = UserRateLimit.TimeUnit.MINUTE;

        // When & Then
        assertThat(timeUnit.getSeconds()).isEqualTo(60);
        assertThat(timeUnit.getRatePerSecond(60)).isEqualTo(1.0);
        assertThat(timeUnit.getRatePerSecond(120)).isEqualTo(2.0);
    }

    @Test
    void testTimeUnit_Hours() {
        // Given
        UserRateLimit.TimeUnit timeUnit = UserRateLimit.TimeUnit.HOUR;

        // When & Then
        assertThat(timeUnit.getSeconds()).isEqualTo(3600);
        assertThat(timeUnit.getRatePerSecond(3600)).isEqualTo(1.0);
        assertThat(timeUnit.getRatePerSecond(7200)).isEqualTo(2.0);
    }

    @Test
    void testTimeUnit_Days() {
        // Given
        UserRateLimit.TimeUnit timeUnit = UserRateLimit.TimeUnit.DAY;

        // When & Then
        assertThat(timeUnit.getSeconds()).isEqualTo(86400);
        assertThat(timeUnit.getRatePerSecond(86400)).isEqualTo(1.0);
        assertThat(timeUnit.getRatePerSecond(172800)).isEqualTo(2.0);
    }

    @Test
    void testTimeUnit_FractionalRates() {
        // Given
        UserRateLimit.TimeUnit minuteUnit = UserRateLimit.TimeUnit.MINUTE;
        UserRateLimit.TimeUnit hourUnit = UserRateLimit.TimeUnit.HOUR;

        // When & Then
        // 분당 1개 = 초당 1/60개
        assertThat(minuteUnit.getRatePerSecond(1)).isEqualTo(1.0 / 60.0);
        
        // 시간당 1개 = 초당 1/3600개
        assertThat(hourUnit.getRatePerSecond(1)).isEqualTo(1.0 / 3600.0);
    }

    @Test
    void testUserIdSource_Values() {
        // Given & When & Then
        assertThat(UserRateLimit.UserIdSource.HEADER_FIRST).isNotNull();
        assertThat(UserRateLimit.UserIdSource.BODY_FIRST).isNotNull();
        assertThat(UserRateLimit.UserIdSource.HEADER_ONLY).isNotNull();
        assertThat(UserRateLimit.UserIdSource.BODY_ONLY).isNotNull();
    }

    @Test
    void testAnnotation_DefaultValues() throws Exception {
        // Given
        Method method = TestController.class.getMethod("defaultMethod");

        // When
        UserRateLimit annotation = method.getAnnotation(UserRateLimit.class);

        // Then
        assertThat(annotation.rate()).isEqualTo(10);
        assertThat(annotation.timeUnit()).isEqualTo(UserRateLimit.TimeUnit.SECOND);
        assertThat(annotation.burst()).isEqualTo(3);
        assertThat(annotation.ttlMillis()).isEqualTo(60000L);
        assertThat(annotation.emitHeaders()).isTrue();
        assertThat(annotation.userHeader()).isEqualTo("openapi-mem-no");
        assertThat(annotation.userBodyField()).isEqualTo("");
        assertThat(annotation.userSource()).isEqualTo(UserRateLimit.UserIdSource.HEADER_FIRST);
    }

    @Test
    void testAnnotation_CustomValues() throws Exception {
        // Given
        Method method = TestController.class.getMethod("customMethod");

        // When
        UserRateLimit annotation = method.getAnnotation(UserRateLimit.class);

        // Then
        assertThat(annotation.rate()).isEqualTo(100);
        assertThat(annotation.timeUnit()).isEqualTo(UserRateLimit.TimeUnit.MINUTE);
        assertThat(annotation.burst()).isEqualTo(10);
        assertThat(annotation.ttlMillis()).isEqualTo(120000L);
        assertThat(annotation.emitHeaders()).isFalse();
        assertThat(annotation.userHeader()).isEqualTo("X-User-Id");
        assertThat(annotation.userBodyField()).isEqualTo("userId");
        assertThat(annotation.userSource()).isEqualTo(UserRateLimit.UserIdSource.BODY_ONLY);
    }

    @Test
    void testAnnotation_HeaderOnly() throws Exception {
        // Given
        Method method = TestController.class.getMethod("headerOnlyMethod");

        // When
        UserRateLimit annotation = method.getAnnotation(UserRateLimit.class);

        // Then
        assertThat(annotation.userSource()).isEqualTo(UserRateLimit.UserIdSource.HEADER_ONLY);
        assertThat(annotation.userBodyField()).isEqualTo("");
    }

    @Test
    void testAnnotation_BodyFirst() throws Exception {
        // Given
        Method method = TestController.class.getMethod("bodyFirstMethod");

        // When
        UserRateLimit annotation = method.getAnnotation(UserRateLimit.class);

        // Then
        assertThat(annotation.userSource()).isEqualTo(UserRateLimit.UserIdSource.BODY_FIRST);
        assertThat(annotation.userHeader()).isEqualTo("X-User-Id");
        assertThat(annotation.userBodyField()).isEqualTo("userId");
    }

    // Test controller for reflection
    static class TestController {
        @UserRateLimit(rate = 10)
        public void defaultMethod() {}

        @UserRateLimit(
            rate = 100,
            timeUnit = UserRateLimit.TimeUnit.MINUTE,
            burst = 10,
            ttlMillis = 120000L,
            emitHeaders = false,
            userHeader = "X-User-Id",
            userBodyField = "userId",
            userSource = UserRateLimit.UserIdSource.BODY_ONLY
        )
        public void customMethod() {}

        @UserRateLimit(
            rate = 20,
            userSource = UserRateLimit.UserIdSource.HEADER_ONLY
        )
        public void headerOnlyMethod() {}

        @UserRateLimit(
            rate = 30,
            userHeader = "X-User-Id",
            userBodyField = "userId",
            userSource = UserRateLimit.UserIdSource.BODY_FIRST
        )
        public void bodyFirstMethod() {}
    }
}

