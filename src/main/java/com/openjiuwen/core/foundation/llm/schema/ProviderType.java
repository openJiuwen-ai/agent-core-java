/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Model client provider names.
 *
 * <p>Mirrors Python's {@code ProviderType} in
 * {@code openjiuwen/core/foundation/llm/schema/config.py}.</p>
 */
public enum ProviderType {

    OPEN_AI("OpenAI", "OpenAI"),
    OPEN_ROUTER("OpenRouter", "OpenRouter"),
    SILICON_FLOW("SiliconFlow", "SiliconFlow"),
    DASH_SCOPE("DashScope", "DashScope"),
    DEEP_SEEK("DeepSeek", "DeepSeek"),
    INFERENCE_AFFINITY("InferenceAffinity", "InferenceAffinity"),
    INTELLI_ROUTER("IntelliRouter", "intelli_router");

    private final String pythonMemberName;
    private final String value;

    ProviderType(String pythonMemberName, String value) {
        this.pythonMemberName = pythonMemberName;
        this.value = value;
    }

    public String getPythonMemberName() {
        return pythonMemberName;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static ProviderType fromPythonMemberName(String provider) {
        if (provider == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(value -> value.pythonMemberName.equals(provider))
                .findFirst()
                .orElse(null);
    }

    public static ProviderType fromLowercaseValue(String provider) {
        if (provider == null) {
            return null;
        }
        String normalized = provider.strip().toLowerCase();
        return Arrays.stream(values())
                .filter(value -> value.value.toLowerCase().equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
