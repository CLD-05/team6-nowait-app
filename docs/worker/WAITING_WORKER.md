# Waiting Worker Pod 운영 가이드

## 개요

API Pod 가 Redis 에 등록한 `waiting:pending-sync` 큐를 Worker Pod 가 비동기로 소비하여 RDS 에 반영한다.

- **API Pod**: HTTP 처리만. Redis 에 쓰고 끝.
- **Worker Pod**: 스케줄러만. Redis → RDS 동기화 + CALLED 타임아웃 처리.

## 기동 방법

### API Pod (기본)
```bash
./gradlew bootRun
# 또는
java -jar build/libs/team6-nowait-app.jar
```
→ `worker` 프로파일 비활성 → Worker 빈은 로드되지 않음.

### Worker Pod
```bash
SPRING_PROFILES_ACTIVE=waiting-worker java -jar build/libs/team6-nowait-app.jar
# 또는 IntelliJ Run Configuration:
#   VM options: -Dspring.profiles.active=waiting-worker
```
→ `waiting-worker` 프로파일 활성 → `WaitingSyncWorker`, `WaitingCallTimeoutScheduler` 만 동작.
→ Reservation Worker 는 별도 프로파일 `reservation-worker` 로 분리 예정 (Phase 6).
→ Controller 도 함께 로드되지만 트래픽이 들어오지 않으므로 무해 (LB 에서 Worker Pod 미포함).

### IntelliJ — 로컬에서 두 JVM 실행
1. **API**: 기존 Application Run Configuration (변경 없음)
2. **Worker**: 새 Run Configuration 복제 후 VM options 에 `-Dspring.profiles.active=worker`
3. 동시에 둘 다 Run → 같은 Redis 와 DB 를 공유, 역할만 분리됨.

## Worker 가 처리하는 일

| 트리거 | 동작 | 주기 |
|---|---|---|
| `waiting:pending-sync` 큐에 메시지 | Redis Hash → DB upsert | 500ms 폴링 |
| `waiting:active-sessions` 의 세션 큐 스캔 | CALLED 후 10분 초과 → cancelByOwner | 매 분 0초 |
| 부팅 시 | `waiting:processing` 잔여 메시지 → pending-sync 로 복구 | 1회 |

## 큐 모니터링

```bash
# 처리 대기 메시지 수
redis-cli LLEN waiting:pending-sync

# 처리 중 (이상적으로 0~5 이내)
redis-cli LLEN waiting:processing

# 처리 실패 (Dead Letter)
redis-cli LLEN waiting:dead-letter
redis-cli LRANGE waiting:dead-letter 0 -1
```

## 운영 시 주의

- **다중 Worker Pod 안전**:
  - `WaitingSyncWorker`: `RPOPLPUSH` 원자 연산이라 동일 메시지 중복 처리 X.
  - `WaitingCallTimeoutScheduler`: Redis SETNX 분산락(`waiting:scheduler:lock:called-timeout`, TTL 50초)으로 매 분 한 Pod 만 스캔.
- 처리량 부족 시: `worker.waiting.poll-interval-ms` 를 줄이거나 (예: 200ms) `batch-size` 를 늘림.
- DLQ 가 쌓이면 즉시 알람. 메시지 포맷: `token|timestamp|reason`.

## 배포 전 체크리스트

- [ ] Redis 영속화 (`appendonly yes`, `appendfsync everysec`) — Worker 처리 전 Redis 다운 시 메시지 유실 방지
- [ ] `waiting` 테이블의 기존 행 `waiting_token` 처리 (UNIQUE NOT NULL 마이그레이션)
- [ ] Worker Pod 의 `SPRING_PROFILES_ACTIVE=waiting-worker` 환경변수 설정
- [ ] LB 에서 Worker Pod 트래픽 제외 (Pod label/Service selector 분리)
- [ ] `LLEN waiting:pending-sync`, `LLEN waiting:dead-letter` 모니터링 대시보드

## 향후 확장

- Phase 4–6: Reservation 도메인에 동일 패턴 적용
- Phase 7: AOF/RDB 백업 정책, 장애 복구 시나리오 테스트
- 멀티 Worker Pod 확장: `WaitingSyncWorker` 는 RPOPLPUSH 라 안전. `WaitingCallTimeoutScheduler` 는 Redis 분산락 필요.
