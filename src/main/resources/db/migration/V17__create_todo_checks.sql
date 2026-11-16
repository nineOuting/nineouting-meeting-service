-- 회의 중 할 일 완료 체크 (디자인 Version02 Live 할 일 표). 할 일은 ai-service 안건 보드가 만든 항목이라
-- 항목 번호(item_id)로 체크한 것만 따로 둔다. 체크를 풀면 행을 지운다
CREATE TABLE todo_checks
(
    meeting_id BIGINT       NOT NULL,
    item_id    VARCHAR(100) NOT NULL,
    checked_by BIGINT       NOT NULL,
    checked_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (meeting_id, item_id)
);
