/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;

import lombok.EqualsAndHashCode;

/**
 * Configuration for ReActAgentComp workflow component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.react.ReActAgentCompConfig}.
 * Extends ReActAgentConfig with no additional fields — workflow-specific
 * configurations may be added later.
 * 
 * @since 0.1.7
 */
@EqualsAndHashCode(callSuper = true)
public class ReActAgentCompConfig extends ReActAgentConfig {
    /**
     * ReActAgentCompConfig.
     * 
     * @since 0.1.7
     */
    public ReActAgentCompConfig() {
        super();
    }

    /**
     * Copy constructor from a ReActAgentConfig.
     * 
     * @param source source config to copy fields from
     * @since 0.1.7
     */
    public ReActAgentCompConfig(ReActAgentConfig source) {
        super();
        if (source != null) {
            this.setMemScopeId(source.getMemScopeId());
            this.setModelName(source.getModelName());
            this.setModelProvider(source.getModelProvider());
            this.setApiKey(source.getApiKey());
            this.setApiBase(source.getApiBase());
            this.setPromptTemplateName(source.getPromptTemplateName());
            this.setPromptTemplate(source.getPromptTemplate());
            this.setCustomHeaders(source.getCustomHeaders());
            this.setMaxIterations(source.getMaxIterations());
            this.setModelClientConfig(source.getModelClientConfig());
            this.setModelConfigObj(source.getModelConfigObj());
            this.setSysOperationId(source.getSysOperationId());
            this.setContextEngineConfig(source.getContextEngineConfig());
            this.setContextProcessors(source.getContextProcessors());
        }
    }
}
