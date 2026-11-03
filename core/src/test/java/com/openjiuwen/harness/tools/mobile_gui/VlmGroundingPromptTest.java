/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.mobile_gui.test_config} in
 * {@code tests/unit_tests/harness/tools/mobile_gui/test_config.py}.
 */
class VlmGroundingPromptTest {

    @Test
    void testBranchVsInlineSkillGuidance() {
        MobileGuiRuntimeSettings branch = MobileGuiRuntimeSettings.fromEnvironment(
                java.util.Map.of("MULTIMODAL_SKILL_CONSULT_MODE", "branch")
        );
        MobileGuiRuntimeSettings inline = MobileGuiRuntimeSettings.fromEnvironment(
                java.util.Map.of("MULTIMODAL_SKILL_CONSULT_MODE", "inline")
        );

        String branchPrompt = VlmGroundingPrompt.buildVlmGroundingSystemPrompt(branch);
        String inlinePrompt = VlmGroundingPrompt.buildVlmGroundingSystemPrompt(inline);

        assertTrue(branchPrompt.contains("planner memo"));
        assertTrue(branchPrompt.contains("Do **not** call `read_file`"));
        assertTrue(inlinePrompt.contains("read_file"));
        assertFalse(inlinePrompt.contains("Do **not** call `read_file`"));
        assertTrue(branchPrompt.toLowerCase().contains("screenshot"));
        assertTrue(inlinePrompt.toLowerCase().contains("screenshot"));
    }
}
