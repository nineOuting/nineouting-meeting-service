-- REQ-185~187 회의 중 챗봇 (docs/services/rag-chatbot.md).

-- 사전 자료를 ai-service 검색용 저장소에 넣은 상태. 보관함 표를 따로 두지 않고 이 열로 다시 보낸다.
-- rag_status: PENDING(보낼 차례) / ACCEPTED(접수, 콜백 기다림) / READY(검색 가능) / FAILED(rag_error_code)
ALTER TABLE meeting_materials
    ADD COLUMN etag                VARCHAR(200),
    ADD COLUMN rag_status          VARCHAR(20),
    ADD COLUMN rag_error_code      VARCHAR(40),
    ADD COLUMN rag_attempts        INT NOT NULL DEFAULT 0,
    ADD COLUMN rag_next_attempt_at TIMESTAMP(6),
    ADD COLUMN rag_sent_at         TIMESTAMP(6);

-- 이미 올라간 자료도 배포 뒤 보낸다 (ETag는 보낼 때 S3에서 읽는다)
UPDATE meeting_materials
SET rag_status = 'PENDING', rag_next_attempt_at = CURRENT_TIMESTAMP
WHERE status = 'READY';

CREATE INDEX idx_meeting_materials_rag ON meeting_materials (rag_status, rag_next_attempt_at);

-- 챗봇 대화 기록. 사용자마다, 회의마다 따로 본다 (다른 참석자에게 보이지 않는다).
-- role: USER(질문) / ASSISTANT(답). status, basis, citations는 답에만 있다 (회의 중 챗봇 명세 5장)
CREATE TABLE chat_messages
(
    id         BIGINT       GENERATED ALWAYS AS IDENTITY,
    meeting_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    text       TEXT         NOT NULL,
    status     VARCHAR(20),
    basis      VARCHAR(20),
    citations  TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_chat_messages_meeting_user ON chat_messages (meeting_id, user_id, id);
