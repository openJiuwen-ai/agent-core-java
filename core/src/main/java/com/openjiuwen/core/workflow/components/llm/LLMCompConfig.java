/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;
import java.util.Map;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.LLMCompConfig}
 * with additional positional constructors and builder support for test compatibility.
 *
 * <p>Mirrors Python's {@code LLMCompConfig} in
 * {@code openjiuwen/core/workflow/components/llm/llm_comp.py}.</p>
 */
public class LLMCompConfig extends com.openjiuwen.core.workflow.component.llm.LLMCompConfig {

    /** Positional constructor matching Python test usage. */
    public LLMCompConfig(
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig,
            List<? extends Map<String, ?>> templateContent,
            Map<String, Object> responseFormat,
            Map<String, Object> outputConfig) {
        super();
        setModelConfig(modelConfig);
        setModelClientConfig(modelClientConfig);
        setTemplateContent(templateContent);
        setResponseFormat(responseFormat);
        setOutputConfig(outputConfig);
    }

    /** Default no-arg constructor. */
    public LLMCompConfig() {
        super();
    }

    /** Return a builder for fluent construction. */
    public static LLMCompConfigBuilder builder() {
        return new LLMCompConfigBuilder();
    }

    /** Fluent builder for LLMCompConfig. */
    public static class LLMCompConfigBuilder {
        private ModelRequestConfig modelConfig;
        private ModelClientConfig modelClientConfig;
        private List<? extends Map<String, ?>> templateContent;
        private Map<String, Object> responseFormat;
        private Map<String, Object> outputConfig;

        public LLMCompConfigBuilder modelConfig(ModelRequestConfig v) { this.modelConfig = v; return this; }
        public LLMCompConfigBuilder modelClientConfig(ModelClientConfig v) { this.modelClientConfig = v; return this; }
        public LLMCompConfigBuilder templateContent(List<? extends Map<String, ?>> v) { this.templateContent = v; return this; }
        public LLMCompConfigBuilder responseFormat(Map<String, Object> v) { this.responseFormat = v; return this; }
        public LLMCompConfigBuilder outputConfig(Map<String, Object> v) { this.outputConfig = v; return this; }

        public LLMCompConfig build() {
            return new LLMCompConfig(modelConfig, modelClientConfig, templateContent, responseFormat, outputConfig);
        }
    }
}
