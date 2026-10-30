-- STT 연동 (docs/services/ai-integration.md 2장). 시각은 모두 한국 시간

-- 음성 연결용 1회용 입장권 (2.1). 회의록 작성자에게 30초짜리로 주고, stt-service가 확인하면 지운다
CREATE TABLE stt_tickets
(
    token      VARCHAR(64)  NOT NULL,
    meeting_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (token)
);
CREATE INDEX idx_stt_tickets_expires ON stt_tickets (expires_at);

-- 전사 텍스트 한 줄 (2.2). seq는 줄 식별 번호일 뿐 시간 순서가 아니므로 start_ms로 정렬한다.
-- rev는 줄마다 이어지는 갱신 번호(STT 갱신·사용자 수정·삭제), sent_rev는 ai-service에 마지막으로 넘긴 rev
CREATE TABLE transcript_lines
(
    id             BIGINT       GENERATED ALWAYS AS IDENTITY,
    meeting_id     BIGINT       NOT NULL,
    seq            INT          NOT NULL,
    rev            INT          NOT NULL,
    speaker_label  VARCHAR(50)  NULL,
    text           TEXT         NOT NULL,
    start_ms       BIGINT       NOT NULL,
    end_ms         BIGINT       NULL,
    is_final       BOOLEAN      NOT NULL,
    low_confidence BOOLEAN      NOT NULL,
    kind           VARCHAR(20)  NULL,
    recovered      BOOLEAN      NOT NULL,
    deleted        BOOLEAN      NOT NULL,
    sent_rev       INT          NULL,
    updated_at     TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transcript_lines UNIQUE (meeting_id, seq)
);
CREATE INDEX idx_transcript_lines_meeting ON transcript_lines (meeting_id, start_ms);

-- 회의 진행 알림 (2.3). received_at은 백엔드가 받은 서버 시각, 같은 알림은 한 번만 남긴다
CREATE TABLE progress_events
(
    id          BIGINT       GENERATED ALWAYS AS IDENTITY,
    meeting_id  BIGINT       NOT NULL,
    event       VARCHAR(10)  NOT NULL,
    at_ms       BIGINT       NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    sent        BOOLEAN      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_progress_events UNIQUE (meeting_id, event, at_ms)
);
CREATE INDEX idx_progress_events_unsent ON progress_events (meeting_id, sent);

-- 회의마다 ai-service로 밀어 주는 상태 (2.4). 회의를 시작하면 생기고, 끝난 뒤 보낼 것이 없으면 지운다
CREATE TABLE ai_feeds
(
    meeting_id      BIGINT       NOT NULL,
    next_batch      INT          NOT NULL,
    resend_from     INT          NULL,
    failures        INT          NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (meeting_id)
);
CREATE INDEX idx_ai_feeds_next_attempt ON ai_feeds (next_attempt_at);

-- 보낸 줄 묶음. ai-service가 빠진 묶음 번호를 알려 주면(409) 그 번호부터 그대로 다시 보낸다
CREATE TABLE ai_batches
(
    id         BIGINT       GENERATED ALWAYS AS IDENTITY,
    meeting_id BIGINT       NOT NULL,
    batch      INT          NOT NULL,
    payload    TEXT         NOT NULL,
    sent_at    TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_batches UNIQUE (meeting_id, batch)
);
