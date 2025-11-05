-- Token Bucket Rate Limiting Script
-- KEYS[1]=bucket(tokens), KEYS[2]=ts
-- ARGV[1]=ratePerSec, ARGV[2]=burst, ARGV[3]=nowMs, ARGV[4]=ttlMs
-- return {allowed(0/1), tokens(float), retryAfterMs(int)}

local bucketKey = KEYS[1]
local tsKey = KEYS[2]
local r = tonumber(ARGV[1])
local b = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

-- 초기 로드 (없으면 가득 찬 상태로 시작)
local tokens = tonumber(redis.call('GET', bucketKey) or b)
local lastTs = tonumber(redis.call('GET', tsKey) or now)

-- 시계 역행 방지
if lastTs > now then now = lastTs end

-- Δt 계산(초). 음수 방지
local delta = (now - lastTs) / 1000.0
if delta < 0 then delta = 0 end

-- 리필: r <= 0이면 리필 없음(0으로 나눔 방지)
local newTokens = tokens
if r and r > 0 then
  newTokens = tokens + (r * delta)
else
  -- r<=0인 경우: 리필이 없으므로 newTokens 그대로
  -- 아래에서 허용/거절 판단만 수행
end

-- 상/하한 캡 + 미세 부동오차 교정
if newTokens > b then newTokens = b end
if newTokens < 0 then newTokens = 0 end

-- 부동소수점 정밀도 개선 (매우 작은 값들을 0으로 처리)
if newTokens < 0.000001 then newTokens = 0 end

local allowed = 0
local retryAfter = 0

if newTokens >= 1.0 then
  newTokens = newTokens - 1.0
  allowed = 1
else
  -- 토큰이 부족한 경우 거절
  allowed = 0
  if r and r > 0 then
    local need = 1.0 - newTokens
    -- 재시도 시간은 올림(ceiling)
    retryAfter = math.ceil((need / r) * 1000.0)
  else
    -- r<=0이면 충전이 없으니 사실상 계속 거절.
    -- 정책에 따라 큰 값이나 ttl을 반환 (여기서는 ttl 사용)
    retryAfter = ttl
  end
end

-- 저장 (유휴 시 자동 만료)
redis.call('SET', bucketKey, newTokens, 'PX', ttl)
redis.call('SET', tsKey, now, 'PX', ttl)
return {allowed, tostring(newTokens), retryAfter}



