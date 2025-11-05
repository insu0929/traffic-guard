package com.trafficguard.core;

import java.lang.reflect.Method;

public interface TrafficKeyResolver {
    /** 엔드포인트/메서드 기반 리소스 키 */
    String resourceKey(Method method);
    /** 인증 컨텍스트/헤더 등에서 유저 ID */
    String userId();
    String userId(Method method);
    /** 필요시 테넌트/플랜 ID */
    String planId();
}



