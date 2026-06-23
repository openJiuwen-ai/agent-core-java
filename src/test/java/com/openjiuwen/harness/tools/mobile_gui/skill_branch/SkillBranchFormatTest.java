/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.mobile_gui.test_skill_branch_format} in
 * {@code tests/unit_tests/harness/tools/mobile_gui/test_skill_branch_format.py}.
 */
class SkillBranchFormatTest {

    @Test
    void plannerMessageIncludesOptionalVisualSelection() {
        String rendered = SkillBranchFormat.formatPlannerToolMessage(
                "calendar",
                Map.of(
                        "skill_applicability", "high",
                        "plan", "Open agenda",
                        "completion_scope", "done"
                ),
                "tap the left card"
        );

        assertTrue(rendered.startsWith("Skill consult: calendar\nVisual selection: tap the left card"));
        assertTrue(rendered.contains("Applicability: high"));
        assertTrue(rendered.contains("Completion scope: done"));
    }

    @Test
    void branchFailureTruncatesExcerptAndKeepsFallbackText() {
        String rendered = SkillBranchFormat.formatBranchFailureToolMessage(
                "maps",
                "timeout",
                "1234567890",
                8
        );

        assertTrue(rendered.contains("Branch consult failed: timeout"));
        assertTrue(rendered.contains("Skill excerpt:\n12345..."));
    }

    @Test
    void plannerMessageUsesPythonFallbacks() {
        String rendered = SkillBranchFormat.formatPlannerToolMessage("notes", Map.of(), "");

        assertEquals(
                String.join("\n",
                        "Skill consult: notes",
                        "Applicability: unknown",
                        "Subgoal: ",
                        "Plan: ",
                        "Do not do: ",
                        "Fallback if no progress: ",
                        "Expected state: ",
                        "Completion scope: needs_verification"),
                rendered
        );
    }

    @Test
    void formatPlannerToolMessageIncludesAllPlannerFields() {
        String body = SkillBranchFormat.formatPlannerToolMessage(
                "github-com",
                Map.of(
                        "skill_applicability", "effective",
                        "subgoal", "open repo",
                        "plan", "Tap search.",
                        "do_not_do", "Do not scroll aimlessly.",
                        "fallback_if_no_progress", "Go back.",
                        "expected_state", "Repo list visible.",
                        "completion_scope", "local_only"
                ),
                "Layout reference from landing.png"
        );

        assertTrue(body.startsWith("Skill consult: github-com"));
        assertTrue(body.contains("Visual selection: Layout reference"));
        assertTrue(body.contains("Applicability: effective"));
        assertTrue(body.contains("Subgoal: open repo"));
        assertTrue(body.contains("Completion scope: local_only"));
    }

    @Test
    void formatPlannerToolMessageOmitsEmptyStage1Note() {
        String body = SkillBranchFormat.formatPlannerToolMessage(
                "demo",
                Map.of(
                        "skill_applicability", "uncertain",
                        "subgoal", "x",
                        "plan", "y",
                        "do_not_do", "z",
                        "fallback_if_no_progress", "a",
                        "expected_state", "b",
                        "completion_scope", "needs_verification"
                ),
                null
        );

        assertFalse(body.contains("Visual selection:"));
    }

    @Test
    void formatBranchFailureToolMessageIncludesError() {
        String body = SkillBranchFormat.formatBranchFailureToolMessage("demo", "Model timeout", null, 1000);

        assertTrue(body.contains("Skill consult: demo"));
        assertTrue(body.contains("Branch consult failed: Model timeout"));
        assertFalse(body.contains("Skill excerpt:"));
    }

    @Test
    void formatBranchFailureToolMessageTruncatesLongExcerpt() {
        String longSkill = "x".repeat(1000);
        String body = SkillBranchFormat.formatBranchFailureToolMessage("demo", "Parse error", longSkill, 100);

        assertTrue(body.contains("Skill excerpt:"));
        assertTrue(body.length() < longSkill.length());
        assertTrue(body.endsWith("..."));
    }
}
