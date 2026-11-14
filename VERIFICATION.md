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

## VIN 차량 계정 및 SDK 속성 반영 — 2026-11-08

- Vehicle SDK 2.3.2 공식 문서와 실제 AAR의 getVINInfo·capability·권한을 확인하고 API 계약에 반영.
- VIN 테스트 13개 미구현 실패 확인 후 구현, Mock 격리 테스트 추가 후 전체 28개 통과 및 bootJar 성공.
- Flyway V3: vehicles(VIN, 출처, 읽기 권한 상태) 및 운행의 차량·사용자 복합 외래키 추가.
- 동일 VIN 중복 연결·다른 계정의 접근·기존 계정의 VIN 변경 차단, 동일 설치 6개 동시 연결 검증.
- Mock 기본 거부 및 Mock/실차 식별 공간 분리, 원문 VIN 응답 마스킹 검증.
- 실제 로컬 서버와 일반 Android 에뮬레이터에서 합성 VIN 연결·운행 등록·토큰 재사용·목적지 변경 검증.
- 기존 행은 삭제하거나 VIN을 추정해 연결하지 않음. 기존 운행의 vehicle_id는 근거 없이 채우지 않음.
- SDK 신고 값만 사용하므로 소유권 검증 완료로 표시하지 않음. 재설치·다른 설치 복구에는 추가 인증 필요.
- 세부 설계 및 필드 근거: [VIN 차량 계정](docs/vin-vehicle-identity.md).

## 차량 세션 갱신 및 복구키 — 2026-11-09

- 갱신·복구 API 테스트 6개를 먼저 작성해 미구현 실패 확인 후 구현. 전체 34개 테스트 통과 및 bootJar 성공.
- Flyway V4: auth_sessions.revoked_at 및 vehicle_recovery_keys 추가. 기존 사용자·차량·운행·세션 행 보존.
- 유효 세션의 동일 토큰 30일 갱신, 365일 복구키 발급·교체, VIN·출처·키가 일치하는 기존 계정 복구 구현.
- 키는 256비트 무작위이며 해시만 저장. 만료·재발급까지 재사용 가능해 복구 응답 유실 후 재시도 지원.
- 복구 시 모든 기존 세션 폐기, 사용자·차량·운행 ID 유지, 만료 세션 복구·이전 키 거부·동시 갱신 경쟁 검증.
- 실제 로컬 JAR의 Flyway V4 적용과 일반 Android 에뮬레이터의 API 연동 확인.
  새 저장소 복구, 동일 차량·운행 ID, 이전 세션 401, 같은 키 재시도 확인. 토큰·키 원문 출력 없음.
- 실제 Pleos 차량·Devbox·에뮬레이터를 사용할 수 없어 실제 VIN 수신 성공은 미검증.
- 복구는 기존 계정 접근권 확인이며 OEM 차량 소유권 확인이 아님. ownershipVerified=false 유지.

## MVP 통화방·운행 종료·음성 권한 — 2026-11-09

- Flyway V5로 방·참가 이력·음성 세션 관계 추가. 기존 사용자·차량·운행 데이터 보존.
- 운행 조회·종료, 5/10km PostGIS 검색, 공개/비공개 방 생성, 참여·퇴장·현재 방·heartbeat 및 음성 토큰 구현.
- 차량당 한 방, 2~8명 정원, 동시 입장·중복 요청, 고정 방 중심, 비공개/가득 찬 방 제외 검증.
- 90초 참가 만료, 2시간 방 만료, 24시간 비활성 운행 만료 및 빈 방 종료 적용.
- 전체 테스트 50개 통과: 기존 34개 + 방/운행 통합 13개 + 실제 HTTP 대역 LiveKit 프로토콜 3개.
- JWT 서명·60초 만료·방별 마이크 전용 권한, 미설정/장애 시 503, 참가 권한 없는 발급 차단 검증.
- 실제 로컬 서버·PostGIS·LiveKit과 일반 Android 세 SDK 세션의 등록→검색→참여→발행/구독→퇴장→재참여→운행 종료 검증.
- 셀프호스팅 토큰 만료만으로 연결 종료를 보장하지 않아 5초 주기 참가 권한 대조·연결 정리 추가.
  LiveKit 관리 API 장애에도 DB 만료는 커밋하고 미디어 정리를 재시도하는 테스트 통과.
- 현재 MVP는 방 변경의 전역 트랜잭션 잠금을 사용하며, 대규모 처리량과 실차 오디오/네트워크 검증은 별도 범위.
- 실제 Pleos VIN·목적지 수신과 물리 3개 단말의 음질·청취는 미검증.
- 임시 PostGIS·Redis 및 별도 설치 패키지를 쓰는 독립 MVP 검증도 두 회차 연속 통과.
  테스트 종료 후 해당 서버·앱·컨테이너만 정리되고 기존 개발 인프라는 유지됨을 확인.
## 설계 점검·운행 동시성·배포 준비 — 2026-11-09

- Java 21의 `test bootJar` 성공. 60개 테스트, 실패·오류·건너뜀 0개.
- 운행 revision·오래된 요청 거부, 인증 재검증, Mock/실차 방 분리, 1km 근삿값,
  LiveKit 정리 직전 권한 확인과 IO 중 잠금 해제 회귀 검증 포함.
- Render PORT 우선순위, PG SSL 기본값, Redis TLS URL 우선순위 설정 테스트 3개 포함.
- 최종 소스의 `docker build -t routemate-backend:design-review .` 성공. Java 21 다단계 이미지, UID 10001 실행.
- 실제 Render/Firebase 자원 생성·배포는 미실시. 클라우드 DB 확장 버전·원격 TLS·음성 포트는 배포 후 확인 필요.
- [설계 점검](docs/design-review.md), [배포 안내](docs/deployment-render-firebase.md)에 남은 조건 기록.

## LiveKit 실패 진단 — 2026-11-14

- 실제 Render health 200/UP, 합성 차량·운행·방 생성 성공, 음성 토큰 API 503 재현. 검사 운행 종료 확인.
- 설정 누락 이름, 잘못된 URL, 상위 HTTP 상태, 시간 초과·IO·JSON 응답 오류를 서버 로그에서 구분하도록 보완.
  앱에는 기존 VOICE_UNAVAILABLE 응답 유지. 원격 오류 원인이 확인된 것은 아님.
- LiveKitGatewayTests 7개 통과. API Key·Secret·Authorization/JWT·응답 본문·URL 내부 자격증명을
  로그에 노출하지 않는 회귀 테스트 4개 추가.
- bootJar 생성 성공. 로컬 전체 테스트 64개 중 Docker가 필요한 54개는 엔진 연결 오류로 실패.
  전체 회귀 검증은 PR의 독립 CI 환경에서도 수행하며, 최종 결과는 PR 체크에서 확인한다.
- 실제 Render→LiveKit 인증·미디어 연결은 새 진단 로그로 원인 확인 후 재검증 필요.
- 로그 해석: [LiveKit 장애 확인](docs/livekit-troubleshooting.md).
