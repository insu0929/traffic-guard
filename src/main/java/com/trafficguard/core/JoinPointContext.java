package com.trafficguard.core;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class JoinPointContext {
    private final String resourceKey;
    private final String userId;
    private final String planId;
    private final Map<String,Object> attrs = new HashMap<>();

    public JoinPointContext(String resourceKey, String userId, String planId) {
        this.resourceKey = resourceKey; this.userId = userId; this.planId = planId;
    }

    public void put(String k, Object v){
        attrs.put(k,v);
    }

    public Object get(String k){
        return attrs.get(k);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String k, Class<T> t){
        return (T) attrs.get(k);
    }
}


