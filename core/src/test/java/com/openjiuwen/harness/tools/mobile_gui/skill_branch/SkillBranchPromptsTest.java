/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillBranchPromptsTest {

    @Test
    void testStage1SystemMessageContainsLimitsAndFormat() {
        String prompt = SkillBranchPrompts.buildStage1SystemMessage(4);
        assertTrue(prompt.contains("Request at most 4 images total."));
        assertTrue(prompt.contains("LOAD_SKILL_IMAGES"));
    }

    @Test
    void testStage1UserMessageContainsManifest() {
        String message = SkillBranchPrompts.buildStage1UserMessage(
                "Open the repo page",
                "github",
                "skill text",
                List.of(new SkillImageEntry("img", "alt", "images/foo.png", "/tmp/foo.png")),
                ""
        );
        assertTrue(message.contains("User instruction: Open the repo page"));
        assertTrue(message.contains("img"));
        assertTrue(message.contains("(no previous steps)"));
    }

    @Test
    void testStage2PromptBuildersContainPlannerFields() {
        assertTrue(SkillBranchPrompts.buildStage2SystemMessage().contains("skill_applicability"));
        assertTrue(SkillBranchPrompts.buildStage2UserMessage(
                "Do x",
                "skill",
                "markdown",
                "decision",
                "images",
                ""
        ).contains("Stage-1 decision: decision"));
    }
}
