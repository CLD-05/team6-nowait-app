<div align="center">

# 🍽️ NoWait

**식당 예약 & 실시간 웨이팅 서비스**

백엔드(Spring Boot)와 프론트엔드(React + Vite)를 단일 `main` 트렁크에서 함께 관리하는 **애플리케이션 모노레포**입니다.

<!-- 서비스 대표 이미지 / 로고를 여기에 넣어주세요 -->
<!-- ![NoWait](docs/images/banner.png) -->

[![Backend CI](https://github.com/CLD-05/team6-nowait-app/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/CLD-05/team6-nowait-app/actions/workflows/backend-ci.yml)
[![Frontend CI](https://github.com/CLD-05/team6-nowait-app/actions/workflows/frontend-ci.yml/badge.svg)](https://github.com/CLD-05/team6-nowait-app/actions/workflows/frontend-ci.yml)

</div>

---

## 📖 프로젝트 소개

**NoWait**는 손님이 식당에 직접 방문하지 않아도 웨이팅을 등록하고, 예약 가능한 시간대를 선택할 수 있는 식당 예약·웨이팅 서비스입니다.

손님은 웨이팅 등록 후 자신의 순번을 **적응형 폴링** 방식으로 확인합니다. 앞에 남은 팀 수가 많을 때는 조회 주기를 길게 가져가고, 입장 순서가 가까워질수록 더 짧은 주기로 갱신하여 불필요한 요청을 줄였습니다.

점주가 손님을 호출하면 `WAITING_CALLED` 타입의 알림을 생성하고, 실시간 알림 타입만 **SSE(Server-Sent Events)** 로 즉시 push합니다. 여러 API Pod 중 어느 Pod에 사용자의 SSE 연결이 붙어 있더라도 알림이 도달할 수 있도록 **Redis Pub/Sub fan-out** 구조를 사용했습니다.

웨이팅과 예약 등록 흐름은 **Redis Lua Script**를 사용해 중복 등록, 정원 초과, 상태 전이를 원자적으로 처리합니다. 이후 Worker Pod가 Redis 동기화 큐를 소비하여 RDS(MySQL)에 비동기 반영합니다.

오픈런, 맛집 방송 출연 등 특정 식당에 트래픽이 순간적으로 몰리는 상황을 고려하여 Redis 큐 기반 비동기 처리, HPA/KEDA/Karpenter 기반 오토스케일링, Prometheus/Grafana 관측까지 함께 설계했습니다.

### 주요 기능

| 도메인 | 설명 |
|---|---|
| 🔐 **인증 (auth · user)** | 회원가입 / 로그인 / JWT Access Token 발급, Refresh Token Redis 관리 |
| 🍴 **식당 (restaurant)** | 식당 목록·상세 조회, 점주 식당 등록·수정, 영업 상태 관리 |
| 🎫 **웨이팅 (waiting)** | Redis Lua 기반 웨이팅 등록, 순번 조회, 적응형 폴링, Worker 비동기 DB 반영 |
| 📅 **예약 (reservation · slot)** | 슬롯 정원·중복 예약 검증, Redis Lua 기반 예약 상태 처리, Worker 비동기 DB 반영 |
| 🔔 **알림 (notification)** | 일반 알림 조회/읽음 처리, `WAITING_CALLED` 실시간 SSE push |
| 🧑‍🍳 **점주 (owner)** | 대기열 조회, 손님 호출(call), 입장(enter), 취소 처리, 예약 승인/거부/방문/노쇼 처리 |
| ⭐ **즐겨찾기·리뷰 (favorite · review)** | 관심 식당 등록·조회, 예약 방문 후 리뷰 작성 |

---

## 🏗️ 아키텍처

> 아래 구조는 **production 환경 기준**입니다.  
> development 환경에서는 CloudFront 없이 로컬 Vite 프론트엔드에서 dev ALB API로 직접 요청합니다.

```text
                  ┌─────────────┐
   손님/점주  ──▶ │ CloudFront  │ ──▶ S3 (Frontend 정적 호스팅)
                  └─────────────┘
                         │
                         ▼
                  ┌─────────────┐
                  │     ALB     │
                  └──────┬──────┘
                         ▼
                  ┌─────────────┐
                  │     EKS     │
                  │ Spring Boot │
                  │ API Pods    │
                  │ API + SSE   │
                  └──────┬──────┘
                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
       ▼                 ▼                 ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│    Redis     │   │ Worker Pods  │   │ RDS (MySQL)  │
│ Queue/Cache  │◀─▶│ waiting /    │──▶│ 최종 저장소  │
│ Pub/Sub      │   │ reservation  │   │              │
└──────────────┘   └──────────────┘   └──────────────┘
```

<!-- 실제 아키텍처 다이어그램 이미지가 있다면 아래 주석을 풀어주세요 -->
<!-- ![Architecture](docs/images/architecture.png) -->

### 핵심 흐름

- **웨이팅 등록**  
  Redis Lua로 중복 등록, 정원 초과, 대기번호 발급을 원자적으로 처리한 뒤 Worker가 DB에 비동기 반영합니다.

- **예약 생성**  
  Redis Lua로 슬롯 정원, 동일 슬롯 중복, 같은 매장·같은 날짜 중복 예약을 검증한 뒤 Worker가 DB에 비동기 반영합니다.

- **순번 조회**  
  프론트엔드가 `GET /api/v1/waitings/me`를 적응형 폴링 방식으로 호출합니다. 앞 팀 수가 줄어들수록 폴링 주기를 짧게 가져가고, 입장·취소 상태가 되면 폴링을 중지합니다.

- **점주 호출 알림**  
  점주가 손님을 호출하면 `WAITING_CALLED` 타입의 알림을 생성합니다. 실시간 알림 타입만 Redis Pub/Sub 채널로 publish하고, SSE 연결이 살아 있는 API Pod에서 사용자에게 `notification` 이벤트로 push합니다.

- **관측 및 장애 대응**  
  Micrometer + Prometheus로 API 응답 시간, HikariCP 커넥션 획득 시간, Redis 큐 pending/processing/dead-letter, Worker 처리 지연을 수집하고 Grafana에서 확인할 수 있도록 구성했습니다.

---

## 🛠️ 기술 스택

### Backend

![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-6DB33F?logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?logo=springsecurity&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-RDS-4479A1?logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-Queue%2FCache%2FPubSub-DC382D?logo=redis&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-Migration-CC0200?logo=flyway&logoColor=white)

- Spring Boot 3.5.7
- Spring Web / Data JPA / Data Redis / Security / Validation / Actuator
- JWT 인증(`jjwt`), Refresh Token Redis 저장
- Redis Lua Script 기반 웨이팅·예약 원자 처리
- SSE + Redis Pub/Sub 기반 실시간 호출 알림
- AWS S3 Presigned URL 기반 이미지 업로드
- Micrometer + Prometheus (`/actuator/prometheus`)

### Frontend

![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)

- React 19 + TypeScript
- React Router 기반 SPA 라우팅
- Vite 빌드
- production 환경에서 S3 + CloudFront 정적 배포

### Infra / DevOps

![AWS](https://img.shields.io/badge/AWS-EKS-FF9900?logo=amazonaws&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-EKS-326CE5?logo=kubernetes&logoColor=white)
![Argo CD](https://img.shields.io/badge/Argo%20CD-GitOps-EF7B4D?logo=argo&logoColor=white)
![Terraform](https://img.shields.io/badge/Terraform-IaC-7B42BC?logo=terraform&logoColor=white)

- EKS 기반 API / Worker Pod 운영
- KEDA 기반 Worker 스케일링
- Karpenter 기반 노드 오토스케일링
- Argo CD GitOps 배포
- Terraform IaC로 VPC, EKS, RDS, ElastiCache, S3, IAM 구성
- Prometheus / Grafana 기반 모니터링

---

## 📂 디렉터리 구조

```text
team6-nowait-app/
├── backend/                # Spring Boot API + Worker
│   ├── src/main/java/com/nowait/
│   │   ├── domain/         # auth · user · restaurant · waiting · reservation · slot
│   │   │                   # notification · owner · favorite · review
│   │   └── global/         # security · sse · exception · config
│   ├── src/main/resources/ # application.yaml · db/migration · seed data
│   ├── docs/worker/        # Worker 구조 문서
│   ├── loadtest/           # k6 부하 테스트 스크립트
│   ├── Dockerfile
│   └── pom.xml
├── frontend/               # React + Vite SPA
│   ├── src/                # components · pages · lib
│   ├── vite.config.ts
│   └── package.json
├── bin/
└── .github/workflows/      # CI/CD 워크플로우
```

---

## 🚀 로컬 실행

### 사전 준비

- Java 17
- Maven
- Node.js 20+
- MySQL 8.x
- Redis 7.x

### Backend API 실행

```bash
cd backend
mvn spring-boot:run
```

기본 포트는 `8080`입니다. 주요 환경변수는 아래와 같습니다.

```bash
DB_URL=jdbc:mysql://localhost:3306/nowait?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=nowait
DB_PASSWORD=nowait1234
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=local-dev-jwt-secret-key-must-be-at-least-32-bytes
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

### Worker 로컬 실행

웨이팅/예약 Worker는 Spring Profile로 분리되어 있습니다.

```bash
# 웨이팅 Worker
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=waiting-worker

# 예약 Worker
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=reservation-worker
```

### Frontend 실행

```bash
cd frontend
npm install
npm run dev
```

Vite dev 서버 기본 포트는 `5173`입니다.

---

## 🔁 CI/CD 개요

| 워크플로우 | 트리거 | 역할 |
|---|---|---|
| `backend-ci.yml` | PR → `main` | `backend/**` 변경 시 Maven 테스트 수행 |
| `frontend-ci.yml` | PR → `main` | `frontend/**` 변경 시 lint + build 수행 |
| `backend-dev-deploy.yml` | push `main` + `backend/**` | 백엔드 이미지 빌드 → ECR push → config repo의 dev values 태그 갱신 |
| `backend-prod-promote.yml` | 수동 dispatch | 검증된 dev 이미지 태그를 prod 태그로 retag → prod values 갱신 |
| `frontend-prod-deploy.yml` | 수동 dispatch | Vite 빌드 → prod S3 sync → CloudFront invalidation |

### 핵심 설계 결정

1. **dev = 자동, prod = 수동**  
   dev는 `main` 머지 시 자동 배포하고, prod는 항상 사람이 `workflow_dispatch`로 승인·실행합니다.

2. **prod 백엔드는 main 재빌드가 아니라 dev 이미지 retag 승격**  
   production에는 새로 빌드한 이미지가 아니라 dev에서 검증한 동일 이미지를 올립니다.

3. **프론트 prod는 수동 dispatch 전용**  
   dev 프론트엔드는 로컬에서 dev ALB API를 바라보며 테스트합니다. 따라서 push 자동 트리거로 prod에 바로 반영되지 않도록 production 배포는 수동으로 제한했습니다.

4. **CI required check pending 문제 회피**  
   GitHub Actions의 `paths:` 트리거를 required check에 직접 걸면, 해당 경로를 수정하지 않은 PR에서 체크가 pending으로 남을 수 있습니다. 이를 피하기 위해 `dorny/paths-filter` 게이트와 항상 실행되는 result job 패턴을 사용했습니다.

---

## 🌿 브랜치 전략

- 단일 `main` 트렁크를 기준으로 운영합니다.
- 기능 작업은 짧은 토픽 브랜치를 만들고 PR을 통해 `main`으로 머지합니다.
- 백엔드/프론트엔드 분리는 브랜치가 아니라 경로(`backend/`, `frontend/`)로 관리합니다.
- 과거에는 백엔드와 프론트엔드 브랜치가 분리되어 있었지만, drift와 배포 워크플로우 불일치 문제를 줄이기 위해 모노레포 구조로 통합했습니다.

---

## 🔗 관련 레포지토리

| 레포지토리 | 설명 |
|---|---|
| **[team6-nowait-app](https://github.com/CLD-05/team6-nowait-app)** | 애플리케이션 모노레포 (Backend + Frontend) — *현재 레포* |
| **[team6-nowait-config](https://github.com/CLD-05/team6-nowait-config)** | Argo CD Application + Helm chart / values (GitOps 배포 대상) |
| **[team6-nowait-infra](https://github.com/CLD-05/team6-nowait-infra)** | Terraform IaC (VPC / EKS / RDS / ElastiCache / S3 / IAM 등) |

---

## 👥 팀원

| 이름 | 역할 | GitHub |
|:---:|:---:|:---:|
| 강성천 |  | [potent93](https://github.com/potent93) |
| 김유현 |  | [Containerxox](https://github.com/Containerxox) |
| 김보경 |  | [bovo22](https://github.com/bovo22) |
| 이윤범 |  | [YunB98](https://github.com/YunB98) |
| 유준영 |  | [yjy1592](https://github.com/yjy1592) |
| 김은지 |  | [namoo0515](https://github.com/namoo0515) |

<!-- 위 표의 빈칸에 이름 / 역할(예: Backend, Frontend, Infra) / GitHub 아이디를 채워주세요 -->

---

<div align="center">

**Team 6 · NoWait** 🍽️

</div>
