-- 회의 멈춤·재개 (REQ-119). paused_at: 멈춘 시각(멈춰 있지 않으면 NULL), paused_total_ms: 끝난 멈춤 시간의 합
-- 경과 시간(REQ-074) = 지금 - started_at - paused_total_ms - (멈춰 있으면 지금 - paused_at)
ALTER TABLE meetings
    ADD COLUMN paused_at       TIMESTAMP(6) NULL,
    ADD COLUMN paused_total_ms BIGINT       NOT NULL DEFAULT 0;
