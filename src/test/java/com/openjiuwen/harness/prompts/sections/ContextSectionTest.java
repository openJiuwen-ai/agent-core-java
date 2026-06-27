/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's context prompt section behavior in
 * {@code openjiuwen/harness/prompts/sections/context.py}.
 */
class ContextSectionTest {

    @Test
    void templateDetectorMatchesPythonRules() {
        assertTrue(ContextSection.isUnfilledTemplate("<!-- comment only -->"));
        assertTrue(ContextSection.isUnfilledTemplate("# Title\n\n## Subtitle"));
        assertTrue(ContextSection.isUnfilledTemplate("# Memory\n\nWhat should be saved here"));
        assertFalse(ContextSection.isUnfilledTemplate("# Title\n\nReal project note"));
        assertFalse(ContextSection.isUnfilledTemplate("x".repeat(501)));
    }

    @Test
    void buildContextSectionReturnsNullWithoutWorkspace() {
        assertNull(ContextSection.buildContextSection(null, null, "cn", null, null, true));
    }

    @Test
    void buildContextSectionWrapsHeaderAndExtraTools() {
        PromptSection section = ContextSection.buildContextSection(
                null,
                new Workspace("/repo/project", "cn"),
                "cn",
                "# 可用工具\n\n- read_file / write_file / edit_file: 文件读写编辑\n",
                null,
                false
        );

        assertEquals(SectionName.CONTEXT, section.getName());
        assertEquals(80, section.getPriority());
        assertTrue(section.render("cn").startsWith("# 项目上下文"));
        assertTrue(section.render("cn").contains("[以下文件仅在有实际内容时注入，空文件跳过]"));
        assertTrue(section.render("cn").contains("read_file / write_file / edit_file"));
    }

    @Test
    void buildToolsContentGroupsToolsAndHidesCronInternals() {
        AbilityManager manager = new AbilityManager();
        manager.add(List.of(
                ToolCard.builder().name("read_file").description("Read a file").build(),
                ToolCard.builder().name("write_file").description("Write a file").build(),
                ToolCard.builder().name("edit_file").description("Edit a file").build(),
                ToolCard.builder().name("cron_list_jobs").description("Hidden cron admin").build(),
                ToolCard.builder().name("bash").description("Execute shell").build(),
                ToolCard.builder().name("custom_tool").description("First line\nSecond line").build()
        ));

        String content = ContextSection.buildToolsContent(manager, "en");

        assertTrue(content.startsWith("# Available Tools"));
        assertTrue(content.contains("- read_file / write_file / edit_file: Read, write, and edit files"));
        assertTrue(content.contains("## bash Guidelines"));
        assertTrue(content.contains("- custom_tool: First line"));
        assertFalse(content.contains("cron_list_jobs"));
    }
}
