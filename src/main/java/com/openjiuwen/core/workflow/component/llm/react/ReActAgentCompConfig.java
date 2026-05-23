/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm.react;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration for ReAct agent workflow component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.react.react_config.ReActAgentCompConfig}.
 * <p>
 * Contains the same fields as {@link com.openjiuwen.core.singleagent.agents.ReActAgentConfig}
 * for workflow-specific configuration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReActAgentCompConfig {

    @Builder.Default
    private String memScopeId = "";

    @Builder.Default
    private String modelName = "";

    @Builder.Default
    private String modelProvider = "openai";

    @Builder.Default
    private String apiKey = "";

    @Builder.Default
    private String apiBase = "";

    @Builder.Default
    private String promptTemplateName = "";

    @Builder.Default
    private List<Map<String, String>> promptTemplate = new ArrayList<>();

    @Builder.Default
    private int maxIterations = 5;

    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelConfigObj;
    private String sysOperationId;

    @Builder.Default
    private ContextEngineConfig contextEngineConfig = ContextEngineConfig.builder()
            .maxContextMessageNum(200)
            .defaultWindowRoundNum(10)
            .build();

    private List<Object> contextProcessors;

    /**
     * Convert to ReActAgentConfig for agent consumption.
     */
    public com.openjiuwen.core.singleagent.agents.ReActAgentConfig toReActAgentConfig() {
        return com.openjiuwen.core.singleagent.agents.ReActAgentConfig.builder()
                .memScopeId(memScopeId)
                .modelName(modelName)
                .modelProvider(modelProvider)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .promptTemplateName(promptTemplateName)
                .promptTemplate(promptTemplate)
                .maxIterations(maxIterations)
                .modelClientConfig(modelClientConfig)
                .modelConfigObj(modelConfigObj)
                .sysOperationId(sysOperationId)
                .contextEngineConfig(contextEngineConfig)
                .contextProcessors(contextProcessors)
                .build();
    }
}