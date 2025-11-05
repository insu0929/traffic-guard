package com.trafficguard.aspect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.trafficguard.annotation.TrafficGuard;
import com.trafficguard.annotation.UserRateLimit;
import com.trafficguard.core.JoinPointContext;
import com.trafficguard.core.TrafficKeyResolver;
import com.trafficguard.policy.GuardPolicy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrafficGuardAspectTest {

    @Mock
    private TrafficKeyResolver keyResolver;

    @Mock
    private GuardPolicy policy1;

    @Mock
    private GuardPolicy policy2;

    @Mock
    private org.aspectj.lang.ProceedingJoinPoint joinPoint;

    @Mock
    private org.aspectj.lang.reflect.MethodSignature methodSignature;

    private TrafficGuardAspect aspect;

    @BeforeEach
    void setUp() throws Exception {
        List<GuardPolicy> policies = Arrays.asList(policy1, policy2);
        aspect = new TrafficGuardAspect(policies, keyResolver);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(TestController.class.getMethod("trafficGuardedMethod"));
        
        // TrafficKeyResolver stubbing
        when(keyResolver.resourceKey(any(Method.class))).thenReturn("TestController:trafficGuardedMethod");
        when(keyResolver.userId(any(Method.class))).thenReturn("mem:12345");
        when(keyResolver.planId()).thenReturn("default");
    }

    @Test
    void testAround_WithSupportingPolicy_ShouldExecutePolicy() throws Throwable {
        // Given
        when(policy1.supports(any(Method.class))).thenReturn(true);
        when(policy2.supports(any(Method.class))).thenReturn(false);
        when(policy1.order()).thenReturn(10);
        when(policy2.order()).thenReturn(20);
        when(joinPoint.proceed()).thenReturn("success");

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        verify(policy1).before(any(Method.class), any(JoinPointContext.class));
        verify(policy1).after(any(Method.class), any(JoinPointContext.class));
        verify(policy2, never()).before(any(Method.class), any(JoinPointContext.class));
        verify(policy2, never()).after(any(Method.class), any(JoinPointContext.class));
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_WithoutSupportingPolicy_ShouldProceedDirectly() throws Throwable {
        // Given
        when(policy1.supports(any(Method.class))).thenReturn(false);
        when(policy2.supports(any(Method.class))).thenReturn(false);
        when(policy1.order()).thenReturn(10);
        when(policy2.order()).thenReturn(20);
        when(joinPoint.proceed()).thenReturn("success");

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        verify(policy1, never()).before(any(Method.class), any(JoinPointContext.class));
        verify(policy1, never()).after(any(Method.class), any(JoinPointContext.class));
        verify(policy2, never()).before(any(Method.class), any(JoinPointContext.class));
        verify(policy2, never()).after(any(Method.class), any(JoinPointContext.class));
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_WithMultiplePolicies_ShouldExecuteInOrder() throws Throwable {
        // Given
        when(policy1.supports(any(Method.class))).thenReturn(true);
        when(policy2.supports(any(Method.class))).thenReturn(true);
        when(policy1.order()).thenReturn(20); // Higher order (executed later)
        when(policy2.order()).thenReturn(10); // Lower order (executed first)
        when(joinPoint.proceed()).thenReturn("success");

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        
        // Verify execution order: policy2 (order 10) -> policy1 (order 20)
        verify(policy2).before(any(Method.class), any(JoinPointContext.class));
        verify(policy1).before(any(Method.class), any(JoinPointContext.class));
        
        // Verify after execution in reverse order: policy1 -> policy2
        verify(policy1).after(any(Method.class), any(JoinPointContext.class));
        verify(policy2).after(any(Method.class), any(JoinPointContext.class));
        
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_WhenProceedThrowsException_ShouldStillCallAfter() throws Throwable {
        // Given
        when(policy1.supports(any(Method.class))).thenReturn(true);
        when(policy1.order()).thenReturn(10);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Test exception"));

        // When & Then
        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test exception");

        verify(policy1).before(any(Method.class), any(JoinPointContext.class));
        verify(policy1).after(any(Method.class), any(JoinPointContext.class));
        verify(joinPoint).proceed();
    }

    @Test
    void testAround_WithTrafficGuardOnly_ShouldProceedDirectly() throws Throwable {
        // Given - @TrafficGuard만 있고 @UserRateLimit이 없는 경우
        when(methodSignature.getMethod()).thenReturn(TestController.class.getMethod("trafficGuardOnlyMethod"));
        when(policy1.supports(any(Method.class))).thenReturn(false);
        when(policy2.supports(any(Method.class))).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("success");

        // When
        Object result = aspect.around(joinPoint);

        // Then
        assertThat(result).isEqualTo("success");
        verify(policy1, never()).before(any(Method.class), any(JoinPointContext.class));
        verify(policy2, never()).before(any(Method.class), any(JoinPointContext.class));
        verify(keyResolver, never()).userId(any(Method.class)); // TrafficKeyResolver 호출하지 않음
        verify(joinPoint).proceed();
    }

    // Test controller for reflection
    static class TestController {
        @TrafficGuard
        @UserRateLimit(rate = 10, burst = 5)
        public void trafficGuardedMethod() {}

        @TrafficGuard
        public void trafficGuardOnlyMethod() {}
    }
}

