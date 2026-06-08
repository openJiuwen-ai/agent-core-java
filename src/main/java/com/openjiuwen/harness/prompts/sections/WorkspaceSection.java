/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.prompts.workspace_content.WorkspaceHeader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Workspace prompt section helpers.
 * <p>
 * Mirrors Python's {@code workspace} in
 * {@code openjiuwen/harness/prompts/sections/workspace.py}.
 */
public final class WorkspaceSection {

    private static final int WORKSPACE_PRIORITY = 70;

    private WorkspaceSection() {
    }

    public static String buildWorkspaceContent(Object sysOperation, Object workspace, String language) {
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        String rootPath = resolveRootPath(workspace);
        String header = WorkspaceHeader.getWorkspaceHeader(resolvedLanguage);
        String importantFiles = WorkspaceHeader.getImportantFiles(resolvedLanguage);

        if ("en".equals(resolvedLanguage)) {
            return header + "Your working directory is: `" + rootPath + "`\n\n" + importantFiles;
        }
        return header + "你的工作目录是：`" + rootPath + "`\n\n" + importantFiles;
    }

    public static PromptSection buildWorkspaceSection(Object sysOperation, Object workspace, String language) {
        if (workspace == null) {
            return null;
        }
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        return new PromptSection(
                SectionName.WORKSPACE,
                Map.of(resolvedLanguage, buildWorkspaceContent(sysOperation, workspace, resolvedLanguage)),
                WORKSPACE_PRIORITY
        );
    }

    private static String resolveRootPath(Object workspace) {
        if (workspace == null) {
            return "";
        }
        for (String methodName : new String[]{"getRootPath", "rootPath"}) {
            try {
                Method method = workspace.getClass().getMethod(methodName);
                Object value = method.invoke(workspace);
                return value == null ? "" : String.valueOf(value);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            Field field = workspace.getClass().getField("rootPath");
            Object value = field.get(workspace);
            return value == null ? "" : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
        }
        return "";
    }
}
