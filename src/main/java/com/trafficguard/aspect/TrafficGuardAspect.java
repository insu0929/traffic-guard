package com.trafficguard.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.trafficguard.core.JoinPointContext;
import com.trafficguard.core.TrafficKeyResolver;
import com.trafficguard.exception.*;
import com.trafficguard.policy.GuardPolicy;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
@Order(0)
public class TrafficGuardAspect {
    private final List<GuardPolicy> policies;
    private final TrafficKeyResolver keyResolver;

    public TrafficGuardAspect(List<GuardPolicy> policies, TrafficKeyResolver resolver) {
        this.policies = policies.stream()
                .sorted(Comparator.comparingInt(GuardPolicy::order))
                .collect(Collectors.toList());
        this.keyResolver = resolver;
    }

    @Around("@annotation(com.trafficguard.annotation.TrafficGuard)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        try {
            List<GuardPolicy> chain = policies.stream()
                    .filter(p -> p.supports(method))
                    .collect(Collectors.toList());

            // 지원하는 GuardPolicy가 없으면 그냥 진행 (TrafficKeyResolver 호출하지 않음) -> 로깅만
            if (chain.isEmpty()) {
                log.warn("TrafficGuardAspect must be used with GuardPolicy");
                return pjp.proceed();
            }

            JoinPointContext ctx = new JoinPointContext(
                    keyResolver.resourceKey(method),
                    keyResolver.userId(method),
                    keyResolver.planId()
            );

            for (GuardPolicy p : chain) p.before(method, ctx);

            try {
                return pjp.proceed();
            } finally {
                for (int i = chain.size() - 1; i >= 0; i--) {
                    chain.get(i).after(method, ctx);
                }
            }
        } catch (UserIdentificationException e) {
            log.warn("TrafficGuardAspect - UserIdentificationException: {}", e.getMessage());
            return handleException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (RateLimitExceededException e) {
            log.warn("TrafficGuardAspect - RateLimitExceededException: {}", e.getMessage());
            return handleException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", e.getMessage());
        } catch (InvalidRequestException e) {
            log.warn("TrafficGuardAspect - InvalidRequestException: {}", e.getMessage());
            return handleException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
        } catch (InternalTrafficException e) {
            log.error("TrafficGuardAspect - InternalTrafficException: {}", e.getMessage());
            return handleException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", e.getMessage());
        }
    }

    private Object handleException(HttpStatus status, String error, String message) {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes) {
                HttpServletResponse response = ((ServletRequestAttributes) attrs).getResponse();
                if (response != null) {
                    response.setStatus(status.value());
                    response.setContentType("application/json;charset=UTF-8");
                    String jsonResponse = String.format("{\"error\":\"%s\",\"message\":\"%s\"}", error, message);
                    response.getWriter().write(jsonResponse);
                    response.getWriter().flush();
                    return null; // AOP에서 null 반환하면 원래 메서드 실행 안됨
                }
            }
        } catch (IOException e) {
            log.error("Failed to write error response", e);
        }
        
        // HTTP 응답을 직접 설정할 수 없는 경우 예외를 다시 던짐
        throw new RuntimeException("Failed to handle traffic exception: " + message);
    }
}



