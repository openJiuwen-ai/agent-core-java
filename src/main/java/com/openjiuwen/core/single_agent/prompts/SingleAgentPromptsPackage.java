/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.prompts;

import java.util.Set;

/**
 * Package bridge for single-agent prompt exports.
 * <p>
 * Mirrors Python's module docstring and exports in
 * {@code openjiuwen/core/single_agent/prompts/__init__.py}.
 */
public final class SingleAgentPromptsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/single_agent/prompts/__init__.py";
    public static final String DESCRIPTION = "System prompt builder for single-agent layer.";
    public static final String DEFAULT_LANGUAGE = SystemPromptBuilder.DEFAULT_LANGUAGE;
    public static final Set<String> SUPPORTED_LANGUAGES = SystemPromptBuilder.SUPPORTED_LANGUAGES;
    public static final Class<PromptSection> PROMPT_SECTION = PromptSection.class;
    public static final Class<SystemPromptBuilder> SYSTEM_PROMPT_BUILDER = SystemPromptBuilder.class;

    private SingleAgentPromptsPackage() {
    }

    public static SystemPromptBuilder newBuilder() {
        return new SystemPromptBuilder();
    }

    public static SystemPromptBuilder newBuilder(String language) {
        return new SystemPromptBuilder(language);
    }
}
