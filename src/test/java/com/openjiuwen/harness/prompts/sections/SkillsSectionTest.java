/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's skills section contract in
 * {@code openjiuwen/harness/prompts/sections/skills.py}.
 */
class SkillsSectionTest {

    @Test
    void buildSkillLineAppendsOptionalPath() {
        String line = SkillsSection.buildSkillLine(1, "browser", "Inspect pages", "skills/browser/SKILL.md");

        assertEquals("1. browser: Inspect pages\n   Path: skills/browser/SKILL.md", line);
    }

    @Test
    void buildSkillLinesSkipsEmptyEntries() {
        String lines = SkillsSection.buildSkillLines(List.of("one", "", "two"));

        assertEquals("one\n\ntwo", lines);
    }

    @Test
    void buildAllModeSkillPromptFallsBackWhenNoSkillsExist() {
        String content = SkillsSection.buildAllModeSkillPrompt("  ", "cn");

        assertTrue(content.contains("当前任务没有选择任何技能"));
    }

    @Test
    void buildAllModeSkillPromptWrapsRenderedLines() {
        String content = SkillsSection.buildAllModeSkillPrompt("1. browser: Inspect pages", "en");

        assertTrue(content.startsWith("# Skills"));
        assertTrue(content.contains("1. browser: Inspect pages"));
        assertTrue(content.endsWith("SKILL.md first."));
    }

    @Test
    void buildAutoListModeSkillPromptPreservesUpdatedShellGuidance() {
        String content = SkillsSection.buildAutoListModeSkillPrompt("cn");

        assertTrue(content.contains("Git Bash/PowerShell"));
        assertTrue(content.contains("Linux/macOS"));
    }

    @Test
    void getListSkillSystemPromptSupportsEnglish() {
        String content = SkillsSection.getListSkillSystemPrompt("en");

        assertTrue(content.contains("list_skill selector"));
        assertTrue(content.contains("\"skills\""));
    }

    @Test
    void buildSkillsSectionCreatesPromptSection() {
        PromptSection section = SkillsSection.buildSkillsSection("1. browser: Inspect pages", "cn", "all");

        assertNotNull(section);
        assertEquals(SectionName.SKILLS, section.getName());
        assertEquals(40, section.getPriority());
        assertTrue(section.render("cn").contains("1. browser: Inspect pages"));
    }

    @Test
    void buildSkillsSectionRejectsUnknownMode() {
        assertNull(SkillsSection.buildSkillsSection("", "cn", "invalid"));
    }
}
