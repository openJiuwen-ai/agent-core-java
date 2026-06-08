/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.prompts;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's prompt-section assembly checks in
 * {@code openjiuwen/auto_harness/prompts/sections.py}.
 */
class PromptSectionsTest {

    @TempDir
    Path tempDir;

    @Test
    void buildAutoHarnessSectionsIncludesIdentityPlatformCiAndWisdom() throws IOException {
        Files.writeString(tempDir.resolve("identity.md"), "identity-body", StandardCharsets.UTF_8);

        List<PromptSection> sections = PromptSections.buildAutoHarnessSections(
                "gate-rules",
                "wisdom-notes",
                tempDir.toString(),
                "win32",
                "Windows 11",
                "Git Bash / cmd.exe"
        );

        assertEquals(List.of(
                "auto_harness_identity",
                "auto_harness_platform_adaptation",
                "auto_harness_ci_gate",
                "auto_harness_wisdom"
        ), sections.stream().map(PromptSection::getName).toList());
        assertEquals(10, sections.get(0).getPriority());
        assertEquals(89, sections.get(1).getPriority());
        assertEquals(20, sections.get(2).getPriority());
        assertEquals(30, sections.get(3).getPriority());
        assertEquals("identity-body", sections.get(0).render("cn"));
        assertTrue(sections.get(1).render("en").contains("Current platform: `win32`"));
        assertTrue(sections.get(1).render("en").contains("Git Bash / cmd.exe"));
        assertTrue(sections.get(1).render("en").contains("Windows `mkdir` does not support the `-p` flag"));
        assertTrue(sections.get(2).render("en").contains("gate-rules"));
        assertTrue(sections.get(3).render("en").contains("wisdom-notes"));
    }

    @Test
    void resolveShellNameMatchesPlatformRules() {
        assertEquals("Git Bash / cmd.exe", PromptSections.resolveShellName("win32", true));
        assertEquals("cmd.exe", PromptSections.resolveShellName("win32", false));
        assertEquals("zsh (default) / bash", PromptSections.resolveShellName("darwin", false));
        assertEquals("bash", PromptSections.resolveShellName("linux", false));
    }

    @Test
    void loadIdentityReturnsEmptyStringWhenMissing() {
        assertEquals("", PromptSections.loadIdentity(tempDir.toString()));
    }
}
