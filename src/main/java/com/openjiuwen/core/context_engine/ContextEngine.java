/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.context.ContextUtils;
import com.openjiuwen.core.context_engine.context.KVCacheManager;
import com.openjiuwen.core.context_engine.context.SessionModelContext;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle and processing of conversational context.
 *
 * <p>Mirrors Python's {@code ContextEngine} in
 * {@code openjiuwen/core/context_engine/context_engine.py}.</p>
 */
public class ContextEngine {
    public static final String DEFAULT_CONTEXT_ID = "default_context_id";
    public static final String DEFAULT_SESSION_ID = "default_session_id";

    private static final Map<String, ProcessorFactory> PROCESSOR_FACTORY_MAP = new ConcurrentHashMap<>();

    private final ContextEngineConfig config;
    private final SessionModelContext.WorkspacePort workspace;
    private final SessionModelContext.SysOperationPort sysOperation;
    private final SessionModelContext.ModelContextWindowTokenProvider modelContextWindowTokenProvider;
    private final Map<String, ModelContext> contextPool = new LinkedHashMap<>();

    public ContextEngine() {
        this(null);
    }

    public ContextEngine(ContextEngineConfig config) {
        this(config, null, null);
    }

    public ContextEngine(ContextEngineConfig config, SessionModelContext.WorkspacePort workspace,
                         SessionModelContext.SysOperationPort sysOperation) {
        this(config, workspace, sysOperation, ContextUtils::fetchOpenrouterModelContextWindowTokens);
    }

    public ContextEngine(ContextEngineConfig config, SessionModelContext.WorkspacePort workspace,
                         SessionModelContext.SysOperationPort sysOperation,
                         SessionModelContext.ModelContextWindowTokenProvider modelContextWindowTokenProvider) {
        this.config = config == null ? new ContextEngineConfig() : config;
        this.workspace = workspace;
        this.sysOperation = sysOperation;
        this.modelContextWindowTokenProvider = modelContextWindowTokenProvider == null
                ? ContextUtils::fetchOpenrouterModelContextWindowTokens
                : modelContextWindowTokenProvider;
    }

    public ModelContext createContext() {
        return createContext(DEFAULT_CONTEXT_ID, null);
    }

    public ModelContext createContext(String contextId, Object session) {
        return createContext(contextId, session, null, null, null);
    }

    public ModelContext createContext(String contextId, Object session, List<ProcessorSpec> processors,
                                      List<BaseMessage> historyMessages,
                                      ModelContext.TokenCounterPort tokenCounter) {
        String processedContextId = processContextId(contextId == null ? DEFAULT_CONTEXT_ID : contextId);
        String sessionId = resolveSessionId(session);
        String fullContextId = sessionId + "_" + processedContextId;
        ModelContext existingContext = contextPool.get(fullContextId);
        if (existingContext != null) {
            if (existingContext instanceof SessionModelContext sessionModelContext) {
                sessionModelContext.setSessionRef(session);
            }
            loadStateFromSession(existingContext, session, historyMessages);
            return existingContext;
        }

        List<SessionModelContext.ContextProcessorPort> processorInstances = new ArrayList<>();
        for (ProcessorSpec processorSpec : processors == null ? List.<ProcessorSpec>of() : processors) {
            processorInstances.add(createProcessor(processorSpec.processorType(), processorSpec.config()));
        }

        if (config.isEnableOpenrouterModelContextWindowTokens()) {
            modelContextWindowTokenProvider.fetch(config.getOpenrouterRequestTimeout());
        }

        SessionModelContext context = new SessionModelContext(
                processedContextId,
                sessionId,
                config,
                historyMessages == null ? List.of() : historyMessages,
                processorInstances,
                tokenCounter,
                session,
                workspace,
                sysOperation,
                config.isEnableKvCacheRelease() ? new KVCacheManager(sessionId) : null,
                modelContextWindowTokenProvider
        );
        loadStateFromSession(context, session, historyMessages);
        contextPool.put(fullContextId, context);
        return context;
    }

    public ModelContext getContext() {
        return getContext(DEFAULT_CONTEXT_ID, DEFAULT_SESSION_ID);
    }

    public ModelContext getContext(String contextId) {
        return getContext(contextId, DEFAULT_SESSION_ID);
    }

    public ModelContext getContext(String contextId, String sessionId) {
        String processedContextId = processContextId(contextId == null ? DEFAULT_CONTEXT_ID : contextId);
        String effectiveSessionId = sessionId == null ? DEFAULT_SESSION_ID : sessionId;
        return contextPool.get(effectiveSessionId + "_" + processedContextId);
    }

    public Object compressContext(String contextId, Object session) {
        return compressContext(contextId, session, null, null, null);
    }

    public Object compressContext(String contextId, Object session, String sessionId, List<String> processorTypes,
                                  Map<String, Object> kwargs) {
        String resolvedSessionId = session != null ? resolveSessionId(session)
                : (sessionId == null ? DEFAULT_SESSION_ID : sessionId);
        String effectiveContextId = contextId == null ? DEFAULT_CONTEXT_ID : contextId;
        ModelContext context = getContext(effectiveContextId, resolvedSessionId);
        if (context == null) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "cannot find context '" + effectiveContextId + "' in session '"
                            + resolvedSessionId + "'");
        }
        if (!(context instanceof SessionModelContext sessionModelContext)) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "context '" + effectiveContextId + "' does not support active compression");
        }

        Map<String, Object> effectiveKwargs = new LinkedHashMap<>(kwargs == null ? Map.of() : kwargs);
        if (sysOperation != null) {
            effectiveKwargs.putIfAbsent("sys_operation", sysOperation);
        }
        return sessionModelContext.compressContext(processorTypes, effectiveKwargs).toCompletableFuture().join();
    }

    public void clearContext() {
        clearContext(null, null);
    }

    public void clearContext(String contextId, String sessionId) {
        if (sessionId == null) {
            contextPool.clear();
            return;
        }

        if (contextId == null) {
            List<String> keysToDelete = new ArrayList<>();
            for (Map.Entry<String, ModelContext> entry : contextPool.entrySet()) {
                if (sessionId.equals(entry.getValue().sessionId())) {
                    keysToDelete.add(entry.getKey());
                }
            }
            for (String key : keysToDelete) {
                contextPool.remove(key);
            }
            return;
        }

        String processedContextId = processContextId(contextId);
        contextPool.remove(sessionId + "_" + processedContextId);
    }

    public Map<String, Object> saveContexts(Object session) {
        return saveContexts(session, null);
    }

    public Map<String, Object> saveContexts(Object session, List<String> contextIds) {
        if (session == null) {
            return null;
        }
        String sessionId = resolveSessionId(session);
        Map<String, Object> states = new LinkedHashMap<>();
        List<String> idsToSave = contextIds == null ? contextIdsForSession(sessionId) : contextIds;

        for (String rawContextId : idsToSave) {
            String processedContextId = processContextId(rawContextId);
            ModelContext context = contextPool.get(sessionId + "_" + processedContextId);
            if (context instanceof SessionModelContext sessionModelContext) {
                states.put(processedContextId, sessionModelContext.saveState());
            }
        }

        saveStateToSession(session, states);
        return states;
    }

    public static void registerProcessor(String processorType, ProcessorFactory factory) {
        PROCESSOR_FACTORY_MAP.put(processorType, factory);
    }

    public static void registerProcessor(String processorType,
                                         Class<? extends SessionModelContext.ContextProcessorPort> processorClass) {
        PROCESSOR_FACTORY_MAP.put(processorType, config -> instantiateProcessor(processorType, processorClass, config));
    }

    public static Set<String> registeredProcessorTypes() {
        return new LinkedHashSet<>(PROCESSOR_FACTORY_MAP.keySet());
    }

    private List<String> contextIdsForSession(String sessionId) {
        List<String> contextIds = new ArrayList<>();
        for (ModelContext context : contextPool.values()) {
            if (sessionId.equals(context.sessionId())) {
                contextIds.add(context.contextId());
            }
        }
        return contextIds;
    }

    private SessionModelContext.ContextProcessorPort createProcessor(String processorType, Object processorConfig) {
        ProcessorFactory factory = PROCESSOR_FACTORY_MAP.get(processorType);
        if (factory == null) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg", "cannot find processor type '" + processorType + "'");
        }
        try {
            return factory.create(processorConfig);
        } catch (RuntimeException ex) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "init processor type '" + processorType + "' failed",
                    null,
                    ex,
                    Map.of("error_msg", "init processor type '" + processorType + "' failed"));
        }
    }

    private static SessionModelContext.ContextProcessorPort instantiateProcessor(
            String processorType,
            Class<? extends SessionModelContext.ContextProcessorPort> processorClass,
            Object config) {
        try {
            try {
                return processorClass.getConstructor(config == null ? Object.class : config.getClass())
                        .newInstance(config);
            } catch (NoSuchMethodException ignored) {
                return processorClass.getConstructor(Object.class).newInstance(config);
            }
        } catch (ReflectiveOperationException ex) {
            throw ErrorHelper.buildError(StatusCode.CONTEXT_EXECUTION_ERROR,
                    "init processor type '" + processorType + "' failed",
                    null,
                    ex,
                    Map.of("error_msg", "init processor type '" + processorType + "' failed"));
        }
    }

    private static void loadStateFromSession(ModelContext context, Object session, List<BaseMessage> historyMessages) {
        if (session == null || !(context instanceof SessionModelContext sessionModelContext)) {
            return;
        }
        Map<String, Object> states = asStringObjectMap(readSessionContextState(session).orElse(null));
        if (states == null) {
            return;
        }
        if (historyMessages != null) {
            Map<String, Object> contextState = asStringObjectMap(states.get(context.contextId()));
            if (contextState == null) {
                contextState = new LinkedHashMap<>();
            }
            contextState.put("messages", historyMessages);
            states.put(context.contextId(), contextState);
        }
        sessionModelContext.loadState(states);
    }

    private static void saveStateToSession(Object session, Map<String, Object> states) {
        if (session == null) {
            return;
        }
        if (invokeUpdateState(session, contextUpdate(null))) {
            invokeUpdateState(session, contextUpdate(states));
            return;
        }
        readInner(session).ifPresent(inner -> {
            if (invokeUpdateState(inner, contextUpdate(null))) {
                invokeUpdateState(inner, contextUpdate(states));
            }
        });
    }

    private static Map<String, Object> contextUpdate(Object value) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("context", value);
        return update;
    }

    private static Optional<Object> readSessionContextState(Object session) {
        Optional<Object> directState = invokeStateGetter(session, "getState")
                .or(() -> invokeStateGetter(session, "get_state"));
        if (directState.isPresent()) {
            Map<String, Object> state = asStringObjectMap(directState.get());
            return state == null ? Optional.empty() : Optional.ofNullable(state.get("context"));
        }

        Optional<Object> directContext = invokeContextGetter(session, "getState")
                .or(() -> invokeContextGetter(session, "get_state"));
        if (directContext.isPresent()) {
            return directContext;
        }

        return readInner(session).flatMap(ContextEngine::readSessionContextState);
    }

    private static Optional<Object> invokeStateGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> invokeContextGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName, String.class);
            return Optional.ofNullable(method.invoke(target, "context"));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static boolean invokeUpdateState(Object target, Map<String, Object> update) {
        return invokeUpdateMethod(target, "updateState", update)
                || invokeUpdateMethod(target, "update_state", update);
    }

    private static boolean invokeUpdateMethod(Object target, String methodName, Map<String, Object> update) {
        try {
            Method method = target.getClass().getMethod(methodName, Map.class);
            method.invoke(target, update);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Optional<Object> readInner(Object target) {
        try {
            Field field = target.getClass().getDeclaredField("_inner");
            field.setAccessible(true);
            return Optional.ofNullable(field.get(target));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static String resolveSessionId(Object session) {
        if (session == null) {
            return DEFAULT_SESSION_ID;
        }
        if (session instanceof SessionPort sessionPort) {
            return sessionPort.getSessionId();
        }
        return invokeString(session, "getSessionId")
                .or(() -> invokeString(session, "get_session_id"))
                .or(() -> readInner(session).flatMap(ContextEngine::resolveSessionIdOptional))
                .orElse(DEFAULT_SESSION_ID);
    }

    private static Optional<String> resolveSessionIdOptional(Object session) {
        return Optional.of(resolveSessionId(session));
    }

    private static Optional<String> invokeString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        return null;
    }

    public static String processContextId(String contextId) {
        return contextId == null ? DEFAULT_CONTEXT_ID : contextId.replace(".", "_");
    }

    /**
     * Processor factory used by Java registrations.
     *
     * <p>Mirrors Python's processor-class construction in
     * {@code openjiuwen/core/context_engine/context_engine.py}.</p>
     */
    @FunctionalInterface
    public interface ProcessorFactory {
        SessionModelContext.ContextProcessorPort create(Object config);
    }

    /**
     * Narrow session surface consumed by {@link ContextEngine}.
     *
     * <p>Mirrors Python's {@code Session.get_session_id} use in
     * {@code openjiuwen/core/context_engine/context_engine.py}.</p>
     */
    public interface SessionPort {
        String getSessionId();
    }

    /**
     * Processor declaration tuple passed to {@link #createContext}.
     *
     * <p>Mirrors Python's {@code Tuple[str, BaseModel]} processor entries in
     * {@code openjiuwen/core/context_engine/context_engine.py}.</p>
     */
    public record ProcessorSpec(String processorType, Object config) {
    }
}
