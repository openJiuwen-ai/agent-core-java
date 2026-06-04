/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.RlRail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collector for running an agent once and retrieving its trajectory.
 * <p>
 * Mirrors Python's {@code TrajectoryCollector} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.runtime.collector}.
 */
public class RolloutCollector {

    private Object agent;
    private final List<Object> collectedRollouts = new ArrayList<>();

    public RolloutCollector() {
    }

    public RolloutCollector(Object agent) {
        this.agent = agent;
    }

    /**
     * Collect a trajectory from the configured agent.
     *
     * @param inputs agent input payload
     * @return collected trajectory when the agent or rail produced one
     */
    public Object collect(Object inputs) {
        Trajectory trajectory = collect(agent, inputs, "", "offline", nullValue());
        collectedRollouts.add(trajectory);
        return trajectory;
    }

    /**
     * Run an agent with an RL rail and return the collected trajectory when available.
     *
     * @param agent agent object supporting rail registration and invocation
     * @param inputs agent inputs
     * @param sessionId session identifier
     * @param source rollout source
     * @param caseId optional case identifier
     * @return collected trajectory or null when no trajectory was emitted
     */
    public Trajectory collect(Object agent, Object inputs, String sessionId, String source, String caseId) {
        if (agent == null || !hasMethod(agent, "registerRail", AgentRail.class)) {
            throw new IllegalArgumentException("Agent does not support rail-based trajectory collection. "
                    + "Use a DeepAgent with registerRail().");
        }

        Map<String, Object> inputMap = normalizeInputs(inputs);
        String effectiveSessionId = firstNonBlank(sessionId, stringValue(inputMap.get("conversation_id")));
        String effectiveCaseId = firstNonBlank(caseId, stringValue(inputMap.get("conversation_id")));
        RlRail rail = new RlRail(effectiveSessionId, firstNonBlank(source, "offline"), effectiveCaseId);
        Object card = readProperty(agent, "card", "getCard");
        if (card == Missing.VALUE) {
            card = nullValue();
        }
        AgentSessionApi session = AgentSessionApi.create(
                firstNonBlank(effectiveSessionId, "default"),
                null,
                card);

        Object result = nullValue();
        try {
            invokeIfPresent(agent, "registerRail", rail);
            session.preRun(inputMap);
            result = invokeAgent(agent, inputMap, session);
        } catch (RuntimeException ignored) {
            result = nullValue();
        } finally {
            invokeIfPresent(agent, "unregisterRail", rail);
            session.postRun();
        }

        Trajectory trajectory = extractTrajectory(result, agent);
        if (trajectory != null) {
            trajectory.setSource(firstNonBlank(source, "offline"));
            trajectory.setSessionId(effectiveSessionId);
            trajectory.setCaseId(effectiveCaseId);
        }
        collectedRollouts.add(trajectory);
        return trajectory;
    }

    public List<Object> getCollectedRollouts() {
        return new ArrayList<>(collectedRollouts);
    }

    public void clear() {
        collectedRollouts.clear();
    }

    private static Object invokeAgent(Object agent, Object inputs, AgentSessionApi session) {
        Object result = invokeIfPresent(agent, "invoke", inputs, session);
        if (result != Missing.VALUE) {
            return result;
        }
        result = invokeIfPresent(agent, "invoke", inputs);
        if (result != Missing.VALUE) {
            return result;
        }
        result = invokeIfPresent(agent, "run", inputs, session);
        if (result != Missing.VALUE) {
            return result;
        }
        return invokeIfPresent(agent, "run", inputs);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeInputs(Object inputs) {
        if (inputs instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>(Map.of("query", inputs != null ? inputs : ""));
    }

    private static Trajectory extractTrajectory(Object result, Object agent) {
        if (result instanceof Trajectory trajectory) {
            return trajectory;
        }
        if (result instanceof Map<?, ?> map) {
            Object trajectory = map.get("trajectory");
            if (trajectory instanceof Trajectory typed) {
                return typed;
            }
        }
        for (String name : List.of("getLastTrajectory", "lastTrajectory", "getTrajectory", "trajectory")) {
            Object value = invokeIfPresent(agent, name);
            if (value instanceof Trajectory trajectory) {
                return trajectory;
            }
        }
        return nullValue();
    }

    private static boolean hasMethod(Object target, String name, Class<?> preferredType) {
        if (target == null) {
            return false;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = wrap(method.getParameterTypes()[0]);
                if (parameterType.isAssignableFrom(preferredType)
                        || parameterType.isAssignableFrom(Object.class)
                        || preferredType.isAssignableFrom(parameterType)) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static Object invokeIfPresent(Object target, String name, Object... args) {
        if (target == null) {
            return Missing.VALUE;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                    continue;
                }
                if (!parametersCompatible(method.getParameterTypes(), args)) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Failed to invoke " + name, exception);
                }
            }
            type = type.getSuperclass();
        }
        return Missing.VALUE;
    }

    private static Object readProperty(Object target, String fieldName, String getterName) {
        Object getter = invokeIfPresent(target, getterName);
        if (getter != Missing.VALUE) {
            return getter;
        }
        return invokeIfPresent(target, fieldName);
    }

    private static boolean parametersCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null || args[i] == Missing.VALUE) {
                continue;
            }
            if (!wrap(parameterTypes[i]).isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
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
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null ? second : "";
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullValue() {
        return (T) null;
    }

    public Object getAgent() { return agent; }
    public void setAgent(Object agent) { this.agent = agent; }

    private enum Missing {
        VALUE
    }
}
