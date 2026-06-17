-- V1 시드 데이터의 식당 상태를 OPEN으로 업데이트 (SlotSeedRunner가 슬롯을 생성하도록)
UPDATE restaurant SET status = 'OPEN' WHERE status = 'CLOSED' AND is_deleted = 'N';
