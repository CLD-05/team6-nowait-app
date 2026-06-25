-- 예약 승인 (점주) — PENDING → CONFIRMED
--
-- KEYS[1] = reservation:token:{token}
-- KEYS[2] = reservation:noshow-candidates
-- KEYS[3] = reservation:pending-sync
--
-- ARGV[1] = token
-- ARGV[2] = approvedAt (epoch millis)
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

local reservationTime = redis.call('HGET', KEYS[1], 'reservationTime')

redis.call('HMSET', KEYS[1], 'status', 'CONFIRMED')

-- 승인 시점에 노쇼 후보 ZSET 등록
redis.call('ZADD', KEYS[2], tonumber(reservationTime), ARGV[1])

redis.call('LPUSH', KEYS[3], ARGV[1])

return { 1, 'OK' }
