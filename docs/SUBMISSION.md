# RouteMate 제출 준비

팀: 노래말고 사람들과 함께. 대표 제출 저장소는 [Android](https://github.com/KIA2026KK/routemate-pleos-app)이며
이 저장소는 API·DB 마이그레이션·서버 배포 소스로 별도 유지합니다.
APK 위치·서명·테스트 결과·공모전 체크리스트는 Android 루트 README와 `docs/`를 참고합니다.

2026-11-15 Gitleaks 8.30.1 기본 규칙으로 현재 추적/신규 제출 파일과 접근 가능한 전체 Git ref 이력을 검사하여 탐지 0건.
실제 비밀값 출력, 기록 재작성, 강제 push, 인증정보 교체는 수행하지 않았습니다.
검사 도구의 탐지 범위에 한하며 공개 전 GitHub Secret scanning도 확인합니다.

기존 main `474be96`의 Backend checks는 성공 상태입니다. 이번 준비 변경은 문서·제외 규칙만이며 API 동작 변경은 없습니다.
PostGIS 통합 테스트에는 정상 Docker 엔진이 필요합니다. 실제 클라우드 API 검증과 Docker 없는 로컬 검사 범위는 구분합니다.

현재 저장소 private. 사용자 제공 조건: **2026-11-16까지 공개 전환 후 2026-11-23까지 유지**.
사용자의 후속 요청에 따라 소스는 CI 통과 후 기능 브랜치 → develop → main 순서로 반영합니다.
Release 게시·공개 전환·접수 메일 발송은 수행하지 않습니다.
