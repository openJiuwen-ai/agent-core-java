/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

/**
 * Model client provider type enumeration.
 * <p>
 * Mirrors Python's {@code ProviderType} enum.
 * 
 * @since 0.1.7
 */
public enum ProviderType {
    OpenAI("OpenAI"),
    OpenRouter("OpenRouter"),
    SiliconFlow("SiliconFlow"),
    DashScope("DashScope");

    private final String value;

    ProviderType(String value) {
        this.value = value;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }

    /**
     * Look up a provider type by its string value.
     * 
     * @param value provider name string
     * @return the matching {@code ProviderType}
     * @since 0.1.7
     */
    public static ProviderType fromValue(String value) {
        for (ProviderType t : values()) {
            if (t.value.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown ProviderType: " + value);
    }
}
