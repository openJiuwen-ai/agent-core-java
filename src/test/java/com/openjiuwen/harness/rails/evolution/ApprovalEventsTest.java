/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import com.openjiuwen.core.session.stream.OutputSchema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code openjiuwen.harness.rails.evolution.approval_events} in
 * {@code openjiuwen/harness/rails/evolution/approval_events.py}.
 *
 * <p>Also mirrors Python's unit tests in
 * {@code tests/unit_tests/harness/rails/evolution/test_evolution_approval_events.py}.</p>
 */
class ApprovalEventsTest {

    @Test
    void buildsProgressEvent() {
        OutputSchema event = ApprovalEvents.buildProgressEvent("[P]", "working");

        assertEquals("llm_reasoning", event.getType());
        assertEquals(0, event.getIndex());
        assertEquals("[P] working\n", payload(event).get("content"));
    }

    @Test
    void buildsEvolutionProgressEventWithOptionalMetadata() {
        OutputSchema event = ApprovalEvents.buildEvolutionProgressEvent(
                "regular",
                "collect",
                "ready",
                "skill-a",
                "req-1",
                null
        );

        Map<String, Object> payload = payload(event);
        assertEquals("[Evolution] ready\n", payload.get("content"));
        Map<String, Object> meta = nested(payload, "evolution_meta");
        assertEquals("progress", meta.get("event_kind"));
        assertEquals("regular", meta.get("rail_kind"));
        assertEquals("collect", meta.get("stage"));
        assertEquals("skill-a", meta.get("skill_name"));
        assertEquals("req-1", meta.get("request_id"));
    }

    @Test
    void attachesEvolutionMetaWithoutOverwritingExistingEventKind() {
        OutputSchema event = new OutputSchema(
                "chat.ask_user_question",
                0,
                Map.of("evolution_meta", Map.of("event_kind", "progress"))
        );

        OutputSchema same = ApprovalEvents.attachEvolutionMeta(event, "team", "user", "cli");

        assertSame(event, same);
        Map<String, Object> meta = nested(payload(event), "evolution_meta");
        assertEquals("progress", meta.get("event_kind"));
        assertEquals("team", meta.get("rail_kind"));
        assertEquals("user", meta.get("signal_type"));
        assertEquals("cli", meta.get("source"));
    }

    @Test
    void buildsChineseSkillApprovalEventForSharedRecords() {
        OutputSchema event = ApprovalEvents.buildSkillApprovalEvent(
                "memory",
                "req-2",
                List.of(record("rec-1", EvolutionTarget.BODY, "Troubleshooting", "新的经验内容")),
                "cn",
                true,
                "regular"
        );

        assertEquals("chat.ask_user_question", event.getType());
        Map<String, Object> payload = payload(event);
        Map<String, Object> meta = nested(payload, "evolution_meta");
        assertEquals("approval", meta.get("event_kind"));
        assertEquals("experience_sharing", meta.get("source"));
        assertEquals("true", meta.get("is_shared_records"));

        Map<String, Object> question = firstQuestion(payload);
        assertEquals("在线共享经验审批", question.get("header"));
        assertEquals("rec-1", question.get("record_id"));
        assertTrue(String.valueOf(question.get("question")).contains("**Skill 'memory' 演进生成了新经验：**"));
        assertTrue(String.valueOf(question.get("question")).contains("- **目标**: body"));
        assertEquals(false, question.get("multi_select"));
    }

    @Test
    void buildsEnglishSimplifyApprovalEventWithActionPreviewLimit() {
        OutputSchema event = ApprovalEvents.buildSimplifyApprovalEvent(
                "memory",
                "req-3",
                List.of(
                        Map.of("action", "merge", "record_id", "r1", "reason", "same topic"),
                        Map.of("action", "drop", "record_id", "r2", "reason", "old")
                ),
                "en",
                "regular"
        );

        Map<String, Object> question = firstQuestion(payload(event));
        assertEquals("Skill Simplify Approval", question.get("header"));
        String text = String.valueOf(question.get("question"));
        assertTrue(text.contains("2 action(s):"));
        assertTrue(text.contains("- **merge** `r1`: same topic"));
        assertTrue(text.contains("Do you want to execute them?"));
    }

    @Test
    void buildsTeamSkillApprovalEventFromRecords() {
        OutputSchema event = ApprovalEvents.buildTeamSkillApprovalEventFromRecords(
                "planner",
                "req-4",
                List.of(record("rec-2", EvolutionTarget.SCRIPT, "Workflow", "team content")),
                "en",
                "team"
        );

        Map<String, Object> payload = payload(event);
        Map<String, Object> meta = nested(payload, "evolution_meta");
        assertEquals("team", meta.get("rail_kind"));
        assertEquals("planner", meta.get("skill_name"));

        Map<String, Object> question = firstQuestion(payload);
        assertEquals("Team Skill Evolution Approval", question.get("header"));
        assertEquals("rec-2", question.get("record_id"));
        assertTrue(String.valueOf(question.get("question")).contains("**Team Skill 'planner' evolution:**"));
        assertTrue(String.valueOf(question.get("question")).contains("- **Section**: Workflow"));
    }

    @Test
    void buildProgressEventMatchesReasoningPayload() {
        OutputSchema event = ApprovalEvents.buildProgressEvent("[Team Skill Evolution]", "analysis started");

        assertEquals("llm_reasoning", event.getType());
        assertEquals(Map.of("content", "[Team Skill Evolution] analysis started\n"), payload(event));
    }

    @Test
    void buildEvolutionProgressEventIncludesNormalizedMeta() {
        OutputSchema event = ApprovalEvents.buildEvolutionProgressEvent(
                "regular",
                "approval_required",
                "awaiting approval",
                "skill-a",
                "req-1",
                "[Skill Evolution]"
        );

        assertEquals("llm_reasoning", event.getType());
        assertEquals("[Skill Evolution] awaiting approval\n", payload(event).get("content"));
        assertEquals(Map.of(
                "event_kind", "progress",
                "rail_kind", "regular",
                "stage", "approval_required",
                "skill_name", "skill-a",
                "request_id", "req-1"
        ), payload(event).get("evolution_meta"));
    }

    @Test
    void buildSkillApprovalEventMatchesExistingContract() {
        List<EvolutionRecord> pending = List.of(
                record("rec-a", EvolutionTarget.BODY, "Troubleshooting", "first experience"),
                record("rec-b", EvolutionTarget.BODY, "Troubleshooting", "second experience")
        );

        OutputSchema event = ApprovalEvents.buildSkillApprovalEvent(
                "skill-a",
                "skill_evolve_1234",
                pending,
                "cn",
                false,
                "regular"
        );

        assertEquals("chat.ask_user_question", event.getType());
        assertEquals("skill_evolve_1234", payload(event).get("request_id"));
        assertEquals(Map.of(
                "event_kind", "approval",
                "rail_kind", "regular",
                "skill_name", "skill-a",
                "request_id", "skill_evolve_1234"
        ), payload(event).get("evolution_meta"));
        assertEquals("技能演进审批", firstQuestion(payload(event)).get("header"));
        assertEquals(2, questions(payload(event)).size());
        assertEquals("rec-a", questions(payload(event)).get(0).get("record_id"));
        assertEquals("rec-b", questions(payload(event)).get(1).get("record_id"));
        assertTrue(String.valueOf(firstQuestion(payload(event)).get("question")).contains("Skill 'skill-a'"));
    }

    @Test
    void buildSimplifyApprovalEventMatchesExistingContract() {
        OutputSchema event = ApprovalEvents.buildSimplifyApprovalEvent(
                "skill-a",
                "evolve_simplify_1234",
                List.of(
                        Map.of("action", "DELETE", "record_id", "ev_1", "reason", "old"),
                        Map.of("action", "KEEP", "record_id", "ev_2", "reason", "good")
                ),
                "cn",
                "regular"
        );

        assertEquals("chat.ask_user_question", event.getType());
        assertEquals("evolve_simplify_1234", payload(event).get("request_id"));
        assertEquals(Map.of(
                "event_kind", "approval",
                "rail_kind", "regular",
                "skill_name", "skill-a",
                "request_id", "evolve_simplify_1234"
        ), payload(event).get("evolution_meta"));
        assertEquals("Skill 精简审批", firstQuestion(payload(event)).get("header"));
        assertTrue(String.valueOf(firstQuestion(payload(event)).get("question")).contains("共 2 项操作"));
    }

    @Test
    void buildSkillApprovalEventSharedHeaderSupportsEnglishLanguage() {
        OutputSchema event = ApprovalEvents.buildSkillApprovalEvent(
                "skill-a",
                "skill_evolve_shared_en",
                List.of(record("rec-shared", EvolutionTarget.BODY, "Troubleshooting", "shared experience")),
                "en",
                true,
                "regular"
        );

        assertEquals("Shared Experience Approval", firstQuestion(payload(event)).get("header"));
    }

    @Test
    void buildSkillApprovalEventSupportsEnglishLanguage() {
        OutputSchema event = ApprovalEvents.buildSkillApprovalEvent(
                "skill-a",
                "skill_evolve_en",
                List.of(record("rec-en", EvolutionTarget.BODY, "Troubleshooting", "english experience")),
                "en",
                false,
                "regular"
        );

        Map<String, Object> question = firstQuestion(payload(event));
        assertEquals("Skill Evolution Approval", question.get("header"));
        assertTrue(String.valueOf(question.get("question"))
                .contains("Skill 'skill-a' generated a new experience"));
        assertEquals("Accept", options(question).get(0).get("label"));
        assertEquals("Reject", options(question).get(1).get("label"));
    }

    @Test
    void buildSimplifyApprovalEventSupportsEnglishLanguage() {
        OutputSchema event = ApprovalEvents.buildSimplifyApprovalEvent(
                "skill-a",
                "evolve_simplify_en",
                List.of(Map.of("action", "DELETE", "record_id", "ev_1", "reason", "old")),
                "en",
                "regular"
        );

        Map<String, Object> question = firstQuestion(payload(event));
        assertEquals("Skill Simplify Approval", question.get("header"));
        assertTrue(String.valueOf(question.get("question"))
                .contains("Simplify evolution experiences for Skill 'skill-a'"));
        assertTrue(String.valueOf(question.get("question")).contains("1 action(s)"));
        assertEquals("Execute", options(question).get(0).get("label"));
        assertEquals("Cancel", options(question).get(1).get("label"));
    }

    @Test
    void buildTeamSkillApprovalEventFromRecordsMatchesRecordPayloads() {
        OutputSchema event = ApprovalEvents.buildTeamSkillApprovalEventFromRecords(
                "team-skill-a",
                "skill_evolve_team_records",
                List.of(
                        record("team-rec-1", EvolutionTarget.BODY, "Troubleshooting", "## Workflow\n- improve handoff"),
                        record("team-rec-2", EvolutionTarget.BODY, "Troubleshooting", "## Troubleshooting\n- add retry note")
                ),
                "en",
                "team"
        );

        assertEquals("chat.ask_user_question", event.getType());
        assertEquals("skill_evolve_team_records", payload(event).get("request_id"));
        assertEquals(Map.of(
                "event_kind", "approval",
                "rail_kind", "team",
                "skill_name", "team-skill-a",
                "request_id", "skill_evolve_team_records"
        ), payload(event).get("evolution_meta"));
        assertEquals(2, questions(payload(event)).size());
        assertTrue(String.valueOf(questions(payload(event)).get(0).get("question"))
                .contains("Team Skill 'team-skill-a' evolution"));
        assertTrue(String.valueOf(questions(payload(event)).get(0).get("question"))
                .contains("improve handoff"));
        assertTrue(String.valueOf(questions(payload(event)).get(1).get("question"))
                .contains("add retry note"));
    }

    private static EvolutionRecord record(String id, EvolutionTarget target, String section, String content) {
        return EvolutionRecord.builder()
                .id(id)
                .source("test")
                .context("ctx")
                .change(EvolutionPatch.builder()
                        .section(section)
                        .action("append")
                        .content(content)
                        .target(target)
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(OutputSchema event) {
        return (Map<String, Object>) event.getPayload();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> payload, String key) {
        return (Map<String, Object>) payload.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstQuestion(Map<String, Object> payload) {
        return ((List<Map<String, Object>>) payload.get("questions")).get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> questions(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("questions");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> options(Map<String, Object> question) {
        return (List<Map<String, Object>>) question.get("options");
    }
}
