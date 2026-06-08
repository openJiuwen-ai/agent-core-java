/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.sections.SectionName;

import java.util.Locale;
import java.util.Set;

/**
 * Mirrors Python's exports and helpers in
 * {@code openjiuwen/harness/prompts/__init__.py}.
 */
public final class HarnessPromptsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/prompts/__init__.py";
    public static final String DEFAULT_LANGUAGE =
            com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder.DEFAULT_LANGUAGE;
    public static final Set<String> SUPPORTED_LANGUAGES =
            com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder.SUPPORTED_LANGUAGES;
    public static final Class<PromptMode> PROMPT_MODE = PromptMode.class;
    public static final Class<PromptSection> PROMPT_SECTION = PromptSection.class;
    public static final Class<PromptReport> PROMPT_REPORT = PromptReport.class;
    public static final Class<SystemPromptBuilder> SYSTEM_PROMPT_BUILDER = SystemPromptBuilder.class;
    public static final Class<SectionName> SECTIONS = SectionName.class;

    private HarnessPromptsPackage() {
    }

    public static String resolveLanguage(String configLanguage) {
        return resolveLanguage(configLanguage, System.getenv("AGENT_PROMPT_LANGUAGE"));
    }

    static String resolveLanguage(String configLanguage, String environmentLanguage) {
        if (configLanguage != null && SUPPORTED_LANGUAGES.contains(configLanguage)) {
            return configLanguage;
        }
        if (environmentLanguage != null && SUPPORTED_LANGUAGES.contains(environmentLanguage)) {
            return environmentLanguage;
        }
        return DEFAULT_LANGUAGE;
    }

    public static PromptMode resolveMode(String configMode) {
        if (configMode != null) {
            for (PromptMode value : PromptMode.values()) {
                if (value.value().equals(configMode)
                        || value.name().equals(configMode.toUpperCase(Locale.ROOT))) {
                    return value;
                }
            }
        }
        return PromptMode.FULL;
    }

    public static String sanitizePath(String path) {
        return PromptSanitizer.sanitizePath(path);
    }

    public static String sanitizeUserContent(String content) {
        return PromptSanitizer.sanitizeUserContent(content);
    }
}
