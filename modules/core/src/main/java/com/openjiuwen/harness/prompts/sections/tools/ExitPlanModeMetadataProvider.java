/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * ExitPlanModeMetadataProvider.
 * 
 * @since 0.1.7
 */
public final class ExitPlanModeMetadataProvider implements ToolMetadataProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "exit_plan_mode";
    }

    /**
     * getDescription.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDescription(String language) {
        if ("en".equalsIgnoreCase(language)) {
            return "Finish the planning phase and switch the current session back to normal mode.";
        }
        return "结束规划阶段，并将当前会话切回 normal 模式。";
    }

    /**
     * getInputParams.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getInputParams(String language) {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }
}
