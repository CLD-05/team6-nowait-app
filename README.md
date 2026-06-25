# team6-nowait-app

NoWait — 식당 예약 및 웨이팅 서비스의 애플리케이션 **모노레포**입니다.
백엔드(Spring Boot)와 프론트엔드(React + Vite)를 단일 `main` 트렁크에서 함께 관리합니다.

## 디렉터리 구조

```
team6-nowait-app/
├── backend/                # Spring Boot API + Worker (pom.xml, src/, Dockerfile)
├── frontend/               # React + Vite SPA (package.json, vite.config.ts, src/)
└── .github/workflows/      # CI/CD 워크플로우
```

## 브랜치 전략

- **단일 `main` 트렁크.** 기능 작업은 짧은 토픽 브랜치 → `main`으로 PR.
- 컴포넌트 분리는 브랜치가 아니라 **경로(`backend/`, `frontend/`)** 로 한다.
  - 과거에는 백엔드=`develop`/`main`, 프론트=`frontend` 브랜치로 분리되어 있었으나
    drift와 워크플로우 불일치 문제가 있어 모노레포로 통합했다.

## CI/CD 개요

| 워크플로우 | 트리거 | 역할 |
|---|---|---|
| `backend-ci.yml` | PR → main | `backend/**` 변경 시 Maven 테스트 (필수 체크) |
| `frontend-ci.yml` | PR → main | `frontend/**` 변경 시 lint + build (필수 체크) |
| `backend-dev-deploy.yml` | push main + `backend/**` | 이미지 빌드 → ECR push → config repo의 dev values 태그 갱신(Argo CD 자동 배포) |
| `backend-prod-promote.yml` | 수동 dispatch | 검증된 dev 이미지 태그를 prod 태그로 **retag 승격** + prod values 갱신 |
| `frontend-prod-deploy.yml` | 수동 dispatch | Vite 빌드 → prod S3 sync → CloudFront 무효화 |

### 핵심 설계 결정

1. **dev = 자동, prod = 수동.** dev는 `main` 머지 시 자동 배포되지만,
   prod는 항상 사람이 dispatch로 승인·실행한다.
2. **prod 백엔드는 main 재빌드가 아니라 dev 이미지 retag 승격.**
   단일 main 트렁크에서 "검증된 바로 그 이미지"를 prod로 올리는 안전벨트.
3. **프론트 prod는 수동 dispatch 전용.** dev 프론트는 로컬에서만 돌아 별도 dev 배포가
   없으므로, push 자동 트리거를 두면 변경이 곧장 prod로 직행해 "prod 수동" 원칙과 충돌한다.
4. **CI는 모든 PR에서 돌고 경로 필터는 잡 내부에서 처리.**
   `paths:` 트리거를 required check에 직접 걸면, 해당 경로를 안 건드린 PR에서
   체크가 영원히 pending으로 남아 머지를 막는다. 이를 피하려고
   `dorny/paths-filter` 게이트 + 항상 실행되는 결과 잡 패턴을 사용한다.

## 관련 레포지토리

- `team6-nowait-infra` — Terraform (VPC/EKS/RDS/ElastiCache/S3/IAM 등)
- `team6-nowait-config` — Argo CD Application + Helm chart/values (GitOps 배포 대상)
