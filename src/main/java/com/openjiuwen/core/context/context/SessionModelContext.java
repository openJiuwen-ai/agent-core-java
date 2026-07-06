/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Backward-compatible alias for the pre-0.1.14 context context package.
 *
 * <p>Mirrors Python's {@code SessionModelContext} in
 * {@code openjiuwen/core/context_engine/context/context.py}.</p>
 */
public class SessionModelContext
        extends com.openjiuwen.core.context.ModelContext {
    public SessionModelContext(com.openjiuwen.core.context_engine.context.SessionModelContext delegate) {
        super(delegate);
    }

    public SessionModelContext(String contextId, String sessionId, ContextEngineConfig config) {
        this(new com.openjiuwen.core.context_engine.context.SessionModelContext(contextId, sessionId, config));
    }

    public SessionModelContext(String contextId, String sessionId, ContextEngineConfig config,
                               List<BaseMessage> historyMessages, List<?> processors, Object tokenCounter) {
        this(contextId, sessionId, config, historyMessages, processors, tokenCounter, null, null, null);
    }

    public SessionModelContext(String contextId, String sessionId, ContextEngineConfig config,
                               List<BaseMessage> historyMessages, List<?> processors, Object tokenCounter,
                               Object sessionRef, Object workspace, Object sysOperation) {
        this(new com.openjiuwen.core.context_engine.context.SessionModelContext(contextId, sessionId, config,
                historyMessages, adaptProcessors(processors), adaptTokenCounter(tokenCounter), sessionRef,
                adaptWorkspace(workspace), adaptSysOperation(sysOperation), null, null));
    }

    public Map<String, Object> saveState() {
        return delegate().saveState();
    }

    public void loadState(Map<String, Object> state) {
        delegate().loadState(state);
    }

    public List<Map<String, Object>> compressionHistory() {
        return delegate().compressionHistory();
    }

    private com.openjiuwen.core.context_engine.context.SessionModelContext delegate() {
        return (com.openjiuwen.core.context_engine.context.SessionModelContext) unwrap();
    }

    private static List<com.openjiuwen.core.context_engine.context.SessionModelContext.ContextProcessorPort>
            adaptProcessors(List<?> processors) {
        if (processors == null) {
            return List.of();
        }
        List<com.openjiuwen.core.context_engine.context.SessionModelContext.ContextProcessorPort> result =
                new ArrayList<>();
        for (Object processor : processors) {
            if (processor instanceof
                    com.openjiuwen.core.context_engine.context.SessionModelContext.ContextProcessorPort port) {
                result.add(port);
            } else if (processor != null) {
                throw new IllegalArgumentException("Unsupported context processor: " + processor.getClass());
            }
        }
        return result;
    }

    private static com.openjiuwen.core.context_engine.ModelContext.TokenCounterPort adaptTokenCounter(
            Object tokenCounter) {
        if (tokenCounter == null) {
            return null;
        }
        if (tokenCounter instanceof com.openjiuwen.core.context_engine.ModelContext.TokenCounterPort port) {
            return port;
        }
        return messages -> invokeInt(tokenCounter, messages);
    }

    private static com.openjiuwen.core.context_engine.context.SessionModelContext.WorkspacePort adaptWorkspace(
            Object workspace) {
        if (workspace == null) {
            return null;
        }
        if (workspace instanceof com.openjiuwen.core.context_engine.context.SessionModelContext.WorkspacePort port) {
            return port;
        }
        return () -> invokeString(workspace, "rootPath")
                .or(() -> invokeString(workspace, "getRootPath"))
                .orElse("");
    }

    private static com.openjiuwen.core.context_engine.context.SessionModelContext.SysOperationPort adaptSysOperation(
            Object sysOperation) {
        if (sysOperation == null) {
            return null;
        }
        if (sysOperation instanceof
                com.openjiuwen.core.context_engine.context.SessionModelContext.SysOperationPort port) {
            return port;
        }
        return path -> Optional.empty();
    }

    private static int invokeInt(Object target, List<BaseMessage> messages) {
        for (String methodName : List.of("countTokens", "countMessages")) {
            try {
                Method method = target.getClass().getMethod(methodName, List.class);
                Object value = method.invoke(target, messages == null ? List.of() : messages);
                if (value instanceof Number number) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next known 0.1.12 token counter shape.
            }
            try {
                Method method = target.getClass().getMethod(methodName, List.class, String.class);
                Object value = method.invoke(target, messages == null ? List.of() : messages, "");
                if (value instanceof Number number) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next known 0.1.12 token counter shape.
            }
        }
        throw new IllegalArgumentException("Unsupported token counter: " + target.getClass());
    }

    private static Optional<String> invokeString(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return Optional.ofNullable(value).map(String::valueOf);
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }
}
