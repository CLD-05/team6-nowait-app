# ---- build stage ----
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# 의존성 캐시 레이어 분리 (pom.xml 변경 없을 시 빌드 속도 최적화)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# 소스 복사 및 테스트 제외 빌드
COPY src ./src
RUN mvn package -DskipTests -q

# ---- runtime stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 컨테이너 보안 강화를 위한 전용 유저 생성 (root 실행 방지)
RUN addgroup -S nowait && adduser -S nowait -G nowait
USER nowait

# 빌드 스테이지에서 생성된 최종 실행 JAR 파일만 추출하여 복사
# (target 폴더 내 서브 jar 파일들과의 충돌을 방지하기 위해 구체적인 명명 패턴 사용 권장)
COPY --from=builder /app/target/*-[0-9]*.jar app.jar

# EKS 환경에서 컨테이너 메모리 제한(Cgroups)을 JVM이 올바르게 인식하도록 GC 및 힙 비율 기본 옵션 설정
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

# 확장된 JAVA_OPTS 런타임 환경변수를 동적으로 주입받아 실행할 수 있도록 sh -c 배열 형태로 선언
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]