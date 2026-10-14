# nineouting-meeting-service

회의 생성·수정·취소, 참석자·회의록 작성자, 안건·남은 안건, 폴더 내 회의 목록, 홈 캘린더, 회의 상태(시작·마무리)를 맡는 서비스.

- 설계: [nineouting-docs/docs/services/meeting-service.md](https://github.com/nineOuting/nineouting-docs/blob/main/docs/services/meeting-service.md)
- 포트: `8083`
- 현재 1차 범위(AI와 무관한 부분). 회의방 WebSocket·전사 텍스트·안건 보드·AI 연동·음성 업로드는 2차
- 폴더 권한은 workspace-service, 사용자 이름은 user-service에 조회한다 (회의는 ID만 저장)
- 알림 이벤트는 공통 모듈 `outbox`로 보내고, `FolderDeleted`는 `inbox`로 받는다

## 로컬 실행

필요한 것: JDK 21, Docker, 실행 중인 user-service(8081)·workspace-service(8082)

1. `~/.gradle/gradle.properties`에 `gpr.user`, `gpr.key`(read:packages 토큰)
2. `docker compose up -d` — MySQL(`localhost:3310`), Kafka(`localhost:9092`)
3. `./gradlew bootRun --args='--spring.profiles.active=local'`

## API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/folders/{folderId}/meetings` | 회의 생성 (이어서 만들기 포함) |
| GET | `/api/folders/{folderId}/meetings?page=0&size=20` | 폴더 내 회의 목록 (시작 시각 최신순, 취소 제외) |
| GET | `/api/meetings/calendar?from=2026-11-01&to=2026-11-30` | 홈 캘린더 (최대 42일) |
| GET | `/api/meetings/{meetingId}` | 회의 정보·참석자·안건 |
| PUT | `/api/meetings/{meetingId}` | 수정 (만든 사람, 시작 전) |
| POST | `/api/meetings/{meetingId}/cancel` | 취소 (만든 사람, 시작 전) |
| PUT | `/api/meetings/{meetingId}/folder` | 다른 폴더로 옮기기 (만든 사람) |
| POST | `/api/meetings/{meetingId}/start` | 시작 (참석자) |
| POST | `/api/meetings/{meetingId}/agendas` | 회의 중 안건 추가 (회의록 작성자) |
| POST | `/api/meetings/{meetingId}/carry-over` | 고른 안건을 남은 안건으로 (회의록 작성자) |
| POST | `/api/meetings/{meetingId}/finish` | 마무리 (회의록 작성자) |
| GET | `/api/folders/{folderId}/pending-agendas` | 폴더의 남은 안건 |

회의 시각은 한국 시간으로 주고받는다 (`"2026-10-20T14:00:00"`).

## 이벤트

| 방향 | 토픽 | 내용 |
|------|------|------|
| 보냄 | `meeting.created` · `meeting.updated` · `meeting.cancelled` | 참석자 알림 (notification) |
| 보냄 | `meeting.moved` | 회의 폴더 이동 (minutes) |
| 받음 | `workspace.folder-deleted` | 폴더 안의 회의·남은 안건 삭제 |

## 테스트

```bash
./gradlew test   # Testcontainers로 MySQL·Kafka를 띄운다. user·workspace-service는 가짜로 바꾼다
```

## 환경 변수

| 이름 | 기본값 | 설명 |
|------|--------|------|
| `SERVER_PORT` | `8083` | |
| `DB_URL` | `jdbc:mysql://localhost:3310/meeting_db` | |
| `DB_USERNAME` / `DB_PASSWORD` | `meeting_service` | |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | |
| `JWT_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | access 토큰 검증 공개키 |
| `USER_SERVICE_URL` | `http://localhost:8081` | 참석자 이름 조회 |
| `WORKSPACE_SERVICE_URL` | `http://localhost:8082` | 폴더 권한·멤버·목록 조회 |
