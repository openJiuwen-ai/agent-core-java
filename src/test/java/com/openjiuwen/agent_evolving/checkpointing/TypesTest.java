/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.openjiuwen.agent_evolving.signal.EvolutionCategory;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for checkpointing types (EvolutionPatch, EvolutionRecord, EvolutionLog).
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.checkpointing.test_types}.
 */
class TypesTest {

    // ========== Factory methods ==========

    private EvolutionPatch makePatch(EvolutionTarget target, Map<String, Object> overrides) {
        EvolutionPatch.Builder builder = EvolutionPatch.builder()
                .section("Troubleshooting")
                .action("append")
                .content("use fallback")
                .target(target != null ? target : EvolutionTarget.BODY);

        if (overrides != null) {
            if (overrides.containsKey("skip_reason")) builder.skipReason((String) overrides.get("skip_reason"));
            if (overrides.containsKey("merge_target")) builder.mergeTarget((String) overrides.get("merge_target"));
            if (overrides.containsKey("script_filename")) builder.scriptFilename((String) overrides.get("script_filename"));
            if (overrides.containsKey("script_language")) builder.scriptLanguage((String) overrides.get("script_language"));
            if (overrides.containsKey("script_purpose")) builder.scriptPurpose((String) overrides.get("script_purpose"));
        }

        return builder.build();
    }

    private EvolutionPatch makePatch() {
        return makePatch(EvolutionTarget.BODY, null);
    }

    private EvolutionRecord makeRecord(Map<String, Object> overrides) {
        EvolutionPatch patch = overrides != null && overrides.containsKey("change")
                ? (EvolutionPatch) overrides.get("change")
                : makePatch();

        EvolutionRecord.Builder builder = EvolutionRecord.builder()
                .id(overrides != null && overrides.containsKey("id") ? (String) overrides.get("id") : "ev_00112233")
                .source(overrides != null && overrides.containsKey("source") ? (String) overrides.get("source") : "execution_failure")
                .timestamp(overrides != null && overrides.containsKey("timestamp") ? (String) overrides.get("timestamp") : "2026-01-01T00:00:00Z")
                .context(overrides != null && overrides.containsKey("context") ? (String) overrides.get("context") : "ctx")
                .change(patch)
                .applied(overrides != null && overrides.containsKey("applied") ? (Boolean) overrides.get("applied") : false);

        return builder.build();
    }

    // ========== TestEvolutionPatch tests ==========

    @Test
    void testToDictIncludesOptionalFields() {
        EvolutionPatch patch = makePatch(EvolutionTarget.BODY, Map.of(
                "skip_reason", "duplicate",
                "merge_target", "ev_xxx"
        ));
        Map<String, Object> data = patch.toDict();
        assertEquals("duplicate", data.get("skip_reason"));
        assertEquals("ev_xxx", data.get("merge_target"));
        assertEquals("body", data.get("target"));
    }

    @Test
    void testToDictIncludesScriptFields() {
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("Scripts")
                .action("append")
                .content("")
                .target(EvolutionTarget.SCRIPT)
                .scriptFilename("gen_chart.py")
                .scriptLanguage("python")
                .scriptPurpose("generate bar chart")
                .build();

        Map<String, Object> data = patch.toDict();
        assertEquals("script", data.get("target"));
        assertEquals("gen_chart.py", data.get("script_filename"));
        assertEquals("python", data.get("script_language"));
        assertEquals("generate bar chart", data.get("script_purpose"));
    }

    @Test
    void testToDictOmitsNoneScriptFields() {
        EvolutionPatch patch = makePatch();
        Map<String, Object> data = patch.toDict();
        assertFalse(data.containsKey("script_filename"));
        assertFalse(data.containsKey("script_language"));
        assertFalse(data.containsKey("script_purpose"));
    }

    @Test
    void testFromDictWithScriptTarget() {
        Map<String, Object> data = new HashMap<>();
        data.put("section", "Scripts");
        data.put("action", "append");
        data.put("content", "print('hello')");
        data.put("target", "script");
        data.put("script_filename", "hello.py");
        data.put("script_language", "python");
        data.put("script_purpose", "demo");

        EvolutionPatch patch = EvolutionPatch.fromDict(data);
        assertEquals(EvolutionTarget.SCRIPT, patch.getTarget());
        assertEquals("hello.py", patch.getScriptFilename());
        assertEquals("python", patch.getScriptLanguage());
        assertEquals("demo", patch.getScriptPurpose());
    }

    @Test
    void testFromDictFallbackTarget() {
        Map<String, Object> data = new HashMap<>();
        data.put("section", "Instructions");
        data.put("action", "append");
        data.put("content", "x");
        data.put("target", "invalid-target");

        EvolutionPatch patch = EvolutionPatch.fromDict(data);
        assertEquals(EvolutionTarget.BODY, patch.getTarget());
    }

    // ========== TestEvolutionRecord tests ==========

    @Test
    void testMakeGeneratesPrefixedId() {
        EvolutionRecord record = EvolutionRecord.make("tool_failure", "ctx", makePatch(), 0.6, null);
        assertTrue(record.getId().startsWith("ev_"));
        assertTrue(record.isPending());
        assertEquals("tool_failure", record.getSource());
    }

    @Test
    void testFromDictUsesDefaults() {
        EvolutionRecord record = EvolutionRecord.fromDict(new HashMap<>());
        assertTrue(record.getId().startsWith("ev_"));
        assertEquals("unknown", record.getSource());
        assertEquals(EvolutionTarget.BODY, record.getChange().getTarget());
        assertFalse(record.isApplied());
    }

    // ========== TestEvolutionLog tests ==========

    @Test
    void testPendingEntriesFiltersApplied() {
        EvolutionRecord recPending = makeRecord(Map.of("id", "ev_a", "applied", false));
        EvolutionRecord recApplied = makeRecord(Map.of("id", "ev_b", "applied", true));

        List<EvolutionRecord> entries = new ArrayList<>();
        entries.add(recPending);
        entries.add(recApplied);

        EvolutionLog log = EvolutionLog.builder()
                .skillId("skill-a")
                .entries(entries)
                .build();

        List<EvolutionRecord> pendingEntries = log.getPendingEntries();
        assertEquals(1, pendingEntries.size());
        assertEquals("ev_a", pendingEntries.get(0).getId());
    }

    @Test
    void testRoundTripAndEmpty() {
        List<EvolutionRecord> entries = new ArrayList<>();
        entries.add(makeRecord(null));

        EvolutionLog original = EvolutionLog.builder()
                .skillId("skill-a")
                .version("1.0.0")
                .updatedAt("2026-01-01T00:00:00Z")
                .entries(entries)
                .build();

        Map<String, Object> data = original.toDict();
        EvolutionLog loaded = EvolutionLog.fromDict(data);
        EvolutionLog empty = EvolutionLog.empty("skill-x");

        assertEquals("skill-a", loaded.getSkillId());
        assertEquals(1, loaded.getEntries().size());
        assertEquals("skill-x", empty.getSkillId());
        assertTrue(empty.getEntries().isEmpty());
    }

    // ========== TestEvolutionSignal tests ==========

    @Test
    void testToDictContainsEnumValue() {
        EvolutionSignal signal = EvolutionSignal.builder()
                .signalType("execution_failure")
                .evolutionType(EvolutionCategory.SKILL_EXPERIENCE)
                .section("Troubleshooting")
                .excerpt("timeout")
                .toolName("bash")
                .skillName("skill-a")
                .build();

        Map<String, Object> data = signal.toDict();
        assertEquals("skill_experience", data.get("evolution_type"));
        assertEquals("bash", data.get("tool_name"));
    }

    @Test
    void testToDictOmitsContextWhenNone() {
        // Online signals have no context; key must be absent from serialized dict
        EvolutionSignal signal = EvolutionSignal.builder()
                .signalType("execution_failure")
                .evolutionType(EvolutionCategory.SKILL_EXPERIENCE)
                .section("Troubleshooting")
                .excerpt("timeout")
                .build();

        assertFalse(signal.toDict().containsKey("context"));
    }

    @Test
    void testToDictIncludesContextWhenPresent() {
        // Offline signals carry evaluation evidence; context must survive serialization
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("question", "How to deploy?");
        ctx.put("label", "use kubectl");
        ctx.put("answer", "wrong answer");
        ctx.put("reason", "missed kubectl");
        ctx.put("score", 0.0);

        EvolutionSignal signal = EvolutionSignal.builder()
                .signalType("low_score")
                .evolutionType(EvolutionCategory.SKILL_EXPERIENCE)
                .section("Troubleshooting")
                .excerpt("score=0.00")
                .skillName("skill-a")
                .context(ctx)
                .build();

        Map<String, Object> data = signal.toDict();
        assertTrue(data.containsKey("context"));
        Map<String, Object> contextData = (Map<String, Object>) data.get("context");
        assertEquals("How to deploy?", contextData.get("question"));
        assertEquals(0.0, contextData.get("score"));
    }
}