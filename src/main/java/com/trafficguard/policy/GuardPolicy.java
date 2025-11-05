package com.trafficguard.policy;

import com.trafficguard.core.JoinPointContext;

import java.lang.reflect.Method;

public interface GuardPolicy {
    /** 우선순위(작을수록 먼저 실행). 또는 @Order 사용 가능 */
    default int order() { return 100; }

    /** 이 메서드에 '내 정책 어노테이션'이 붙어있다면 true */
    boolean supports(Method method);

    /** 사전 검사(거절 시 예외 던짐). acquire 등은 여기서 */
    void before(Method method, JoinPointContext joinPointContext);

    /** 사후 정리(release 등) */
    void after(Method method, JoinPointContext joinPointContext);
}



