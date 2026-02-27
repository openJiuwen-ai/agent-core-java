// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.contextengine.ContextWindow;
import com.openjiuwen.core.contextengine.ModelContext;
import com.openjiuwen.core.contextengine.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.skills.SkillUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * ReAct paradigm Agent implementation.
 * ReAct loop: Reasoning -> Acting -> Observation -> Repeat
 *
 * <p>Input format (compatible with legacy):
 * {@code {"query": "user question", "conversation_id": "session_123"}}
 *
 * <p>Output format (compatible with legacy):
 * <ul>
 * <li>invoke: {@code {"output": "response content", "result_type": "answer|error"}}</li>
 * <li>stream: yields OutputSchema objects</li>
 * </ul>
 *
 * 对应 Python: agent-core/openjiuwen/core/single_agent/agents/react_agent.py
 */
public class ReActAgent extends BaseAgent {
    
    private static final LoggerProtocol logger = LogManager.getLogger(ReActAgent.class.getName());
    
    private ReActAgentConfig config;
    private ContextEngine contextEngine;
    private Model llm;
    private SkillUtil skillUtil;
    
    /**
     * Initialize ReActAgent.
     *
     * @param card Agent card (required)
     */
    public ReActAgent(AgentCard card) {
        super(card);
        this.config = createDefaultConfig();
        this.contextEngine = new ContextEngine(
            ContextEngineConfig.builder()
                .defaultWindowMessageNum(config.getContextWindowLimit())
                .build()
        );
        this.llm = null;
        initMemoryScope();
        this.skillUtil = new SkillUtil(config.getSysOperationId());
    }
    
    /**
     * Initialize memory scope.
     */
    private void initMemoryScope() {
        if (config.getMemScopeId() != null && !config.getMemScopeId().isEmpty()) {
            try {
                LongTermMemory.getInstance().setScopeConfig(
                    config.getMemScopeId(),
                    MemoryScopeConfig.builder().build()
                );
            } catch (Exception e) {
                logger.warning("Failed to init memory scope: %s", e.getMessage());
            }
        }
    }
    
    /**
     * Create default configuration.
     */
    private ReActAgentConfig createDefaultConfig() {
        return new ReActAgentConfig();
    }
    
    /**
     * Get current configuration.
     */
    public ReActAgentConfig getConfig() {
        return config;
    }
    
    /**
     * Get context engine.
     */
    public ContextEngine getContextEngine() {
        return contextEngine;
    }
    
    /**
     * Get LLM instance (for testing).
     */
    public Object getLlm() {
        return llm;
    }
    
    /**
     * Set LLM instance (for testing).
     */
    public void setLlm(Object llm) {
        if (llm instanceof Model model) {
            this.llm = model;
        }
    }
    
    @Override
    public ReActAgent configure(Object configObj) {
        if (!(configObj instanceof ReActAgentConfig newConfig)) {
            throw new IllegalArgumentException("Config must be ReActAgentConfig");
        }
        
        ReActAgentConfig oldConfig = this.config;
        this.config = newConfig;
        
        // Reset LLM if model config changed
        if (!Objects.equals(oldConfig.getModelProvider(), newConfig.getModelProvider()) ||
            !Objects.equals(oldConfig.getApiKey(), newConfig.getApiKey()) ||
            !Objects.equals(oldConfig.getApiBase(), newConfig.getApiBase())) {
            this.llm = null;
        }
        
        // Update context_engine if context window limit changed
        if (!Objects.equals(oldConfig.getContextWindowLimit(), newConfig.getContextWindowLimit())) {
            this.contextEngine = new ContextEngine(
                ContextEngineConfig.builder()
                    .defaultWindowMessageNum(newConfig.getContextWindowLimit())
                    .build()
            );
        }
        
        // Update memory_scope if memory scope ID changed
        if (!Objects.equals(oldConfig.getMemScopeId(), newConfig.getMemScopeId())) {
            initMemoryScope();
        }
        
        // Reset sys operation id if changed
        if (!Objects.equals(oldConfig.getSysOperationId(), newConfig.getSysOperationId())) {
            skillUtil.setSysOperationId(newConfig.getSysOperationId());
        }
        
        return this;
    }
    
    /**
     * Get or create LLM instance (lazy initialization).
     *
     * @return Model instance
     * @throws IllegalStateException If model_client_config is not configured
     */
    public Model getOrCreateLlm() {
        if (llm == null) {
            if (config.getModelClientConfig() == null) {
                throw new IllegalStateException(
                    "model_client_config is required. Use configureModelClient() to set it."
                );
            }
            llm = new Model(config.getModelClientConfig(), config.getModelConfigObj());
        }
        return llm;
    }
    
    /**
     * Register a skill.
     *
     * @param skillPath the path to the skill directory to register
     * @return CompletableFuture that completes when registration is done
     */
    public CompletableFuture<Void> registerSkill(String skillPath) {
        return skillUtil.registerSkills(skillPath, false);
    }
    
    /**
     * Call LLM with messages and optional tools.
     *
     * @param messages Message list
     * @param tools Optional tool definitions
     * @return CompletableFuture with AssistantMessage from LLM
     */
    private CompletableFuture<AssistantMessage> callLlm(
            List<BaseMessage> messages, 
            List<ToolInfo> tools) {
        Model model = getOrCreateLlm();
        return model.invoke(
            messages,
            tools,
            null,  // temperature
            null,  // topP
            null,  // maxTokens
            null,  // stop
            config.getModelName(),
            null,  // outputParser
            null,  // timeout
            null   // kwargs
        );
    }
    
    @Override
    public CompletableFuture<Object> invoke(Object inputs, Session session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doInvoke(inputs, session);
            } catch (Exception e) {
                logger.error("ReActAgent invoke error: %s", e.getMessage());
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("output", e.getMessage());
                errorResult.put("result_type", "error");
                return errorResult;
            }
        });
    }
    
    /**
     * Execute ReAct process synchronously.
     */
    private Map<String, Object> doInvoke(Object inputs, Session session) throws Exception {
        // Normalize inputs
        String userInput;
        if (inputs instanceof Map<?, ?> inputMap) {
            Object query = inputMap.get("query");
            if (query == null) {
                throw new IllegalArgumentException("Input dict must contain 'query'");
            }
            userInput = String.valueOf(query);
        } else if (inputs instanceof String str) {
            userInput = str;
        } else {
            throw new IllegalArgumentException("Input must be Map with 'query' or String");
        }
        
        // Get or create model context
        ModelContext context = contextEngine.createContext(null, session, null, null, null).get();
        
        // Add user message to context
        context.addMessages(new UserMessage(userInput)).get();
        
        // Build system messages from prompt template
        List<BaseMessage> systemMessages = new ArrayList<>();
        if (config.getPromptTemplate() != null) {
            for (Map<String, Object> msg : config.getPromptTemplate()) {
                if ("system".equals(msg.get("role"))) {
                    String content = String.valueOf(msg.getOrDefault("content", ""));
                    systemMessages.add(new SystemMessage(content));
                }
            }
        }
        
        // Add skill prompt if skills are registered
        if (!systemMessages.isEmpty() && skillUtil.hasSkill()) {
            String skillPrompt = skillUtil.getSkillPrompt();
            BaseMessage lastMsg = systemMessages.get(systemMessages.size() - 1);
            if (lastMsg instanceof SystemMessage sysMsg) {
                String newContent = (sysMsg.getContent() != null ? sysMsg.getContent().toString() : "") + "\n" + skillPrompt;
                sysMsg.setContent(newContent);
            }
        }
        
        // Get tool info from _ability_kit
        List<ToolInfo> tools = listToolInfo(null).get();
        
        // ReAct loop
        for (int iteration = 0; iteration < config.getMaxIterations(); iteration++) {
            logger.info("ReAct iteration %d/%d", iteration + 1, config.getMaxIterations());
            
            // Get context window with system messages and tools
            ContextWindow contextWindow = context.getContextWindow(
                systemMessages,
                (tools != null && !tools.isEmpty()) ? tools : null,
                config.getContextWindowLimit()
            ).get();
            
            // Call LLM with messages and tools from context window
            AssistantMessage aiMessage = callLlm(
                contextWindow.getMessages(),
                contextWindow.getTools()
            ).get();
            
            // Add AI message to context
            AssistantMessage aiMsgForContext = new AssistantMessage();
            aiMsgForContext.setContent(aiMessage.getContent());
            aiMsgForContext.setToolCalls(aiMessage.getToolCalls());
            context.addMessages(aiMsgForContext).get();
            
            // Check for tool calls
            if (aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty()) {
                // Log tool calls
                for (var toolCall : aiMessage.getToolCalls()) {
                    logger.info("Executing tool: %s with args: %s", 
                        toolCall.getName(), toolCall.getArguments());
                }
                
                // Execute tools using executeAbility (supports parallel)
                List<AbilityManager.ExecutionResult> results = executeAbility(
                    aiMessage.getToolCalls(), session
                ).get();
                
                // Process results and add tool messages to context
                for (AbilityManager.ExecutionResult result : results) {
                    logger.info("Tool result: %s", result.result());
                    ToolMessage toolMsg = result.toolMessage();
                    if (toolMsg != null) {
                        context.addMessages(toolMsg).get();
                    }
                }
            } else {
                // No tool calls, return AI response
                Map<String, Object> successResult = new HashMap<>();
                successResult.put("output", aiMessage.getContent());
                successResult.put("result_type", "answer");
                return successResult;
            }
        }
        
        // Max iterations reached
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("output", "Max iterations reached without completion");
        errorResult.put("result_type", "error");
        return errorResult;
    }
    
    @Override
    public CompletableFuture<Iterator<Object>> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        return CompletableFuture.supplyAsync(() -> {
            // Create stream process task
            Map<String, Object> finalResult;
            try {
                finalResult = (Map<String, Object>) invoke(inputs, session).get();
            } catch (Exception e) {
                logger.error("ReActAgent stream error: %s", e.getMessage());
                finalResult = new HashMap<>();
                finalResult.put("output", e.getMessage());
                finalResult.put("result_type", "error");
            }
            
            // Return result as iterator
            Map<String, Object> output = new HashMap<>();
            output.put("type", "answer");
            output.put("index", 0);
            output.put("payload", finalResult);
            
            return List.<Object>of(output).iterator();
        });
    }
}
