package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 점검에서 고친 오늘 회의 요약에 고친 표시를 붙인다 (ai-service minutes-draft-contract.md 5.3).
 * <ul>
 *   <li>저장된 요약과 비교해 글이 바뀐 항목은 {@code edited: true}. 한 번 켜진 표시는 그대로 둔다</li>
 *   <li>근거 전사 줄({@code evidenceSeqs})은 사람이 바꿀 수 없어 저장된 값을 쓴다</li>
 *   <li>저장된 요약에 없는 항목은 점검에서 새로 넣은 것: {@code u-} 번호를 붙이고 근거를 비운다</li>
 *   <li>결정할 사항은 안건 목록에서 오므로 새로 넣을 수 없다 (번호가 저장된 요약에 없으면 거절)</li>
 * </ul>
 */
final class TodaySummaryRevision {

    /** 점검에서 넣은 항목 번호의 접두어. ai-service 번호와 겹치지 않는다 */
    private static final String USER_ID_PREFIX = "u-";
    private static final Pattern USER_ID = Pattern.compile("^u-(\\d+)$");

    /** 종류마다 사람이 고칠 수 있는 칸. 이 칸이 바뀌면 고친 것으로 본다 */
    private static final Map<String, List<String>> CONTENT_FIELDS = new LinkedHashMap<>();

    static {
        CONTENT_FIELDS.put("summary", List.of("text"));
        CONTENT_FIELDS.put("opinions", List.of("text", "kind"));
        CONTENT_FIELDS.put("todos", List.of("task", "assigneeName", "assigneeStatus", "assigneeUserId", "dueText", "unclear"));
    }
    private static final List<String> DECISION_FIELDS = List.of("status", "decisionText");

    private TodaySummaryRevision() {
    }

    /**
     * @param saved   저장된 요약 (회의 끝 요약 또는 앞서 고친 값)
     * @param revised 작성자가 보낸 요약 {agendas[...]}. 이 객체를 고쳐 돌려준다
     */
    static JsonNode mark(JsonNode saved, ObjectNode revised) {
        Map<String, JsonNode> savedAgendas = new HashMap<>();
        Map<String, JsonNode> savedItems = new HashMap<>();
        int[] lastUserId = {0};
        for (JsonNode agenda : saved.path("agendas")) {
            String agendaId = agenda.path("agendaId").asString("");
            savedAgendas.put(agendaId, agenda);
            for (String kind : CONTENT_FIELDS.keySet()) {
                for (JsonNode item : agenda.path(kind)) {
                    String id = item.path("id").asString("");
                    savedItems.put(kind + "/" + id, item);
                    lastUserId[0] = Math.max(lastUserId[0], userNumber(id));
                }
            }
        }
        for (JsonNode node : revised.path("agendas")) {
            if (!(node instanceof ObjectNode agenda)) {
                throw invalid("agendas의 항목은 객체여야 합니다");
            }
            JsonNode savedAgenda = savedAgendas.get(agenda.path("agendaId").asString(""));
            if (savedAgenda == null) {
                throw invalid("저장된 요약에 없는 안건입니다: " + agenda.path("agendaId"));
            }
            markDecisionPoints(savedAgenda, agenda);
            for (Map.Entry<String, List<String>> entry : CONTENT_FIELDS.entrySet()) {
                for (JsonNode itemNode : array(agenda, entry.getKey())) {
                    if (!(itemNode instanceof ObjectNode item)) {
                        throw invalid(entry.getKey() + "의 항목은 객체여야 합니다");
                    }
                    JsonNode before = savedItems.get(entry.getKey() + "/" + item.path("id").asString(""));
                    if (before == null || item.path("id").asString("").isEmpty()) {
                        item.put("id", USER_ID_PREFIX + (++lastUserId[0]));
                        item.set("evidenceSeqs", item.arrayNode());
                        item.put("edited", true);
                        item.put("needsCheck", false);
                    } else {
                        keepEvidence(before, item, entry.getValue());
                    }
                }
            }
        }
        return revised;
    }

    private static void markDecisionPoints(JsonNode savedAgenda, ObjectNode agenda) {
        Map<String, JsonNode> savedPoints = new HashMap<>();
        for (JsonNode point : savedAgenda.path("decisionPoints")) {
            savedPoints.put(point.path("decisionPointId").asString(""), point);
        }
        for (JsonNode pointNode : array(agenda, "decisionPoints")) {
            if (!(pointNode instanceof ObjectNode point)) {
                throw invalid("decisionPoints의 항목은 객체여야 합니다");
            }
            JsonNode before = savedPoints.get(point.path("decisionPointId").asString(""));
            if (before == null) {
                throw invalid("저장된 요약에 없는 결정할 사항입니다: " + point.path("decisionPointId"));
            }
            keepEvidence(before, point, DECISION_FIELDS);
        }
    }

    /** 근거는 저장된 값으로 두고, 고칠 수 있는 칸이 바뀌었으면 고친 표시를 켠다 */
    private static void keepEvidence(JsonNode before, ObjectNode item, List<String> contentFields) {
        boolean changed = contentFields.stream().anyMatch(field -> !before.path(field).equals(item.path(field)));
        item.set("evidenceSeqs", before.path("evidenceSeqs").isArray() ? before.path("evidenceSeqs").deepCopy() : item.arrayNode());
        item.put("edited", before.path("edited").asBoolean(false) || changed);
        item.put("needsCheck", before.path("needsCheck").asBoolean(false));
    }

    private static ArrayNode array(ObjectNode agenda, String field) {
        JsonNode value = agenda.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return agenda.putArray(field);
        }
        if (!(value instanceof ArrayNode array)) {
            throw invalid(field + "는 배열이어야 합니다");
        }
        return array;
    }

    private static int userNumber(String id) {
        Matcher matcher = USER_ID.matcher(id);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
    }
}
