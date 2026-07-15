/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
import com.openjiuwen.core.context.processor.compressor.FullCompactProcessor;
import com.openjiuwen.core.context.processor.compressor.FullCompactProcessorConfig;
import com.openjiuwen.core.context.processor.compressor.MicroCompactProcessor;
import com.openjiuwen.core.context.processor.compressor.MicroCompactProcessorConfig;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressor;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig;
import com.openjiuwen.core.context.processor.offloader.MessageOffloader;
import com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloader;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloaderConfig;
import com.openjiuwen.core.context.processor.offloader.ToolResultBudgetProcessor;
import com.openjiuwen.core.context.processor.offloader.ToolResultBudgetProcessorConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.token.SimpleTokenCounter;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.sysop.SysOperation;
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
 * <li>Registering and configuring message processors.</li>
 * <li>Creating isolated {@link ModelContext} instances tied to a session.</li>
 * <li>Applying processor chains to enforce window limits, compression, etc.</li>
 * </ol>
 * <p>
 * Mirrors Python's {@code ContextEngine} from {@code context_engine/context_engine.py}.
 * 
 * @since 0.1.7
 */
public class ContextEngine {
    private static final Map<String, Function<Object, ContextProcessor>> PROCESSOR_FACTORY_MAP = new LinkedHashMap<>();

    /**
     * Global registry mapping processor type names to their class.
     * 
     * @since 0.1.7
     */
    private static final Map<String, Class<? extends ContextProcessor>> PROCESSOR_CLASS_MAP = new LinkedHashMap<>();

    /*
     * Auto-register all built-in processors so they can be isResolved by type name at runtime.
     * Mirrors Python's @ContextEngine.register_processor() decorator applied to each processor class.
     */
    static {
        registerProcessor("CurrentRoundCompressor", CurrentRoundCompressor.class, cfg -> new CurrentRoundCompressor(
                (cfg instanceof CurrentRoundCompressorConfig __cast73 ? __cast73 : null)));
        registerProcessor("DialogueCompressor", DialogueCompressor.class,
                cfg -> new DialogueCompressor((cfg instanceof DialogueCompressorConfig __cast75 ? __cast75 : null)));
        registerProcessor("RoundLevelCompressor", RoundLevelCompressor.class, cfg -> new RoundLevelCompressor(
                (cfg instanceof RoundLevelCompressorConfig __cast77 ? __cast77 : null)));
        registerProcessor("MicroCompactProcessor", MicroCompactProcessor.class, cfg -> new MicroCompactProcessor(
                (cfg instanceof MicroCompactProcessorConfig __cast79 ? __cast79 : null)));
        registerProcessor("FullCompactProcessor", FullCompactProcessor.class, cfg -> new FullCompactProcessor(
                (cfg instanceof FullCompactProcessorConfig __cast81 ? __cast81 : null)));
        registerProcessor("MessageOffloader", MessageOffloader.class,
                cfg -> new MessageOffloader((cfg instanceof MessageOffloaderConfig __cast83 ? __cast83 : null)));
        registerProcessor("MessageSummaryOffloader", MessageSummaryOffloader.class, cfg -> new MessageSummaryOffloader(
                (cfg instanceof MessageSummaryOffloaderConfig __cast85 ? __cast85 : null)));
        registerProcessor("ToolResultBudgetProcessor", ToolResultBudgetProcessor.class,
                cfg -> new ToolResultBudgetProcessor((ToolResultBudgetProcessorConfig) cfg));
    }

    private final ContextEngineConfig config;

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, ModelContext> contextPool = new HashMap<>();
    private final Object workspace;
    private final SysOperation sysOperation;

    /**
     * ContextEngine.
     * 
     * @since 0.1.7
     */
    public ContextEngine() {
        this(null, null, null);
    }

    /**
     * ContextEngine.
     * 
     * @param config config
     * @since 0.1.7
     */
    public ContextEngine(ContextEngineConfig config) {
        this(config, null, null);
    }

    /**
     * ContextEngine.
     * 
     * @param config config
     * @param workspace workspace
     * @param sysOperation sysOperation
     * @since 0.1.7
     */
    public ContextEngine(ContextEngineConfig config, Object workspace, SysOperation sysOperation) {
        this.config = config != null ? config : ContextEngineConfig.builder().build();
        this.config.validate();
        this.workspace = workspace;
        this.sysOperation = sysOperation;
    }

    // Context lifecycle

    /**
     * Create or retrieve a ModelContext for the given session and context ID.
     * 
     * @param contextId unique identifier for this context within the session
     * @param session session object; if null, a default session ID is used
     * @param processors list of (processorType, configObject) tuples
     * @param historyMessages initial message list
     * @param tokenCounter token counting strategy
     * @return the created or cached ModelContext
     * @since 0.1.7
     */
    public ModelContext createContext(String contextId, Session session, List<ProcessorSpec> processors,
            List<BaseMessage> historyMessages, TokenCounter tokenCounter) {
        TokenCounter effectiveTokenCounter = tokenCounter != null ? tokenCounter : new SimpleTokenCounter();
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

        SessionModelContext context = new SessionModelContext(contextId, sessionId, config,
                historyMessages != null ? historyMessages : new ArrayList<>(), processorInstances,
                effectiveTokenCounter, session, workspace, sysOperation);

        loadStateFromSession(context, session, historyMessages);
        contextPool.put(fullContextId, context);
        return context;
    }

    /**
     * Create context with defaults.
     * 
     * @param contextId contextId
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public ModelContext createContext(String contextId, Session session) {
        return createContext(contextId, session, null, null, null);
    }

    /**
     * Compatibility helper for translated tests that create a context without
     * explicitly passing processors, history, or token counter.
     * 
     * @param contextId contextId
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public ModelContext createContextSimple(String contextId, Session session) {
        return createContext(contextId, session);
    }

    /**
     * Compatibility helper for translated tests that create a context with
     * initial history messages only.
     * 
     * @param contextId contextId
     * @param session session
     * @param historyMessages historyMessages
     * @return the result
     * @since 0.1.7
     */
    public ModelContext createContextWithHistory(String contextId, Session session, List<BaseMessage> historyMessages) {
        return createContext(contextId, session, null, historyMessages, null);
    }

    /**
     * Retrieve an existing ModelContext from the pool.
     * 
     * @param contextId contextId
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    public ModelContext getContext(String contextId, String sessionId) {
        contextId = processContextId(contextId);
        String fullContextId = sessionId + "_" + contextId;
        return contextPool.getOrDefault(fullContextId, null);
    }

    /**
     * Retrieve a context from the default session scope.
     * 
     * @param contextId contextId
     * @return the result
     * @since 0.1.7
     */
    public ModelContext getContext(String contextId) {
        return getContext(contextId, "default_session_id");
    }

    /**
     * Remove contexts from the internal pool.
     * 
     * @param contextId if null and sessionId is provided, removes all contexts for that session
     * @param sessionId if null, removes all contexts
     * @since 0.1.7
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
                Loggers.CONTEXT_ENGINE.warning("Delete context failed, session does not exist: " + sessionId);
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
            Loggers.CONTEXT_ENGINE.warning("Delete context failed, context does not exist: " + fullContextId);
            return;
        }
        contextPool.remove(fullContextId);
    }

    /**
     * Clear all contexts across all sessions.
     * 
     * @since 0.1.7
     */
    public void clearContext() {
        clearContext(null, null);
    }

    /**
     * Clear all contexts associated with a given session.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public void clearContextBySession(String sessionId) {
        clearContext(null, sessionId);
    }

    /**
     * Batch-persist multiple contexts and their runtime states.
     * 
     * @param session the session to save to
     * @param contextIds list of target context identifiers; if null, saves all for the session
     * @since 0.1.7
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

    // Processor registration (static)

    /**
     * Register a processor class so the engine can instantiate it at runtime.
     * 
     * @param processorType the type name (typically the simple class name)
     * @param processorClass the processor class
     * @param factory a function that takes a config object and creates the processor
     * @since 0.1.7
     */
    public static void registerProcessor(String processorType, Class<? extends ContextProcessor> processorClass,
            Function<Object, ContextProcessor> factory) {
        PROCESSOR_CLASS_MAP.put(processorType, processorClass);
        PROCESSOR_FACTORY_MAP.put(processorType, factory);
    }

    /**
     * Register a processor class with a constructor-based factory.
     * 
     * @param processorType processorType
     * @param processorClass processorClass
     * @since 0.1.7
     */
    public static void registerProcessor(String processorType, Class<? extends ContextProcessor> processorClass) {
        PROCESSOR_CLASS_MAP.put(processorType, processorClass);
    }

    /**
     * Get a registered processor class by type name.
     * 
     * @param processorType processorType
     * @return the result
     * @since 0.1.7
     */
    public static Class<? extends ContextProcessor> getProcessorClass(String processorType) {
        return PROCESSOR_CLASS_MAP.get(processorType);
    }

    // Processor spec record

    /**
     * Specifies a processor type and its associated configuration.
     * 
     * @since 0.1.7
     */
    public record ProcessorSpec(String processorType, Object config) {
    }

    // Private helpers

    /**
     * createProcessor.
     * 
     * @param processorType processorType
     * @param processorConfig processorConfig
     * @return the result
     * @since 0.1.7
     */
    private ContextProcessor createProcessor(String processorType, Object processorConfig) {
        // Try factory first
        Function<Object, ContextProcessor> factory = PROCESSOR_FACTORY_MAP.get(processorType);
        if (factory != null) {
            try {
                return factory.apply(processorConfig);
            } catch (Exception e) {
                throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR, "error_msg",
                        "init processor type '" + processorType + "' failed: " + e.getMessage());
            }
        }

        // Try class-based instantiation
        Class<? extends ContextProcessor> processorClass = PROCESSOR_CLASS_MAP.get(processorType);
        if (processorClass == null) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR, "error_msg",
                    "cannot find processor type '" + processorType + "'");
        }

        try {
            var constructor = processorClass.getConstructor(processorConfig.getClass());
            return constructor.newInstance(processorConfig);
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR, "error_msg",
                    "init processor type '" + processorType + "' failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * loadStateFromSession.
     * 
     * @param context context
     * @param session session
     * @param historyMessages historyMessages
     * @since 0.1.7
     */
    private static void loadStateFromSession(ModelContext context, Session session, List<BaseMessage> historyMessages) {
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

        if (!(rawStates instanceof Map<?, ?> rawStateMap)) {
            return;
        }
        Map<String, Object> states = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawStateMap.entrySet()) {
            states.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        if (historyMessages != null) {
            String contextId = context.contextId();
            Object rawContextState = states.get(contextId);
            Map<String, Object> ctxState = rawContextState instanceof Map<?, ?> rawCtxMap
                    ? new HashMap<>(rawCtxMap.entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(entry -> String.valueOf(entry.getKey()),
                                    Map.Entry::getValue, (left, right) -> right, HashMap::new)))
                    : new HashMap<>();
            ctxState.put("messages", historyMessages);
            states.put(contextId, ctxState);
        }

        stateful.loadState(states);
    }

    /**
     * saveStateToSession.
     * 
     * @param session session
     * @param states states
     * @since 0.1.7
     */
    private static void saveStateToSession(Session session, Map<String, Object> states) {
        if (session == null) {
            return;
        }
        session.updateState(Map.of("context", states));
    }

    /**
     * processContextId.
     * 
     * @param contextId contextId
     * @return the result
     * @since 0.1.7
     */
    private static String processContextId(String contextId) {
        if (contextId == null) {
            return "default_context_id";
        }
        return contextId.replace(".", "_");
    }
}
