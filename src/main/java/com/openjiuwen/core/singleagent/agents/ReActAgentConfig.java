// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ReActAgent Configuration Class.
 *
 * <p>对齐 Python Pydantic BaseModel 的构造方式，支持：
 * <ul>
 *   <li>无参构造：{@code new ReActAgentConfig()} + setter/configure 链式调用</li>
 *   <li>Builder 构造：{@code ReActAgentConfig.builder().modelClientConfig(...).build()}</li>
 * </ul>
 *
 * <p>Python 用法示例：
 * <pre>{@code
 * config = ReActAgentConfig(
 *     model_client_config=model_client,
 *     model_config_obj=model_config,
 *     prompt_template=prompt_template
 * )
 * }</pre>
 *
 * <p>对应 Java Builder 用法：
 * <pre>{@code
 * ReActAgentConfig config = ReActAgentConfig.builder()
 *     .modelClientConfig(modelClient)
 *     .modelConfigObj(modelConfig)
 *     .promptTemplate(promptTemplate)
 *     .build();
 * }</pre>
 *
 * 对应 Python: agent-core/openjiuwen/core/single_agent/agents/react_agent.py
 */
public class ReActAgentConfig {
    
    private String memScopeId = "";
    private String modelName = "";
    private String modelProvider = "openai";
    private String apiKey = "";
    private String apiBase = "";
    private String promptTemplateName = "";
    private List<Map<String, Object>> promptTemplate = new ArrayList<>();
    private Integer contextWindowLimit = 20;
    private int maxIterations = 5;
    
    // LLM configuration objects (for Model initialization)
    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelConfigObj;
    
    private String sysOperationId;
    
    /**
     * 默认无参构造函数。
     */
    public ReActAgentConfig() {
    }
    
    /**
     * Builder 构造函数（私有）。
     */
    private ReActAgentConfig(Builder builder) {
        this.memScopeId = builder.memScopeId;
        this.modelName = builder.modelName;
        this.modelProvider = builder.modelProvider;
        this.apiKey = builder.apiKey;
        this.apiBase = builder.apiBase;
        this.promptTemplateName = builder.promptTemplateName;
        this.promptTemplate = builder.promptTemplate;
        this.contextWindowLimit = builder.contextWindowLimit;
        this.maxIterations = builder.maxIterations;
        this.modelClientConfig = builder.modelClientConfig;
        this.modelConfigObj = builder.modelConfigObj;
        this.sysOperationId = builder.sysOperationId;
    }
    
    /**
     * 创建 Builder 实例。
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Configure model name.
     *
     * @param modelName Model name
     * @return self (supports chaining)
     */
    public ReActAgentConfig configureModel(String modelName) {
        this.modelName = modelName;
        return this;
    }
    
    /**
     * Configure model provider details.
     *
     * @param provider Model provider name (e.g., "openai")
     * @param apiKey API key
     * @param apiBase API base URL
     * @return self (supports chaining)
     */
    public ReActAgentConfig configureModelProvider(String provider, String apiKey, String apiBase) {
        this.modelProvider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        return this;
    }
    
    /**
     * Configure prompt template name.
     *
     * @param promptName Prompt template name
     * @return self (supports chaining)
     */
    public ReActAgentConfig configurePrompt(String promptName) {
        this.promptTemplateName = promptName;
        return this;
    }
    
    /**
     * Configure prompt template directly.
     *
     * @param promptTemplate Prompt template list
     * @return self (supports chaining)
     */
    public ReActAgentConfig configurePromptTemplate(List<Map<String, Object>> promptTemplate) {
        this.promptTemplate = promptTemplate;
        return this;
    }
    
    /**
     * Configure context window limit.
     *
     * @param limit Context window limit (message count)
     * @return self (supports chaining)
     */
    public ReActAgentConfig configureContextLimit(Integer limit) {
        this.contextWindowLimit = limit;
        return this;
    }
    
    /**
     * Configure memory scope ID.
     *
     * @param memScopeId Memory scope ID
     * @return self (supports chaining)
     */
    public ReActAgentConfig configureMemScope(String memScopeId) {
        this.memScopeId = memScopeId;
        return this;
    }
    
    /**
     * Configure maximum iterations.
     *
     * @param maxIterations Maximum number of ReAct loop iterations
     * @return self (supports chaining)
     */
    public ReActAgentConfig configureMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }
    
    /**
     * Configure model client for LLM initialization.
     *
     * @param provider Model provider name
     * @param apiKey API key
     * @param apiBase API base URL
     * @param modelName Model name
     * @param verifySsl Whether to verify SSL
     * @return self (supports chaining)
     */
    public ReActAgentConfig configureModelClient(
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            boolean verifySsl) {
        this.modelProvider = provider;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.modelName = modelName;
        
        this.modelClientConfig = new ModelClientConfig.Builder()
            .clientProvider(provider)
            .apiKey(apiKey)
            .apiBase(apiBase)
            .verifySsl(verifySsl)
            .build();
        
        this.modelConfigObj = new ModelRequestConfig.Builder()
            .modelName(modelName)
            .build();
        
        return this;
    }
    
    // Getters and Setters
    
    public String getMemScopeId() {
        return memScopeId;
    }
    
    public void setMemScopeId(String memScopeId) {
        this.memScopeId = memScopeId;
    }
    
    public String getModelName() {
        return modelName;
    }
    
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    public String getModelProvider() {
        return modelProvider;
    }
    
    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getApiBase() {
        return apiBase;
    }
    
    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }
    
    public String getPromptTemplateName() {
        return promptTemplateName;
    }
    
    public void setPromptTemplateName(String promptTemplateName) {
        this.promptTemplateName = promptTemplateName;
    }
    
    public List<Map<String, Object>> getPromptTemplate() {
        return promptTemplate;
    }
    
    public void setPromptTemplate(List<Map<String, Object>> promptTemplate) {
        this.promptTemplate = promptTemplate;
    }
    
    public Integer getContextWindowLimit() {
        return contextWindowLimit;
    }
    
    public void setContextWindowLimit(Integer contextWindowLimit) {
        this.contextWindowLimit = contextWindowLimit;
    }
    
    public int getMaxIterations() {
        return maxIterations;
    }
    
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
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
    
    /**
     * Builder 类，对齐 Python Pydantic BaseModel 在构造时传入所有字段的能力。
     *
     * <p>所有字段均有与 Python 一致的默认值。
     */
    public static class Builder {
        private String memScopeId = "";
        private String modelName = "";
        private String modelProvider = "openai";
        private String apiKey = "";
        private String apiBase = "";
        private String promptTemplateName = "";
        private List<Map<String, Object>> promptTemplate = new ArrayList<>();
        private Integer contextWindowLimit = 20;
        private int maxIterations = 5;
        private ModelClientConfig modelClientConfig;
        private ModelRequestConfig modelConfigObj;
        private String sysOperationId;
        
        public Builder memScopeId(String memScopeId) {
            this.memScopeId = memScopeId;
            return this;
        }
        
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }
        
        public Builder modelProvider(String modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }
        
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
        
        public Builder apiBase(String apiBase) {
            this.apiBase = apiBase;
            return this;
        }
        
        public Builder promptTemplateName(String promptTemplateName) {
            this.promptTemplateName = promptTemplateName;
            return this;
        }
        
        public Builder promptTemplate(List<Map<String, Object>> promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }
        
        public Builder contextWindowLimit(Integer contextWindowLimit) {
            this.contextWindowLimit = contextWindowLimit;
            return this;
        }
        
        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }
        
        public Builder modelClientConfig(ModelClientConfig modelClientConfig) {
            this.modelClientConfig = modelClientConfig;
            return this;
        }
        
        public Builder modelConfigObj(ModelRequestConfig modelConfigObj) {
            this.modelConfigObj = modelConfigObj;
            return this;
        }
        
        public Builder sysOperationId(String sysOperationId) {
            this.sysOperationId = sysOperationId;
            return this;
        }
        
        public ReActAgentConfig build() {
            return new ReActAgentConfig(this);
        }
    }
}

