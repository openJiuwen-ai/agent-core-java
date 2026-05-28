/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

/**
 * Language resolution utilities.
 * <p>
 * Mirrors Python's {@code resolve_language / resolve_mode} in
 * {@code openjiuwen.harness.prompts.__init__}.
 */
public final class PromptUtils {

    private PromptUtils() {
    }

    /** Resolve prompt language. Priority: config param > env var > default. */
    public static String resolveLanguage(String configLanguage) {
        if (configLanguage != null && isValidLanguage(configLanguage)) {
            return configLanguage;
        }
        String envLang = System.getenv("AGENT_PROMPT_LANGUAGE");
        if (envLang != null && isValidLanguage(envLang)) {
            return envLang;
        }
        return PromptSection.DEFAULT_LANGUAGE;
    }

    /** Resolve prompt language with no config. */
    public static String resolveLanguage() {
        return resolveLanguage(null);
    }

    private static boolean isValidLanguage(String lang) {
        for (String supported : PromptSection.SUPPORTED_LANGUAGES) {
            if (supported.equals(lang)) {
                return true;
            }
        }
        return false;
    }
}
