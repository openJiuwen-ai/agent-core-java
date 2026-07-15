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
 * @since 0.1.7
 */
public class LLMCompConfig extends com.openjiuwen.core.workflow.component.llm.LLMCompConfig {
    /**
     * LLMCompConfig.
     * 
     * @param modelConfig modelConfig
     * @param modelClientConfig modelClientConfig
     * @param templateContent templateContent
     * @param responseFormat responseFormat
     * @param outputConfig outputConfig
     * @since 0.1.7
     */
    public LLMCompConfig(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig,
            List<Map<String, Object>> templateContent, Map<String, Object> responseFormat,
            Map<String, Object> outputConfig) {
        super();
        setModelConfig(modelConfig);
        setModelClientConfig(modelClientConfig);
        setTemplateContent(templateContent);
        setResponseFormat(responseFormat);
        setOutputConfig(outputConfig);
    }

    /**
     * Default no-arg constructor.
     * 
     * @since 0.1.7
     */
    public LLMCompConfig() {
        super();
    }

    /**
     * Return a builder for fluent construction.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static LLMCompConfigBuilder builder() {
        return new LLMCompConfigBuilder();
    }

    /**
     * Fluent builder for LLMCompConfig.
     * 
     * @since 0.1.7
     */
    public static class LLMCompConfigBuilder {
        private ModelRequestConfig modelConfig;
        private ModelClientConfig modelClientConfig;
        private List<Map<String, Object>> templateContent;
        private Map<String, Object> responseFormat;
        private Map<String, Object> outputConfig;

        /**
         * modelConfig.
         * 
         * @param v v
         * @return the result
         * @since 0.1.7
         */
        public LLMCompConfigBuilder modelConfig(ModelRequestConfig v) {
            this.modelConfig = v;
            return this;
        }

        /**
         * modelClientConfig.
         * 
         * @param v v
         * @return the result
         * @since 0.1.7
         */
        public LLMCompConfigBuilder modelClientConfig(ModelClientConfig v) {
            this.modelClientConfig = v;
            return this;
        }

        /**
         * templateContent.
         * 
         * @param v v
         * @return the result
         * @since 0.1.7
         */
        public LLMCompConfigBuilder templateContent(List<Map<String, Object>> v) {
            this.templateContent = v;
            return this;
        }

        /**
         * responseFormat.
         * 
         * @param v v
         * @return the result
         * @since 0.1.7
         */
        public LLMCompConfigBuilder responseFormat(Map<String, Object> v) {
            this.responseFormat = v;
            return this;
        }

        /**
         * outputConfig.
         * 
         * @param v v
         * @return the result
         * @since 0.1.7
         */
        public LLMCompConfigBuilder outputConfig(Map<String, Object> v) {
            this.outputConfig = v;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public LLMCompConfig build() {
            return new LLMCompConfig(modelConfig, modelClientConfig, templateContent, responseFormat, outputConfig);
        }
    }
}
