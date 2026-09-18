/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm.react;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;

import java.util.List;
import java.util.Map;

/**
 * Singular-package compatibility facade for ReAct workflow component config.
 *
 * <p>Mirrors Python's {@code ReActAgentCompConfig} in
 * {@code openjiuwen/core/workflow/components/llm/react/react_config.py}.</p>
 */
public class ReActAgentCompConfig
        extends com.openjiuwen.core.workflow.components.llm.react.ReActAgentCompConfig {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends com.openjiuwen.core.singleagent.agents.ReActAgentConfig.Builder {
        private Builder() {
            super(new ReActAgentCompConfig());
        }

        @Override
        public Builder memScopeId(String memScopeId) {
            super.memScopeId(memScopeId);
            return this;
        }

        @Override
        public Builder modelName(String modelName) {
            super.modelName(modelName);
            return this;
        }

        @Override
        public Builder modelProvider(String modelProvider) {
            super.modelProvider(modelProvider);
            return this;
        }

        @Override
        public Builder apiKey(String apiKey) {
            super.apiKey(apiKey);
            return this;
        }

        @Override
        public Builder apiBase(String apiBase) {
            super.apiBase(apiBase);
            return this;
        }

        @Override
        public Builder customHeaders(Map<String, Object> customHeaders) {
            super.customHeaders(customHeaders);
            return this;
        }

        @Override
        public Builder promptTemplateName(String promptTemplateName) {
            super.promptTemplateName(promptTemplateName);
            return this;
        }

        @Override
        public Builder modelClientConfig(ModelClientConfig modelClientConfig) {
            super.modelClientConfig(modelClientConfig);
            return this;
        }

        @Override
        public Builder modelConfigObj(ModelRequestConfig modelConfigObj) {
            super.modelConfigObj(modelConfigObj);
            return this;
        }

        @Override
        public Builder maxIterations(int maxIterations) {
            super.maxIterations(maxIterations);
            return this;
        }

        @Override
        public Builder promptTemplate(List<? extends Map<String, ?>> promptTemplate) {
            super.promptTemplate(promptTemplate);
            return this;
        }

        @Override
        public Builder llmReturnTokenIds(boolean llmReturnTokenIds) {
            super.llmReturnTokenIds(llmReturnTokenIds);
            return this;
        }

        @Override
        public Builder llmLogprobs(boolean llmLogprobs) {
            super.llmLogprobs(llmLogprobs);
            return this;
        }

        @Override
        public Builder llmTopLogprobs(int llmTopLogprobs) {
            super.llmTopLogprobs(llmTopLogprobs);
            return this;
        }

        @Override
        public Builder sysOperationId(String sysOperationId) {
            super.sysOperationId(sysOperationId);
            return this;
        }

        @Override
        public Builder contextEngineConfig(ContextEngineConfig contextEngineConfig) {
            super.contextEngineConfig(contextEngineConfig);
            return this;
        }

        @Override
        public Builder contextProcessors(List<ContextEngine.ProcessorSpec> contextProcessors) {
            super.contextProcessors(contextProcessors);
            return this;
        }

        @Override
        public Builder workspace(Object workspace) {
            super.workspace(workspace);
            return this;
        }

        @Override
        public ReActAgentCompConfig build() {
            return (ReActAgentCompConfig) config;
        }
    }
}
