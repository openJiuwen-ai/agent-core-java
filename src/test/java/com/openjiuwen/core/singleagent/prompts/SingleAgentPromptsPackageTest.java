/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.prompts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SingleAgentPromptsPackageTest {

    @Test
    void exportsMatchPythonModuleSurface() {
        assertThat(SingleAgentPromptsPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/single_agent/prompts/__init__.py");
        assertThat(SingleAgentPromptsPackage.DESCRIPTION)
                .isEqualTo("System prompt builder for single-agent layer.");
        assertThat(SingleAgentPromptsPackage.DEFAULT_LANGUAGE)
                .isEqualTo(SystemPromptBuilder.DEFAULT_LANGUAGE);
        assertThat(SingleAgentPromptsPackage.SUPPORTED_LANGUAGES)
                .isEqualTo(SystemPromptBuilder.SUPPORTED_LANGUAGES);
        assertThat(SingleAgentPromptsPackage.PROMPT_SECTION).isEqualTo(PromptSection.class);
        assertThat(SingleAgentPromptsPackage.SYSTEM_PROMPT_BUILDER).isEqualTo(SystemPromptBuilder.class);
    }

    @Test
    void builderFactoriesDelegateToSystemPromptBuilder() {
        assertThat(SingleAgentPromptsPackage.newBuilder().getLanguage())
                .isEqualTo(SystemPromptBuilder.DEFAULT_LANGUAGE);
        assertThat(SingleAgentPromptsPackage.newBuilder("en").getLanguage())
                .isEqualTo("en");
    }
}
