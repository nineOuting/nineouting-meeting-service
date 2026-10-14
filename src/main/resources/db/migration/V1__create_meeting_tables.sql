-- 시각은 모두 한국 시간
CREATE TABLE meetings
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    workspace_id        BIGINT       NOT NULL,
    folder_id           BIGINT       NOT NULL,
    title               VARCHAR(100) NOT NULL,
    start_at            DATETIME(6)  NOT NULL,
    end_at              DATETIME(6)  NOT NULL,
    location            VARCHAR(100) NULL,
    owner_user_id       BIGINT       NOT NULL,
    writer_user_id      BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    previous_meeting_id BIGINT       NULL,
    started_at          DATETIME(6)  NULL,
    finished_at         DATETIME(6)  NULL,
    cancelled_at        DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_meetings_folder_start (folder_id, start_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 만든 사람도 참석자로 들어간다
CREATE TABLE meeting_attendees
(
    id         BIGINT NOT NULL AUTO_INCREMENT,
    meeting_id BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_meeting_attendees UNIQUE (meeting_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE agendas
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    meeting_id           BIGINT       NOT NULL,
    position             INT          NOT NULL,
    title                VARCHAR(100) NOT NULL,
    decision_point       VARCHAR(500) NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    pending_agenda_id    BIGINT       NULL,
    added_during_meeting BOOLEAN      NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_agendas_meeting (meeting_id, position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 폴더의 남은 안건. 같은 안건을 여러 번 넘겨도 한 줄로 유지한다
CREATE TABLE pending_agendas
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    workspace_id     BIGINT       NOT NULL,
    folder_id        BIGINT       NOT NULL,
    title            VARCHAR(100) NOT NULL,
    decision_point   VARCHAR(500) NOT NULL,
    from_meeting_id  BIGINT       NOT NULL,
    carried_at       DATETIME(6)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    taken_meeting_id BIGINT       NULL,
    PRIMARY KEY (id),
    INDEX idx_pending_agendas_folder (folder_id, status),
    INDEX idx_pending_agendas_taken (taken_meeting_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
