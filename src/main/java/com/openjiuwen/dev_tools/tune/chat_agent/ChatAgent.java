  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.dev_tools.tune.chat_agent;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.legacy.BaseAgent;
import com.openjiuwen.core.singleagent.legacy.config.LLMCallConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ChatAgent - 最简单的LLM聊天Agent
 * <p>
 * Mirrors Python's {@code ChatAgent} from {@code dev_tools/tune/chat_agent/chat_agent.py}.
 */
public class ChatAgent extends BaseAgent {

    private final LLMCall llmCall;
    private final Session defaultSession;
    private final ContextEngine contextEngine;

    /**
     * 构造ChatAgent
     *
     * @param agentConfig Agent配置
     */
    public ChatAgent(ChatAgentConfig agentConfig) {
        super(agentConfig);

        // 初始化LLMCall
        var llmConfig = agentConfig.getLlmCallConfig();
        this.llmCall = new LLMCall(
                llmConfig.getModel().getModelName(),
                initModel(llmConfig.getModel(), llmConfig.getModelClient()),
                llmConfig.getSystemPrompt(),
                llmConfig.getUserPrompt(),
                llmConfig.isFreezeSystemPrompt(),
                llmConfig.isFreezeUserPrompt(),
                "llm_call"
        );

        // 初始化默认Session
        this.defaultSession = createDefaultSession();

        // 初始化ContextEngine
        this.contextEngine = createContextEngine();
    }

    /**
     * 初始化模型
     *
     * @param modelConfig       模型配置
     * @param modelClientConfig 模型客户端配置
     * @return Model实例
     */
    protected Model initModel(Object modelConfig, Object modelClientConfig) {
        return new Model(
                (com.openjiuwen.core.foundation.llm.schema.ModelClientConfig) modelClientConfig,
                (com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig) modelConfig
        );
    }

    /**
     * 创建默认Session
     *
     * @return Session实例
     */
    protected Session createDefaultSession() {
        return new SimpleSession("default_session");
    }

    /**
     * 创建ContextEngine
     * <p>
     * ChatAgent使用默认配置的ContextEngine
     *
     * @return ContextEngine实例
     */
    protected ContextEngine createContextEngine() {
        ContextEngineConfig contextConfig = ContextEngineConfig.builder().build();
        return new ContextEngine(contextConfig);
    }

    /**
     * 同步调用Agent
     *
     * @param inputs  输入参数
     * @param session 会话对象
     * @return 调用结果
     */
    @Override
    public Map<String, Object> invoke(Map<String, Object> inputs, Session session) {
        try {
            // 1. 初始化ContextEngine和Session
            Session agentSession = session != null ? session : defaultSession;

            // 2. 调用LLMCall
            ModelContext agentContext = contextEngine.createContext("default", agentSession);
            List<BaseMessage> history = agentContext.getMessages();

            Object tools = null;
            if (!this.tools.isEmpty()) {
                List<String> toolIds = new ArrayList<>();
                for (Tool tool : this.tools) {
                    toolIds.add(tool.getCard().getId());
                }
                List<ToolInfo> toolInfos = Runner.resourceMgr().getToolInfos(
                        toolIds, null, agentConfig.getId(), TagMatchStrategy.ALL);
                tools = toolInfos;
            }

            AssistantMessage result = llmCall.invoke(inputs, agentSession, history, tools);

            // 3. 构造返回结果
            Map<String, Object> output = new HashMap<>();
            output.put("output", result.getContent());
            output.put("tool_calls", result.getToolCalls());
            return output;

        } catch (Exception e) {
            throw new RuntimeException("ChatAgent invoke failed", e);
        }
    }

    /**
     * 流式调用Agent
     *
     * @param inputs  输入参数
     * @param session 会话对象
     * @return 流式结果迭代器
     */
    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Session session) {
        try {
            // 1. 初始化ContextEngine和Session
            Session agentSession = session != null ? session : defaultSession;

            // 2. 流式调用LLMCall
            ModelContext agentContext = contextEngine.createContext("default", agentSession);
            List<BaseMessage> history = agentContext.getMessages();

            Object tools = null;
            if (!this.tools.isEmpty()) {
                List<String> toolIds = new ArrayList<>();
                for (Tool tool : this.tools) {
                    toolIds.add(tool.getCard().getId());
                }
                List<ToolInfo> toolInfos = Runner.resourceMgr().getToolInfos(
                        toolIds, null, agentConfig.getId(), TagMatchStrategy.ALL);
                tools = toolInfos;
            }

            OperatorStream<AssistantMessageChunk> streamIterator = llmCall.stream(inputs, agentSession, history, tools);

            // 3. 返回包装后的迭代器
            return new StreamResultIterator(streamIterator);

        } catch (Exception e) {
            throw new RuntimeException("ChatAgent stream failed", e);
        }
    }

    /**
     * 获取LLMCall实例
     *
     * @return LLMCall映射
     */
    public Map<String, LLMCall> getLlmCalls() {
        Map<String, LLMCall> result = new HashMap<>();
        result.put("llm_call", llmCall);
        return result;
    }

    /**
     * 复制Agent
     *
     * @return 新的Agent实例
     */
    public ChatAgent copy() {
        return createChatAgent((ChatAgentConfig) agentConfig, this.tools);
    }

    /**
     * 创建ChatAgent工厂方法
     *
     * @param agentConfig Agent配置
     * @param tools       工具列表
     * @return ChatAgent实例
     */
    public static ChatAgent createChatAgent(ChatAgentConfig agentConfig, List<Tool> tools) {
        ChatAgent agent = new ChatAgent(agentConfig);
        agent.addTools(tools != null ? tools : new ArrayList<>());
        return agent;
    }

    /**
     * 创建ChatAgentConfig工厂方法
     *
     * @param agentId      Agent ID
     * @param agentVersion Agent版本
     * @param description  描述
     * @param model        LLM配置
     * @return ChatAgentConfig实例
     */
    public static ChatAgentConfig createChatAgentConfig(
            String agentId,
            String agentVersion,
            String description,
            LLMCallConfig model) {
        ChatAgentConfig config = new ChatAgentConfig();
        config.setId(agentId);
        config.setVersion(agentVersion);
        config.setDescription(description);
        config.setLlmCallConfig(model);
        return config;
    }

    /**
     * 流式结果迭代器
     */
    private static class StreamResultIterator implements Iterator<Object> {
        private final OperatorStream<AssistantMessageChunk> delegate;

        public StreamResultIterator(OperatorStream<AssistantMessageChunk> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Object next() {
            AssistantMessageChunk chunk = delegate.next();
            Map<String, Object> result = new HashMap<>();
            result.put("output", chunk.getContent());
            result.put("tool_calls", chunk.getToolCalls());
            return result;
        }
    }

    /**
     * 简单Session实现
     */
    private static class SimpleSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

        public SimpleSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> newState) {
            state.putAll(newState);
        }
    }
}
