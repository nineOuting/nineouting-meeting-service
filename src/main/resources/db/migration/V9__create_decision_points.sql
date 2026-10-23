-- 결정할 사항은 안건마다 여러 개 (2026-10-23 결정, REQ-066·086·124~126). 칸당 200자, 안건당 최대 5개
-- 회의 안건의 결정할 사항은 ai-service 안건 보드가 decisionPointId로 가리키므로 따로 번호를 둔다
CREATE TABLE agenda_decision_points
(
    id        BIGINT       GENERATED ALWAYS AS IDENTITY,
    agenda_id BIGINT       NOT NULL REFERENCES agendas (id) ON DELETE CASCADE,
    position  INT          NOT NULL,
    content   VARCHAR(200) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_agenda_decision_points_agenda ON agenda_decision_points (agenda_id, position);

-- 남은 안건의 결정할 사항 (다음 회의로 불러갈 때 그대로 옮긴다)
CREATE TABLE pending_agenda_decision_points
(
    pending_agenda_id BIGINT       NOT NULL REFERENCES pending_agendas (id) ON DELETE CASCADE,
    position          INT          NOT NULL,
    content           VARCHAR(200) NOT NULL,
    PRIMARY KEY (pending_agenda_id, position)
);

-- 지금까지의 결정할 사항 한 칸을 첫 번째 사항으로 옮긴다 (200자를 넘는 부분은 자른다)
INSERT INTO agenda_decision_points (agenda_id, position, content)
SELECT id, 0, LEFT(decision_point, 200) FROM agendas;
INSERT INTO pending_agenda_decision_points (pending_agenda_id, position, content)
SELECT id, 0, LEFT(decision_point, 200) FROM pending_agendas;

ALTER TABLE agendas DROP COLUMN decision_point;
ALTER TABLE pending_agendas DROP COLUMN decision_point;
