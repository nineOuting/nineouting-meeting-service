-- REQ-123 회의 대기 화면에서 고른 회의록 양식 (minutes-service의 양식 ID). 회의를 마무리하면 이 양식으로 회의록을 만든다
ALTER TABLE meetings ADD COLUMN template_id BIGINT;
