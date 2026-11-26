-- REQ-123 대기 화면에서 고른 회의록 양식의 이름 (고를 때 프론트가 보낸 값). 다른 참석자에게 보여 준다 (SCRUM-419)
-- 개인 양식은 다른 참석자의 양식 목록에 없어서 ID만으로는 이름을 알 수 없다
ALTER TABLE meetings ADD COLUMN template_name VARCHAR(100);
