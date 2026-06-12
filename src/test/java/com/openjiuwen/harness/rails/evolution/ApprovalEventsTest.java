/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
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
}
