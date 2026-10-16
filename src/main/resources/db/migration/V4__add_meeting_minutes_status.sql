-- 회의록 상태 (REQ-046 "검토 중·확정"). minutes-service 이벤트로 바뀐다
ALTER TABLE meetings
    ADD COLUMN minutes_status VARCHAR(20) NULL;
