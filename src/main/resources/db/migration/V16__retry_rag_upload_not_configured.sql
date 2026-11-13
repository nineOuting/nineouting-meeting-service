-- dev ai-service에 사전 자료 저장소 허용(RAG_UPLOAD_ALLOWED_HOSTS)이 없어 400을 받고 거절로 굳은 자료를 다시 보낸다.
-- 이제 그 400은 설정 전으로 보고 다시 보낸다 (RagClient.isUploadNotConfigured)
UPDATE meeting_materials
SET rag_status = 'PENDING', rag_error_code = NULL, rag_attempts = 0, rag_next_attempt_at = CURRENT_TIMESTAMP
WHERE rag_status = 'FAILED' AND rag_error_code = 'rejected';
