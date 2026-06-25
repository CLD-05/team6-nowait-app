-- 예약 취소 — 본인 검증 + 상태 전이 + 정원 복구
--
-- KEYS[1] = reservation:token:{token}
-- KEYS[2] = reservation:slot:{slotId}:queue
-- KEYS[3] = reservation:slot:{slotId}:count
-- KEYS[4] = reservation:user-slot:{uid}:{slotId}
-- KEYS[5] = reservation:noshow-candidates
-- KEYS[6] = reservation:pending-sync
--
-- ARGV[1] = token
-- ARGV[2] = expectedUserId (검증용)
-- ARGV[3] = canceledAt (epoch millis)
--
-- 반환:
--   성공: { 1, 'OK' }
--   실패: { 0, errorCode } — RESERVATION_NOT_FOUND / ACCESS_DENIED / ALREADY_PROCESSED

if redis.call('EXISTS', KEYS[1]) == 0 then
  return { 0, 'RESERVATION_NOT_FOUND' }
end

local userId = redis.call('HGET', KEYS[1], 'userId')
if userId ~= ARGV[2] then
  return { 0, 'ACCESS_DENIED' }
end

local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'CONFIRMED' and status ~= 'PENDING' then
  return { 0, 'ALREADY_PROCESSED' }
end

redis.call('HMSET', KEYS[1], 'status', 'CANCELLED', 'canceledAt', ARGV[3])
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('ZREM', KEYS[5], ARGV[1])

-- 정원 복구
local newCount = redis.call('DECR', KEYS[3])
if tonumber(newCount) < 0 then
  redis.call('SET', KEYS[3], 0)
end

-- 사용자-슬롯 매핑 해제 (같은 슬롯 재예약 가능)
redis.call('DEL', KEYS[4])

redis.call('LPUSH', KEYS[6], ARGV[1])

return { 1, 'OK' }
