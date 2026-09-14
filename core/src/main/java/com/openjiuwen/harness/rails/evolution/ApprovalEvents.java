/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Helpers for evolution approval and progress output events.
 * <p>
 * Mirrors Python's {@code openjiuwen.harness.rails.evolution.approval_events} in
 * {@code openjiuwen/harness/rails/evolution/approval_events.py}.
 * </p>
 */
public final class ApprovalEvents {

    private static final String LLM_REASONING = "llm_reasoning";
    private static final String ASK_USER_QUESTION = "chat.ask_user_question";
    private static final int DEFAULT_INDEX = 0;
    private static final int CONTENT_PREVIEW_LIMIT = 1000;
    private static final int ACTION_PREVIEW_LIMIT = 10;

    private ApprovalEvents() {
    }

    public static OutputSchema buildProgressEvent(String prefix, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", prefix + " " + message + "\n");
        return new OutputSchema(LLM_REASONING, DEFAULT_INDEX, payload);
    }

    public static OutputSchema buildEvolutionProgressEvent(String railKind,
                                                           String stage,
                                                           String message,
                                                           String skillName,
                                                           String requestId,
                                                           String prefix) {
        String displayPrefix = prefix == null ? "[Evolution]" : prefix;
        Map<String, Object> evolutionMeta = new LinkedHashMap<>(
                EvolutionHostEventMeta.builder(EvolutionEventKind.PROGRESS)
                        .railKind(railKind)
                        .stage(stage)
                        .build()
                        .toPayload()
        );
        if (skillName != null) {
            evolutionMeta.put("skill_name", skillName);
        }
        if (requestId != null) {
            evolutionMeta.put("request_id", requestId);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", displayPrefix + " " + message + "\n");
        payload.put("evolution_meta", evolutionMeta);
        return new OutputSchema(LLM_REASONING, DEFAULT_INDEX, payload);
    }

    public static OutputSchema attachEvolutionMeta(OutputSchema event,
                                                   String railKind,
                                                   String signalType,
                                                   String signalSource) {
        Map<String, Object> payload = mutablePayloadMap(event);
        Map<String, Object> evolutionMeta = mutableNestedMap(payload, "evolution_meta");
        evolutionMeta.putIfAbsent("event_kind", EvolutionEventKind.APPROVAL.value());
        if (railKind != null) {
            evolutionMeta.put("rail_kind", railKind);
        }
        if (signalType != null) {
            evolutionMeta.put("signal_type", signalType);
        }
        if (signalSource != null) {
            evolutionMeta.put("source", signalSource);
        }
        return event;
    }

    public static OutputSchema buildSkillApprovalEvent(String skillName,
                                                       String requestId,
                                                       Iterable<EvolutionRecord> records,
                                                       String language,
                                                       boolean sharedRecords,
                                                       String railKind) {
        boolean english = isEn(language);
        String header = sharedRecords
                ? (english ? "Shared Experience Approval" : "在线共享经验审批")
                : (english ? "Skill Evolution Approval" : "技能演进审批");

        List<Map<String, Object>> questions = new ArrayList<>();
        for (EvolutionRecord record : records) {
            EvolutionPatch change = Objects.requireNonNull(record.getChange(), "record change is required");
            questions.add(questionPayload(
                    skillApprovalQuestion(skillName, change, english),
                    header,
                    record.getId(),
                    approvalOptions(english),
                    false
            ));
        }

        Map<String, Object> evolutionMeta = new LinkedHashMap<>(
                EvolutionHostEventMeta.builder(EvolutionEventKind.APPROVAL)
                        .railKind(railKind)
                        .skillName(skillName)
                        .requestId(requestId)
                        .source(sharedRecords ? "experience_sharing" : null)
                        .build()
                        .toPayload()
        );
        if (sharedRecords) {
            evolutionMeta.put("is_shared_records", "true");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", requestId);
        payload.put("evolution_meta", evolutionMeta);
        payload.put("questions", questions);
        return new OutputSchema(ASK_USER_QUESTION, DEFAULT_INDEX, payload);
    }

    public static OutputSchema buildSimplifyApprovalEvent(String skillName,
                                                          String requestId,
                                                          List<Map<String, Object>> actions,
                                                          String language,
                                                          String railKind) {
        boolean english = isEn(language);
        String preview = buildActionPreview(actions);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", requestId);
        payload.put("evolution_meta", new LinkedHashMap<>(
                EvolutionHostEventMeta.builder(EvolutionEventKind.APPROVAL)
                        .railKind(railKind)
                        .skillName(skillName)
                        .requestId(requestId)
                        .build()
                        .toPayload()
        ));
        payload.put("questions", List.of(questionPayload(
                simplifyQuestion(skillName, actions.size(), preview, english),
                english ? "Skill Simplify Approval" : "Skill 精简审批",
                null,
                simplifyOptions(english),
                false
        )));
        return new OutputSchema(ASK_USER_QUESTION, DEFAULT_INDEX, payload);
    }

    public static OutputSchema buildTeamSkillApprovalEventFromRecords(String skillName,
                                                                      String requestId,
                                                                      Iterable<EvolutionRecord> records,
                                                                      String language,
                                                                      String railKind) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (EvolutionRecord record : records) {
            EvolutionPatch change = Objects.requireNonNull(record.getChange(), "record change is required");
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("section", change.getSection());
            question.put("content", Objects.requireNonNull(change.getContent(), "record change content is required"));
            question.put("record_id", record.getId());
            questions.add(question);
        }
        return buildTeamSkillExperienceQuestionEvent(skillName, requestId, questions, language, railKind);
    }

    private static OutputSchema buildTeamSkillExperienceQuestionEvent(String skillName,
                                                                      String requestId,
                                                                      Iterable<Map<String, Object>> questions,
                                                                      String language,
                                                                      String railKind) {
        boolean english = isEn(language);
        List<Map<String, Object>> questionPayload = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            String section = pyString(required(question, "section"));
            String content = pyString(required(question, "content"));
            questionPayload.add(questionPayload(
                    teamSkillQuestion(skillName, section, content, english),
                    english ? "Team Skill Evolution Approval" : "团队技能演进审批",
                    valueOrDefault(question, "record_id", ""),
                    approvalOptions(english),
                    false
            ));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", requestId);
        payload.put("evolution_meta", new LinkedHashMap<>(
                EvolutionHostEventMeta.builder(EvolutionEventKind.APPROVAL)
                        .railKind(railKind)
                        .skillName(skillName)
                        .requestId(requestId)
                        .build()
                        .toPayload()
        ));
        payload.put("questions", questionPayload);
        return new OutputSchema(ASK_USER_QUESTION, DEFAULT_INDEX, payload);
    }

    private static boolean isEn(String language) {
        return String.valueOf(language).toLowerCase(Locale.ROOT).equals("en");
    }

    private static String skillApprovalQuestion(String skillName, EvolutionPatch change, boolean english) {
        String target = Objects.requireNonNull(change.getTarget(), "record change target is required").getValue();
        String section = pyString(change.getSection());
        String content = preview(Objects.requireNonNull(change.getContent(), "record change content is required"));
        if (english) {
            return "**Skill '" + skillName + "' generated a new experience:**\n\n"
                    + "- **Target**: " + target + "\n"
                    + "- **Section**: " + section + "\n\n"
                    + content;
        }
        return "**Skill '" + skillName + "' 演进生成了新经验：**\n\n"
                + "- **目标**: " + target + "\n"
                + "- **章节**: " + section + "\n\n"
                + content;
    }

    private static String simplifyQuestion(String skillName, int actionCount, String preview, boolean english) {
        if (english) {
            return "**Simplify evolution experiences for Skill '" + skillName + "'**\n\n"
                    + actionCount + " action(s):\n" + preview + "\n\n"
                    + "Do you want to execute them?";
        }
        return "**精简 Skill '" + skillName + "' 的演进经验**\n\n"
                + "共 " + actionCount + " 项操作：\n" + preview + "\n\n"
                + "是否执行？";
    }

    private static String teamSkillQuestion(String skillName, String section, String content, boolean english) {
        if (english) {
            return "**Team Skill '" + skillName + "' evolution:**\n\n"
                    + "- **Section**: " + section + "\n\n"
                    + preview(content);
        }
        return "**团队技能 '" + skillName + "' 生成了演进经验：**\n\n"
                + "- **章节**: " + section + "\n\n"
                + preview(content);
    }

    private static List<Map<String, String>> approvalOptions(boolean english) {
        if (english) {
            return List.of(
                    option("Accept", "Keep this evolution experience"),
                    option("Reject", "Discard this evolution experience")
            );
        }
        return List.of(
                option("接收", "保留此演进经验"),
                option("拒绝", "丢弃此演进经验")
        );
    }

    private static List<Map<String, String>> simplifyOptions(boolean english) {
        if (english) {
            return List.of(
                    option("Execute", "Run the simplify actions"),
                    option("Cancel", "Discard this simplify request")
            );
        }
        return List.of(
                option("执行", "执行精简操作"),
                option("取消", "放弃本次精简")
        );
    }

    private static Map<String, String> option(String label, String description) {
        Map<String, String> option = new LinkedHashMap<>();
        option.put("label", label);
        option.put("description", description);
        return option;
    }

    private static Map<String, Object> questionPayload(String question,
                                                       String header,
                                                       String recordId,
                                                       List<Map<String, String>> options,
                                                       boolean multiSelect) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question);
        payload.put("header", header);
        if (recordId != null) {
            payload.put("record_id", recordId);
        }
        payload.put("options", options);
        payload.put("multi_select", multiSelect);
        return payload;
    }

    private static String buildActionPreview(List<Map<String, Object>> actions) {
        List<String> previewLines = new ArrayList<>();
        int limit = Math.min(ACTION_PREVIEW_LIMIT, actions.size());
        for (int index = 0; index < limit; index++) {
            Map<String, Object> action = actions.get(index);
            previewLines.add("- **" + valueOrDefault(action, "action", "?")
                    + "** `" + valueOrDefault(action, "record_id", "?")
                    + "`: " + valueOrDefault(action, "reason", ""));
        }
        return String.join("\n", previewLines);
    }

    private static String preview(String content) {
        return content.length() <= CONTENT_PREVIEW_LIMIT ? content : content.substring(0, CONTENT_PREVIEW_LIMIT);
    }

    private static Object required(Map<String, Object> payload, String key) {
        if (!payload.containsKey(key)) {
            throw new IllegalArgumentException("missing required key: " + key);
        }
        return payload.get(key);
    }

    private static String valueOrDefault(Map<String, Object> payload, String key, String fallback) {
        return payload.containsKey(key) ? pyString(payload.get(key)) : fallback;
    }

    private static String pyString(Object value) {
        return value == null ? "None" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutablePayloadMap(OutputSchema event) {
        Object payload = event.getPayload();
        if (payload == null) {
            Map<String, Object> newPayload = new LinkedHashMap<>();
            event.setPayload(newPayload);
            return newPayload;
        }
        if (!(payload instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("OutputSchema payload must be a map");
        }
        Map<String, Object> mutablePayload = new LinkedHashMap<>((Map<String, Object>) map);
        event.setPayload(mutablePayload);
        return mutablePayload;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableNestedMap(Map<String, Object> payload, String key) {
        Object nested = payload.get(key);
        if (nested == null) {
            Map<String, Object> newNested = new LinkedHashMap<>();
            payload.put(key, newNested);
            return newNested;
        }
        if (!(nested instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(key + " must be a map");
        }
        Map<String, Object> mutableNested = new LinkedHashMap<>((Map<String, Object>) map);
        payload.put(key, mutableNested);
        return mutableNested;
    }
}
