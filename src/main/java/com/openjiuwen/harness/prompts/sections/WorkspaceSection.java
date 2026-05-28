/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workspace prompt section builder.
 * <p>
 * Mirrors Python's {@code workspace} in
 * {@code openjiuwen.harness.prompts.sections.workspace}.
 */
public final class WorkspaceSection {

    private WorkspaceSection() {
    }

    private static final String CN = "# 工作区\n"
            + "\n"
            + "- 当前位于项目工作区中\n"
            + "- 优先在工作区内操作\n"
            + "- 注意保护重要文件，修改前先备份\n";

    private static final String EN = "# Workspace\n"
            + "\n"
            + "- Currently in the project workspace\n"
            + "- Prefer operating within the workspace\n"
            + "- Protect important files; back up before modifying\n";

    private static final Map<String, String> WORKSPACE = new LinkedHashMap<>();

    static {
        WORKSPACE.put("cn", CN);
        WORKSPACE.put("en", EN);
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.WORKSPACE, WORKSPACE, 75);
    }
}
