-- 탈퇴 회원 이메일 익명화
-- withdrawUser()에서 이메일을 익명화하기 전에 탈퇴한 기존 레코드를 정리.
-- UNIQUE 제약(users.email)을 해제해 동일 이메일로 재가입이 가능하게 함.
UPDATE users
SET email = CONCAT('deleted_', id, '@withdrawn.nowait')
WHERE is_deleted = 'Y'
  AND email NOT LIKE 'deleted_%@withdrawn.nowait';
