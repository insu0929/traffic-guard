package com.trafficguard.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.trafficguard.exception.UserIdentificationException;
import com.trafficguard.exception.InternalTrafficException;
import com.trafficguard.exception.InvalidRequestException;
import com.trafficguard.annotation.UserRateLimit;
import com.trafficguard.demo.RateLimitDemoController;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class DefaultTrafficKeyResolverTest {

    private DefaultTrafficKeyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultTrafficKeyResolver();
        // Request context 정리
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testResourceKey() throws Exception {
        // Given
        Method method = RateLimitDemoController.class.getMethod("items");

        // When
        String resourceKey = resolver.resourceKey(method);

        // Then
        assertThat(resourceKey).isEqualTo("RateLimitDemoController:items");
    }

    @Test
    void testUserId_ValidMemNo() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("openapi-mem-no", "12345");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        String userId = resolver.userId();

        // Then
        assertThat(userId).isEqualTo("mem:12345");
    }

    @Test
    void testUserId_InvalidMemNo_ShouldAllow() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("openapi-mem-no", "invalid");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        String userId = resolver.userId();

        // Then
        assertThat(userId).isEqualTo("mem:invalid");
    }

    @Test
    void testUserId_MissingMemNo_ShouldThrowException() {
        // Given - 기본 동작 테스트 (어노테이션 없음, 기본 헤더명 "openapi-mem-no" 사용)
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When & Then
        assertThatThrownBy(() -> resolver.userId())
                .isInstanceOf(UserIdentificationException.class)
                .hasMessageContaining("missing openapi-mem-no");
    }

    @Test
    void testUserId_NoRequestContext_ShouldThrowException() {
        // Given - Request context가 없는 상태

        // When & Then
        assertThatThrownBy(() -> resolver.userId())
                .isInstanceOf(InternalTrafficException.class)
                .hasMessageContaining("request context not available");
    }

    @Test
    void testUserId_EmptyMemNo_ShouldThrowException() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("openapi-mem-no", "");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When & Then
        assertThatThrownBy(() -> resolver.userId())
                .isInstanceOf(UserIdentificationException.class)
                .hasMessageContaining("missing openapi-mem-no");
    }

    @Test
    void testUserId_WhitespaceMemNo_ShouldThrowException() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("openapi-mem-no", "   ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When & Then
        assertThatThrownBy(() -> resolver.userId())
                .isInstanceOf(UserIdentificationException.class)
                .hasMessageContaining("missing openapi-mem-no");
    }

    @Test
    void testPlanId_WithHeader() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Plan-Id", "premium");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        String planId = resolver.planId();

        // Then
        assertThat(planId).isEqualTo("premium");
    }

    @Test
    void testPlanId_WithoutHeader() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // When
        String planId = resolver.planId();

        // Then
        assertThat(planId).isEqualTo("default");
    }

    @Test
    void testPlanId_NoRequestContext() {
        // Given - Request context가 없는 상태

        // When
        String planId = resolver.planId();

        // Then
        assertThat(planId).isEqualTo("default");
    }

    // === 새로운 기능 테스트 ===

    @Test
    void testUserId_WithCustomHeader() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user_123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("customHeaderMethod");

        // When
        String userId = resolver.userId(method);

        // Then
        assertThat(userId).isEqualTo("mem:user_123");
    }


    @Test
    void testUserId_FromBody() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("cachedRequestBody", "{\"userId\":\"12345\"}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("bodyOnlyMethod");

        // When
        String userId = resolver.userId(method);

        // Then
        assertThat(userId).isEqualTo("mem:12345");
    }


    @Test
    void testUserId_HeaderFirst_HeaderExists() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "12345");
        request.setAttribute("cachedRequestBody", "{\"userId\":\"12345\"}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("headerFirstMethod");

        // When
        String userId = resolver.userId(method);

        // Then
        assertThat(userId).isEqualTo("mem:12345");
    }

    @Test
    void testUserId_HeaderFirst_HeaderMissing() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("cachedRequestBody", "{\"userId\":\"12345\"}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("headerFirstMethod");

        // When
        String userId = resolver.userId(method);

        // Then
        assertThat(userId).isEqualTo("mem:12345");
    }

    @Test
    void testUserId_BodyFirst_BodyExists() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "12345");
        request.setAttribute("cachedRequestBody", "{\"userId\":\"12345\"}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("bodyFirstMethod");

        // When
        String userId = resolver.userId(method);

        // Then
        assertThat(userId).isEqualTo("mem:12345");
    }

    @Test
    void testUserId_BodyFirst_BodyMissing() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "12345");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("bodyFirstMethod");

        // When
        String userId = resolver.userId(method);

        // Then
        assertThat(userId).isEqualTo("mem:12345");
    }

    @Test
    void testUserId_HeaderOnly() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "12345");
        request.setAttribute("cachedRequestBody", "{\"userId\":\"12345\"}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("headerOnlyMethod");

        // When
        String userId = resolver.userId(method);

        // Then
        assertThat(userId).isEqualTo("mem:12345");
    }

    @Test
    void testUserId_BodyOnly() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "12345");
        request.setAttribute("cachedRequestBody", "{\"userId\":\"12345\"}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("bodyOnlyMethod");

        // When
        String userId = resolver.userId(method);

        // Then
        assertThat(userId).isEqualTo("mem:12345");
    }

    @Test
    void testUserId_EmptyBody() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("cachedRequestBody", "");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("bodyOnlyMethod");

        // When & Then
        assertThatThrownBy(() -> resolver.userId(method))
                .isInstanceOf(UserIdentificationException.class)
                .hasMessageContaining("missing user identification");
    }

    @Test
    void testUserId_InvalidJsonBody() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("cachedRequestBody", "invalid json");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("bodyOnlyMethod");

        // When & Then
        assertThatThrownBy(() -> resolver.userId(method))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("failed to parse user id from body");
    }

    @Test
    void testUserId_MissingFieldInBody() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("cachedRequestBody", "{\"otherField\":\"value\"}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Method method = TestController.class.getMethod("bodyOnlyMethod");

        // When & Then
        assertThatThrownBy(() -> resolver.userId(method))
                .isInstanceOf(UserIdentificationException.class)
                .hasMessageContaining("missing user identification");
    }

    // Test controller for reflection
    static class TestController {
        @UserRateLimit(rate = 10, userHeader = "X-User-Id")
        public void customHeaderMethod() {}


        @UserRateLimit(rate = 10, userBodyField = "userId", userSource = UserRateLimit.UserIdSource.BODY_ONLY)
        public void bodyOnlyMethod() {}

        @UserRateLimit(rate = 10, userHeader = "X-User-Id", userBodyField = "userId", userSource = UserRateLimit.UserIdSource.HEADER_FIRST)
        public void headerFirstMethod() {}

        @UserRateLimit(rate = 10, userHeader = "X-User-Id", userBodyField = "userId", userSource = UserRateLimit.UserIdSource.BODY_FIRST)
        public void bodyFirstMethod() {}

        @UserRateLimit(rate = 10, userHeader = "X-User-Id", userSource = UserRateLimit.UserIdSource.HEADER_ONLY)
        public void headerOnlyMethod() {}
    }
}

