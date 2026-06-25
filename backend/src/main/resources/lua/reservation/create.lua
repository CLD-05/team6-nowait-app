-- 예약 생성 — 슬롯 정원 검증 + 토큰 생성 + 큐 푸시
--
-- KEYS[1] = reservation:slot:{slotId}:count
-- KEYS[2] = reservation:slot:{slotId}:queue
-- KEYS[3] = reservation:token:{token}
-- KEYS[4] = reservation:user-slot:{uid}:{slotId}
-- KEYS[5] = reservation:noshow-candidates
-- KEYS[6] = reservation:pending-sync
-- KEYS[7] = reservation:user:{userId}:tokens   (사용자별 토큰 목록 — score=createdAt)
-- KEYS[8] = reservation:restaurant:{restaurantId}:tokens  (매장별 토큰 목록 — score=createdAt)
-- KEYS[9] = reservation:user-restaurant-date:{userId}:{restaurantId}:{date}  (같은 매장·같은 날짜 중복 방지)
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
--   실패: { 0, errorCode } — DUPLICATE_RESERVATION / DUPLICATE_RESERVATION_SAME_DAY / SLOT_FULL

-- 1) 동일 슬롯 중복 예약 차단 (PENDING/CONFIRMED 상태일 때만)
if redis.call('EXISTS', KEYS[4]) == 1 then
  local existingToken = redis.call('GET', KEYS[4])
  local existingStatus = redis.call('HGET', 'reservation:token:' .. existingToken, 'status')
  -- 종료 상태(취소/거부/방문/노쇼)면 키를 정리하고 재예약 허용
  if existingStatus == 'CANCELLED' or existingStatus == 'REJECTED'
      or existingStatus == 'VISITED' or existingStatus == 'NO_SHOW' then
    redis.call('DEL', KEYS[4])
  else
    return { 0, 'DUPLICATE_RESERVATION' }
  end
end

-- 1-1) 같은 매장·같은 날짜 중복 예약 차단 (시간대가 달라도 활성 예약은 1건만)
if redis.call('EXISTS', KEYS[9]) == 1 then
  local existingDayToken = redis.call('GET', KEYS[9])
  local existingDayStatus = redis.call('HGET', 'reservation:token:' .. existingDayToken, 'status')
  if existingDayStatus == 'CANCELLED' or existingDayStatus == 'REJECTED'
      or existingDayStatus == 'VISITED' or existingDayStatus == 'NO_SHOW' then
    redis.call('DEL', KEYS[9])
  else
    return { 0, 'DUPLICATE_RESERVATION_SAME_DAY' }
  end
end

-- 2) 정원 검증 — INCR 후 초과면 즉시 롤백
local newCount = redis.call('INCR', KEYS[1])
if newCount > tonumber(ARGV[5]) then
  redis.call('DECR', KEYS[1])
  return { 0, 'SLOT_FULL' }
end

-- 3) 슬롯 큐 등록 (score = createdAt)
redis.call('ZADD', KEYS[2], ARGV[7], ARGV[6])

-- 4) 토큰 Hash (초기 상태: PENDING — 점주 승인 후 CONFIRMED)
redis.call('HMSET', KEYS[3],
  'userId',           ARGV[1],
  'restaurantId',     ARGV[2],
  'slotId',           ARGV[3],
  'headcount',        ARGV[4],
  'status',           'PENDING',
  'createdAt',        ARGV[7],
  'reservationTime',  ARGV[8])
redis.call('EXPIRE', KEYS[3], ARGV[9])

-- 5) 사용자-슬롯 중복 매핑 / 사용자-매장-날짜 중복 매핑
redis.call('SET', KEYS[4], ARGV[6], 'EX', ARGV[9])
redis.call('SET', KEYS[9], ARGV[6], 'EX', ARGV[9])

-- 6) 노쇼 후보 등록은 점주 승인(approve) 시점으로 이동 — PENDING 단계에서는 제외

-- 7) Worker 동기화 큐
redis.call('LPUSH', KEYS[6], ARGV[6])

-- 8) 사용자별 / 매장별 토큰 목록 (score = createdAt, TTL 동일)
redis.call('ZADD', KEYS[7], ARGV[7], ARGV[6])
redis.call('EXPIRE', KEYS[7], ARGV[9])
redis.call('ZADD', KEYS[8], ARGV[7], ARGV[6])
redis.call('EXPIRE', KEYS[8], ARGV[9])

-- 슬롯 카운터/큐 TTL 갱신
redis.call('EXPIRE', KEYS[1], ARGV[9])
redis.call('EXPIRE', KEYS[2], ARGV[9])

return { 1, ARGV[6] }
