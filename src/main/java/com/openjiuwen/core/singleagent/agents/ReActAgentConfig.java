/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
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
 * ReActAgent Configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActAgentConfig {

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

    // Builder-pattern configuration methods

    public ReActAgentConfig configureModel(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public ReActAgentConfig configureModelProvider(String provider, String apiKey, String apiBase) {
        this.modelProvider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        return this;
    }

    public ReActAgentConfig configurePrompt(String promptName) {
        this.promptTemplateName = promptName;
        return this;
    }

    public ReActAgentConfig configurePromptTemplate(List<Map<String, String>> promptTemplate) {
        this.promptTemplate = promptTemplate;
        return this;
    }

    public ReActAgentConfig configureContextEngine(
            Integer maxContextMessageNum,
            Integer defaultWindowRoundNum,
            boolean enableReload
    ) {
        this.contextEngineConfig = ContextEngineConfig.builder()
                .maxContextMessageNum(maxContextMessageNum != null ? maxContextMessageNum : 200)
                .defaultWindowRoundNum(defaultWindowRoundNum != null ? defaultWindowRoundNum : 10)
                .enableReload(enableReload)
                .build();
        return this;
    }

    public ReActAgentConfig configureMemScope(String memScopeId) {
        this.memScopeId = memScopeId;
        return this;
    }

    public ReActAgentConfig configureMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    public ReActAgentConfig configureModelClient(
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            boolean verifySsl
    ) {
        return configureModelClient(provider, apiKey, apiBase, modelName, verifySsl, null, null);
    }

    public ReActAgentConfig configureModelClient(
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            boolean verifySsl,
            String sslCert,
            Map<String, String> headers
    ) {
        this.modelProvider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;

        this.modelClientConfig = ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .verifySsl(verifySsl)
                .sslCert(sslCert)
                .headers(headers)
                .build();

        if (this.modelConfigObj == null) {
            this.modelConfigObj = ModelRequestConfig.builder()
                    .modelName(modelName)
                    .build();
        } else {
            this.modelConfigObj.setModelName(modelName);
        }
        return this;
    }

    public ReActAgentConfig configureContextProcessors(List<Object> processors) {
        this.contextProcessors = processors;
        return this;
    }

    public static ReActAgentConfigBuilder builder() {
        return new ReActAgentConfigBuilder();
    }

    public String getMemScopeId() {
        return memScopeId;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiBase() {
        return apiBase;
    }

    public String getPromptTemplateName() {
        return promptTemplateName;
    }

    public List<Map<String, String>> getPromptTemplate() {
        return promptTemplate;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    public ModelRequestConfig getModelConfigObj() {
        return modelConfigObj;
    }

    public ContextEngineConfig getContextEngineConfig() {
        return contextEngineConfig;
    }

    public List<Object> getContextProcessors() {
        return contextProcessors;
    }

    public String getSysOperationId() {
        return sysOperationId;
    }

    public static final class ReActAgentConfigBuilder {
        private String memScopeId = "";
        private String modelName = "";
        private String modelProvider = "openai";
        private String apiKey = "";
        private String apiBase = "";
        private String promptTemplateName = "";
        private List<Map<String, String>> promptTemplate = new ArrayList<>();
        private int maxIterations = 5;
        private ModelClientConfig modelClientConfig;
        private ModelRequestConfig modelConfigObj;
        private String sysOperationId;
        private ContextEngineConfig contextEngineConfig = ContextEngineConfig.builder()
                .maxContextMessageNum(200)
                .defaultWindowRoundNum(10)
                .build();
        private List<Object> contextProcessors;

        public ReActAgentConfigBuilder memScopeId(String memScopeId) {
            this.memScopeId = memScopeId;
            return this;
        }

        public ReActAgentConfigBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public ReActAgentConfigBuilder modelProvider(String modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }

        public ReActAgentConfigBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ReActAgentConfigBuilder apiBase(String apiBase) {
            this.apiBase = apiBase;
            return this;
        }

        public ReActAgentConfigBuilder promptTemplateName(String promptTemplateName) {
            this.promptTemplateName = promptTemplateName;
            return this;
        }

        public ReActAgentConfigBuilder promptTemplate(List<Map<String, String>> promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public ReActAgentConfigBuilder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public ReActAgentConfigBuilder modelClientConfig(ModelClientConfig modelClientConfig) {
            this.modelClientConfig = modelClientConfig;
            return this;
        }

        public ReActAgentConfigBuilder modelConfigObj(ModelRequestConfig modelConfigObj) {
            this.modelConfigObj = modelConfigObj;
            return this;
        }

        public ReActAgentConfigBuilder sysOperationId(String sysOperationId) {
            this.sysOperationId = sysOperationId;
            return this;
        }

        public ReActAgentConfigBuilder contextEngineConfig(ContextEngineConfig contextEngineConfig) {
            this.contextEngineConfig = contextEngineConfig;
            return this;
        }

        public ReActAgentConfigBuilder contextProcessors(List<Object> contextProcessors) {
            this.contextProcessors = contextProcessors;
            return this;
        }

        public ReActAgentConfig build() {
            ReActAgentConfig config = new ReActAgentConfig();
            config.memScopeId = memScopeId;
            config.modelName = modelName;
            config.modelProvider = modelProvider;
            config.apiKey = apiKey;
            config.apiBase = apiBase;
            config.promptTemplateName = promptTemplateName;
            config.promptTemplate = promptTemplate;
            config.maxIterations = maxIterations;
            config.modelClientConfig = modelClientConfig;
            config.modelConfigObj = modelConfigObj;
            config.sysOperationId = sysOperationId;
            config.contextEngineConfig = contextEngineConfig;
            config.contextProcessors = contextProcessors;
            return config;
        }
    }
}
