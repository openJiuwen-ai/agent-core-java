/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.processor.compressor.CurrentRoundCompressor;
import com.openjiuwen.core.context.processor.compressor.CurrentRoundCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.DialogueCompressor;
import com.openjiuwen.core.context.processor.compressor.DialogueCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressor;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig;
import com.openjiuwen.core.context.processor.offloader.MessageOffloader;
import com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloader;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloaderConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Manages the lifecycle and processing of conversational context.
 * <p>
 * ContextEngine acts as the central entry-point for:
 * <ol>
 *   <li>Registering and configuring message processors.</li>
 *   <li>Creating isolated {@link ModelContext} instances tied to a session.</li>
 *   <li>Applying processor chains to enforce window limits, compression, etc.</li>
 * </ol>
 * <p>
 * Mirrors Python's {@code ContextEngine} from {@code context_engine/context_engine.py}.
 */
public class ContextEngine {

    /**
     * Global registry mapping processor type names to their factory functions.
     * Each factory takes a config object and returns a ContextProcessor instance.
     */
    private static final Map<String, Function<Object, ContextProcessor>> PROCESSOR_FACTORY_MAP =
            new LinkedHashMap<>();

    /**
     * Global registry mapping processor type names to their class.
     */
    private static final Map<String, Class<? extends ContextProcessor>> PROCESSOR_CLASS_MAP =
            new LinkedHashMap<>();

    /*
     * Auto-register all built-in processors so they can be resolved by type name at runtime.
     * Mirrors Python's @ContextEngine.register_processor() decorator applied to each processor class.
     */
    static {
        registerProcessor("CurrentRoundCompressor", CurrentRoundCompressor.class,
                cfg -> new CurrentRoundCompressor((CurrentRoundCompressorConfig) cfg));
        registerProcessor("DialogueCompressor", DialogueCompressor.class,
                cfg -> new DialogueCompressor((DialogueCompressorConfig) cfg));
        registerProcessor("RoundLevelCompressor", RoundLevelCompressor.class,
                cfg -> new RoundLevelCompressor((RoundLevelCompressorConfig) cfg));
        registerProcessor("MessageOffloader", MessageOffloader.class,
                cfg -> new MessageOffloader((MessageOffloaderConfig) cfg));
        registerProcessor("MessageSummaryOffloader", MessageSummaryOffloader.class,
                cfg -> new MessageSummaryOffloader((MessageSummaryOffloaderConfig) cfg));
    }

    private final ContextEngineConfig config;
    private final Map<String, ModelContext> contextPool = new HashMap<>();

    public ContextEngine() {
        this(null);
    }

    public ContextEngine(ContextEngineConfig config) {
        this.config = config != null ? config : ContextEngineConfig.builder().build();
        this.config.validate();
    }

    // ==================================================================
    // Context lifecycle
    // ==================================================================

    /**
     * Create or retrieve a ModelContext for the given session and context ID.
     *
     * @param contextId       unique identifier for this context within the session
     * @param session         session object; if null, a default session ID is used
     * @param processors      list of (processorType, configObject) tuples
     * @param historyMessages initial message list
     * @param tokenCounter    token counting strategy
     * @return the created or cached ModelContext
     */
    public ModelContext createContext(
            String contextId,
            Session session,
            List<ProcessorSpec> processors,
            List<BaseMessage> historyMessages,
            TokenCounter tokenCounter) {

        contextId = processContextId(contextId);
        String sessionId = session != null ? session.getSessionId() : "default_session_id";
        String fullContextId = sessionId + "_" + contextId;

        if (contextPool.containsKey(fullContextId)) {
            ModelContext context = contextPool.get(fullContextId);
            loadStateFromSession(context, session, historyMessages);
            return context;
        }

        List<ContextProcessor> processorInstances = new ArrayList<>();
        if (processors != null) {
            for (ProcessorSpec spec : processors) {
                processorInstances.add(createProcessor(spec.processorType(), spec.config()));
            }
        }

        SessionModelContext context = new SessionModelContext(
                contextId,
                sessionId,
                config,
                historyMessages != null ? historyMessages : new ArrayList<>(),
                processorInstances,
                tokenCounter);

        loadStateFromSession(context, session, historyMessages);
        contextPool.put(fullContextId, context);
        return context;
    }

    /**
     * Create context with defaults.
     */
    public ModelContext createContext(String contextId, Session session) {
        return createContext(contextId, session, null, null, null);
    }

    /**
     * Compatibility helper for translated tests that create a context without
     * explicitly passing processors, history, or token counter.
     */
    public ModelContext createContextSimple(String contextId, Session session) {
        return createContext(contextId, session);
    }

    /**
     * Compatibility helper for translated tests that create a context with
     * initial history messages only.
     */
    public ModelContext createContextWithHistory(
            String contextId,
            Session session,
            List<BaseMessage> historyMessages) {
        return createContext(contextId, session, null, historyMessages, null);
    }

    /**
     * Retrieve an existing ModelContext from the pool.
     */
    public ModelContext getContext(String contextId, String sessionId) {
        contextId = processContextId(contextId);
        String fullContextId = sessionId + "_" + contextId;
        return contextPool.getOrDefault(fullContextId, null);
    }

    /**
     * Retrieve a context from the default session scope.
     */
    public ModelContext getContext(String contextId) {
        return getContext(contextId, "default_session_id");
    }

    /**
     * Remove contexts from the internal pool.
     *
     * @param contextId if null and sessionId is provided, removes all contexts for that session
     * @param sessionId if null, removes all contexts
     */
    public void clearContext(String contextId, String sessionId) {
        if (sessionId == null) {
            contextPool.clear();
            return;
        }

        if (contextId == null) {
            List<String> toDelete = new ArrayList<>();
            for (var entry : contextPool.entrySet()) {
                if (entry.getValue().sessionId().equals(sessionId)) {
                    toDelete.add(entry.getKey());
                }
            }

            if (toDelete.isEmpty()) {
                Loggers.CONTEXT_ENGINE.warning(
                        "Delete context failed, session does not exist: " + sessionId);
                return;
            }

            for (String key : toDelete) {
                contextPool.remove(key);
            }
            return;
        }

        contextId = processContextId(contextId);
        String fullContextId = sessionId + "_" + contextId;
        if (!contextPool.containsKey(fullContextId)) {
            Loggers.CONTEXT_ENGINE.warning(
                    "Delete context failed, context does not exist: " + fullContextId);
            return;
        }
        contextPool.remove(fullContextId);
    }

    /**
     * Clear all contexts across all sessions.
     */
    public void clearContext() {
        clearContext(null, null);
    }

    /**
     * Clear all contexts associated with a given session.
     */
    public void clearContextBySession(String sessionId) {
        clearContext(null, sessionId);
    }

    /**
     * Batch-persist multiple contexts and their runtime states.
     *
     * @param session    the session to save to
     * @param contextIds list of target context identifiers; if null, saves all for the session
     */
    public void saveContexts(Session session, List<String> contextIds) {
        if (session == null) {
            Loggers.CONTEXT_ENGINE.warning("Save context failed, session cannot be None");
            return;
        }

        String sessionId = session.getSessionId();
        Map<String, Object> states = new HashMap<>();

        List<String> idsToSave = contextIds;
        if (idsToSave == null) {
            idsToSave = new ArrayList<>();
            for (var entry : contextPool.entrySet()) {
                if (entry.getValue().sessionId().equals(sessionId)) {
                    idsToSave.add(entry.getValue().contextId());
                }
            }
        }

        for (String ctxId : idsToSave) {
            String processedId = processContextId(ctxId);
            String fullId = sessionId + "_" + processedId;
            ModelContext context = contextPool.get(fullId);
            if (context instanceof StatefulContext stateful) {
                states.put(processedId, stateful.saveState());
            }
        }

        saveStateToSession(session, states);
    }

    // ==================================================================
    // Processor registration (static)
    // ==================================================================

    /**
     * Register a processor class so the engine can instantiate it at runtime.
     *
     * @param processorType the type name (typically the simple class name)
     * @param processorClass the processor class
     * @param factory a function that takes a config object and creates the processor
     */
    public static void registerProcessor(
            String processorType,
            Class<? extends ContextProcessor> processorClass,
            Function<Object, ContextProcessor> factory) {
        PROCESSOR_CLASS_MAP.put(processorType, processorClass);
        PROCESSOR_FACTORY_MAP.put(processorType, factory);
    }

    /**
     * Register a processor class with a constructor-based factory.
     */
    public static void registerProcessor(
            String processorType,
            Class<? extends ContextProcessor> processorClass) {
        PROCESSOR_CLASS_MAP.put(processorType, processorClass);
    }

    /**
     * Get a registered processor class by type name.
     */
    public static Class<? extends ContextProcessor> getProcessorClass(String processorType) {
        return PROCESSOR_CLASS_MAP.get(processorType);
    }

    // ==================================================================
    // Processor spec record
    // ==================================================================

    /**
     * Specifies a processor type and its associated configuration.
     */
    public record ProcessorSpec(String processorType, Object config) {
    }

    // ==================================================================
    // Private helpers
    // ==================================================================

    private ContextProcessor createProcessor(String processorType, Object processorConfig) {
        // Try factory first
        Function<Object, ContextProcessor> factory = PROCESSOR_FACTORY_MAP.get(processorType);
        if (factory != null) {
            try {
                return factory.apply(processorConfig);
            } catch (Exception e) {
                throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                        "error_msg", "init processor type '" + processorType + "' failed: " + e.getMessage());
            }
        }

        // Try class-based instantiation
        Class<? extends ContextProcessor> processorClass = PROCESSOR_CLASS_MAP.get(processorType);
        if (processorClass == null) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "cannot find processor type '" + processorType + "'");
        }

        try {
            var constructor = processorClass.getConstructor(processorConfig.getClass());
            return constructor.newInstance(processorConfig);
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "init processor type '" + processorType + "' failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadStateFromSession(
            ModelContext context,
            Session session,
            List<BaseMessage> historyMessages) {

        if (session == null) {
            return;
        }

        Object rawStates = session.getState("context");
        if (rawStates == null) {
            return;
        }

        if (!(context instanceof StatefulContext stateful)) {
            return;
        }

        Map<String, Object> states = (Map<String, Object>) rawStates;

        if (historyMessages != null) {
            String contextId = context.contextId();
            Map<String, Object> ctxState = (Map<String, Object>) states.getOrDefault(contextId, new HashMap<>());
            ctxState.put("messages", historyMessages);
            states.put(contextId, ctxState);
        }

        stateful.loadState(states);
    }

    private static void saveStateToSession(Session session, Map<String, Object> states) {
        if (session == null) {
            return;
        }
        session.updateState(Map.of("context", states));
    }

    private static String processContextId(String contextId) {
        if (contextId == null) {
            return "default_context_id";
        }
        return contextId.replace(".", "_");
    }
}
