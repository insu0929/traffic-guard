package com.trafficguard.demo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;
import com.trafficguard.annotation.TrafficGuard;
import com.trafficguard.annotation.UserRateLimit;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/rate-limit-demo")
public class RateLimitDemoController {

    @GetMapping("/items")
    @TrafficGuard
    @UserRateLimit(rate = 2, timeUnit = UserRateLimit.TimeUnit.SECOND, burst = 2) // 초당 2개, 버스트 2개
    public List<String> items() throws InterruptedException {
        // 무거운 처리 흉내
        Thread.sleep(120);
        return Arrays.asList("A","B","C");
    }

    @PostMapping("/orders")
    @TrafficGuard
    @UserRateLimit(rate = 2, timeUnit = UserRateLimit.TimeUnit.SECOND, burst = 2, userHeader = "X-User-Id") // 초당 2개
    public List<String> createOrder(@RequestBody OrderRequest request) throws InterruptedException {
        Thread.sleep(100);
        return Collections.singletonList("Order created for user: " + request.getUserId());
    }

    @PostMapping("/payments")
    @TrafficGuard
    @UserRateLimit(rate = 1, timeUnit = UserRateLimit.TimeUnit.SECOND, burst = 1, 
                   userBodyField = "userId", 
                   userSource = UserRateLimit.UserIdSource.BODY_ONLY) // 초당 1개
    public List<String> processPayment(@RequestBody PaymentRequest request) throws InterruptedException {
        log.debug("RateLimitDemoController.processPayment called with request: {}", request);
        Thread.sleep(150);
        return Collections.singletonList("Payment processed for user: " + request.getUserId());
    }

    @PostMapping("/notifications")
    @TrafficGuard
    @UserRateLimit(rate = 2, timeUnit = UserRateLimit.TimeUnit.SECOND, burst = 2,
                   userHeader = "X-User-Id",
                   userBodyField = "userId",
                   userSource = UserRateLimit.UserIdSource.HEADER_FIRST) // 초당 2개
    public List<String> sendNotification(@RequestBody NotificationRequest request) throws InterruptedException {
        Thread.sleep(80);
        return Collections.singletonList("Notification sent to user: " + request.getUserId());
    }

    @GetMapping("/reports")
    @TrafficGuard
    @UserRateLimit(rate = 2, timeUnit = UserRateLimit.TimeUnit.MINUTE, burst = 2) // 분당 2개
    public List<String> generateReport() throws InterruptedException {
        Thread.sleep(200);
        return Collections.singletonList("Report generated");
    }

    @PostMapping("/bulk-upload")
    @TrafficGuard
    @UserRateLimit(rate = 2, timeUnit = UserRateLimit.TimeUnit.HOUR, burst = 2) // 시간당 2개
    public List<String> bulkUpload(@RequestBody BulkUploadRequest request) throws InterruptedException {
        Thread.sleep(500);
        return Collections.singletonList("Bulk upload completed for user: " + request.getUserId());
    }

    @GetMapping("/analytics")
    @TrafficGuard
    @UserRateLimit(rate = 1, timeUnit = UserRateLimit.TimeUnit.DAY, burst = 1) // 일당 1개
    public List<String> getAnalytics() throws InterruptedException {
        Thread.sleep(1000);
        return Collections.singletonList("Analytics data");
    }

    @GetMapping("/traffic-guard-only")
    @TrafficGuard
    // @UserRateLimit 없음 - TrafficGuard만 있는 경우
    public List<String> trafficGuardOnly() throws InterruptedException {
        Thread.sleep(50);
        return Collections.singletonList("TrafficGuard only endpoint");
    }

    // DTO 클래스들
    @Setter
    @Getter
    public static class OrderRequest {
        private String userId;
        private String productId;
    }

    @Setter
    @Getter
    public static class PaymentRequest {
        private String userId;
        private String amount;
    }

    @Setter
    @Getter
    public static class NotificationRequest {
        private String userId;
        private String message;
    }

    @Setter
    @Getter
    public static class BulkUploadRequest {
        private String userId;
        private String fileName;
        private int recordCount;
    }
}


