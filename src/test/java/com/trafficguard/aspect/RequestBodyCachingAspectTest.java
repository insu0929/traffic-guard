package com.trafficguard.aspect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.trafficguard.annotation.TrafficGuard;
import com.trafficguard.annotation.UserRateLimit;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestBodyCachingAspectTest {

    @Mock
    private org.aspectj.lang.ProceedingJoinPoint joinPoint;

    @Mock
    private org.aspectj.lang.reflect.MethodSignature methodSignature;

    private RequestBodyCachingAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new RequestBodyCachingAspect();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testAround_WithTrafficGuardOnly_ShouldNotCacheBody() throws Throwable {
        // Given - @TrafficGuard만 있고 @UserRateLimit가 없는 경우 body 캐싱 안함
        Method method = TestController.class.getMethod("trafficGuardedMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("success");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"userId\":\"test123\"}".getBytes());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("cachedRequestBody")).isNull(); // 캐싱 안됨
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_AlreadyCached_ShouldNotCacheAgain() throws Throwable {
        // Given - @TrafficGuard만 있는 메서드는 body 캐싱 안함
        Method method = TestController.class.getMethod("trafficGuardedMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("success");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"userId\":\"test123\"}".getBytes());
        request.setAttribute("cachedRequestBody", "already cached");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("cachedRequestBody")).isEqualTo("already cached"); // 기존 값 유지
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_NoRequestContext_ShouldProceed() throws Throwable {
        // Given - Request context가 없는 경우
        Method method = TestController.class.getMethod("trafficGuardedMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("success");

        // Request context 설정하지 않음

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_EmptyBody_ShouldNotCacheBody() throws Throwable {
        // Given - @TrafficGuard만 있는 메서드는 빈 body도 캐싱 안함
        Method method = TestController.class.getMethod("trafficGuardedMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("success");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("".getBytes());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("cachedRequestBody")).isNull(); // 캐싱 안됨
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_IOException_ShouldProceed() throws Throwable {
        // Given - @TrafficGuard만 있는 메서드는 body 캐싱 안함
        Method method = TestController.class.getMethod("trafficGuardedMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("success");

        MockHttpServletRequest request = mock(MockHttpServletRequest.class);
        when(request.getAttribute("cachedRequestBody")).thenReturn(null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        verify(request, never()).setAttribute(eq("cachedRequestBody"), anyString()); // 캐싱 안함
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_ProceedThrowsException_ShouldNotCacheBody() throws Throwable {
        // Given - @TrafficGuard만 있는 메서드는 body 캐싱 안함
        Method method = TestController.class.getMethod("trafficGuardedMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Test exception"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"userId\":\"test123\"}".getBytes());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When & Then
        try {
            aspect.around(joinPoint);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Test exception");
        }

        // Body는 캐싱되지 않음
        assertThat(request.getAttribute("cachedRequestBody")).isNull();
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_WithUserRateLimitButNoBodyField_ShouldNotCacheBody() throws Throwable {
        // Given - @UserRateLimit이 있지만 userBodyField가 없는 경우 body 캐싱 안함
        Method method = TestController.class.getMethod("userRateLimitMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("success");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"userId\":\"test456\"}".getBytes());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("cachedRequestBody")).isNull(); // 캐싱 안됨
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_WithBodyOnlyUserRateLimit_ShouldCacheBody() throws Throwable {
        // Given - BODY_ONLY 설정이 있는 메서드
        Method method = TestController.class.getMethod("bodyOnlyMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("success");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"userId\":\"test789\"}".getBytes());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("cachedRequestBody")).isEqualTo("{\"userId\":\"test789\"}");
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_WithHeaderFirstUserRateLimit_ShouldCacheBody() throws Throwable {
        // Given - HEADER_FIRST 설정이 있는 메서드
        Method method = TestController.class.getMethod("headerFirstMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("success");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"userId\":\"test101\"}".getBytes());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("cachedRequestBody")).isEqualTo("{\"userId\":\"test101\"}");
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_WithHeaderOnlyUserRateLimit_ShouldNotCacheBody() throws Throwable {
        // Given - HEADER_ONLY 설정이 있는 메서드는 body 캐싱 안함
        Method method = TestController.class.getMethod("headerOnlyMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("success");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"userId\":\"test202\"}".getBytes());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        assertThat(request.getAttribute("cachedRequestBody")).isNull(); // 캐싱 안됨
        verify(joinPoint).proceed();
    }

    // Test controller for reflection
    static class TestController {
        @TrafficGuard
        public void trafficGuardedMethod() {}

        @TrafficGuard
        @UserRateLimit(rate = 10, burst = 5)
        public void userRateLimitMethod() {}

        @TrafficGuard
        @UserRateLimit(rate = 10, userBodyField = "userId", userSource = UserRateLimit.UserIdSource.BODY_ONLY)
        public void bodyOnlyMethod() {}

        @TrafficGuard
        @UserRateLimit(rate = 10, userHeader = "X-User-Id", userBodyField = "userId", userSource = UserRateLimit.UserIdSource.HEADER_FIRST)
        public void headerFirstMethod() {}

        @TrafficGuard
        @UserRateLimit(rate = 10, userHeader = "X-User-Id", userSource = UserRateLimit.UserIdSource.HEADER_ONLY)
        public void headerOnlyMethod() {}
    }
}

