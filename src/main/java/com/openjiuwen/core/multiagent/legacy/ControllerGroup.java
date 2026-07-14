/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.session.AgentSessionApi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Legacy controller group facade backed by direct agent invocation.
 *
 * <p>Mirrors Python's legacy controller group in
 * {@code openjiuwen/core/multi_agent/legacy/group.py}.</p>
 */
public class ControllerGroup {
    private final AgentGroupConfig config;
    private final DefaultGroupController groupController;
    private final Map<String, Object> agents = new LinkedHashMap<>();

    public ControllerGroup(AgentGroupConfig config, DefaultGroupController groupController) {
        this.config = config == null ? new AgentGroupConfig("") : config;
        this.groupController = groupController == null ? new DefaultGroupController() : groupController;
    }

    public void addAgent(String agentId, Object agent) {
        if (agents.size() >= config.getMaxAgents() && !agents.containsKey(agentId)) {
            throw new IllegalStateException("Agent count exceeds maxAgents (" + config.getMaxAgents() + ")");
        }
        agents.put(agentId, agent);
    }

    public String getGroupId() {
        return config.getGroupId();
    }

    public AgentGroupConfig getConfig() {
        return config;
    }

    public DefaultGroupController getGroupController() {
        return groupController;
    }

    public int getAgentCount() {
        return agents.size();
    }

    public Object invoke(GroupEvent event, AgentSessionApi session) {
        List<String> targets = groupController.route(event);
        if (targets.isEmpty()) {
            return Map.of("output", Map.of());
        }
        Object lastResult = null;
        for (String agentId : targets) {
            Object agent = agents.get(agentId);
            if (agent == null) {
                continue;
            }
            lastResult = Map.of("output", normalizeAgentPayload(
                    invokeAgent(agent, Map.of("query", event.getPayload()), session)));
        }
        return lastResult == null ? Map.of("output", Map.of()) : lastResult;
    }

    private Object invokeAgent(Object agent, Map<String, Object> inputs, AgentSessionApi session) {
        try {
            Method method = agent.getClass().getMethod("invoke", Map.class, AgentSessionApi.class);
            Object result = method.invoke(agent, inputs, session);
            if (result instanceof CompletionStage<?> stage) {
                return stage.toCompletableFuture().join();
            }
            return result;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Agent does not expose invoke(Map, AgentSessionApi)", exception);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access agent invoke method", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static Object normalizeAgentPayload(Object result) {
        Object normalized = result;
        if (result instanceof CompletionStage<?> stage) {
            normalized = result instanceof Map<?, ?> ? result : stage.toCompletableFuture().join();
        }
        if (normalized instanceof ControllerOutput controllerOutput) {
            Map<String, Object> dataMap = controllerOutput.getDataAsMap();
            if (dataMap != null) {
                return unwrapSingleOutputMap(dataMap);
            }
            return controllerOutput.getData();
        }
        if (normalized instanceof Map<?, ?> resultMap) {
            return unwrapSingleOutputMap(stringKeyMap(resultMap));
        }
        return normalized;
    }

    private static Object unwrapSingleOutputMap(Map<String, Object> resultMap) {
        Object output = resultMap.get("output");
        if ("answer".equals(resultMap.get("result_type")) && output != null) {
            return unwrapOutputValue(output);
        }
        if (resultMap.size() == 1 && output instanceof Map<?, ?> outputMap) {
            return unwrapSingleOutputMap(stringKeyMap(outputMap));
        }
        return resultMap;
    }

    private static Object unwrapOutputValue(Object output) {
        if (output instanceof com.openjiuwen.core.workflow.WorkflowOutput workflowOutput) {
            return unwrapOutputValue(workflowOutput.getResult());
        }
        if (output instanceof Map<?, ?> outputMap) {
            return unwrapSingleOutputMap(stringKeyMap(outputMap));
        }
        return output;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
