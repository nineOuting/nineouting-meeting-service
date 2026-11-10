-- REQ-067 회의 사전 자료. 파일은 S3(meeting-materials/)에 두고 여기에는 위치와 이름만 남긴다.
-- 업로드 주소를 받은 뒤(UPLOADING) 브라우저가 다 올렸다고 알리면 서버가 크기를 확인하고 READY로 바꾼다.
CREATE TABLE meeting_materials
(
    id               BIGINT       GENERATED ALWAYS AS IDENTITY,
    meeting_id       BIGINT       NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    storage_key      VARCHAR(300) NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    uploaded_by      BIGINT       NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_meeting_materials_key UNIQUE (storage_key)
);
CREATE INDEX idx_meeting_materials_meeting ON meeting_materials (meeting_id, id);
