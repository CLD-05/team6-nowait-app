-- 웨이팅 취소 — 활성 상태 해제
--
-- KEYS[1] = waiting:token:{token}
-- KEYS[2] = waiting:session:{sid}:queue
-- KEYS[3] = waiting:session:{sid}:count
-- KEYS[4] = waiting:user:{uid}:active
-- KEYS[5] = waiting:pending-sync
--
-- ARGV[1] = token
-- ARGV[2] = expectedUserId (검증용)
-- ARGV[3] = canceledAt (epoch millis)
--
-- 반환:
--   성공: { 1, 'OK' }
--   실패: { 0, errorCode } — WAITING_NOT_FOUND / ACCESS_DENIED / ALREADY_PROCESSED

if redis.call('EXISTS', KEYS[1]) == 0 then
  return { 0, 'WAITING_NOT_FOUND' }
end

local userId = redis.call('HGET', KEYS[1], 'userId')
if userId ~= ARGV[2] then
  return { 0, 'ACCESS_DENIED' }
end

local status = redis.call('HGET', KEYS[1], 'status')
if status == 'ENTERED' or status == 'CANCELLED' then
  return { 0, 'ALREADY_PROCESSED' }
end

-- 상태 전이
redis.call('HSET', KEYS[1], 'status', 'CANCELLED', 'canceledAt', ARGV[3])

-- Sorted Set 에서 제거 → 뒷 사람들 자동으로 한 칸씩 당겨짐
redis.call('ZREM', KEYS[2], ARGV[1])

-- 카운트 -1 (0 미만 방지)
local newCount = redis.call('DECR', KEYS[3])
if tonumber(newCount) < 0 then
  redis.call('SET', KEYS[3], 0)
end

-- 사용자 활성 토큰 매핑 해제
redis.call('DEL', KEYS[4])

-- Worker 동기화 큐 (상태 변경 반영용)
redis.call('LPUSH', KEYS[5], ARGV[1])

return { 1, 'OK' }
