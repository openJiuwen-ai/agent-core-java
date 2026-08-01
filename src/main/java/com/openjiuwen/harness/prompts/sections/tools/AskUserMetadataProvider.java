/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ask-user tool metadata provider.
 * <p>
 * Aligned with Python openjiuwen's harness.prompts.sections.tools.ask_user.
 * 
 * @since 0.1.7
 */
public final class AskUserMetadataProvider implements ToolMetadataProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "ask_user";
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
        if ("en".equals(language)) {
            return "Interrupts the execution and requests input from the user";
        }
        return "中断执行并向用户请求输入";
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
        String queryDescription = "en".equals(language) ? "The question to present to the user." : "向用户展示的问题";

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("query", Map.of("type", "string", "description", queryDescription));

        return Map.of("type", "object", "properties", properties, "required", List.of("query"));
    }
}
