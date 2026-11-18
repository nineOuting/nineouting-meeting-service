-- REQ-191 전사 속 용어 목록 (ai-service가 회의마다 지금까지 찾은 용어 전체를 보낸다). 마지막으로 받은 것만 둔다
ALTER TABLE agenda_states
    ADD COLUMN terms_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN terms         TEXT;
