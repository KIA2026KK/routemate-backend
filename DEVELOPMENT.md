# DDD 및 브랜치 규칙

auth, user, trip, matching, room, voice를 기능별 경계로 유지합니다.
각 기능은 필요에 따라 domain, application, infrastructure, presentation으로 나눕니다.
domain은 비즈니스 규칙과 값 객체/애그리거트/필요한 저장소 포트를 정의합니다.
application은 유스케이스 및 트랜잭션을 조율합니다.
infrastructure는 DB·Redis·LiveKit 등 외부 구현을, presentation은 HTTP 요청/응답을 담당합니다.
외부 SDK 타입이나 HTTP DTO가 도메인으로 유입되지 않도록 합니다.
실제 비즈니스 규칙과 외부 연동에 필요한 계층만 구성하며 불필요한 빈 계층을 만들지 않습니다.

## 작업 흐름

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c feat/trip-registration
# API 하나 구현 → 테스트 → 커밋 → 푸시
```

1. 기능/API 브랜치는 항상 최신 develop에서 분리합니다.
2. 기능 PR의 대상은 develop이며 Backend checks 통과 후 머지합니다.
3. develop 푸시의 검증이 통과한 뒤 별도 develop → main PR을 생성합니다.
4. 승격 PR 검증까지 통과한 뒤 main에 머지합니다.

main 대상 PR의 출발 브랜치는 CI에서 develop으로 제한합니다.
GitHub에서 main/develop 보호와 Backend checks 필수 검사를 설정하면 우회를 차단할 수 있습니다.
이번 변경은 작업 흐름과 CI를 추가하며 저장소 관리자 권한 설정은 변경하지 않습니다.
