# 초기 환경 검증 — 2026-11-02

- Windows, Java 21.0.2, Docker Desktop 4.25.2 / Linux 엔진에서 검증.
- `gradlew.bat clean test bootJar --no-daemon`: BUILD SUCCESSFUL.
- 애플리케이션 통합 테스트 1개 통과, 건너뛴 테스트 없음.
- 임시 PostGIS 컨테이너에서 Flyway V1 적용 및 PostGIS_Version() 응답 확인.
- 임시 Redis 컨테이너 PING 및 랜덤 포트 HTTP health 200 / UP 확인.
- `docker compose up -d --wait`: PostGIS·Redis 모두 healthy.
- 생성한 JAR을 실행해 Compose DB에 연결하고 `GET /actuator/health` UP 확인.
- 최신 Spring Boot 4.1.2와 JUnit 5 조합은 SpringExtension NoSuchMethodError로 실패.
  JUnit 5 요구사항을 유지하도록 공식 안정 계열 3.5.16과 Gradle 8.14.3으로 조정 후 통과.
- Android 빌드, LiveKit 3인 통화, Pleos SDK 및 차량 통화 정책은 미검증.
