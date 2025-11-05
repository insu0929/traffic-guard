package com.trafficguard.core;

import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.trafficguard.exception.UserIdentificationException;
import com.trafficguard.exception.InvalidRequestException;
import com.trafficguard.exception.InternalTrafficException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.trafficguard.annotation.UserRateLimit;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Slf4j
@Primary
@Component
public class DefaultTrafficKeyResolver implements TrafficKeyResolver {
    private static final String HDR_MEMBER_NO = "openapi-mem-no";
    private static final String HDR_PLAN_ID   = "X-Plan-Id";

    @Override
    public String resourceKey(Method method) {
        return method.getDeclaringClass().getSimpleName() + ":" + method.getName();
    }

    @Override
    public String userId() {
        return userId(null);
    }
    
    @Override
    public String userId(Method method) {

        HttpServletRequest req = currentRequest();
        if (req == null) {
            throw new InternalTrafficException("request context not available");
        }

        // 어노테이션 정보가 없으면 기본 동작
        if (method == null) {
            return extractUserIdFromHeader(req, HDR_MEMBER_NO);
        }

        UserRateLimit ann = AnnotationUtils.findAnnotation(method, UserRateLimit.class);
        if (ann == null) {
            return extractUserIdFromHeader(req, HDR_MEMBER_NO);
        }

        return extractUserIdWithAnnotation(req, ann);
    }

    private String extractUserIdWithAnnotation(HttpServletRequest req, UserRateLimit ann) {
        String headerValue = null;
        String bodyValue = null;

        // 헤더에서 추출
        if (ann.userSource() != UserRateLimit.UserIdSource.BODY_ONLY) {
            boolean headerRequired = (ann.userSource() == UserRateLimit.UserIdSource.HEADER_ONLY);
            headerValue = extractUserIdFromHeader(req, ann.userHeader(), headerRequired);
        }

        // Body에서 추출
        if (ann.userSource() != UserRateLimit.UserIdSource.HEADER_ONLY && !ann.userBodyField().isEmpty()) {
            bodyValue = extractUserIdFromBody(req, ann.userBodyField());
        }

        // 우선순위에 따라 반환
        String result;
        switch (ann.userSource()) {
            case HEADER_FIRST:
                result = headerValue != null ? headerValue : bodyValue;
                break;
            case BODY_FIRST:
                result = bodyValue != null ? bodyValue : headerValue;
                break;
            case HEADER_ONLY:
                result = headerValue;
                break;
            case BODY_ONLY:
                result = bodyValue;
                break;
            default:
                result = headerValue;
        }
        
        // 사용자 ID를 찾지 못한 경우 예외 발생
        if (result == null) {
            throw new UserIdentificationException("missing user identification");
        }
        
        return result;
    }

    private String extractUserIdFromHeader(HttpServletRequest req, String headerName) {
        return extractUserIdFromHeader(req, headerName, true);
    }

    private String extractUserIdFromHeader(HttpServletRequest req, String headerName, boolean required) {
        String value = trimOrNull(req.getHeader(headerName));
        if (value == null || value.isEmpty()) {
            if (required) {
                throw new UserIdentificationException("missing " + headerName);
            }
            return null; // 헤더가 없으면 null 반환 (fallback을 위해)
        }
        return "mem:" + value;
    }

    private String extractUserIdFromBody(HttpServletRequest req, String fieldName) {
        try {
            // Request Body 읽기 (AOP에서 캐싱된 것 사용)
            String body = getRequestBody(req);

            if (body == null || body.isEmpty()) {
                log.warn("DefaultTrafficKeyResolver - body is null or empty");
                return null;
            }

            // JSON 파싱 (Jackson 사용)
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(body);
            JsonNode fieldNode = jsonNode.get(fieldName);
            
            if (fieldNode == null || fieldNode.isNull()) {
                log.warn("DefaultTrafficKeyResolver - fieldNode is null");
                return null;
            }

            String value = fieldNode.asText();
            if (value == null || value.isEmpty()) {
                throw new UserIdentificationException("missing " + fieldName + " in request body");
            }
            
            return "mem:" + value;
        } catch (Exception e) {
            log.error("DefaultTrafficKeyResolver - exception: {}", e.getMessage(), e);
            throw new InvalidRequestException("failed to parse user id from body: " + e.getMessage());
        }
    }

    private String getRequestBody(HttpServletRequest req) {
        // AOP에서 캐싱된 Request Body 사용
        return (String) req.getAttribute("cachedRequestBody");
    }

    @Override
    public String planId() {
        HttpServletRequest req = currentRequest();
        String plan = (req != null) ? trimOrNull(req.getHeader(HDR_PLAN_ID)) : null;
        return (plan != null) ? plan : "default";
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }
    
    private String trimOrNull(String v) {
        if (v == null) return null;
        v = v.trim();
        return v.isEmpty() ? null : v;
    }
}



