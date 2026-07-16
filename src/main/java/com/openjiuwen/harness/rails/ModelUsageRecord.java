/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class ModelUsageRecord used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelUsageRecord {
    private String modelId;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;

    /**
     * add.
     * 
     * @param usage usage
     * @since 0.1.7
     */
    public void add(UsageMetadata usage) {
        if (usage == null) {
            return;
        }
        inputTokens += usage.getInputTokens();
        outputTokens += usage.getOutputTokens();
        totalTokens += usage.getTotalTokens();
    }

    /**
     * toUsageMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public UsageMetadata toUsageMetadata() {
        return UsageMetadata.builder().modelName(modelId).inputTokens(inputTokens).outputTokens(outputTokens)
                .totalTokens(totalTokens).build();
    }
}
