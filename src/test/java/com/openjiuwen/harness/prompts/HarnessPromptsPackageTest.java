/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessPromptsPackageTest {

    @Test
    void resolveLanguagePrefersConfigThenEnvThenDefault() {
        assertThat(HarnessPromptsPackage.resolveLanguage("en", "cn")).isEqualTo("en");
        assertThat(HarnessPromptsPackage.resolveLanguage("de", "en")).isEqualTo("en");
        assertThat(HarnessPromptsPackage.resolveLanguage(null, "de"))
                .isEqualTo(HarnessPromptsPackage.DEFAULT_LANGUAGE);
    }

    @Test
    void resolveModeAcceptsValueOrEnumNameAndFallsBackToFull() {
        assertThat(HarnessPromptsPackage.resolveMode("minimal")).isEqualTo(PromptMode.MINIMAL);
        assertThat(HarnessPromptsPackage.resolveMode("NONE")).isEqualTo(PromptMode.NONE);
        assertThat(HarnessPromptsPackage.resolveMode("invalid")).isEqualTo(PromptMode.FULL);
        assertThat(HarnessPromptsPackage.resolveMode(null)).isEqualTo(PromptMode.FULL);
    }

    @Test
    void bridgeExportsExistingPromptSurface() {
        assertThat(HarnessPromptsPackage.SUPPORTED_LANGUAGES).contains("cn", "en");
        assertThat(HarnessPromptsPackage.PROMPT_REPORT).isEqualTo(PromptReport.class);
        assertThat(HarnessPromptsPackage.SYSTEM_PROMPT_BUILDER).isEqualTo(SystemPromptBuilder.class);
        assertThat(HarnessPromptsPackage.sanitizePath("C:\\\\tmp\\\\a")).isEqualTo("C:\\\\tmp\\\\a");
        assertThat(HarnessPromptsPackage.sanitizeUserContent("x".repeat(2100))).hasSize(2000);
    }
}
