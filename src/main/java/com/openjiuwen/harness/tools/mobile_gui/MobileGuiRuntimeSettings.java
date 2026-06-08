/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import java.util.Map;

/**
 * Runtime settings for the mobile GUI (VLM grounding) subagent.
 *
 * <p>Mirrors Python's {@code MobileGuiRuntimeSettings} in
 * {@code openjiuwen/harness/tools/mobile_gui/config.py}.
 */
public final class MobileGuiRuntimeSettings {

    private final String deviceSerial;
    private final Object device;
    private final boolean cleanupGoHome;
    private final boolean healthCheck;
    private final int vlmGroundingMaxWidth;
    private final int vlmGroundingJpegQuality;
    private final double vlmGroundingUiSettleSeconds;
    private final int vlmCoordinateScale;
    private final int vlmClaudeImageWidth;
    private final int vlmClaudeImageHeight;
    private final int vlmClaudeOpusMaxDimension;
    private final int mcsScreenshotsToKeep;
    private final int scrollDefaultWidth;
    private final int scrollDefaultHeight;
    private final int scrollDurationMsDefault;
    private final double waitGuiLoadMinSeconds;
    private final double waitGuiLoadMaxSeconds;
    private final double waitGuiLoadDefaultSeconds;
    private final boolean vlmGroundingOnlySettleAfterTools;
    private final int contextMaxMessageNum;
    private final int contextDefaultWindowRoundNum;
    private final SkillConsultMode skillConsultMode;
    private final int skillBranchMaxImages;
    private final int skillBranchMaxConsultsPerSkill;
    private final int skillBranchPreviousStepsTurns;

    public MobileGuiRuntimeSettings(
            String deviceSerial,
            Object device,
            boolean cleanupGoHome,
            boolean healthCheck,
            int vlmGroundingMaxWidth,
            int vlmGroundingJpegQuality,
            double vlmGroundingUiSettleSeconds,
            int vlmCoordinateScale,
            int vlmClaudeImageWidth,
            int vlmClaudeImageHeight,
            int vlmClaudeOpusMaxDimension,
            int mcsScreenshotsToKeep,
            int scrollDefaultWidth,
            int scrollDefaultHeight,
            int scrollDurationMsDefault,
            double waitGuiLoadMinSeconds,
            double waitGuiLoadMaxSeconds,
            double waitGuiLoadDefaultSeconds,
            boolean vlmGroundingOnlySettleAfterTools,
            int contextMaxMessageNum,
            int contextDefaultWindowRoundNum,
            SkillConsultMode skillConsultMode,
            int skillBranchMaxImages,
            int skillBranchMaxConsultsPerSkill,
            int skillBranchPreviousStepsTurns
    ) {
        this.deviceSerial = deviceSerial;
        this.device = device;
        this.cleanupGoHome = cleanupGoHome;
        this.healthCheck = healthCheck;
        this.vlmGroundingMaxWidth = vlmGroundingMaxWidth;
        this.vlmGroundingJpegQuality = vlmGroundingJpegQuality;
        this.vlmGroundingUiSettleSeconds = vlmGroundingUiSettleSeconds;
        this.vlmCoordinateScale = vlmCoordinateScale;
        this.vlmClaudeImageWidth = vlmClaudeImageWidth;
        this.vlmClaudeImageHeight = vlmClaudeImageHeight;
        this.vlmClaudeOpusMaxDimension = vlmClaudeOpusMaxDimension;
        this.mcsScreenshotsToKeep = mcsScreenshotsToKeep;
        this.scrollDefaultWidth = scrollDefaultWidth;
        this.scrollDefaultHeight = scrollDefaultHeight;
        this.scrollDurationMsDefault = scrollDurationMsDefault;
        this.waitGuiLoadMinSeconds = waitGuiLoadMinSeconds;
        this.waitGuiLoadMaxSeconds = waitGuiLoadMaxSeconds;
        this.waitGuiLoadDefaultSeconds = waitGuiLoadDefaultSeconds;
        this.vlmGroundingOnlySettleAfterTools = vlmGroundingOnlySettleAfterTools;
        this.contextMaxMessageNum = contextMaxMessageNum;
        this.contextDefaultWindowRoundNum = contextDefaultWindowRoundNum;
        this.skillConsultMode = skillConsultMode;
        this.skillBranchMaxImages = skillBranchMaxImages;
        this.skillBranchMaxConsultsPerSkill = skillBranchMaxConsultsPerSkill;
        this.skillBranchPreviousStepsTurns = skillBranchPreviousStepsTurns;
    }

    public static MobileGuiRuntimeSettings fromEnv() {
        return fromEnvironment(System.getenv());
    }

    public static MobileGuiRuntimeSettings fromEnvironment(Map<String, String> env) {
        Map<String, String> values = env == null ? Map.of() : env;
        return new MobileGuiRuntimeSettings(
                getStr(values, "DEVICE_SERIAL", "emulator-5554"),
                null,
                true,
                true,
                getInt(values, "VLM_GROUNDING_MAX_WIDTH", 1280),
                getInt(values, "VLM_GROUNDING_JPEG_QUALITY", 85),
                getDouble(values, "VLM_GROUNDING_UI_SETTLE_SECONDS", 1.0),
                getInt(values, "VLM_COORDINATE_SCALE", 1000),
                getInt(values, "VLM_CLAUDE_IMAGE_WIDTH", 1280),
                getInt(values, "VLM_CLAUDE_IMAGE_HEIGHT", 720),
                getInt(values, "VLM_CLAUDE_OPUS_MAX_DIMENSION", 1280),
                getInt(values, "MCS_SCREENSHOTS_TO_KEEP", 3),
                getInt(values, "SCROLL_DEFAULT_WIDTH", 1080),
                getInt(values, "SCROLL_DEFAULT_HEIGHT", 1920),
                getInt(values, "SCROLL_DURATION_MS_DEFAULT", 300),
                getDouble(values, "WAIT_GUI_LOAD_MIN_SECONDS", 0.5),
                getDouble(values, "WAIT_GUI_LOAD_MAX_SECONDS", 30.0),
                getDouble(values, "WAIT_GUI_LOAD_DEFAULT_SECONDS", 2.0),
                true,
                getInt(values, "MOBILE_CONTEXT_MAX_MESSAGES", 120),
                getInt(values, "MOBILE_CONTEXT_WINDOW_ROUNDS", 20),
                SkillConsultMode.fromRaw(getEnvStr(
                        values,
                        "MULTIMODAL_SKILL_CONSULT_MODE",
                        "MOBILE_SKILL_CONSULT_MODE",
                        "branch"
                )),
                getEnvInt(values, "MULTIMODAL_SKILL_BRANCH_MAX_IMAGES", "MOBILE_SKILL_BRANCH_MAX_IMAGES", 4),
                getEnvInt(
                        values,
                        "MULTIMODAL_SKILL_BRANCH_MAX_CONSULTS_PER_SKILL",
                        "MOBILE_SKILL_BRANCH_MAX_CONSULTS_PER_SKILL",
                        2
                ),
                getEnvInt(
                        values,
                        "MULTIMODAL_SKILL_BRANCH_PREVIOUS_STEPS_TURNS",
                        "MOBILE_SKILL_BRANCH_PREVIOUS_STEPS_TURNS",
                        10
                )
        );
    }

    public String getDeviceSerial() {
        return deviceSerial;
    }

    public Object getDevice() {
        return device;
    }

    public boolean isCleanupGoHome() {
        return cleanupGoHome;
    }

    public boolean isHealthCheck() {
        return healthCheck;
    }

    public int getVlmGroundingMaxWidth() {
        return vlmGroundingMaxWidth;
    }

    public int getVlmGroundingJpegQuality() {
        return vlmGroundingJpegQuality;
    }

    public double getVlmGroundingUiSettleSeconds() {
        return vlmGroundingUiSettleSeconds;
    }

    public int getVlmCoordinateScale() {
        return vlmCoordinateScale;
    }

    public int getVlmClaudeImageWidth() {
        return vlmClaudeImageWidth;
    }

    public int getVlmClaudeImageHeight() {
        return vlmClaudeImageHeight;
    }

    public int getVlmClaudeOpusMaxDimension() {
        return vlmClaudeOpusMaxDimension;
    }

    public int getMcsScreenshotsToKeep() {
        return mcsScreenshotsToKeep;
    }

    public int getScrollDefaultWidth() {
        return scrollDefaultWidth;
    }

    public int getScrollDefaultHeight() {
        return scrollDefaultHeight;
    }

    public int getScrollDurationMsDefault() {
        return scrollDurationMsDefault;
    }

    public double getWaitGuiLoadMinSeconds() {
        return waitGuiLoadMinSeconds;
    }

    public double getWaitGuiLoadMaxSeconds() {
        return waitGuiLoadMaxSeconds;
    }

    public double getWaitGuiLoadDefaultSeconds() {
        return waitGuiLoadDefaultSeconds;
    }

    public boolean isVlmGroundingOnlySettleAfterTools() {
        return vlmGroundingOnlySettleAfterTools;
    }

    public int getContextMaxMessageNum() {
        return contextMaxMessageNum;
    }

    public int getContextDefaultWindowRoundNum() {
        return contextDefaultWindowRoundNum;
    }

    public SkillConsultMode getSkillConsultMode() {
        return skillConsultMode;
    }

    public int getSkillBranchMaxImages() {
        return skillBranchMaxImages;
    }

    public int getSkillBranchMaxConsultsPerSkill() {
        return skillBranchMaxConsultsPerSkill;
    }

    public int getSkillBranchPreviousStepsTurns() {
        return skillBranchPreviousStepsTurns;
    }

    private static String getStr(Map<String, String> env, String name, String defaultValue) {
        String value = env.get(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    private static int getInt(Map<String, String> env, String name, int defaultValue) {
        String value = env.get(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private static double getDouble(Map<String, String> env, String name, double defaultValue) {
        String value = env.get(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Double.parseDouble(value.trim());
    }

    private static String getEnvStr(Map<String, String> env, String primary, String legacy, String defaultValue) {
        String primaryValue = env.get(primary);
        if (primaryValue != null && !primaryValue.trim().isEmpty()) {
            return primaryValue;
        }
        return getStr(env, legacy, defaultValue);
    }

    private static int getEnvInt(Map<String, String> env, String primary, String legacy, int defaultValue) {
        String primaryValue = env.get(primary);
        if (primaryValue != null && !primaryValue.trim().isEmpty()) {
            return Integer.parseInt(primaryValue.trim());
        }
        return getInt(env, legacy, defaultValue);
    }
}
