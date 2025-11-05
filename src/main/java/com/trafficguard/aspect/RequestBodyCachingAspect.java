package com.trafficguard.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.trafficguard.annotation.UserRateLimit;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@Order(-1) // TrafficGuard가 있는 경우에만 body 캐싱
public class RequestBodyCachingAspect {
    
    @Around("@annotation(com.trafficguard.annotation.TrafficGuard)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        // Body 캐싱이 필요한지 확인
        boolean needsCaching = needsBodyCaching(method);

        if (needsCaching) {
            HttpServletRequest request = getCurrentRequest();
            if (request != null && request.getAttribute("cachedRequestBody") == null) {
                // 아직 캐싱되지 않은 경우만
                String body = readRequestBody(request);
                request.setAttribute("cachedRequestBody", body);
            }
        }
        
        return pjp.proceed();
    }
    
    private boolean needsBodyCaching(Method method) {
        UserRateLimit ann = AnnotationUtils.findAnnotation(method, UserRateLimit.class);
        if (ann == null) {
            return false; // @UserRateLimit가 없으면 body 캐싱 불필요
        }
        
        // body에서 user ID를 추출해야 하는 경우에만 캐싱
        return ann.userSource() != UserRateLimit.UserIdSource.HEADER_ONLY 
               && !ann.userBodyField().isEmpty();
    }
    
    private HttpServletRequest getCurrentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }
    
    private String readRequestBody(HttpServletRequest request) {
        try {
            // ContentCachingRequestWrapper를 사용하는 경우
            if (request instanceof org.springframework.web.util.ContentCachingRequestWrapper) {
                org.springframework.web.util.ContentCachingRequestWrapper wrapper = 
                    (org.springframework.web.util.ContentCachingRequestWrapper) request;
                byte[] content = wrapper.getContentAsByteArray();
                return content.length > 0 ? new String(content, java.nio.charset.StandardCharsets.UTF_8) : "";
            }
            
            // 일반 HttpServletRequest의 경우
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } catch (IOException e) {
            // Body 읽기 실패 시 빈 문자열 반환
            return "";
        } catch (Exception e) {
            // 기타 예외 처리
            return "";
        }
    }
}



