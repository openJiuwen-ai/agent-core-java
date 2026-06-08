/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's workspace section contract in
 * {@code openjiuwen/harness/prompts/sections/workspace.py}.
 */
class WorkspaceSectionTest {

    @Test
    void buildWorkspaceContentUsesChineseByDefault() {
        String content = WorkspaceSection.buildWorkspaceContent(null, new StubWorkspace("/repo/project"), "cn");

        assertTrue(content.startsWith("# 工作空间"));
        assertTrue(content.contains("你的工作目录是：`/repo/project`"));
        assertTrue(content.contains("## 工作目录下重要文件"));
    }

    @Test
    void buildWorkspaceContentSupportsEnglish() {
        String content = WorkspaceSection.buildWorkspaceContent(null, new StubWorkspace("/repo/project"), "en");

        assertTrue(content.startsWith("# Workspace"));
        assertTrue(content.contains("Your working directory is: `/repo/project`"));
        assertTrue(content.contains("## Important Files in Working Directory"));
    }

    @Test
    void buildWorkspaceSectionWrapsPromptSection() {
        PromptSection section = WorkspaceSection.buildWorkspaceSection(null, new StubWorkspace("/repo/project"), "cn");

        assertNotNull(section);
        assertEquals(SectionName.WORKSPACE, section.getName());
        assertEquals(70, section.getPriority());
        assertTrue(section.render("cn").contains("/repo/project"));
    }

    @Test
    void buildWorkspaceSectionReturnsNullForMissingWorkspace() {
        assertNull(WorkspaceSection.buildWorkspaceSection(null, null, "cn"));
    }

    private record StubWorkspace(String rootPath) {
        public String getRootPath() {
            return rootPath;
        }
    }
}
