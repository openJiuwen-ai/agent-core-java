/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.mobile_gui.test_config} in
 * {@code tests/unit_tests/harness/tools/mobile_gui/test_config.py}.
 */
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

    @Test
    void normalizesSkillConsultModeAliasesAndUnknowns() {
        assertEquals(SkillConsultMode.INLINE, SkillConsultMode.fromRaw("inline"));
        assertEquals(SkillConsultMode.INLINE, SkillConsultMode.fromRaw("INLINE"));
        assertEquals(SkillConsultMode.BRANCH, SkillConsultMode.fromRaw("BRANCH"));
        assertEquals(SkillConsultMode.BRANCH, SkillConsultMode.fromRaw("branch"));
        assertEquals(SkillConsultMode.BRANCH, SkillConsultMode.fromRaw("unknown"));
        assertEquals(SkillConsultMode.BRANCH, SkillConsultMode.fromRaw(""));
    }

    @Test
    void skillConsultModeUsesPrimaryEnvWhenSet() {
        MobileGuiRuntimeSettings settings = MobileGuiRuntimeSettings.fromEnvironment(Map.of(
                "MULTIMODAL_SKILL_CONSULT_MODE", "inline",
                "MOBILE_SKILL_CONSULT_MODE", "branch"
        ));

        assertEquals(SkillConsultMode.INLINE, settings.getSkillConsultMode());
    }

    @Test
    void skillConsultModeFallsBackToLegacyEnv() {
        MobileGuiRuntimeSettings settings = MobileGuiRuntimeSettings.fromEnvironment(Map.of(
                "MOBILE_SKILL_CONSULT_MODE", "branch"
        ));

        assertEquals(SkillConsultMode.BRANCH, settings.getSkillConsultMode());
    }

    @Test
    void skillBranchLimitsLoadFromMultimodalEnv() {
        MobileGuiRuntimeSettings settings = MobileGuiRuntimeSettings.fromEnvironment(Map.of(
                "MULTIMODAL_SKILL_BRANCH_MAX_IMAGES", "7",
                "MULTIMODAL_SKILL_BRANCH_MAX_CONSULTS_PER_SKILL", "3",
                "MULTIMODAL_SKILL_BRANCH_PREVIOUS_STEPS_TURNS", "5"
        ));

        assertEquals(7, settings.getSkillBranchMaxImages());
        assertEquals(3, settings.getSkillBranchMaxConsultsPerSkill());
        assertEquals(5, settings.getSkillBranchPreviousStepsTurns());
    }

    @Test
    void directMobileGuiEnvOverridesAreApplied() {
        MobileGuiRuntimeSettings settings = MobileGuiRuntimeSettings.fromEnvironment(Map.of(
                "VLM_GROUNDING_MAX_WIDTH", "512",
                "MCS_SCREENSHOTS_TO_KEEP", "5"
        ));

        assertEquals(512, settings.getVlmGroundingMaxWidth());
        assertEquals(5, settings.getMcsScreenshotsToKeep());
    }
}
