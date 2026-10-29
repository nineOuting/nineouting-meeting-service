-- 안건 보드 (REQ-085~097, 124~126). ai-service docs/api/agenda-*.md
-- ai-service가 보낸 보드는 모양이 자주 늘어나므로 JSON 그대로 두고, 백엔드가 쓰는 값만 칸으로 뺀다.

-- 회의마다 하나
CREATE TABLE agenda_states
(
    meeting_id             BIGINT       NOT NULL,
    -- 백엔드 → ai-service 안건 목록 (PUT /agendas). 바뀌면 dirty, 보내면 false
    agendas_version        BIGINT       NOT NULL DEFAULT 0,
    agendas_dirty          BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 고치기·무시·끝내기 요청의 rev. 회의 안에서 계속 커진다
    edit_rev               INT          NOT NULL DEFAULT 0,
    -- ai-service → 백엔드 줄별 분류 (PUT /agenda-classification)
    classification_version BIGINT       NOT NULL DEFAULT 0,
    current_agenda_id      BIGINT       NULL,
    -- ai-service → 백엔드 안건 보드 (PUT /agenda-board)
    board_version          BIGINT       NOT NULL DEFAULT 0,
    board                  TEXT         NULL,
    board_received_at      TIMESTAMP(6) NULL,
    -- 새 안건 제안(SUGGEST)을 처음 본 시각. 30초 안에 등록·무시가 없으면 무시로 보낸다 (REQ-091)
    off_topic_suggested_at TIMESTAMP(6) NULL,
    off_topic_detected_at  VARCHAR(40)  NULL,
    -- 회의가 끝난 뒤 마지막 보드의 todaySummary(final). 점검에서 고친 내용을 여기에 둔다 (REQ-097)
    today_summary          TEXT         NULL,
    PRIMARY KEY (meeting_id)
);
CREATE INDEX idx_agenda_states_off_topic ON agenda_states (off_topic_suggested_at) WHERE off_topic_suggested_at IS NOT NULL;

-- 전사 줄마다 어느 안건인지 (ai-service 분류, 회의록 작성자가 고치면 corrected)
CREATE TABLE line_agendas
(
    meeting_id BIGINT  NOT NULL,
    seq        INT     NOT NULL,
    agenda_id  BIGINT  NULL,
    corrected  BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (meeting_id, seq)
);

-- ai-service로 보낼 고치기·무시·끝내기. ai-service가 재시작하면 replay인 것을 모두 다시 보낸다
CREATE TABLE agenda_edits
(
    id         BIGINT       GENERATED ALWAYS AS IDENTITY,
    meeting_id BIGINT       NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    item_key   VARCHAR(200) NOT NULL,
    payload    TEXT         NOT NULL,
    replay     BOOLEAN      NOT NULL,
    sent       BOOLEAN      NOT NULL DEFAULT FALSE,
    resend     BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_agenda_edits UNIQUE (meeting_id, kind, item_key)
);
CREATE INDEX idx_agenda_edits_pending ON agenda_edits (meeting_id) WHERE NOT sent;
