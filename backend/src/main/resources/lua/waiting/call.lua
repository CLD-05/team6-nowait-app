-- 점주 호출 — WAITING → CALLED
--
-- KEYS[1] = waiting:token:{token}
-- KEYS[2] = waiting:pending-sync
--
-- ARGV[1] = token
-- ARGV[2] = calledAt (epoch millis)
--
-- 반환:
--   성공: { 1, 'OK' }
--   실패: { 0, errorCode } — WAITING_NOT_FOUND / INVALID_STATUS_TRANSITION

if redis.call('EXISTS', KEYS[1]) == 0 then
  return { 0, 'WAITING_NOT_FOUND' }
end

local status = redis.call('HGET', KEYS[1], 'status')
if status ~= 'WAITING' then
  return { 0, 'INVALID_STATUS_TRANSITION' }
end

redis.call('HMSET', KEYS[1], 'status', 'CALLED', 'calledAt', ARGV[2])
redis.call('LPUSH', KEYS[2], ARGV[1])

return { 1, 'OK' }
