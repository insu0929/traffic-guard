package com.trafficguard.core;


import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;

@Component
public class RateLimitHeaderSupport {
    public void writeHeaders(int limit, double remaining, long retryAfterMs){
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (!(ra instanceof ServletRequestAttributes)) return;
        HttpServletResponse resp = ((ServletRequestAttributes) ra).getResponse();
        if (resp == null) return;

        resp.setHeader("RateLimit-Limit", String.valueOf(limit));
        resp.setHeader("RateLimit-Remaining", String.valueOf(Math.max(0, (int)Math.floor(remaining))));
        if (retryAfterMs > 0) {
            resp.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterMs/1000)));
        }
    }
}


