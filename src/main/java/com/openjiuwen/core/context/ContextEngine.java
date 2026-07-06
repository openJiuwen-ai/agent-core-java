/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.Session;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Backward-compatible facade for the pre-0.1.14 root context package.
 *
 * <p>Mirrors Python's {@code ContextEngine} in
 * {@code openjiuwen/core/context_engine/context_engine.py}.</p>
 */
public class ContextEngine extends com.openjiuwen.core.context_engine.ContextEngine {
    public static final String DEFAULT_CONTEXT_ID =
            com.openjiuwen.core.context_engine.ContextEngine.DEFAULT_CONTEXT_ID;
    public static final String DEFAULT_SESSION_ID =
            com.openjiuwen.core.context_engine.ContextEngine.DEFAULT_SESSION_ID;

    private static final Map<String, Class<?>> PROCESSOR_CLASS_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Function<Object, ?>> PROCESSOR_FACTORY_MAP = new ConcurrentHashMap<>();

    private final Map<com.openjiuwen.core.context_engine.ModelContext, ModelContext> wrappers = new LinkedHashMap<>();

    public ContextEngine() {
        this(null, null, null);
    }

    public ContextEngine(ContextEngineConfig config) {
        this(config, null, null);
    }

    public ContextEngine(ContextEngineConfig config, Object workspace, Object sysOperation) {
        super(
                config,
                adaptWorkspace(workspace),
                adaptSysOperation(sysOperation));
    }

    @Override
    public ModelContext createContext() {
        return wrap(super.createContext());
    }

    public ModelContext createContext(String contextId, Session session) {
        return createContext(contextId, (Object) session);
    }

    @Override
    public ModelContext createContext(String contextId, Object session) {
        return wrap(super.createContext(contextId, session));
    }

    @Override
    public ModelContext createContext(String contextId, Object session,
                                      List<com.openjiuwen.core.context_engine.ContextEngine.ProcessorSpec> processors,
                                      List<BaseMessage> historyMessages,
                                      com.openjiuwen.core.context_engine.ModelContext.TokenCounterPort tokenCounter) {
        return wrap(super.createContext(contextId, session, processors, historyMessages, tokenCounter));
    }

    public ModelContext createContext(String contextId, Object session, List<?> processors,
                                      List<BaseMessage> historyMessages, Object tokenCounter) {
        return wrap(super.createContext(contextId, session, adaptProcessorSpecs(processors), historyMessages,
                adaptTokenCounter(tokenCounter)));
    }

    public ModelContext createContextSimple(String contextId, Session session) {
        return createContext(contextId, session);
    }

    public ModelContext createContextSimple(String contextId, Object session) {
        return createContext(contextId, session);
    }

    public ModelContext createContextWithHistory(String contextId, Session session,
                                                 List<BaseMessage> historyMessages) {
        return createContext(contextId, session, (List<ProcessorSpec>) null, historyMessages, (Object) null);
    }

    public ModelContext createContextWithHistory(String contextId, Object session,
                                                 List<BaseMessage> historyMessages) {
        return createContext(contextId, session, (List<ProcessorSpec>) null, historyMessages, (Object) null);
    }

    @Override
    public ModelContext getContext() {
        return wrap(super.getContext());
    }

    @Override
    public ModelContext getContext(String contextId) {
        return wrap(super.getContext(contextId));
    }

    @Override
    public ModelContext getContext(String contextId, String sessionId) {
        return wrap(super.getContext(contextId, sessionId));
    }

    @Override
    public Object compressContext(String contextId, Object session) {
        return super.compressContext(contextId, session);
    }

    @Override
    public Object compressContext(String contextId, Object session, String sessionId, List<String> processorTypes,
                                  Map<String, Object> kwargs) {
        return super.compressContext(contextId, session, sessionId, processorTypes, kwargs);
    }

    @Override
    public void clearContext() {
        super.clearContext();
        wrappers.clear();
    }

    @Override
    public void clearContext(String contextId, String sessionId) {
        super.clearContext(contextId, sessionId);
        wrappers.clear();
    }

    public void clearContextBySession(String sessionId) {
        clearContext(null, sessionId);
    }

    public Map<String, Object> saveContexts(Session session, List<String> contextIds) {
        return saveContexts((Object) session, contextIds);
    }

    @Override
    public Map<String, Object> saveContexts(Object session, List<String> contextIds) {
        return super.saveContexts(session, contextIds);
    }

    public static void registerProcessor(String processorType, Class<?> processorClass,
                                         Function<Object, ?> factory) {
        PROCESSOR_CLASS_MAP.put(processorType, processorClass);
        PROCESSOR_FACTORY_MAP.put(processorType, factory);
        com.openjiuwen.core.context_engine.ContextEngine.registerProcessor(processorType,
                config -> adaptProcessor(factory.apply(config)));
    }

    public static void registerProcessor(String processorType,
            Class<? extends com.openjiuwen.core.context_engine.context.SessionModelContext.ContextProcessorPort>
                    processorClass) {
        PROCESSOR_CLASS_MAP.put(processorType, processorClass);
        com.openjiuwen.core.context_engine.ContextEngine.registerProcessor(processorType,
                config -> instantiateProcessor(processorType, processorClass, config));
    }

    public static Class<?> getProcessorClass(String processorType) {
        return PROCESSOR_CLASS_MAP.get(processorType);
    }

    public static Set<String> registeredProcessorTypes() {
        return com.openjiuwen.core.context_engine.ContextEngine.registeredProcessorTypes();
    }

    public static String processContextId(String contextId) {
        return com.openjiuwen.core.context_engine.ContextEngine.processContextId(contextId);
    }

    private ModelContext wrap(com.openjiuwen.core.context_engine.ModelContext context) {
        if (context == null) {
            return null;
        }
        return wrappers.computeIfAbsent(context, this::wrapContext);
    }

    private ModelContext wrapContext(com.openjiuwen.core.context_engine.ModelContext context) {
        if (context instanceof com.openjiuwen.core.context_engine.context.SessionModelContext sessionModelContext) {
            return new com.openjiuwen.core.context.context.SessionModelContext(sessionModelContext);
        }
        return new ModelContext(context);
    }

    private static List<com.openjiuwen.core.context_engine.ContextEngine.ProcessorSpec> adaptProcessorSpecs(
            List<?> processors) {
        if (processors == null) {
            return null;
        }
        return processors.stream()
                .map(ContextEngine::adaptProcessorSpec)
                .toList();
    }

    private static com.openjiuwen.core.context_engine.ContextEngine.ProcessorSpec adaptProcessorSpec(Object spec) {
        if (spec instanceof com.openjiuwen.core.context_engine.ContextEngine.ProcessorSpec engineSpec) {
            return engineSpec;
        }
        if (spec instanceof ProcessorSpec rootSpec) {
            return new com.openjiuwen.core.context_engine.ContextEngine.ProcessorSpec(
                    rootSpec.processorType(), rootSpec.config());
        }
        throw new IllegalArgumentException("Unsupported processor spec: " + spec);
    }

    private static com.openjiuwen.core.context_engine.ModelContext.TokenCounterPort adaptTokenCounter(
            Object tokenCounter) {
        if (tokenCounter == null) {
            return null;
        }
        if (tokenCounter instanceof com.openjiuwen.core.context_engine.ModelContext.TokenCounterPort port) {
            return port;
        }
        return messages -> {
            try {
                return (int) tokenCounter.getClass().getMethod("countMessages", List.class).invoke(tokenCounter,
                        messages);
            } catch (ReflectiveOperationException first) {
                try {
                    return (int) tokenCounter.getClass().getMethod("countMessages", List.class, String.class)
                            .invoke(tokenCounter, messages, "");
                } catch (ReflectiveOperationException second) {
                    throw new IllegalArgumentException("Unsupported token counter: " + tokenCounter.getClass(),
                            second);
                }
            }
        };
    }

    private static com.openjiuwen.core.context_engine.context.SessionModelContext.WorkspacePort adaptWorkspace(
            Object workspace) {
        if (workspace == null) {
            return null;
        }
        if (workspace instanceof com.openjiuwen.core.context_engine.context.SessionModelContext.WorkspacePort port) {
            return port;
        }
        return () -> invokeString(workspace, "rootPath");
    }

    private static com.openjiuwen.core.context_engine.context.SessionModelContext.SysOperationPort adaptSysOperation(
            Object sysOperation) {
        if (sysOperation == null) {
            return null;
        }
        if (sysOperation instanceof com.openjiuwen.core.context_engine.context.SessionModelContext.SysOperationPort port) {
            return port;
        }
        return path -> java.util.Optional.empty();
    }

    private static com.openjiuwen.core.context_engine.context.SessionModelContext.ContextProcessorPort adaptProcessor(
            Object processor) {
        if (processor instanceof com.openjiuwen.core.context_engine.context.SessionModelContext.ContextProcessorPort port) {
            return port;
        }
        throw new IllegalArgumentException("Unsupported context processor: "
                + (processor == null ? "null" : processor.getClass()));
    }

    private static com.openjiuwen.core.context_engine.context.SessionModelContext.ContextProcessorPort
            instantiateProcessor(String processorType, Class<?> processorClass, Object config) {
        try {
            Object processor;
            try {
                Constructor<?> constructor = processorClass.getConstructor(config == null ? Object.class
                        : config.getClass());
                processor = constructor.newInstance(config);
            } catch (NoSuchMethodException ignored) {
                Constructor<?> constructor = processorClass.getConstructor(Object.class);
                processor = constructor.newInstance(config);
            }
            return adaptProcessor(processor);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("init processor type '" + processorType + "' failed", ex);
        }
    }

    private static String invokeString(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? "" : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    public record ProcessorSpec(String processorType, Object config) {
    }
}
