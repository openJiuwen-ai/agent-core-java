/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.runtime;

import com.openjiuwen.agentevolving.agent_rl.RLRail;
import com.openjiuwen.agentevolving.trajectory.InMemoryTrajectoryStore;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs an agent and returns the collected RL trajectory.
 *
 * <p>Mirrors Python's {@code TrajectoryCollector} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/runtime/collector.py}.</p>
 */
public class TrajectoryCollector {

    private static final Logger LOGGER = Logger.getLogger(TrajectoryCollector.class.getName());
    private static final String CONVERSATION_ID = "conversation_id";

    public CompletionStage<Trajectory> collect(Object agent, Map<String, Object> inputs) {
        return collect(agent, inputs, "", "offline", null);
    }

    public CompletionStage<Trajectory> collect(
            Object agent,
            Map<String, Object> inputs,
            String sessionId,
            String source,
            String caseId
    ) {
        return CompletableFuture.completedFuture(collectBlocking(agent, inputs, sessionId, source, caseId));
    }

    public Trajectory collectBlocking(
            Object agent,
            Map<String, Object> inputs,
            String sessionId,
            String source,
            String caseId
    ) {
        Map<String, Object> effectiveInputs = inputs == null ? Map.of() : inputs;
        if (!supportsRegisterRail(agent)) {
            throw new IllegalArgumentException(
                    "Agent does not support rail-based trajectory collection. "
                            + "Use a DeepAgent with register_rail().");
        }
        String effectiveSessionId = firstNonEmpty(sessionId, stringValue(effectiveInputs.get(CONVERSATION_ID), ""));
        String effectiveCaseId = firstNonEmpty(caseId, stringValue(effectiveInputs.get(CONVERSATION_ID), null));
        String effectiveSource = source == null ? "offline" : source;
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        RLRail rail = new RLRail(effectiveSessionId, effectiveSource, effectiveCaseId, store);
        await(registerRail(agent, rail));

        AgentSession session = null;
        String sessionIdForAgent = resolveSessionIdForAgent(effectiveSessionId, effectiveInputs);
        session = new AgentSession(sessionIdForAgent, null, resolveCardForSession(agent, sessionIdForAgent));
        session.preRun(Map.of("inputs", effectiveInputs));

        try {
            invokeAgent(agent, effectiveInputs, session);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING,
                    "Agent invoke raised exception during trajectory collection, returning partial trajectory. error="
                            + exception.getMessage(),
                    exception);
        } finally {
            if (supportsUnregisterRail(agent)) {
                await(unregisterRail(agent, rail));
            }
            session.closeStream();
            session.commit();
        }

        List<Trajectory> trajectories = store.query(null, null, null);
        if (trajectories.isEmpty()) {
            return null;
        }
        Trajectory trajectory = trajectories.get(trajectories.size() - 1);
        trajectory.setSource(effectiveSource);
        trajectory.setSessionId(effectiveSessionId);
        trajectory.setCaseId(effectiveCaseId);
        return trajectory;
    }

    private static boolean supportsRegisterRail(Object agent) {
        return agent instanceof BaseAgent || findOneArgMethod(agent, "registerRail", "register_rail") != null;
    }

    private static boolean supportsUnregisterRail(Object agent) {
        return agent instanceof BaseAgent || findOneArgMethod(agent, "unregisterRail", "unregister_rail") != null;
    }

    private static CompletionStage<?> registerRail(Object agent, RLRail rail) {
        return toStage(invokeOneArg(agent, rail, "registerRail", "register_rail"));
    }

    private static CompletionStage<?> unregisterRail(Object agent, RLRail rail) {
        return toStage(invokeOneArg(agent, rail, "unregisterRail", "unregister_rail"));
    }

    private static void invokeAgent(Object agent, Map<String, Object> inputs, AgentSession session) {
        if (agent instanceof BaseAgent baseAgent) {
            await(baseAgent.invoke(inputs, session));
            return;
        }
        Method invoke = findInvokeMethod(agent, 2);
        if (invoke != null) {
            await(toStage(invoke(agent, invoke, inputs, session)));
            return;
        }
        invoke = findInvokeMethod(agent, 1);
        if (invoke != null) {
            await(toStage(invoke(agent, invoke, inputs)));
            return;
        }
        await(Runner.runAgent(agent, inputs, session, null, null));
    }

    private static Method findInvokeMethod(Object target, int parameterCount) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if ("invoke".equals(method.getName()) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findOneArgMethod(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() != 1) {
                    continue;
                }
                for (String name : names) {
                    if (name.equals(method.getName())) {
                        return method;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object invokeOneArg(Object target, Object argument, String... names) {
        Method method = findOneArgMethod(target, names);
        if (method == null) {
            return null;
        }
        return invoke(target, method, argument);
    }

    private static Object invoke(Object target, Method method, Object... arguments) {
        try {
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Cannot invoke " + method.getName(), exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new CompletionException(cause);
        }
    }

    private static CompletionStage<?> toStage(Object value) {
        if (value instanceof CompletionStage<?> stage) {
            return stage;
        }
        return CompletableFuture.completedFuture(value);
    }

    private static void await(CompletionStage<?> stage) {
        try {
            stage.toCompletableFuture().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private static Object readCard(Object agent) {
        if (agent instanceof BaseAgent baseAgent) {
            return baseAgent.getCard();
        }
        Object value = invokeNoArg(agent, "getCard", "card");
        if (value != null) {
            return value;
        }
        Field field = findField(agent, "card");
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(agent);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object resolveCardForSession(Object agent, String sessionIdForAgent) {
        Object card = readCard(agent);
        if (readCardId(card) != null) {
            return card;
        }
        String fallbackId = firstNonEmpty(sessionIdForAgent, "trajectory_collector_agent");
        return new AgentCard(fallbackId, fallbackId, "");
    }

    private static Object readCardId(Object card) {
        if (card == null) {
            return null;
        }
        if (card instanceof Map<?, ?> map) {
            return map.get("id");
        }
        Object value = invokeNoArg(card, "getId", "id");
        if (value != null) {
            return value;
        }
        Field field = findField(card, "id");
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(card);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            for (String methodName : methodNames) {
                try {
                    Method method = current.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException ignored) {
                    // Try next name/current class.
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String resolveSessionIdForAgent(String effectiveSessionId, Map<String, Object> inputs) {
        if (effectiveSessionId != null && !effectiveSessionId.isEmpty()) {
            return effectiveSessionId;
        }
        if (inputs.containsKey(CONVERSATION_ID)) {
            Object value = inputs.get(CONVERSATION_ID);
            return value == null ? null : String.valueOf(value);
        }
        return "default";
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second;
    }

    private static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
