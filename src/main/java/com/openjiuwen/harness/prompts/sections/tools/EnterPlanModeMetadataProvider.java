/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class EnterPlanModeMetadataProvider implements ToolMetadataProvider {
    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "enter_plan_mode";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription(String language) {
        if ("en".equalsIgnoreCase(language)) {
            return "Initialize the plan workflow and switch the current session into plan mode.";
        }
        return "初始化 plan 工作流，并将当前会话切换到 plan 模式。";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputParams(String language) {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
        );
    }
}
