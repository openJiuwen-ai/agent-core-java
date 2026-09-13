/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import java.util.Map;

/**
 * Configuration for the End component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.end_comp.EndConfig}.
 * 
 * @since 0.1.7
 */
public class EndConfig {
    private final String responseTemplate;

    /**
     * EndConfig.
     * 
     * @param responseTemplate responseTemplate
     * @since 0.1.7
     */
    public EndConfig(String responseTemplate) {
        if (responseTemplate == null || responseTemplate.isEmpty()) {
            throw new IllegalArgumentException("responseTemplate must not be null or empty");
        }
        this.responseTemplate = responseTemplate;
    }

    /**
     * fromMap.
     * 
     * @param map map
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static EndConfig fromMap(Map<String, Object> map) {
        String template = (String) map.getOrDefault("responseTemplate", map.get("response_template"));
        return new EndConfig(template);
    }

    /**
     * getResponseTemplate.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getResponseTemplate() {
        return responseTemplate;
    }
}
