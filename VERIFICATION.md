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

## 익명 인증 및 목적지 등록 — 2026-11-08

- 최신 develop 기반 `feat/trip-registration`에서 API 테스트부터 작성.
- 미구현 상태의 API 테스트 13개 실패 확인 후 구현, 최종 전체 14개 테스트 통과 및 bootJar 성공.
- Flyway V2: users, auth_sessions, trip_sessions 및 사용자당 활성 운행 하나의 부분 고유 인덱스 추가.
- 256비트 익명 토큰 발급, SHA-256 해시 저장, 30일 만료 및 인증된 사용자 ID로 운행 등록.
- 실제 PostGIS에서 좌표 순서, 재시도, 목적지 갱신, 사용자 분리, 잘못된 입력·위조·만료 인증 검증.
- 동일 사용자 6개 동시 등록이 하나의 운행 ID와 행으로 처리되는지 검증.
- 로컬 Compose DB 마이그레이션 및 JAR 실행 후 HTTP health 200 확인.
- Android 에뮬레이터에서 실제 API를 호출해 등록 성공 화면, 토큰 재사용, 목적지 변경 검증.
- 세션 갱신·익명 사용자 복구, 운행 종료, 통화방·매칭 API는 후속 범위.
