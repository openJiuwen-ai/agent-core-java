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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReActAgent Configuration.
 * 
 * @since 0.1.7
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
    private String promptMode = "full";

    private Map<String, String> customHeaders;

    @Builder.Default
    private int maxIterations = 5;

    @Builder.Default
    private boolean shouldFailTaskOnToolError = false;

    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelConfigObj;
    private String sysOperationId;

    @Builder.Default
    /**
     * ContextEngineConfig.builder.
     * 
     * @since 0.1.7
     */
    private ContextEngineConfig contextEngineConfig =
        ContextEngineConfig.builder().maxContextMessageNum(200).defaultWindowRoundNum(10).build();

    private List<Object> contextProcessors;

    // Builder-pattern configuration methods

    /**
     * Set the model name.
     * 
     * @param modelName target model name
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configureModel(String modelName) {
        this.modelName = modelName;
        return this;
    }

    /**
     * Set the model provider credentials.
     * 
     * @param provider provider name
     * @param apiKey provider api key
     * @param apiBase provider api base
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configureModelProvider(String provider, String apiKey, String apiBase) {
        this.modelProvider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        return this;
    }

    /**
     * Set the system prompt template name.
     * 
     * @param promptName prompt template name
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configurePrompt(String promptName) {
        this.promptTemplateName = promptName;
        return this;
    }

    /**
     * Replace the explicit prompt template messages.
     * 
     * @param promptTemplate prompt template messages
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configurePromptTemplate(List<Map<String, String>> promptTemplate) {
        this.promptTemplate = promptTemplate;
        return this;
    }

    /**
     * Configure the context engine window limits.
     * 
     * @param maxContextMessageNum max messages retained in context
     * @param defaultWindowRoundNum default rolling window rounds
     * @param enableReload whether context reload is enabled
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configureContextEngine(Integer maxContextMessageNum, Integer defaultWindowRoundNum,
            boolean enableReload) {
        this.contextEngineConfig = ContextEngineConfig.builder()
                .maxContextMessageNum(maxContextMessageNum != null ? maxContextMessageNum : 200)
                .defaultWindowRoundNum(defaultWindowRoundNum != null ? defaultWindowRoundNum : 10)
                .enableReload(enableReload).build();
        return this;
    }

    /**
     * Set the memory scope identifier.
     * 
     * @param memScopeId memory scope id
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configureMemScope(String memScopeId) {
        this.memScopeId = memScopeId;
        return this;
    }

    /**
     * Set the maximum ReAct iteration count.
     * 
     * @param maxIterations maximum iterations
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configureMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    /**
     * Configure the model client without custom certificate or headers.
     * 
     * @param provider provider name
     * @param apiKey provider api key
     * @param apiBase provider api base
     * @param modelName model name
     * @param verifySsl whether ssl verification is enabled
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configureModelClient(String provider, String apiKey, String apiBase, String modelName,
            boolean verifySsl) {
        return configureModelClient(provider, apiKey, apiBase, modelName, verifySsl, null, null);
    }

    /**
     * Configure the concrete model client request settings.
     * 
     * @param provider provider name
     * @param apiKey provider api key
     * @param apiBase provider api base
     * @param modelName model name
     * @param verifySsl whether ssl verification is enabled
     * @param sslCert custom certificate path
     * @param headers additional request headers
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configureModelClient(String provider, String apiKey, String apiBase, String modelName,
            boolean verifySsl, String sslCert, Map<String, String> headers) {
        this.modelProvider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        Map<String, String> effectiveHeaders = mergeHeaders(this.customHeaders, headers);

        this.modelClientConfig = ModelClientConfig.builder().clientProvider(provider).apiKey(apiKey).apiBase(apiBase)
                .verifySsl(verifySsl).sslCert(sslCert).headers(effectiveHeaders).build();

        if (this.modelConfigObj == null) {
            this.modelConfigObj = ModelRequestConfig.builder().modelName(modelName).build();
        } else {
            this.modelConfigObj.setModelName(modelName);
        }
        return this;
    }

    /**
     * Configure additional headers sent with each LLM request.
     * 
     * @param customHeaders additional request headers
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configureCustomHeaders(Map<String, ?> customHeaders) {
        this.customHeaders = normalizeHeaders(customHeaders);
        if (this.modelClientConfig != null) {
            this.modelClientConfig = ModelClientConfig.builder().clientId(this.modelClientConfig.getClientId())
                    .clientProvider(this.modelClientConfig.getClientProvider())
                    .apiKey(this.modelClientConfig.getApiKey()).apiBase(this.modelClientConfig.getApiBase())
                    .timeout(this.modelClientConfig.getTimeout()).maxRetries(this.modelClientConfig.getMaxRetries())
                    .verifySsl(this.modelClientConfig.isVerifySsl()).sslCert(this.modelClientConfig.getSslCert())
                    .headers(mergeHeaders(this.modelClientConfig.getHeaders(), this.customHeaders)).build();
        }
        return this;
    }

    /**
     * Configure context-engine processors.
     * 
     * @param processors processors to install
     * @return this config
     * @since 0.1.7
     */
    public ReActAgentConfig configureContextProcessors(List<Object> processors) {
        this.contextProcessors = processors;
        return this;
    }

    /**
     * mergeHeaders.
     * 
     * @param base base
     * @param overlay overlay
     * @return the result
     * @since 0.1.7
     */
    private Map<String, String> mergeHeaders(Map<String, ?> base, Map<String, ?> overlay) {
        Map<String, String> merged = new LinkedHashMap<String, String>();
        merged.putAll(normalizeHeaders(base));
        merged.putAll(normalizeHeaders(overlay));
        return merged;
    }

    /**
     * normalizeHeaders.
     * 
     * @param headers headers
     * @return the result
     * @since 0.1.7
     */
    private Map<String, String> normalizeHeaders(Map<String, ?> headers) {
        Map<String, String> normalized = new LinkedHashMap<String, String>();
        if (headers == null) {
            return normalized;
        }
        for (Map.Entry<String, ?> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return normalized;
    }
}
