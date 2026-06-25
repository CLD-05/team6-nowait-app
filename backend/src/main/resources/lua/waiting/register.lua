-- 웨이팅 등록 — 6개 키를 원자적으로 처리
--
-- KEYS[1] = waiting:session:{sid}:next-number   (INCR)
-- KEYS[2] = waiting:session:{sid}:count         (INCR)
-- KEYS[3] = waiting:session:{sid}:queue         (Sorted Set ZADD)
-- KEYS[4] = waiting:token:{token}               (Hash)
-- KEYS[5] = waiting:user:{uid}:active           (중복 등록 방지)
-- KEYS[6] = waiting:pending-sync                (Worker 큐)
--
-- ARGV[1] = userId
-- ARGV[2] = sessionId
-- ARGV[3] = restaurantId
-- ARGV[4] = partySize
-- ARGV[5] = maxWaitingCount
-- ARGV[6] = token (UUID)
-- ARGV[7] = registeredAt (epoch millis, ZADD score)
-- ARGV[8] = ttlSeconds
--
-- 반환:
--   성공: { 1, waitingNumber, token }
--   실패: { 0, errorCode } — DUPLICATE_WAITING 또는 WAITING_COUNT_EXCEEDED

-- 1) 중복 등록 차단
if redis.call('EXISTS', KEYS[5]) == 1 then
  return { 0, 'DUPLICATE_WAITING' }
end

-- 2) 한도 검증 — INCR 후 초과면 즉시 롤백
local newCount = redis.call('INCR', KEYS[2])
if newCount > tonumber(ARGV[5]) then
  redis.call('DECR', KEYS[2])
  return { 0, 'WAITING_COUNT_EXCEEDED' }
end

-- 3) 대기번호 채번
local waitingNumber = redis.call('INCR', KEYS[1])

-- 4) Sorted Set 등록 (score = registeredAt)
redis.call('ZADD', KEYS[3], ARGV[7], ARGV[6])

-- 5) Hash 저장
redis.call('HMSET', KEYS[4],
  'userId',        ARGV[1],
  'sessionId',     ARGV[2],
  'restaurantId',  ARGV[3],
  'waitingNumber', waitingNumber,
  'partySize',     ARGV[4],
  'status',        'WAITING',
  'registeredAt',  ARGV[7])
redis.call('EXPIRE', KEYS[4], ARGV[8])

-- 6) 사용자 활성 토큰 매핑 (중복 차단)
redis.call('SET', KEYS[5], ARGV[6], 'EX', ARGV[8])

-- 7) Worker 동기화 큐
redis.call('LPUSH', KEYS[6], ARGV[6])

-- 세션 키 TTL 갱신 (휘발 방지)
redis.call('EXPIRE', KEYS[1], ARGV[8])
redis.call('EXPIRE', KEYS[2], ARGV[8])
redis.call('EXPIRE', KEYS[3], ARGV[8])

return { 1, waitingNumber, ARGV[6] }
