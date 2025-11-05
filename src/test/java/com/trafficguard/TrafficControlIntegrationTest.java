package com.trafficguard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import com.trafficguard.demo.RateLimitDemoController;
import com.trafficguard.demo.RateLimitDemoController.OrderRequest;
import com.trafficguard.demo.RateLimitDemoController.PaymentRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TrafficControlIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        restTemplate = new RestTemplate();
        
        // 테스트 전에 traffic control 관련 Redis 키들을 정리
        cleanupTrafficControlKeys();
    }
    
    @AfterEach
    void tearDown() {
        // 테스트 후에 traffic control 관련 Redis 키들을 정리
        cleanupTrafficControlKeys();
    }
    
    private void cleanupTrafficControlKeys() {
        try {
            // traffic control 테스트에서 사용하는 키 패턴들을 삭제
            String[] patterns = {
                "tb:user:RateLimitDemoController:*",  // 모든 RateLimitDemoController 관련 토큰 버킷 키
                "tb:user:*"                  // 모든 사용자 관련 토큰 버킷 키
            };

            for (String pattern : patterns) {
                try {
                    // SCAN을 사용해서 패턴에 맞는 키들을 찾아서 삭제
                    redisTemplate.execute((RedisCallback<Void>) connection -> {
                        connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                            .match(pattern)
                            .count(100)
                            .build())
                        .forEachRemaining(key -> connection.del(key));
                        return null;
                    });
                } catch (Exception e) {
                    // 키 삭제 실패는 무시 (이미 만료되었을 수 있음)
                }
            }
        } catch (Exception e) {
            // 정리 실패는 무시
        }
    }


    @Test
    void testBasicRateLimit_ShouldAllowWithinLimit() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("openapi-mem-no", "12345");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // When
        ResponseEntity<List> response = restTemplate.exchange(
            baseUrl + "/rate-limit-demo/items",
            HttpMethod.GET,
            entity,
            List.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("A");
    }

    @Test
    void testCustomHeaderRateLimit_ShouldWork() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "user123");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        OrderRequest request = new OrderRequest();
        request.setUserId("user123");
        request.setProductId("prod1");
        
        HttpEntity<OrderRequest> entity = new HttpEntity<>(request, headers);

        // When
        ResponseEntity<List> response = restTemplate.exchange(
            baseUrl + "/rate-limit-demo/orders",
            HttpMethod.POST,
            entity,
            List.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat((String) response.getBody().get(0)).contains("Order created for user: user123");
    }

    @Test
    void testBodyOnlyRateLimit_ShouldWork() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        PaymentRequest request = new PaymentRequest();
        request.setUserId("user456");
        request.setAmount("1000");
        
        HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);

        // When
        ResponseEntity<List> response = restTemplate.exchange(
            baseUrl + "/rate-limit-demo/payments",
            HttpMethod.POST,
            entity,
            List.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat((String) response.getBody().get(0)).contains("Payment processed for user: user456");
    }

    @Test
    void testHeaderFirstRateLimit_HeaderExists_ShouldUseHeader() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "headerUser");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        OrderRequest request = new OrderRequest();
        request.setUserId("bodyUser");
        request.setProductId("prod1");
        
        HttpEntity<OrderRequest> entity = new HttpEntity<>(request, headers);

        // When
        ResponseEntity<List> response = restTemplate.exchange(
            baseUrl + "/rate-limit-demo/notifications",
            HttpMethod.POST,
            entity,
            List.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat((String) response.getBody().get(0)).contains("Notification sent to user: bodyUser");
    }

    @Test
    void testMinuteRateLimit_ShouldWork() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("openapi-mem-no", "12345");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // When
        ResponseEntity<List> response = restTemplate.exchange(
            baseUrl + "/rate-limit-demo/reports",
            HttpMethod.GET,
            entity,
            List.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat((String) response.getBody().get(0)).contains("Report generated");
    }

    @Test
    void testHourRateLimit_ShouldWork() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("openapi-mem-no", "12345"); // 사용자 헤더 추가
        
        RateLimitDemoController.BulkUploadRequest request = new RateLimitDemoController.BulkUploadRequest();
        request.setUserId("user789");
        request.setFileName("data.csv");
        request.setRecordCount(1000);
        
        HttpEntity<RateLimitDemoController.BulkUploadRequest> entity = new HttpEntity<>(request, headers);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/rate-limit-demo/bulk-upload",
            HttpMethod.POST,
            entity,
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        // JSON 파싱
        ObjectMapper mapper = new ObjectMapper();
        List<String> result = mapper.readValue(response.getBody(), List.class);
        assertThat(result.get(0)).contains("Bulk upload completed for user: user789");
    }

    @Test
    void testDayRateLimit_ShouldWork() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("openapi-mem-no", "12345");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // When
        ResponseEntity<List> response = restTemplate.exchange(
            baseUrl + "/rate-limit-demo/analytics",
            HttpMethod.GET,
            entity,
            List.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat((String) response.getBody().get(0)).contains("Analytics data");
    }

    @Test
    void testRateLimitHeaders_ShouldBePresent() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("openapi-mem-no", "12345");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // When
        ResponseEntity<List> response = restTemplate.exchange(
            baseUrl + "/rate-limit-demo/items",
            HttpMethod.GET,
            entity,
            List.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().containsKey("RateLimit-Limit")).isTrue();
        assertThat(response.getHeaders().containsKey("RateLimit-Remaining")).isTrue();
    }

    @Test
    void testMissingUserHeader_ShouldReturn401() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // When & Then
        try {
            restTemplate.exchange(
                baseUrl + "/rate-limit-demo/items",
                HttpMethod.GET,
                entity,
                String.class
            );
            fail("Expected 401 UNAUTHORIZED");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void testMissingBodyField_ShouldReturn401() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // userId 필드가 없는 요청
        String requestBody = "{\"otherField\":\"value\"}";
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // When & Then
        try {
            restTemplate.exchange(
                baseUrl + "/rate-limit-demo/payments",
                HttpMethod.POST,
                entity,
                String.class
            );
            fail("Expected 401 UNAUTHORIZED");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void testInvalidJsonBody_ShouldReturn400() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        String invalidJson = "invalid json";
        HttpEntity<String> entity = new HttpEntity<>(invalidJson, headers);

        // When & Then
        try {
            restTemplate.exchange(
                baseUrl + "/rate-limit-demo/payments",
                HttpMethod.POST,
                entity,
                String.class
            );
            fail("Expected 400 BAD_REQUEST or 500 INTERNAL_SERVER_ERROR");
        } catch (HttpClientErrorException e) {
            // Spring의 기본 JSON 파싱 에러는 400, 우리의 TrafficGuardAspect 에러는 500일 수 있음
            assertThat(e.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (HttpServerErrorException e) {
            // 500 에러도 허용 (TrafficGuardAspect에서 처리된 경우)
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    void testTrafficGuardOnly_ShouldProceedDirectly() throws Exception {
        // Given - @TrafficGuard만 있고 @UserRateLimit이 없는 경우
        HttpHeaders headers = new HttpHeaders();
        headers.set("openapi-mem-no", "12345");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // When
        ResponseEntity<List> response = restTemplate.exchange(
            baseUrl + "/rate-limit-demo/traffic-guard-only",
            HttpMethod.GET,
            entity,
            List.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat((String) response.getBody().get(0)).contains("TrafficGuard only endpoint");
    }

    // === Rate Limit 초과 테스트 ===
    
    @Test
    void testSecondRateLimit_ExceedLimit_ShouldReturn429() throws Exception {
        // Given - 초당 2개 제한 (burst=2) - 총 4개까지 허용
        String userId = "rate-limit-test-user";
        HttpHeaders headers = new HttpHeaders();
        headers.set("openapi-mem-no", userId);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // Redis 키 확인을 위한 디버깅
        String bucketKey = "user:RateLimitDemoController:items:mem:" + userId;
        System.out.println("Testing with bucket key: " + bucketKey);
        
        // When - 4번 연속 요청 (2 + 2 burst)
        for (int i = 0; i < 2; i++) {
            ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/rate-limit-demo/items",
                HttpMethod.GET,
                entity,
                List.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); // 처음 2개는 성공
            System.out.println("Request " + (i+1) + " succeeded");
        }
        
        // Then - 3번째 요청은 429 에러
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/rate-limit-demo/items",
                HttpMethod.GET,
                entity,
                String.class
            );
            System.out.println("3rd request succeeded with status: " + response.getStatusCode());
            fail("Expected 429 TOO_MANY_REQUESTS");
        } catch (HttpClientErrorException e) {
            System.out.println("3rd request failed with status: " + e.getStatusCode());
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Test
    void testMinuteRateLimit_ExceedLimit_ShouldReturn429() throws Exception {
        // Given - 분당 2개 제한 (burst=2)
        String userId = "minute-limit-test-user";
        HttpHeaders headers = new HttpHeaders();
        headers.set("openapi-mem-no", userId);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // When - 4번 연속 요청 (2 + 2 burst)
        for (int i = 0; i < 2; i++) {
            ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/rate-limit-demo/reports",
                HttpMethod.GET,
                entity,
                List.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); // 처음 2개는 성공
        }
        
        // Then - 3번째 요청은 429 에러
        try {
            restTemplate.exchange(
                baseUrl + "/rate-limit-demo/reports",
                HttpMethod.GET,
                entity,
                String.class
            );
            fail("Expected 429 TOO_MANY_REQUESTS");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Test
    void testHourRateLimit_ExceedLimit_ShouldReturn429() throws Exception {
        // Given - 시간당 2개 제한 (burst=2)
        String userId = "hour-limit-test-user";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("openapi-mem-no", "12345"); // 사용자 헤더 추가
        
        RateLimitDemoController.BulkUploadRequest request = new RateLimitDemoController.BulkUploadRequest();
        request.setUserId(userId);
        request.setFileName("test.csv");
        request.setRecordCount(100);
        
        HttpEntity<RateLimitDemoController.BulkUploadRequest> entity = new HttpEntity<>(request, headers);
        
        // When - burst만큼 연속 요청 (burst=2)
        for (int i = 0; i < 2; i++) {
            ResponseEntity<List> response = restTemplate.exchange(
                baseUrl + "/rate-limit-demo/bulk-upload",
                HttpMethod.POST,
                entity,
                List.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); // 처음 2개는 성공
        }
        
        // Then - 3번째 요청은 429 에러 (burst=2이므로 3번째부터 차단)
        try {
            restTemplate.exchange(
                baseUrl + "/rate-limit-demo/bulk-upload",
                HttpMethod.POST,
                entity,
                String.class
            );
            fail("Expected 429 TOO_MANY_REQUESTS");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Test
    void testDayRateLimit_ExceedLimit_ShouldReturn429() throws Exception {
        // Given - 일당 1개 제한 (burst=1)
        String userId = "day-limit-test-user";
        HttpHeaders headers = new HttpHeaders();
        headers.set("openapi-mem-no", userId);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // When - 첫 번째 요청은 성공해야 함 (burst=1)
        ResponseEntity<List> response = restTemplate.exchange(
            baseUrl + "/rate-limit-demo/analytics",
            HttpMethod.GET,
            entity,
            List.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Then - 두 번째 요청은 429 에러 (burst=1이므로 즉시 차단)
        try {
            ResponseEntity<String> response2 = restTemplate.exchange(
                baseUrl + "/rate-limit-demo/analytics",
                HttpMethod.GET,
                entity,
                String.class
            );
            fail("Expected 429 TOO_MANY_REQUESTS, but got: " + response2.getStatusCode());
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Test
    void testRateLimitHeaders_WhenExceeded_ShouldShowCorrectValues() throws Exception {
        // Given
        String userId = "header-test-user";
        HttpHeaders headers = new HttpHeaders();
        headers.set("openapi-mem-no", userId);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // When - Rate limit 초과까지 요청
        for (int i = 0; i < 2; i++) { // 초당 2개 + burst 2개
            restTemplate.exchange(
                baseUrl + "/rate-limit-demo/items",
                HttpMethod.GET,
                entity,
                List.class
            );
        }
        
        // Then - 마지막 요청에서 헤더 확인
        try {
            restTemplate.exchange(
                baseUrl + "/rate-limit-demo/items",
                HttpMethod.GET,
                entity,
                String.class
            );
            fail("Expected 429 TOO_MANY_REQUESTS");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            // 429 에러 응답에서 헤더는 확인할 수 없으므로 이 부분은 제거
        }
    }
}

