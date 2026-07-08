/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm.react;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

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

    public static class Builder {
        private final ReActAgentCompConfig config = new ReActAgentCompConfig();

        public Builder modelClientConfig(ModelClientConfig modelClientConfig) {
            config.setModelClientConfig(modelClientConfig);
            return this;
        }

        public Builder modelConfigObj(ModelRequestConfig modelConfigObj) {
            config.setModelConfigObj(modelConfigObj);
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            config.setMaxIterations(maxIterations);
            return this;
        }

        public Builder promptTemplate(List<Map<String, Object>> promptTemplate) {
            config.setPromptTemplate(promptTemplate);
            return this;
        }

        public ReActAgentCompConfig build() {
            return config;
        }
    }
}
