-- 방문 완료 (점주) — CONFIRMED → VISITED
--
-- KEYS[1] = reservation:token:{token}
-- KEYS[2] = reservation:slot:{slotId}:queue
-- KEYS[3] = reservation:user-slot:{uid}:{slotId}
-- KEYS[4] = reservation:noshow-candidates
-- KEYS[5] = reservation:pending-sync
--
-- ARGV[1] = token
-- ARGV[2] = visitedAt (epoch millis)
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

redis.call('HMSET', KEYS[1], 'status', 'VISITED', 'visitedAt', ARGV[2])
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('ZREM', KEYS[4], ARGV[1])

-- 정원은 그대로 유지 (방문 완료 = 점유 확정)
redis.call('DEL', KEYS[3])

redis.call('LPUSH', KEYS[5], ARGV[1])

return { 1, 'OK' }
