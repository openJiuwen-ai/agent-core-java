/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import lombok.Builder;
import lombok.Getter;

/**
 * Session-memory update thresholds.
 * <p>
 * Mirrors Python's {@code SessionMemoryConfig}.
 */
@Getter
@Builder
public class SessionMemoryConfig {
    @Builder.Default
    private int triggerTokens = 10000;
    @Builder.Default
    private int triggerAddTokens = 5000;
    @Builder.Default
    private int toolMin = 3;
    private ModelRequestConfig model;
    private ModelClientConfig modelClient;
    @Builder.Default
    private String updateMode = "agent_edit";
    @Builder.Default
    private int directReplaceMaxRetries = 2;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionMemoryConfig() {
        this(10000, 5000, 3, null, null, "agent_edit", 2);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionMemoryConfig(
            int triggerTokens,
            int triggerAddTokens,
            int toolMin,
            ModelRequestConfig model,
            ModelClientConfig modelClient,
            String updateMode,
            int directReplaceMaxRetries) {
        if (triggerTokens <= 0 || triggerAddTokens <= 0 || toolMin <= 0) {
            throw new IllegalArgumentException("session-memory thresholds must be positive");
        }
        if (directReplaceMaxRetries < 0) {
            throw new IllegalArgumentException("directReplaceMaxRetries must be non-negative");
        }
        this.triggerTokens = triggerTokens;
        this.triggerAddTokens = triggerAddTokens;
        this.toolMin = toolMin;
        this.model = model;
        this.modelClient = modelClient;
        this.updateMode = updateMode == null || updateMode.isBlank() ? "agent_edit" : updateMode;
        this.directReplaceMaxRetries = directReplaceMaxRetries;
    }
}
