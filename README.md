# RouteMate Backend

Java 21 / Spring Boot 3.5.16 / Gradle Wrapper 8.14.3 기반 초기 서버입니다.
PostgreSQL 17 + PostGIS 3.5, Redis 7.4, Flyway, JUnit 5, Testcontainers를 사용합니다.
공식 Spring Initializr로 생성 후 JUnit 5 호환성을 위해 안정 3.5 계열로 조정했습니다.
호환성: https://docs.spring.io/spring-boot/3.5/system-requirements.html

## 실행 (PowerShell)

Java 21, Linux 컨테이너 모드 Docker Desktop을 설치하고 Java와 Docker를 PATH에 추가하세요.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
Copy-Item .env.example .env
# .env의 DB_PASSWORD와 REDIS_PASSWORD에 서로 다른 로컬 비밀번호를 입력
docker compose up -d --wait
.\gradlew.bat bootRun
```

다른 터미널에서 `Invoke-RestMethod http://localhost:8080/actuator/health`를 실행하세요.
DB와 Redis가 정상 연결되면 status는 UP입니다. .env는 Git에서 제외됩니다.
환경 변수는 .env보다 우선합니다. .env에는 단순 영문/숫자 값을 권장합니다.
기존 DB 볼륨의 비밀번호는 .env 변경만으로 바뀌지 않습니다.

## 검증과 종료

```powershell
.\gradlew.bat test bootJar
docker compose down
```

테스트는 임시 PostGIS·Redis 컨테이너를 생성해 Flyway 적용, PostGIS 확장,
Redis PING 및 실제 HTTP health 응답을 확인합니다. Docker가 없으면 실패합니다.
테스트는 로컬 .env를 읽지 않습니다. JAR은 build/libs에 생성됩니다.
macOS/Linux에서는 `./gradlew`를 사용하세요.

## 구조

`src/main/java/com/routemate` 아래 auth, user, trip, matching, room, voice, common은
향후 기능별 패키지입니다. 비즈니스 기능은 아직 없습니다.
`src/main/resources/db/migration`에 Flyway SQL을 추가합니다.
JPA ddl-auto는 validate입니다. V1은 PostGIS 확장만 활성화합니다.
운영 DB에서는 관리자가 확장을 설치하고 계정 권한을 분리하세요.

3인 음성 통화, Pleos 목적지 조회, 차량 통화 정책은 이번 검증 범위 밖입니다.
추천 커밋: `chore: bootstrap RouteMate backend infrastructure`
