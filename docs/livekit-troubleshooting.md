# LiveKit 토큰 API 503 확인

`/actuator/health`의 UP은 DB·Redis·애플리케이션 상태이며 LiveKit 접속 성공을 의미하지 않는다.
실제 방 참가 상태에서 `POST /api/v1/voice/token`을 요청한 시각의 **Render 서비스 런타임 Logs**를 확인한다.
Docker 이미지 빌드·서비스 시작 로그만으로는 음성 오류 원인을 알 수 없다.

백엔드는 클라이언트에는 기존 `503 VOICE_UNAVAILABLE`을 반환하고 서버 로그에 다음 항목만 남긴다.
설정 값, API Key/Secret, JWT, Authorization 헤더, LiveKit 응답 본문과 예외 메시지는 기록하지 않는다.

```text
LiveKit request failed: method=CreateRoom reason=HTTP_ERROR httpStatus=401 diagnostic=none
```

| reason | 확인할 사항 |
| --- | --- |
| MISSING_CONFIGURATION | diagnostic에 표시된 환경 변수 이름의 누락·빈 값 확인 |
| INVALID_URL | SERVER_URL은 http/https, PUBLIC_URL은 ws/wss. 계정 정보·query·fragment·잘못된 공백 확인 |
| HTTP_ERROR, 401 | 백엔드가 보낸 인증 요청이 거부됨. 해당 프로젝트의 Key·Secret 한 쌍과 저장·재배포 여부 확인 |
| HTTP_ERROR, 403 | 프로젝트 접근 정책·권한 확인. 키 오류라고 단정하지 않음 |
| HTTP_ERROR, 그 외 | HTTP 상태 코드로 경로·사용량 제한·상위 서버 장애 확인 |
| TIMEOUT | 관리 API의 5초 응답 제한 초과. 네트워크·상위 서비스 상태 확인 |
| IO_ERROR | diagnostic의 예외 클래스에 따라 DNS·연결·TLS 문제 확인 |
| INVALID_RESPONSE | 성공 상태의 응답이 JSON으로 해석되지 않음. 실제 관리 API 주소인지 확인 |
| SIGNING_ERROR / INTERRUPTED / UNEXPECTED_ERROR | 해당 시각의 서버 상태와 diagnostic 예외 클래스 확인 |

키·비밀번호를 로그 확인용으로 출력하지 않는다. 로그를 공유할 때는 위 진단 한 줄이면 된다.
같은 접두어 `rmv-`의 미디어 정리를 수행하므로 RouteMate 전용 LiveKit 프로젝트를 사용한다.

근거: [LiveKit RoomService API](https://docs.livekit.io/reference/other/roomservice-api/).
