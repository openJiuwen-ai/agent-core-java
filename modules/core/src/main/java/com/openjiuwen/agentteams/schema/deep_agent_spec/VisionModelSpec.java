/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.deep_agent_spec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serializable mirror of VisionModelConfig.
 * Mirrors Python VisionModelSpec.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionModelSpec {
    @Builder.Default
    private String apiKey = "";
    @Builder.Default
    private String baseUrl = "https://api.openai.com/v1";
    @Builder.Default
    private String model = "gpt-4o";
    @Builder.Default
    private int maxRetries = 3;
}
