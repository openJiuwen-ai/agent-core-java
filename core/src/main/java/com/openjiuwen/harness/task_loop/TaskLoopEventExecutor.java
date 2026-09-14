/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Executes DeepAgent task-plan tasks through the task-loop.
 *
 * <p>Mirrors Python's {@code TaskLoopEventExecutor} in
 * {@code openjiuwen/harness/task_loop/task_loop_event_executor.py}.</p>
 */
public class TaskLoopEventExecutor extends TaskExecutor {

    public static final String DEEP_TASK_TYPE = "deep_agent_task";

    private final DeepAgent deepAgent;

    public TaskLoopEventExecutor(TaskExecutorDependencies dependencies, DeepAgent deepAgent) {
        super(dependencies);
        this.deepAgent = deepAgent;
    }

    @Override
    public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
        Object reactAgent = deepAgent == null ? null : deepAgent.reactAgent();
        if (reactAgent == null) {
            return List.<ControllerOutputChunk>of().iterator();
        }
        String query = resolveTaskQuery(taskId);
        Map<String, Object> inputs = new java.util.LinkedHashMap<>();
        inputs.put("query", query);
        if (session != null && session.getSessionId() != null && !session.getSessionId().isBlank()) {
            inputs.put("conversation_id", session.getSessionId());
        }
        try {
            Map<String, Object> result = invokeReactAgent(reactAgent, inputs, session);
            ControllerOutputPayload payload = new ControllerOutputPayload(
                    EventType.TASK_COMPLETION.getValue(),
                    List.of(new DataFrame.JsonDataFrame(result)),
                    Map.of("task_id", taskId)
            );
            return List.of(new ControllerOutputChunk(0, payload, true)).iterator();
        } catch (RuntimeException exception) {
            ControllerOutputPayload payload = new ControllerOutputPayload(
                    EventType.TASK_FAILED.getValue(),
                    List.of(new DataFrame.TextDataFrame(exception.getMessage())),
                    Map.of("task_id", taskId)
            );
            return List.of(new ControllerOutputChunk(0, payload, true)).iterator();
        }
    }

    @Override
    public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
        return new PauseCheckResult(false, "pause not supported");
    }

    @Override
    public boolean pause(String taskId, AgentSessionApi session) {
        return false;
    }

    @Override
    public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
        return new CancelCheckResult(true, null);
    }

    @Override
    public boolean cancel(String taskId, AgentSessionApi session) {
        if (deepAgent != null) {
            deepAgent.abort(session);
        }
        return true;
    }

    public static TaskLoopEventExecutor buildDeepExecutor(
            TaskExecutorDependencies dependencies,
            DeepAgent deepAgent
    ) {
        return new TaskLoopEventExecutor(dependencies, deepAgent);
    }

    private String resolveTaskQuery(String taskId) {
        List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(taskId));
        if (tasks.isEmpty() || tasks.get(0).getDescription() == null || tasks.get(0).getDescription().isBlank()) {
            return taskId;
        }
        return tasks.get(0).getDescription();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeReactAgent(
            Object reactAgent,
            Map<String, Object> inputs,
            AgentSessionApi session
    ) {
        Object result = invokeFirstMatching(
                reactAgent,
                List.of(
                        new Object[]{inputs, session, Boolean.TRUE},
                        new Object[]{inputs, session},
                        new Object[]{inputs}
                )
        );
        if (result instanceof CompletionStage<?> stage) {
            result = stage.toCompletableFuture().join();
        }
        // Handle nested CompletionStage (e.g., innerInvoke returns CompletableFuture)
        while (result instanceof CompletionStage<?> stage) {
            result = stage.toCompletableFuture().join();
        }
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new java.util.LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        return Map.of("output", result == null ? "" : String.valueOf(result));
    }

    private static Object invokeFirstMatching(Object target, List<Object[]> argumentOptions) {
        for (Object[] arguments : argumentOptions) {
            Method method = findInvokeMethod(target.getClass(), arguments);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target, arguments);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(cause);
            }
        }
        throw new IllegalStateException("react_agent.invoke is not available");
    }

    private static Method findInvokeMethod(Class<?> type, Object[] arguments) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!"invoke".equals(method.getName()) || method.getParameterCount() != arguments.length) {
                    continue;
                }
                if (parametersAccept(method.getParameterTypes(), arguments)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean parametersAccept(Class<?>[] parameterTypes, Object[] arguments) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (arguments[i] == null) {
                continue;
            }
            Class<?> parameterType = parameterTypes[i].isPrimitive()
                    ? primitiveWrapper(parameterTypes[i])
                    : parameterTypes[i];
            if (!parameterType.isInstance(arguments[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> primitiveWrapper(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
