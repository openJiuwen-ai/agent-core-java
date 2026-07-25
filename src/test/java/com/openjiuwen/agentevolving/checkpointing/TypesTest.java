/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.experience.EvolutionContext;
import com.openjiuwen.agent_evolving.experience.PendingChange;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for checkpointing types.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_evolving/checkpointing/test_types.py}.</p>
 * <p>Mirrors Python's {@code test_valid_sections_contains_required_sections} in
 * {@code tests/unit_tests/agent_evolving/checkpointing/test_evolution_types.py}.</p>
 */
class TypesTest {

    private EvolutionPatch makePatch() {
        return makePatch(EvolutionTarget.BODY, Map.of());
    }

    private EvolutionPatch makePatch(EvolutionTarget target, Map<String, Object> overrides) {
        return EvolutionPatch.builder()
                .section(stringOverride(overrides, "section", "Troubleshooting"))
                .action(stringOverride(overrides, "action", "append"))
                .content(stringOverride(overrides, "content", "use fallback"))
                .target(target)
                .skipReason(stringOverride(overrides, "skip_reason", null))
                .mergeTarget(stringOverride(overrides, "merge_target", null))
                .scriptFilename(stringOverride(overrides, "script_filename", null))
                .scriptLanguage(stringOverride(overrides, "script_language", null))
                .scriptPurpose(stringOverride(overrides, "script_purpose", null))
                .summary(stringOverride(overrides, "summary", null))
                .build();
    }

    private EvolutionRecord makeRecord(Map<String, Object> overrides) {
        EvolutionPatch patch = overrides != null && overrides.containsKey("change")
                ? (EvolutionPatch) overrides.get("change")
                : makePatch();
        return EvolutionRecord.builder()
                .id(stringOverride(overrides, "id", "ev_00112233"))
                .source(stringOverride(overrides, "source", "execution_failure"))
                .timestamp(stringOverride(overrides, "timestamp", "2026-01-01T00:00:00+00:00"))
                .context(stringOverride(overrides, "context", "ctx"))
                .change(patch)
                .applied(booleanOverride(overrides, "applied", false))
                .summary(stringOverride(overrides, "summary", null))
                .build();
    }

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
        EvolutionPatch patch = makePatch(EvolutionTarget.SCRIPT, Map.of(
                "section", "Scripts",
                "script_filename", "gen_chart.py",
                "script_language", "python",
                "script_purpose", "generate bar chart"
        ));
        Map<String, Object> data = patch.toDict();
        assertEquals("script", data.get("target"));
        assertEquals("gen_chart.py", data.get("script_filename"));
        assertEquals("python", data.get("script_language"));
        assertEquals("generate bar chart", data.get("script_purpose"));
    }

    @Test
    void testToDictOmitsNoneScriptFields() {
        Map<String, Object> data = makePatch().toDict();
        assertFalse(data.containsKey("script_filename"));
        assertFalse(data.containsKey("script_language"));
        assertFalse(data.containsKey("script_purpose"));
    }

    @Test
    void testFromDictWithScriptTarget() {
        EvolutionPatch patch = EvolutionPatch.fromDict(Map.of(
                "section", "Scripts",
                "action", "append",
                "content", "print('hello')",
                "target", "script",
                "script_filename", "hello.py",
                "script_language", "python",
                "script_purpose", "demo"
        ));
        assertEquals(EvolutionTarget.SCRIPT, patch.getTarget());
        assertEquals("hello.py", patch.getScriptFilename());
        assertEquals("python", patch.getScriptLanguage());
        assertEquals("demo", patch.getScriptPurpose());
    }

    @Test
    void testFromDictRejectsInvalidTarget() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EvolutionPatch.fromDict(Map.of(
                        "section", "Instructions",
                        "action", "append",
                        "content", "x",
                        "target", "invalid-target"
                ))
        );
        assertTrue(exception.getMessage().contains("invalid-target"));
    }

    @Test
    void testRejectsInvalidSection() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> makePatch(EvolutionTarget.BODY, Map.of("section", "Unknown"))
        );
        assertTrue(exception.getMessage().contains("section"));
    }

    @Test
    void testValidSectionsContainsRequiredSections() {
        Set<String> expected = Set.of(
                "Instructions",
                "Examples",
                "Troubleshooting",
                "Scripts",
                "Collaboration",
                "Roles",
                "Constraints",
                "Workflow"
        );

        assertTrue(Protocols.VALID_SECTIONS.containsAll(expected));
        assertEquals(expected.size(), Protocols.VALID_SECTIONS.size());
    }

    @Test
    void testRejectsInvalidAction() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> makePatch(EvolutionTarget.BODY, Map.of("action", "unknown"))
        );
        assertTrue(exception.getMessage().contains("action"));
    }

    @Test
    void testSkipPatchDoesNotRequireSection() {
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("")
                .action("skip")
                .content("")
                .skipReason("duplicate")
                .build();
        assertEquals("duplicate", patch.getSkipReason());
        assertEquals(EvolutionTarget.BODY, patch.getTarget());
    }

    @Test
    void testCompatImportsPointToNewTypeOwners() {
        assertEquals("com.openjiuwen.agent_evolving.checkpointing", EvolveCheckpoint.class.getPackageName());
        assertEquals("com.openjiuwen.agent_evolving.experience", PendingChange.class.getPackageName());
        assertEquals("com.openjiuwen.agent_evolving.experience", EvolutionContext.class.getPackageName());
    }

    @Test
    void testFromDictUsesDefaultsWhenFieldsMissing() {
        EvolutionPatch patch = EvolutionPatch.fromDict(Map.of("content", "x"));
        assertEquals(EvolutionTarget.BODY, patch.getTarget());
        assertEquals("Troubleshooting", patch.getSection());
        assertEquals("append", patch.getAction());
    }

    @Test
    void testMakeGeneratesPrefixedId() {
        EvolutionRecord record = EvolutionRecord.make("tool_failure", "ctx", makePatch());
        assertTrue(record.getId().startsWith("ev_"));
        assertTrue(record.isPending());
        assertEquals("tool_failure", record.getSource());
    }

    @Test
    void testFromDictUsesDefaults() {
        EvolutionRecord record = EvolutionRecord.fromDict(Map.of());
        assertTrue(record.getId().startsWith("ev_"));
        assertEquals("unknown", record.getSource());
        assertEquals(EvolutionTarget.BODY, record.getChange().getTarget());
        assertFalse(record.isApplied());
    }

    @Test
    void testPendingEntriesFiltersApplied() {
        EvolutionRecord pending = makeRecord(Map.of("id", "ev_a", "applied", false));
        EvolutionRecord applied = makeRecord(Map.of("id", "ev_b", "applied", true));
        EvolutionLog log = EvolutionLog.builder()
                .skillId("skill-a")
                .entries(List.of(pending, applied))
                .build();
        assertEquals(List.of("ev_a"), log.getPendingEntries().stream().map(EvolutionRecord::getId).toList());
    }

    @Test
    void testRoundTripAndEmpty() {
        EvolutionLog original = EvolutionLog.builder()
                .skillId("skill-a")
                .version("1.0.0")
                .updatedAt("2026-01-01T00:00:00+00:00")
                .entries(List.of(makeRecord(Map.of())))
                .build();
        EvolutionLog loaded = EvolutionLog.fromDict(original.toDict());
        EvolutionLog empty = EvolutionLog.empty("skill-x");
        assertEquals("skill-a", loaded.getSkillId());
        assertEquals(1, loaded.getEntries().size());
        assertEquals("skill-x", empty.getSkillId());
        assertTrue(empty.getEntries().isEmpty());
    }

    @Test
    void testSignalToDictContainsStableTopLevelFieldsOnly() {
        EvolutionSignal signal = EvolutionSignal.builder()
                .signalType("execution_failure")
                .section("Troubleshooting")
                .excerpt("timeout")
                .skillName("skill-a")
                .context(Map.of("tool_name", "bash", "source", "passive_conversation"))
                .build();
        assertEquals(Map.of(
                "type", "execution_failure",
                "section", "Troubleshooting",
                "excerpt", "timeout",
                "skill_name", "skill-a",
                "context", Map.of("tool_name", "bash", "source", "passive_conversation")
        ), signal.toDict());
    }

    @Test
    void testSignalToDictOmitsContextWhenNone() {
        EvolutionSignal signal = EvolutionSignal.builder()
                .signalType("execution_failure")
                .section("Troubleshooting")
                .excerpt("timeout")
                .build();
        assertFalse(signal.toDict().containsKey("context"));
    }

    @Test
    void testSignalToDictIncludesContextWhenPresent() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("question", "How to deploy?");
        context.put("label", "use kubectl");
        context.put("answer", "wrong answer");
        context.put("reason", "missed kubectl");
        context.put("score", 0.0);
        EvolutionSignal signal = EvolutionSignal.builder()
                .signalType("low_score")
                .section("Troubleshooting")
                .excerpt("score=0.00")
                .skillName("skill-a")
                .context(context)
                .build();
        Map<String, Object> data = signal.toDict();
        assertTrue(data.containsKey("context"));
        assertEquals("How to deploy?", ((Map<?, ?>) data.get("context")).get("question"));
        assertEquals(0.0, ((Map<?, ?>) data.get("context")).get("score"));
    }

    private static String stringOverride(Map<String, Object> overrides, String key, String fallback) {
        if (overrides == null || !overrides.containsKey(key)) {
            return fallback;
        }
        Object value = overrides.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanOverride(Map<String, Object> overrides, String key, boolean fallback) {
        if (overrides == null || !overrides.containsKey(key)) {
            return fallback;
        }
        return (Boolean) overrides.get(key);
    }
}
