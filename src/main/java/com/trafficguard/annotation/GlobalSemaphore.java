package com.trafficguard.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GlobalSemaphore {
    int permits();
    long leaseMillis() default 0;
    long tryAcquireWaitMillis() default 0;
}


