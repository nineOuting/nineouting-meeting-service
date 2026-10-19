-- 회의방 WebSocket(REQ-072, 112 실시간 중계)도 1회용 입장권을 쓰므로 음성 연결용 입장권 테이블을 함께 쓴다
ALTER TABLE stt_tickets RENAME TO connection_tickets;
ALTER INDEX idx_stt_tickets_expires RENAME TO idx_connection_tickets_expires;
ALTER TABLE connection_tickets ADD COLUMN purpose VARCHAR(10) NOT NULL DEFAULT 'STT';
ALTER TABLE connection_tickets ALTER COLUMN purpose DROP DEFAULT;
