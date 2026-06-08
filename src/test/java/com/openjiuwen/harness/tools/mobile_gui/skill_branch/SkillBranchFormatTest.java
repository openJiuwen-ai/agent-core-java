/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

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
}
