/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.prompts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliPromptsPackageTest {

    @TempDir
    Path tempDir;

    @Test
    void exposesPythonPackageBridge() {
        assertEquals("openjiuwen/harness/cli/prompts/__init__.py", CliPromptsPackage.PYTHON_MODULE);
        assertEquals("Prompt construction for the CLI agent.", CliPromptsPackage.DESCRIPTION);
        assertEquals(List.of("build_system_prompt"), CliPromptsPackage.EXPORTED_SYMBOLS);
        assertSame(CliPromptBuilder.class, CliPromptsPackage.PROMPT_BUILDER);
    }

    @Test
    void delegatesBuildSystemPromptExportToBuilder() {
        String viaPackage = CliPromptsPackage.buildSystemPrompt(tempDir.toString(), "gpt-4o", "OpenAI");
        String viaBuilder = CliPromptBuilder.buildSystemPrompt(tempDir.toString(), "gpt-4o", "OpenAI");

        assertEquals(viaBuilder, viaPackage);
        assertTrue(viaPackage.contains("Environment"));
    }
}
