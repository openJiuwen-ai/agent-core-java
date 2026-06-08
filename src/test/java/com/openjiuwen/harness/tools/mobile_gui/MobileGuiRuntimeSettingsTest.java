/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MobileGuiRuntimeSettingsTest {

    @Test
    void fromEnvironmentUsesPythonDefaults() {
        MobileGuiRuntimeSettings settings = MobileGuiRuntimeSettings.fromEnvironment(Map.of());

        assertEquals("emulator-5554", settings.getDeviceSerial());
        assertNull(settings.getDevice());
        assertTrue(settings.isCleanupGoHome());
        assertTrue(settings.isHealthCheck());
        assertEquals(1280, settings.getVlmGroundingMaxWidth());
        assertEquals(85, settings.getVlmGroundingJpegQuality());
        assertEquals(1.0, settings.getVlmGroundingUiSettleSeconds());
        assertEquals(1000, settings.getVlmCoordinateScale());
        assertEquals(1280, settings.getVlmClaudeImageWidth());
        assertEquals(720, settings.getVlmClaudeImageHeight());
        assertEquals(1280, settings.getVlmClaudeOpusMaxDimension());
        assertEquals(3, settings.getMcsScreenshotsToKeep());
        assertEquals(1080, settings.getScrollDefaultWidth());
        assertEquals(1920, settings.getScrollDefaultHeight());
        assertEquals(300, settings.getScrollDurationMsDefault());
        assertEquals(0.5, settings.getWaitGuiLoadMinSeconds());
        assertEquals(30.0, settings.getWaitGuiLoadMaxSeconds());
        assertEquals(2.0, settings.getWaitGuiLoadDefaultSeconds());
        assertTrue(settings.isVlmGroundingOnlySettleAfterTools());
        assertEquals(120, settings.getContextMaxMessageNum());
        assertEquals(20, settings.getContextDefaultWindowRoundNum());
        assertEquals(SkillConsultMode.BRANCH, settings.getSkillConsultMode());
        assertEquals(4, settings.getSkillBranchMaxImages());
        assertEquals(2, settings.getSkillBranchMaxConsultsPerSkill());
        assertEquals(10, settings.getSkillBranchPreviousStepsTurns());
    }

    @Test
    void fromEnvironmentHonorsPrimaryAndLegacyVariables() {
        MobileGuiRuntimeSettings settings = MobileGuiRuntimeSettings.fromEnvironment(Map.ofEntries(
                Map.entry("DEVICE_SERIAL", "phone-1"),
                Map.entry("VLM_GROUNDING_MAX_WIDTH", "1440"),
                Map.entry("WAIT_GUI_LOAD_DEFAULT_SECONDS", "3.5"),
                Map.entry("MULTIMODAL_SKILL_CONSULT_MODE", "inline"),
                Map.entry("MOBILE_SKILL_BRANCH_MAX_IMAGES", "9"),
                Map.entry("MULTIMODAL_SKILL_BRANCH_MAX_CONSULTS_PER_SKILL", "5"),
                Map.entry("MULTIMODAL_SKILL_BRANCH_PREVIOUS_STEPS_TURNS", "12")
        ));

        assertEquals("phone-1", settings.getDeviceSerial());
        assertEquals(1440, settings.getVlmGroundingMaxWidth());
        assertEquals(3.5, settings.getWaitGuiLoadDefaultSeconds());
        assertEquals(SkillConsultMode.INLINE, settings.getSkillConsultMode());
        assertEquals(9, settings.getSkillBranchMaxImages());
        assertEquals(5, settings.getSkillBranchMaxConsultsPerSkill());
        assertEquals(12, settings.getSkillBranchPreviousStepsTurns());
    }

    @Test
    void primarySkillConsultModeWinsOverLegacyAndUnknownFallsBackToBranch() {
        MobileGuiRuntimeSettings inline = MobileGuiRuntimeSettings.fromEnvironment(Map.ofEntries(
                Map.entry("MULTIMODAL_SKILL_CONSULT_MODE", "inline"),
                Map.entry("MOBILE_SKILL_CONSULT_MODE", "branch")
        ));
        MobileGuiRuntimeSettings branch = MobileGuiRuntimeSettings.fromEnvironment(Map.of(
                "MULTIMODAL_SKILL_CONSULT_MODE", "surprising-value"
        ));

        assertEquals(SkillConsultMode.INLINE, inline.getSkillConsultMode());
        assertEquals(SkillConsultMode.BRANCH, branch.getSkillConsultMode());
    }
}
