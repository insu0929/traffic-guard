# Traffic Guard

Spring Boot 기반의 Rate Limiting 및 Traffic Control 라이브러리입니다. Redis를 활용한 Token Bucket 알고리즘을 사용하여 사용자별 Rate Limiting을 제공합니다.

## 주요 기능

<img width="1521" height="820" alt="image" src="https://github.com/user-attachments/assets/93087f7f-80f7-46d2-8793-cd789b4eb489" />

- **Token Bucket 알고리즘**: Redis Lua 스크립트를 사용한 효율적인 Rate Limiting
- **사용자별 Rate Limiting**: 사용자 ID 기반의 개별 Rate Limit 관리
- **다양한 시간 단위 지원**: 초, 분, 시간, 일 단위 Rate Limit 설정
- **유연한 사용자 식별**: HTTP 헤더 또는 Request Body에서 사용자 ID 추출
- **Rate Limit 헤더**: 표준 Rate Limit 응답 헤더 제공
- **AOP 기반**: 어노테이션 기반의 간편한 사용법

## 사용 방법

### 1. 의존성 추가

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.redisson:redisson-spring-boot-starter:3.23.2'
}
```

### 2. Redis 설정

`application.yml`에 Redis 설정을 추가합니다:

```yaml
spring:
  redis:
    properties:
      master-host: redis://localhost:6379
      slave-host: redis://localhost:6379
```

### 3. 컨트롤러에 적용

```java
@RestController
public class ApiController {
    
    @GetMapping("/items")
    @TrafficGuard
    @UserRateLimit(rate = 10, timeUnit = UserRateLimit.TimeUnit.SECOND, burst = 5)
    public List<String> getItems() {
        return Arrays.asList("item1", "item2");
    }
}
```

## 어노테이션 옵션

### @UserRateLimit

- `rate`: 허용 속도 (예: 10)
- `timeUnit`: 시간 단위 (SECOND, MINUTE, HOUR, DAY)
- `burst`: 허용 버스트 (짧은 순간 추가 여유)
- `ttlMillis`: Redis 키 TTL (기본 60초)
- `emitHeaders`: Rate Limit 헤더 추가 여부 (기본 true)
- `userHeader`: 사용자 식별 헤더명 (기본 "openapi-mem-no")
- `userBodyField`: Request Body에서 사용자 ID 필드명
- `userSource`: 사용자 ID 추출 우선순위 (HEADER_FIRST, BODY_FIRST, HEADER_ONLY, BODY_ONLY)

## 예제

### 헤더에서 사용자 ID 추출

```java
@GetMapping("/api/items")
@TrafficGuard
@UserRateLimit(rate = 20, timeUnit = UserRateLimit.TimeUnit.SECOND, burst = 5)
public ResponseEntity<List<String>> getItems() {
    // 초당 20개, 버스트 5개
    return ResponseEntity.ok(Arrays.asList("item1", "item2"));
}
```

### Body에서 사용자 ID 추출

```java
@PostMapping("/api/payments")
@TrafficGuard
@UserRateLimit(
    rate = 1, 
    timeUnit = UserRateLimit.TimeUnit.SECOND, 
    burst = 1,
    userBodyField = "userId",
    userSource = UserRateLimit.UserIdSource.BODY_ONLY
)
public ResponseEntity<String> processPayment(@RequestBody PaymentRequest request) {
    // 초당 1개, 요청 body의 userId 필드 사용
    return ResponseEntity.ok("Payment processed");
}
```

### 헤더 우선, Body 보조

```java
@PostMapping("/api/orders")
@TrafficGuard
@UserRateLimit(
    rate = 2,
    timeUnit = UserRateLimit.TimeUnit.SECOND,
    burst = 2,
    userHeader = "X-User-Id",
    userBodyField = "userId",
    userSource = UserRateLimit.UserIdSource.HEADER_FIRST
)
public ResponseEntity<String> createOrder(@RequestBody OrderRequest request) {
    // 헤더 먼저 확인, 없으면 body에서 추출
    return ResponseEntity.ok("Order created");
}
```

## 응답 헤더

Rate Limit이 적용된 경우 다음 헤더가 응답에 포함됩니다:

- `RateLimit-Limit`: 허용된 요청 수
- `RateLimit-Remaining`: 남은 요청 수
- `Retry-After`: 재시도까지 대기 시간 (초)

## 에러 응답

- `429 TOO_MANY_REQUESTS`: Rate Limit 초과
- `401 UNAUTHORIZED`: 사용자 식별 실패
- `400 BAD_REQUEST`: 잘못된 요청 (예: JSON 파싱 실패)

## 빌드 및 실행

```bash
# 빌드
./gradlew build

# 테스트
./gradlew test

# 실행
./gradlew bootRun
```

## 테스트

테스트를 실행하기 전에 Redis가 실행 중이어야 합니다.

```bash
# Redis 실행 (Docker 예제)
docker run -d -p 6379:6379 redis:alpine

# 테스트 실행
./gradlew test
```

## 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.


