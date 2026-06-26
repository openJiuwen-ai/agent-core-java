/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct agent runtime configuration.
 *
 * <p>Mirrors Python's {@code ReActAgentConfig} in
 * {@code openjiuwen/core/single_agent/agents/react_agent.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReActAgentConfig {
    @JsonProperty("mem_scope_id")
    private String memScopeId = "";

    @JsonProperty("model_name")
    private String modelName = "";

    @JsonProperty("model_provider")
    private String modelProvider = "openai";

    @JsonProperty("api_key")
    private String apiKey = "";

    @JsonProperty("api_base")
    private String apiBase = "";

    @JsonProperty("custom_headers")
    private Map<String, Object> customHeaders;

    @JsonProperty("prompt_template_name")
    private String promptTemplateName = "";

    @JsonProperty("prompt_template")
    private List<Map<String, Object>> promptTemplate = new ArrayList<>();

    @JsonProperty("max_iterations")
    private int maxIterations = 5;

    @JsonProperty("llm_return_token_ids")
    private boolean llmReturnTokenIds;

    @JsonProperty("llm_logprobs")
    private boolean llmLogprobs;

    @JsonProperty("llm_top_logprobs")
    private int llmTopLogprobs = 1;

    @JsonProperty("model_client_config")
    private ModelClientConfig modelClientConfig;

    @JsonProperty("model_config_obj")
    private ModelRequestConfig modelConfigObj;

    @JsonProperty("sys_operation_id")
    private String sysOperationId;

    @JsonProperty("context_engine_config")
    private ContextEngineConfig contextEngineConfig = defaultContextEngineConfig();

    @JsonProperty("context_processors")
    private List<ContextEngine.ProcessorSpec> contextProcessors;

    private Object workspace;

    public ReActAgentConfig configureModel(String modelName) {
        this.modelName = normalizeString(modelName);
        return this;
    }

    public ReActAgentConfig configure_model(String modelName) {
        return configureModel(modelName);
    }

    public ReActAgentConfig configureModelProvider(String provider, String apiKey, String apiBase) {
        this.modelProvider = normalizeString(provider);
        this.apiKey = normalizeString(apiKey);
        this.apiBase = normalizeString(apiBase);
        return this;
    }

    public ReActAgentConfig configure_model_provider(String provider, String apiKey, String apiBase) {
        return configureModelProvider(provider, apiKey, apiBase);
    }

    public ReActAgentConfig configurePrompt(String promptName) {
        this.promptTemplateName = normalizeString(promptName);
        return this;
    }

    public ReActAgentConfig configure_prompt(String promptName) {
        return configurePrompt(promptName);
    }

    public ReActAgentConfig configurePromptTemplate(List<Map<String, Object>> promptTemplate) {
        setPromptTemplate(promptTemplate);
        return this;
    }

    public ReActAgentConfig configure_prompt_template(List<Map<String, Object>> promptTemplate) {
        return configurePromptTemplate(promptTemplate);
    }

    public ReActAgentConfig configureContextEngine(Integer maxContextMessageNum,
                                                   Integer defaultWindowRoundNum,
                                                   boolean enableReload,
                                                   boolean enableKvCacheRelease) {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setMaxContextMessageNum(maxContextMessageNum);
        config.setDefaultWindowRoundNum(defaultWindowRoundNum);
        config.setEnableReload(enableReload);
        config.setEnableKvCacheRelease(enableKvCacheRelease);
        this.contextEngineConfig = config;
        return this;
    }

    public ReActAgentConfig configure_context_engine(Integer maxContextMessageNum,
                                                     Integer defaultWindowRoundNum,
                                                     boolean enableReload,
                                                     boolean enableKvCacheRelease) {
        return configureContextEngine(maxContextMessageNum, defaultWindowRoundNum, enableReload,
                enableKvCacheRelease);
    }

    public ReActAgentConfig configureMemScope(String memScopeId) {
        this.memScopeId = normalizeString(memScopeId);
        return this;
    }

    public ReActAgentConfig configure_mem_scope(String memScopeId) {
        return configureMemScope(memScopeId);
    }

    public ReActAgentConfig configureMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    public ReActAgentConfig configure_max_iterations(int maxIterations) {
        return configureMaxIterations(maxIterations);
    }

    public ReActAgentConfig configureModelClient(String provider, String apiKey, String apiBase, String modelName,
                                                 boolean verifySsl) {
        return configureModelClient(provider, apiKey, apiBase, modelName, verifySsl, null);
    }

    public ReActAgentConfig configureModelClient(String provider, String apiKey, String apiBase, String modelName,
                                                 boolean verifySsl, ModelHttpVersion httpVersion) {
        this.modelProvider = normalizeString(provider);
        this.apiKey = normalizeString(apiKey);
        this.apiBase = normalizeString(apiBase);
        this.modelName = normalizeString(modelName);
        this.modelClientConfig = ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .verifySsl(verifySsl)
                .customHeaders(customHeaders)
                .httpVersion(httpVersion)
                .build();
        if (modelConfigObj == null) {
            modelConfigObj = ModelRequestConfig.builder().modelName(this.modelName).build();
        } else {
            modelConfigObj.setModelName(this.modelName);
        }
        return this;
    }

    public ReActAgentConfig configure_model_client(String provider, String apiKey, String apiBase, String modelName,
                                                   boolean verifySsl) {
        return configureModelClient(provider, apiKey, apiBase, modelName, verifySsl);
    }

    public ReActAgentConfig configure_model_client(String provider, String apiKey, String apiBase, String modelName,
                                                   boolean verifySsl, ModelHttpVersion httpVersion) {
        return configureModelClient(provider, apiKey, apiBase, modelName, verifySsl, httpVersion);
    }

    public ReActAgentConfig configureCustomHeaders(Map<String, Object> customHeaders) {
        setCustomHeaders(customHeaders);
        if (modelClientConfig != null) {
            modelClientConfig.setCustomHeaders(this.customHeaders);
        }
        return this;
    }

    public ReActAgentConfig configure_custom_headers(Map<String, Object> customHeaders) {
        return configureCustomHeaders(customHeaders);
    }

    public ReActAgentConfig configureContextProcessors(List<ContextEngine.ProcessorSpec> processors) {
        setContextProcessors(processors);
        return this;
    }

    public ReActAgentConfig configure_context_processors(List<ContextEngine.ProcessorSpec> processors) {
        return configureContextProcessors(processors);
    }

    public String getMemScopeId() {
        return memScopeId;
    }

    public void setMemScopeId(String memScopeId) {
        this.memScopeId = normalizeString(memScopeId);
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = normalizeString(modelName);
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = normalizeString(modelProvider);
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = normalizeString(apiKey);
    }

    public String getApiBase() {
        return apiBase;
    }

    public void setApiBase(String apiBase) {
        this.apiBase = normalizeString(apiBase);
    }

    public Map<String, Object> getCustomHeaders() {
        return customHeaders == null ? null : new LinkedHashMap<>(customHeaders);
    }

    public void setCustomHeaders(Map<String, Object> customHeaders) {
        this.customHeaders = customHeaders == null ? null : new LinkedHashMap<>(customHeaders);
    }

    public String getPromptTemplateName() {
        return promptTemplateName;
    }

    public void setPromptTemplateName(String promptTemplateName) {
        this.promptTemplateName = normalizeString(promptTemplateName);
    }

    public List<Map<String, Object>> getPromptTemplate() {
        return deepCopyPromptTemplate(promptTemplate);
    }

    public void setPromptTemplate(List<Map<String, Object>> promptTemplate) {
        this.promptTemplate = deepCopyPromptTemplate(promptTemplate);
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public boolean isLlmReturnTokenIds() {
        return llmReturnTokenIds;
    }

    public void setLlmReturnTokenIds(boolean llmReturnTokenIds) {
        this.llmReturnTokenIds = llmReturnTokenIds;
    }

    public boolean isLlmLogprobs() {
        return llmLogprobs;
    }

    public void setLlmLogprobs(boolean llmLogprobs) {
        this.llmLogprobs = llmLogprobs;
    }

    public int getLlmTopLogprobs() {
        return llmTopLogprobs;
    }

    public void setLlmTopLogprobs(int llmTopLogprobs) {
        this.llmTopLogprobs = llmTopLogprobs;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    public void setModelClientConfig(ModelClientConfig modelClientConfig) {
        this.modelClientConfig = modelClientConfig;
    }

    public ModelRequestConfig getModelConfigObj() {
        return modelConfigObj;
    }

    public void setModelConfigObj(ModelRequestConfig modelConfigObj) {
        this.modelConfigObj = modelConfigObj;
    }

    public String getSysOperationId() {
        return sysOperationId;
    }

    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    public ContextEngineConfig getContextEngineConfig() {
        return contextEngineConfig;
    }

    public void setContextEngineConfig(ContextEngineConfig contextEngineConfig) {
        this.contextEngineConfig = contextEngineConfig == null ? defaultContextEngineConfig() : contextEngineConfig;
    }

    public List<ContextEngine.ProcessorSpec> getContextProcessors() {
        return contextProcessors == null ? null : new ArrayList<>(contextProcessors);
    }

    public void setContextProcessors(List<ContextEngine.ProcessorSpec> contextProcessors) {
        this.contextProcessors = contextProcessors == null ? null : new ArrayList<>(contextProcessors);
    }

    public Object getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Object workspace) {
        this.workspace = workspace;
    }

    private static ContextEngineConfig defaultContextEngineConfig() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setMaxContextMessageNum(200);
        config.setDefaultWindowRoundNum(10);
        return config;
    }

    private static String normalizeString(String value) {
        return value == null ? "" : value;
    }

    private static List<Map<String, Object>> deepCopyPromptTemplate(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Map<String, Object> message : source) {
                copy.add(message == null ? new LinkedHashMap<>() : new LinkedHashMap<>(message));
            }
        }
        return copy;
    }
}
