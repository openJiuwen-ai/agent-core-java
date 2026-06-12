/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Model client provider names.
 *
 * <p>Mirrors Python's {@code ProviderType} in
 * {@code openjiuwen/core/foundation/llm/schema/config.py}.</p>
 */
public enum ProviderType {

    OPEN_AI("OpenAI"),
    OPEN_ROUTER("OpenRouter"),
    SILICON_FLOW("SiliconFlow"),
    DASH_SCOPE("DashScope"),
    DEEP_SEEK("DeepSeek"),
    INFERENCE_AFFINITY("InferenceAffinity"),
    INTELLI_ROUTER("intelli_router");

    private final String value;

    ProviderType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
