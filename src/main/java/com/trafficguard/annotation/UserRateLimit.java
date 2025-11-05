package com.trafficguard.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserRateLimit {
    /** 허용 속도 (예: 20) */
    int rate();
    /** 시간 단위 (예: SECOND, MINUTE, HOUR) */
    TimeUnit timeUnit() default TimeUnit.SECOND;
    /** 허용 버스트 b (짧은 순간 추가 여유), heavy API면 작게 (2~5 권장) */
    int burst() default 3;
    /** Redis 키 TTL(ms). 기본 60s. 윈도/버스트보다 살짝 크게. */
    long ttlMillis() default 60000L;
    /** RateLimit 헤더 추가 여부 */
    boolean emitHeaders() default true;
    
    // === 사용자 식별 관련 ===
    /** 사용자 식별 헤더명 (기본값: openapi-mem-no) */
    String userHeader() default "openapi-mem-no";
    /** Request Body에서 사용자 ID 필드명 (선택사항) */
    String userBodyField() default "";
    /** 사용자 ID 추출 우선순위: HEADER_FIRST, BODY_FIRST */
    UserIdSource userSource() default UserIdSource.HEADER_FIRST;
    
    enum UserIdSource {
        HEADER_FIRST,  // 헤더 먼저, 없으면 body
        BODY_FIRST,    // body 먼저, 없으면 헤더
        HEADER_ONLY,   // 헤더만
        BODY_ONLY      // body만
    }
    
    enum TimeUnit {
        SECOND(1),     // 초
        MINUTE(60),    // 분
        HOUR(3600),    // 시간
        DAY(86400);    // 일
        
        private final int seconds;
        
        TimeUnit(int seconds) {
            this.seconds = seconds;
        }
        
        public int getSeconds() {
            return seconds;
        }
        
        public double getRatePerSecond(int rate) {
            return (double) rate / seconds;
        }
    }
}



