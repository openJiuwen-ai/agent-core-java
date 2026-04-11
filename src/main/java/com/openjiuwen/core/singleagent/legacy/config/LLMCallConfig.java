/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.config;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Legacy LLM call configuration.
 *
 * <p>Mirrors Python's {@code LLMCallConfig} in {@code single_agent/legacy/config.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMCallConfig {

    private ModelRequestConfig model;

    private ModelClientConfig modelClient;

    @Builder.Default
    private List<Map<String, String>> systemPrompt = new ArrayList<>();

    @Builder.Default
    private List<Map<String, String>> userPrompt = new ArrayList<>();

    @Builder.Default
    private boolean freezeSystemPrompt = false;

    @Builder.Default
    private boolean freezeUserPrompt = true;
}
