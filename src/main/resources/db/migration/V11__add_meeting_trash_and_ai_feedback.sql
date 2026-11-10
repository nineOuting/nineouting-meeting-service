-- REQ-166, REQ-143 (2026-11-09 요구사항): 회의를 지우면 바로 지우지 않고 쓰레기통에 3개월 둔 뒤 영구 삭제한다.
-- 쓰레기통에 있는 동안은 목록, 캘린더, 상세 어디에도 보이지 않는다.
ALTER TABLE meetings ADD COLUMN deleted_at TIMESTAMP(6) NULL;
ALTER TABLE meetings ADD COLUMN deleted_by_user_id BIGINT NULL;
CREATE INDEX idx_meetings_trash ON meetings (workspace_id, deleted_at) WHERE deleted_at IS NOT NULL;

-- REQ-172 AI 결과 피드백 ([정확해요], [고칠 부분 있어요]). 같은 사람이 같은 자리에 다시 누르면 고친다
CREATE TABLE ai_feedbacks
(
    id         BIGINT      GENERATED ALWAYS AS IDENTITY,
    meeting_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    target     VARCHAR(30) NOT NULL,
    rating     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_feedbacks UNIQUE (meeting_id, user_id, target)
);
