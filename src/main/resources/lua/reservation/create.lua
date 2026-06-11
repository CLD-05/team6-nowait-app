-- 예약 생성 — 슬롯 정원 검증 + 토큰 생성 + 큐 푸시
--
-- KEYS[1] = reservation:slot:{slotId}:count
-- KEYS[2] = reservation:slot:{slotId}:queue
-- KEYS[3] = reservation:token:{token}
-- KEYS[4] = reservation:user-slot:{uid}:{slotId}
-- KEYS[5] = reservation:noshow-candidates
-- KEYS[6] = reservation:pending-sync
--
-- ARGV[1] = userId
-- ARGV[2] = restaurantId
-- ARGV[3] = slotId
-- ARGV[4] = headcount
-- ARGV[5] = slotCapacity (총 정원)
-- ARGV[6] = token (UUID)
-- ARGV[7] = createdAt (epoch millis)
-- ARGV[8] = reservationTime (slot date+time, epoch millis) — 노쇼 스케줄러 score
-- ARGV[9] = ttlSeconds
--
-- 반환:
--   성공: { 1, token }
--   실패: { 0, errorCode } — DUPLICATE_RESERVATION / SLOT_FULL

-- 1) 동일 슬롯 중복 예약 차단
if redis.call('EXISTS', KEYS[4]) == 1 then
  return { 0, 'DUPLICATE_RESERVATION' }
end

-- 2) 정원 검증 — INCR 후 초과면 즉시 롤백
local newCount = redis.call('INCR', KEYS[1])
if newCount > tonumber(ARGV[5]) then
  redis.call('DECR', KEYS[1])
  return { 0, 'SLOT_FULL' }
end

-- 3) 슬롯 큐 등록 (score = createdAt)
redis.call('ZADD', KEYS[2], ARGV[7], ARGV[6])

-- 4) 토큰 Hash
redis.call('HMSET', KEYS[3],
  'userId',           ARGV[1],
  'restaurantId',     ARGV[2],
  'slotId',           ARGV[3],
  'headcount',        ARGV[4],
  'status',           'CONFIRMED',
  'createdAt',        ARGV[7],
  'reservationTime',  ARGV[8])
redis.call('EXPIRE', KEYS[3], ARGV[9])

-- 5) 사용자-슬롯 중복 매핑
redis.call('SET', KEYS[4], ARGV[6], 'EX', ARGV[9])

-- 6) 노쇼 후보 ZSET (스케줄러가 ZRANGEBYSCORE 0 (now-grace) 로 만료 탐색)
redis.call('ZADD', KEYS[5], ARGV[8], ARGV[6])

-- 7) Worker 동기화 큐
redis.call('LPUSH', KEYS[6], ARGV[6])

-- 슬롯 카운터/큐 TTL 갱신
redis.call('EXPIRE', KEYS[1], ARGV[9])
redis.call('EXPIRE', KEYS[2], ARGV[9])

return { 1, ARGV[6] }
