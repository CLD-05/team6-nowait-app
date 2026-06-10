-- 노쇼 처리 (스케줄러 또는 점주) — CONFIRMED → NO_SHOW
--
-- KEYS[1] = reservation:token:{token}
-- KEYS[2] = reservation:slot:{slotId}:queue
-- KEYS[3] = reservation:user-slot:{uid}:{slotId}
-- KEYS[4] = reservation:noshow-candidates
-- KEYS[5] = reservation:pending-sync
--
-- ARGV[1] = token
-- ARGV[2] = noShowAt (epoch millis)
--
-- 반환:
--   성공: { 1, 'OK' }
--   실패: { 0, errorCode } — RESERVATION_NOT_FOUND / INVALID_STATUS_TRANSITION

if redis.call('EXISTS', KEYS[1]) == 0 then
  return { 0, 'RESERVATION_NOT_FOUND' }
end

local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'CONFIRMED' then
  return { 0, 'INVALID_STATUS_TRANSITION' }
end

redis.call('HSET', KEYS[1], 'status', 'NO_SHOW', 'noShowAt', ARGV[2])
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('ZREM', KEYS[4], ARGV[1])

-- 정원은 복구하지 않음 (예약 시간 이미 지났으므로 의미 없음)
redis.call('DEL', KEYS[3])

redis.call('LPUSH', KEYS[5], ARGV[1])

return { 1, 'OK' }
