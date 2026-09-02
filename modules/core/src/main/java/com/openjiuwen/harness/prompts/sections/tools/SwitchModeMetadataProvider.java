/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SwitchModeMetadataProvider.
 * 
 * @since 0.1.7
 */
public final class SwitchModeMetadataProvider implements ToolMetadataProvider {
    private static final String DESCRIPTION_CN = "在 normal 与 plan 模式间切换当前会话模式。";
    private static final String DESCRIPTION_EN = "Switch the current session between normal and plan modes.";

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "switch_mode";
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
        return "en".equalsIgnoreCase(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
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
        String description = "en".equalsIgnoreCase(language) ? "Target mode: normal or plan" : "目标模式：normal 或 plan";
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", Map.of("type", "string", "enum", List.of("normal", "plan"), "description", description));
        return Map.of("type", "object", "properties", properties, "required", List.of("mode"));
    }
}
