-- 회의 중 요약 (ai-service → meeting-service, docs/services/ai-integration.md 2.5).
-- 회의마다 가장 최신 하나만 둔다. version은 ai-service가 밀리초 이상으로 매기므로 BIGINT
CREATE TABLE live_summaries
(
    meeting_id  BIGINT       NOT NULL,
    version     BIGINT       NOT NULL,
    at_ms       BIGINT       NOT NULL,
    is_final    BOOLEAN      NOT NULL,
    summary     TEXT         NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (meeting_id)
);
