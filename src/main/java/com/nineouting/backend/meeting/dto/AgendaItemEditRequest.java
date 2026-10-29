package com.nineouting.backend.meeting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 안건 보드 항목 고치기 (ai-service agenda-summary-contract.md, agenda-opinions-contract.md).
 * <ul>
 *   <li>summary: text, evidenceSeqs (비어 있으면 안 된다)</li>
 *   <li>opinions: text, kind(OPINION / EVIDENCE / CONCERN / QUESTION), evidenceSeqs (비어 있으면 안 된다)</li>
 *   <li>todos: task, evidenceSeqs, assigneeName·assigneeUserId·dueText (선택)</li>
 * </ul>
 * evidenceSeqs는 보드에서 받은 근거를 그대로 보낸다 (ai-service가 재시작해도 고친 항목을 되살리려면 필요하다).
 */
public record AgendaItemEditRequest(
        @NotNull(message = "agendaId를 보내 주세요")
        Long agendaId,
        @Size(max = 1000, message = "1000자 이하로 입력해 주세요")
        String text,
        String kind,
        List<Integer> evidenceSeqs,
        @Size(max = 500, message = "500자 이하로 입력해 주세요")
        String task,
        @Size(max = 50, message = "50자 이하로 입력해 주세요")
        String assigneeName,
        Long assigneeUserId,
        @Size(max = 100, message = "100자 이하로 입력해 주세요")
        String dueText
) {
}
