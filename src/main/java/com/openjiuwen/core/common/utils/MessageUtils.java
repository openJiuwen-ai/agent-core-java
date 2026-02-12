package com.openjiuwen.core.common.utils;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.contextengine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.Session;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 消息工具类
 * 
 * 从 Python message_utils.py 转换
 * 
 * 已完成的依赖模块：
 * - ✅ openjiuwen.core.foundation.llm.BaseMessage
 * - ✅ openjiuwen.core.foundation.llm.AssistantMessage
 * - ✅ openjiuwen.core.foundation.llm.UserMessage
 * - ✅ openjiuwen.core.foundation.llm.ToolMessage
 * - ✅ openjiuwen.core.common.security.UserConfig
 * - ✅ openjiuwen.core.context_engine.ContextEngine
 * - ✅ openjiuwen.core.session.Session
 * 
 * 待转换的依赖模块：
 * - openjiuwen.core.single_agent.legacy.config.AgentConfig（仅getChatHistory需要）
 */
public final class MessageUtils {
    
    private static final LoggerProtocol logger = LogManager.getLogger(MessageUtils.class.getName());

    private MessageUtils() {
        // 防止实例化
    }

    /**
     * 检查是否应该添加用户消息
     * 
     * 逻辑说明：
     * 1. 获取最后一条消息
     * 2. 如果最后一条是tool消息，跳过（工具调用后的请求）
     * 3. 如果最后一条是相同内容的user消息，跳过（重复消息）
     * 4. 其他情况返回true
     *
     * @param query         用户输入
     * @param contextEngine 上下文引擎
     * @param session       会话实例
     * @return 是否添加用户消息
     */
    public static boolean shouldAddUserMessage(
            String query,
            ContextEngine contextEngine,
            Session session
    ) {
        ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
        if (agentContext == null) {
            return true;
        }
        
        List<BaseMessage> lastMessages = agentContext.getMessages(1, true);
        if (lastMessages == null || lastMessages.isEmpty()) {
            return true;
        }
        
        BaseMessage lastMessage = lastMessages.get(0);
        if ("tool".equals(lastMessage.getRole())) {
            logger.info("Skipping user message - post-tool-call request");
            return false;
        }
        
        if ("user".equals(lastMessage.getRole()) && query != null && query.equals(lastMessage.getContent())) {
            logger.info("Skipping duplicate user message");
            return false;
        }
        
        return true;
    }

    /**
     * 添加用户消息到聊天历史（异步）
     * 
     * 逻辑说明：
     * 1. 调用shouldAddUserMessage检查
     * 2. 如果需要添加，创建UserMessage并添加到上下文
     * 3. 根据敏感模式记录日志
     *
     * @param query         用户输入
     * @param contextEngine 上下文引擎
     * @param session       会话实例
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> addUserMessage(
            String query,
            ContextEngine contextEngine,
            Session session
    ) {
        if (shouldAddUserMessage(query, contextEngine, session)) {
            ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
            if (agentContext == null) {
                return CompletableFuture.completedFuture(null);
            }
            
            UserMessage userMessage = new UserMessage.Builder().content(query).build();
            return agentContext.addMessages(userMessage).thenRun(() -> {
                if (UserConfig.isSensitive()) {
                    logger.info("Added user message");
                } else {
                    logger.info("Added user message: %s", query);
                }
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 添加助手消息到聊天历史（异步）
     * 
     * 逻辑说明：
     * 1. 如果aiMessage不为null，添加到上下文
     *
     * @param aiMessage     助手消息对象
     * @param contextEngine 上下文引擎
     * @param session       会话实例
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> addAiMessage(
            AssistantMessage aiMessage,
            ContextEngine contextEngine,
            Session session
    ) {
        if (aiMessage != null) {
            ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
            if (agentContext == null) {
                return CompletableFuture.completedFuture(null);
            }
            return agentContext.addMessages(aiMessage).thenAccept(msgs -> {});
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 添加工具消息到聊天历史（异步）
     * 
     * 逻辑说明：
     * 1. 如果toolMessage不为null，添加到上下文
     *
     * @param toolMessage   工具消息对象
     * @param contextEngine 上下文引擎
     * @param session       会话实例
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> addToolMessage(
            ToolMessage toolMessage,
            ContextEngine contextEngine,
            Session session
    ) {
        if (toolMessage != null) {
            ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
            if (agentContext == null) {
                return CompletableFuture.completedFuture(null);
            }
            return agentContext.addMessages(toolMessage).thenAccept(msgs -> {});
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 添加workflow消息到聊天历史（异步）
     * 
     * 逻辑说明：
     * 1. 获取指定workflowId的上下文
     * 2. 添加消息到该上下文
     *
     * @param message       消息对象
     * @param workflowId    Workflow ID
     * @param contextEngine 上下文引擎
     * @param session       会话实例
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> addWorkflowMessage(
            BaseMessage message,
            String workflowId,
            ContextEngine contextEngine,
            Session session
    ) {
        ModelContext workflowContext = contextEngine.getContext(workflowId, session.getSessionId());
        if (workflowContext == null) {
            return CompletableFuture.completedFuture(null);
        }
        return workflowContext.addMessages(message).thenAccept(msgs -> {});
    }

    /**
     * 获取聊天历史（占位）
     * 
     * 逻辑说明（待实现）：
     * 1. 获取agent上下文
     * 2. 获取所有消息
     * 3. 根据config.constrain.reserved_max_chat_rounds截取最后N轮对话
     *
     * @param contextEngine 上下文引擎
     * @param session       会话实例
     * @param config        Agent配置（占位类型：Object，待转换为AgentConfig）
     * @return 聊天历史消息列表
     * @throws UnsupportedOperationException 功能尚未实现（依赖AgentConfig）
     */
    public static List<BaseMessage> getChatHistory(
            ContextEngine contextEngine,
            Session session,
            Object config
    ) {
        // TODO: 待 single_agent 模块转换完成后实现
        // 实现逻辑：
        // ModelContext agentContext = contextEngine.getContext(null, session.getSessionId());
        // List<BaseMessage> chatHistory = agentContext.getMessages();
        // int maxRounds = ((AgentConfig) config).getConstrain().getReservedMaxChatRounds();
        // int startIndex = Math.max(0, chatHistory.size() - 2 * maxRounds);
        // return chatHistory.subList(startIndex, chatHistory.size());
        throw new UnsupportedOperationException(
                "MessageUtils.getChatHistory: 依赖未转换模块(AgentConfig)，待转换single_agent模块时实现"
        );
    }
}
