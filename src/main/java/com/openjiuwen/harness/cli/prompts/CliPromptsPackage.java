/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.prompts;

import java.util.List;

/**
 * Package bridge for CLI prompt exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/harness/cli/prompts/__init__.py}.
 */
public final class CliPromptsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/cli/prompts/__init__.py";
    public static final String DESCRIPTION = "Prompt construction for the CLI agent.";
    public static final List<String> EXPORTED_SYMBOLS = List.of("build_system_prompt");
    public static final Class<CliPromptBuilder> PROMPT_BUILDER = CliPromptBuilder.class;

    private CliPromptsPackage() {
    }

    public static String buildSystemPrompt(String cwd, String model, String provider) {
        return CliPromptBuilder.buildSystemPrompt(cwd, model, provider);
    }

    public static String buildSystemPrompt(String cwd, String model, String provider, String language) {
        return CliPromptBuilder.buildSystemPrompt(cwd, model, provider, language);
    }
}
