# nineouting-meeting-service

회의 생성·수정·취소, 참석자·회의록 작성자, 안건·남은 안건, 폴더 내 회의 목록, 홈 캘린더, 회의 상태(시작·마무리)를 맡는 서비스.

- 설계: [nineouting-docs/docs/services/meeting-service.md](https://github.com/nineOuting/nineouting-docs/blob/main/docs/services/meeting-service.md)
- 포트: `8083`
- 1차 범위(AI와 무관한 부분) + STT 연동 백엔드 부분(음성 연결 입장권, 전사 텍스트 저장·수정, 회의 진행 알림, ai-service로 밀어 주기). 회의방 WebSocket·안건 보드·초안 요청·음성 업로드는 2차
- 폴더 권한은 workspace-service, 사용자 이름은 user-service에 조회한다 (공통 모듈 `client-common`, 회의는 ID만 저장)
- 알림 이벤트는 공통 모듈 `outbox`로 보내고, `FolderDeleted`는 `inbox`로 받는다

## 로컬 실행

필요한 것: JDK 21, Docker, 실행 중인 user-service(8081)·workspace-service(8082)

1. `~/.gradle/gradle.properties`에 `gpr.user`, `gpr.key`(read:packages 토큰)
2. `docker compose up -d` — PostgreSQL(`localhost:5436`), Kafka(`localhost:9092`), Redis(`localhost:6379`)
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
| POST | `/api/meetings/{meetingId}/pause` · `/resume` | 멈춤·재개 (회의록 작성자, 진행 중). 경과 시간은 `startedAt`·`pausedSeconds`·`pausedAt`으로 계산 |
| POST | `/api/meetings/{meetingId}/finish` | 마무리 (회의록 작성자) |
| GET | `/api/folders/{folderId}/pending-agendas` | 폴더의 남은 안건 |
| POST | `/api/meetings/{meetingId}/stt-ticket` | 음성 연결(stt-service WebSocket)용 30초 1회용 입장권 (회의록 작성자, 진행 중) |
| GET | `/api/meetings/{meetingId}/transcripts` | 전사 텍스트 (참석자, `startMs` 순) |
| PATCH | `/api/meetings/{meetingId}/transcripts/{seq}` | 확정된 줄 고치기 (회의록 작성자, 진행 중) |
| DELETE | `/api/meetings/{meetingId}/transcripts/{seq}` | 줄 지우기 (회의록 작성자, 진행 중) |
| POST | `/api/meetings/{meetingId}/room-ticket` | 회의방 WebSocket용 30초 1회용 입장권 (참석자, 진행 중) |
| WS | `/ws/meetings/{meetingId}?ticket=...` | 회의방: 전사 텍스트(`transcript`)·수정(`transcriptEdited`)·삭제(`transcriptDeleted`)·멈춤·재개(`paused`·`resumed`)·AI 일시 중단(`aiStatus`)·마무리(`finished`)를 실시간으로 받는다 |

stt-service가 클러스터 안에서 부르는 주소 (Gateway가 외부로 열지 않는다):

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/internal/stt-tickets/{ticket}/consume` | 입장권 확인 (한 번 쓰면 지움) → `meetingId`, `userId` |
| POST | `/internal/meetings/{meetingId}/transcripts` | 전사 텍스트 저장 (`rev`가 클 때만, 확정된 줄은 그대로) |
| POST | `/internal/meetings/{meetingId}/events` | 회의 진행 알림 (`start`·`pause`·`resume`·`end`, 서버 시각을 붙여 저장) |

ai-service로는 회의 알림을 바로, 줄 묶음을 10초마다 밀어 준다 (`POST /internal/meetings/{id}/events`, `POST /internal/meetings/{id}/batches`, `meetingId`는 문자열). 종료 알림은 남은 줄을 보낸 뒤에 보낸다. `AI_SERVICE_URL`이 비어 있으면 보내지 않는다.

회의 시각은 한국 시간으로 주고받는다 (`"2026-10-20T14:00:00"`).

## 이벤트

| 방향 | 토픽 | 내용 |
|------|------|------|
| 보냄 | `meeting.created` · `meeting.updated` · `meeting.cancelled` | 참석자 알림 (notification) |
| 보냄 | `meeting.moved` | 회의 폴더 이동 (minutes) |
| 보냄 | `meeting.finished` | 회의 마무리 → 회의록 만들기 (minutes) |
| 받음 | `workspace.folder-deleted` | 폴더 안의 회의·남은 안건 삭제 |
| 받음 | `minutes.review-requested` · `minutes.confirmed` | 회의 목록·캘린더의 회의록 상태(`minutesStatus`: 검토 중·확정) |

## 테스트

```bash
./gradlew test   # Testcontainers로 PostgreSQL·Kafka를 띄운다. user·workspace·ai-service는 가짜로 바꾼다
```

## 환경 변수

| 이름 | 기본값 | 설명 |
|------|--------|------|
| `SERVER_PORT` | `8083` | |
| `DB_URL` | `jdbc:postgresql://localhost:5436/meeting_db` | |
| `DB_USERNAME` / `DB_PASSWORD` | `meeting_service` | |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | |
| `JWT_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | access 토큰 검증 공개키 |
| `USER_SERVICE_URL` | `http://localhost:8081` | 참석자 이름 조회 |
| `WORKSPACE_SERVICE_URL` | `http://localhost:8082` | 폴더 권한·멤버·목록 조회 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | 회의방 메시지를 여러 인스턴스에 나눠 보내는 Pub/Sub |
| `AI_SERVICE_URL` | (비움) | 회의 알림·줄 묶음을 밀어 줄 ai-service 주소. 비우면 보내지 않는다 |
