// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.contextengine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.contextengine.context.SessionModelContext;
import com.openjiuwen.core.contextengine.schema.ContextEngineConfig;
import com.openjiuwen.core.contextengine.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.session.Session;

/**
 * Manages the lifecycle and processing of conversational context.
 *
 * ContextEngine acts as the central entry-point for:
 * <ol>
 * <li>Registering and configuring message processors.</li>
 * <li>Creating isolated ModelContext instances tied to a session.</li>
 * <li>Applying processor chains to enforce window limits, compression, etc.</li>
 * </ol>
 *
 * 对应 Python: agent-core/openjiuwen/core/context_engine/context_engine.py - ContextEngine
 */
public class ContextEngine {
    
    private static final LoggerProtocol logger = LogManager.getLogger(ContextEngine.class.getName());
    private static final String DEFAULT_SESSION_ID = "default_session_id";
    private static final String DEFAULT_CONTEXT_ID = "default_context_id";
    
    private final ContextEngineConfig config;
    private final Map<String, ModelContext> contextPool;
    
    /**
     * Creates a new ContextEngine with default configuration.
     */
    public ContextEngine() {
        this(null);
    }
    
    /**
     * Creates a new ContextEngine with the specified configuration.
     *
     * @param config global engine settings; if null, defaults are used
     */
    public ContextEngine(ContextEngineConfig config) {
        this.config = config != null ? config : ContextEngineConfig.defaults();
        this.contextPool = new ConcurrentHashMap<>();
    }
    
    /**
     * Create or retrieve a ModelContext for the given session & context ID.
     *
     * <p>Token counting: if tokenCounter is null, no token counting is performed.</p>
     *
     * <p>Message seeding:
     * <ul>
     * <li>if historyMessages is provided, it is used as-is;</li>
     * <li>else if memScopeId is given, the engine attempts to restore
     *     previous messages from long-term memory under that scope;</li>
     * <li>otherwise an empty message list is adopted.</li>
     * </ul>
     * </p>
     *
     * @param contextId       unique identifier for this context within the session
     * @param session         session object supplying session_id; if null, default session ID is used
     * @param historyMessages initial message list; when null, behaviour depends on memScopeId
     * @param tokenCounter    strategy for counting tokens; may be null
     * @param memScopeId      optional memory scope key; when given, messages are loaded from
     *                        long-term memory if historyMessages is null
     * @return completable future with the newly created or cached context instance
     */
    public CompletableFuture<ModelContext> createContext(
            String contextId,
            Session session,
            List<BaseMessage> historyMessages,
            TokenCounter tokenCounter,
            String memScopeId) {
        
        return CompletableFuture.supplyAsync(() -> {
            String sessionId = session != null ? session.getSessionId() : DEFAULT_SESSION_ID;
            String actualContextId = contextId != null ? contextId : DEFAULT_CONTEXT_ID;
            String fullContextId = sessionId + "_" + actualContextId;
            
            // Return existing context if available
            if (contextPool.containsKey(fullContextId)) {
                return contextPool.get(fullContextId);
            }
            
            // Load from memory if needed
            List<BaseMessage> messages = historyMessages;
            if (messages == null && memScopeId != null) {
                messages = loadContextFromMemory(
                    sessionId,
                    memScopeId,
                    config.getMemoryMessageNum()
                ).join();
            }
            
            // Create new context
            SessionModelContext context = new SessionModelContext(
                actualContextId,
                sessionId,
                messages != null ? messages : Collections.emptyList(),
                config.getDefaultWindowMessageNum(),
                tokenCounter
            );
            
            contextPool.put(fullContextId, context);
            return context;
        });
    }
    
    /**
     * Create or retrieve a ModelContext with default parameters.
     *
     * @return completable future with the context instance
     */
    public CompletableFuture<ModelContext> createContext() {
        return createContext(null, null, null, null, null);
    }
    
    /**
     * Retrieve an existing ModelContext from the pool.
     *
     * @param contextId context identifier within the session
     * @param sessionId session identifier
     * @return the ModelContext if found, or null if not found
     */
    public ModelContext getContext(String contextId, String sessionId) {
        String actualContextId = contextId != null ? contextId : DEFAULT_CONTEXT_ID;
        String actualSessionId = sessionId != null ? sessionId : DEFAULT_SESSION_ID;
        String fullContextId = actualSessionId + "_" + actualContextId;
        return contextPool.get(fullContextId);
    }
    
    /**
     * Retrieve an existing ModelContext with default IDs.
     *
     * @return the ModelContext if found, or null if not found
     */
    public ModelContext getContext() {
        return getContext(null, null);
    }
    
    /**
     * Remove contexts from the internal pool.
     *
     * <p>Behavior depends on the arguments provided:
     * <ol>
     * <li>Neither argument supplied -> delete all contexts.</li>
     * <li>Only sessionId supplied -> delete every context belonging to that session.</li>
     * <li>Both arguments supplied -> delete the single context identified.</li>
     * </ol>
     * </p>
     *
     * @param contextId logical context identifier; when provided, sessionId must also be supplied
     * @param sessionId session identifier used to scope the deletion
     */
    public void clearContext(String contextId, String sessionId) {
        // Clear all contexts
        if (sessionId == null) {
            contextPool.clear();
            return;
        }
        
        // Clear all contexts for session
        if (contextId == null) {
            List<String> toDelete = new ArrayList<>();
            for (Map.Entry<String, ModelContext> entry : contextPool.entrySet()) {
                if (entry.getValue().getSessionId().equals(sessionId)) {
                    toDelete.add(entry.getKey());
                }
            }
            
            if (toDelete.isEmpty()) {
                logger.warning("Delete context failed, session %s does not exist", sessionId);
                return;
            }
            
            for (String key : toDelete) {
                contextPool.remove(key);
            }
            return;
        }
        
        // Clear specific context
        String fullContextId = sessionId + "_" + contextId;
        if (!contextPool.containsKey(fullContextId)) {
            throw new IllegalArgumentException(
                String.format("Delete context failed, context %s does not exist", fullContextId)
            );
        }
        contextPool.remove(fullContextId);
    }
    
    /**
     * Clear all contexts.
     */
    public void clearContext() {
        clearContext(null, null);
    }
    
    /**
     * Batch-persist multiple contexts and their runtime states.
     *
     * <p>Each context's messages, sliding-window position, token count and statistics
     * are saved locally. If memScopeId is provided, the same snapshots are
     * also written to long-term memory under that scope for later cross-session
     * restoration.</p>
     *
     * @param contextIds  list of target context identifiers to save
     * @param session     session object; if null, default session ID is used
     * @param memScopeId  optional memory scope key; when given, all listed contexts
     *                    are additionally saved to long-term memory with this ID
     * @return completable future that completes when all saves are done
     */
    public CompletableFuture<Void> saveContexts(
            List<String> contextIds,
            Session session,
            String memScopeId) {
        
        return CompletableFuture.runAsync(() -> {
            for (String contextId : contextIds) {
                String sessionId = session != null ? session.getSessionId() : DEFAULT_SESSION_ID;
                String fullContextId = sessionId + "_" + contextId;
                
                ModelContext modelContext = contextPool.get(fullContextId);
                if (modelContext == null) {
                    continue;
                }
                
                SessionModelContext context = (SessionModelContext) modelContext;
                
                if (memScopeId != null) {
                    List<BaseMessage> newMessages = context.getMessages(false);
                    saveContextToMemory(sessionId, memScopeId, newMessages).join();
                }
                
                context.onSave();
            }
        });
    }
    
    /**
     * Load context messages from long-term memory.
     *
     * <p>Python source: agent-core/openjiuwen/core/context_engine/context_engine.py</p>
     *
     * @param sessionId  session identifier
     * @param memScopeId memory scope identifier
     * @param messageNum number of messages to load
     * @return completable future with list of messages
     */
    protected CompletableFuture<List<BaseMessage>> loadContextFromMemory(
            String sessionId,
            String memScopeId,
            int messageNum) {
        // Python: messages = await LongTermMemory().get_recent_messages(
        //     scope_id=mem_scope_id,
        //     session_id=session_id,
        //     num=message_num
        // )
        try {
            return LongTermMemory.getInstance().getRecentMessages(
                    null,       // userId - uses default
                    memScopeId,
                    sessionId,
                    messageNum
            );
        } catch (Exception e) {
            logger.warning("Failed to load context from memory: %s", e.getMessage());
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }
    
    /**
     * Save context messages to long-term memory.
     *
     * <p>Python source: agent-core/openjiuwen/core/context_engine/context_engine.py</p>
     *
     * @param sessionId  session identifier
     * @param memScopeId memory scope identifier
     * @param messages   messages to save
     * @return completable future that completes when save is done
     */
    protected CompletableFuture<Void> saveContextToMemory(
            String sessionId,
            String memScopeId,
            List<BaseMessage> messages) {
        // Python: await LongTermMemory().add_messages(
        //     messages,
        //     AgentMemoryConfig(),
        //     timestamp=datetime.datetime.now(tz=timezone.utc),
        //     scope_id=mem_scope_id,
        //     session_id=session_id
        // )
        if (messages == null || messages.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            // Use default AgentMemoryConfig
            // Python default: gen_mem=True, gen_mem_with_history_msg_num=5
            return LongTermMemory.getInstance().addMessages(
                    messages,
                    AgentMemoryConfig.builder().build(),
                    null,           // userId - uses default
                    memScopeId,
                    sessionId,
                    Instant.now(),
                    true,           // genMem - Python default
                    5               // genMemWithHistoryMsgNum - Python default
            );
        } catch (Exception e) {
            logger.warning("Failed to save context to memory: %s", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
}

