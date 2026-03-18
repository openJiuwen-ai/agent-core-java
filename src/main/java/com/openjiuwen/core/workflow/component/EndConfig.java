/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component;


import java.util.Map;

/**
 * Configuration for the End component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.end_comp.EndConfig}.
 */
public class EndConfig {

    private final String responseTemplate;

    public EndConfig(String responseTemplate) {
        if (responseTemplate == null || responseTemplate.isEmpty()) {
            throw new IllegalArgumentException("responseTemplate must not be null or empty");
        }
        this.responseTemplate = responseTemplate;
    }

    @SuppressWarnings("unchecked")
    public static EndConfig fromMap(Map<String, Object> map) {
        String template = (String) map.getOrDefault("responseTemplate",
                map.get("response_template"));
        return new EndConfig(template);
    }

    public String getResponseTemplate() {
        return responseTemplate;
    }
}
