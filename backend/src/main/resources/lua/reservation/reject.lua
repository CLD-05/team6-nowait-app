-- 예약 거부 (점주) — PENDING → REJECTED
--
-- KEYS[1] = reservation:token:{token}
-- KEYS[2] = reservation:slot:{slotId}:queue
-- KEYS[3] = reservation:slot:{slotId}:count
-- KEYS[4] = reservation:user-slot:{uid}:{slotId}
-- KEYS[5] = reservation:pending-sync
--
-- ARGV[1] = token
-- ARGV[2] = rejectedAt (epoch millis)
-- ARGV[3] = rejectionReason (enum 문자열)
--
-- 반환:
--   성공: { 1, 'OK' }
--   실패: { 0, errorCode } — RESERVATION_NOT_FOUND / INVALID_STATUS_TRANSITION

if redis.call('EXISTS', KEYS[1]) == 0 then
  return { 0, 'RESERVATION_NOT_FOUND' }
end

local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'PENDING' then
  return { 0, 'INVALID_STATUS_TRANSITION' }
end

redis.call('HMSET', KEYS[1], 'status', 'REJECTED', 'rejectedAt', ARGV[2], 'rejectionReason', ARGV[3])

-- 슬롯 카운트 복구 (거부 = 점유 해제)
local newCount = redis.call('DECR', KEYS[3])
if tonumber(newCount) < 0 then
  redis.call('SET', KEYS[3], 0)
end

-- 슬롯 큐에서 제거
redis.call('ZREM', KEYS[2], ARGV[1])

-- 사용자-슬롯 매핑 해제 (재예약 허용)
redis.call('DEL', KEYS[4])

redis.call('LPUSH', KEYS[5], ARGV[1])

return { 1, 'OK' }
