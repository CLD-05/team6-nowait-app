-- 점주 강제 취소 — 사용자 검증 없이 강제 취소
--
-- KEYS[1] = waiting:token:{token}
-- KEYS[2] = waiting:session:{sid}:queue
-- KEYS[3] = waiting:session:{sid}:count
-- KEYS[4] = waiting:user:{uid}:active
-- KEYS[5] = waiting:pending-sync
--
-- ARGV[1] = token
-- ARGV[2] = canceledAt (epoch millis)
--
-- 반환:
--   성공: { 1, 'OK' }
--   실패: { 0, errorCode } — WAITING_NOT_FOUND / ALREADY_PROCESSED

if redis.call('EXISTS', KEYS[1]) == 0 then
  return { 0, 'WAITING_NOT_FOUND' }
end

local status = redis.call('HGET', KEYS[1], 'status')
if status == 'ENTERED' or status == 'CANCELLED' then
  return { 0, 'ALREADY_PROCESSED' }
end

redis.call('HMSET', KEYS[1], 'status', 'CANCELLED', 'canceledAt', ARGV[2])
redis.call('ZREM', KEYS[2], ARGV[1])

local newCount = redis.call('DECR', KEYS[3])
if tonumber(newCount) < 0 then
  redis.call('SET', KEYS[3], 0)
end

redis.call('DEL', KEYS[4])
redis.call('LPUSH', KEYS[5], ARGV[1])

return { 1, 'OK' }
