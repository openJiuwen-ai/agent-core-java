/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
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

    /** Stream failure retry count (excluding the first attempt). */
    @JsonProperty("stream_max_retries")
    private int streamMaxRetries = 2;

    /** Delay between stream retries in milliseconds. */
    @JsonProperty("stream_retry_delay_ms")
    private long streamRetryDelayMs = 1000;

    @JsonProperty("parallel_tool_calls")
    private boolean parallelToolCalls = true;

    /**
     * When true, a tool execution error force-finishes the task as FAILED instead of
     * continuing the ReAct loop toward a misleading COMPLETED state (issue #51).
     */
    @JsonProperty("fail_task_on_tool_error")
    private boolean shouldFailTaskOnToolError;

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

    public ReActAgentConfig configurePromptTemplate(List<? extends Map<String, ?>> promptTemplate) {
        setPromptTemplate(promptTemplate);
        return this;
    }

    public ReActAgentConfig configure_prompt_template(List<? extends Map<String, ?>> promptTemplate) {
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

    /**
     * Set the stream retry parameters for streaming model calls.
     *
     * @param maxRetries max retry count (excluding the first attempt)
     * @param retryDelayMs delay between retries in milliseconds
     * @return this config
     */
    public ReActAgentConfig configureStreamRetry(int maxRetries, long retryDelayMs) {
        this.streamMaxRetries = maxRetries;
        this.streamRetryDelayMs = retryDelayMs;
        return this;
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

    public void setPromptTemplate(List<? extends Map<String, ?>> promptTemplate) {
        this.promptTemplate = deepCopyPromptTemplate(promptTemplate);
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getStreamMaxRetries() {
        return streamMaxRetries;
    }

    public void setStreamMaxRetries(int streamMaxRetries) {
        this.streamMaxRetries = streamMaxRetries;
    }

    public long getStreamRetryDelayMs() {
        return streamRetryDelayMs;
    }

    public void setStreamRetryDelayMs(long streamRetryDelayMs) {
        this.streamRetryDelayMs = streamRetryDelayMs;
    }

    public boolean isParallelToolCalls() {
        return parallelToolCalls;
    }

    public void setParallelToolCalls(boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
    }

    public ReActAgentConfig configureParallelToolCalls(boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
        return this;
    }

    public ReActAgentConfig configure_parallel_tool_calls(boolean parallelToolCalls) {
        return configureParallelToolCalls(parallelToolCalls);
    }

    public boolean isShouldFailTaskOnToolError() {
        return shouldFailTaskOnToolError;
    }

    public void setShouldFailTaskOnToolError(boolean shouldFailTaskOnToolError) {
        this.shouldFailTaskOnToolError = shouldFailTaskOnToolError;
    }

    public ReActAgentConfig configureFailTaskOnToolError(boolean shouldFailTaskOnToolError) {
        this.shouldFailTaskOnToolError = shouldFailTaskOnToolError;
        return this;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        protected final ReActAgentConfig config;

        protected Builder() {
            this(new ReActAgentConfig());
        }

        protected Builder(ReActAgentConfig config) {
            if (config == null) {
                throw new IllegalArgumentException("config must not be null");
            }
            this.config = config;
        }

        public Builder memScopeId(String memScopeId) {
            config.setMemScopeId(memScopeId);
            return this;
        }

        public Builder modelName(String modelName) {
            config.setModelName(modelName);
            return this;
        }

        public Builder modelProvider(String modelProvider) {
            config.setModelProvider(modelProvider);
            return this;
        }

        public Builder apiKey(String apiKey) {
            config.setApiKey(apiKey);
            return this;
        }

        public Builder apiBase(String apiBase) {
            config.setApiBase(apiBase);
            return this;
        }

        public Builder customHeaders(Map<String, Object> customHeaders) {
            config.setCustomHeaders(customHeaders);
            return this;
        }

        public Builder promptTemplateName(String promptTemplateName) {
            config.setPromptTemplateName(promptTemplateName);
            return this;
        }

        public Builder promptTemplate(List<? extends Map<String, ?>> promptTemplate) {
            config.setPromptTemplate(promptTemplate);
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            config.setMaxIterations(maxIterations);
            return this;
        }

        public Builder streamMaxRetries(int streamMaxRetries) {
            config.setStreamMaxRetries(streamMaxRetries);
            return this;
        }

        public Builder streamRetryDelayMs(long streamRetryDelayMs) {
            config.setStreamRetryDelayMs(streamRetryDelayMs);
            return this;
        }

        public Builder parallelToolCalls(boolean parallelToolCalls) {
            config.setParallelToolCalls(parallelToolCalls);
            return this;
        }

        public Builder llmReturnTokenIds(boolean llmReturnTokenIds) {
            config.setLlmReturnTokenIds(llmReturnTokenIds);
            return this;
        }

        public Builder llmLogprobs(boolean llmLogprobs) {
            config.setLlmLogprobs(llmLogprobs);
            return this;
        }

        public Builder llmTopLogprobs(int llmTopLogprobs) {
            config.setLlmTopLogprobs(llmTopLogprobs);
            return this;
        }

        public Builder modelClientConfig(ModelClientConfig modelClientConfig) {
            config.setModelClientConfig(modelClientConfig);
            return this;
        }

        public Builder modelConfigObj(ModelRequestConfig modelConfigObj) {
            config.setModelConfigObj(modelConfigObj);
            return this;
        }

        public Builder sysOperationId(String sysOperationId) {
            config.setSysOperationId(sysOperationId);
            return this;
        }

        public Builder contextEngineConfig(ContextEngineConfig contextEngineConfig) {
            config.setContextEngineConfig(contextEngineConfig);
            return this;
        }

        public Builder contextProcessors(List<ContextEngine.ProcessorSpec> contextProcessors) {
            config.setContextProcessors(contextProcessors);
            return this;
        }

        public Builder workspace(Object workspace) {
            config.setWorkspace(workspace);
            return this;
        }

        public ReActAgentConfig build() {
            return config;
        }
    }

    private static List<Map<String, Object>> deepCopyPromptTemplate(List<? extends Map<String, ?>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Map<String, ?> message : source) {
                copy.add(message == null ? new LinkedHashMap<>() : new LinkedHashMap<>(message));
            }
        }
        return copy;
    }
}
