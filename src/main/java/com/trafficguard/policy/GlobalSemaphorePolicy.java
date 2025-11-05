package com.trafficguard.policy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.trafficguard.core.JoinPointContext;

import java.lang.reflect.Method;

@Component
@Order(1)
public class GlobalSemaphorePolicy implements GuardPolicy {

    @Override
    public boolean supports(Method method) {
        return false;
    }

    @Override
    public void before(Method method, JoinPointContext joinPointContext) {

    }

    @Override
    public void after(Method method, JoinPointContext joinPointContext) {

    }
}



