/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.tools.TaskTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers a minimal delegated task tool for configured subagents.
 *
 * <p>Mirrors Python's subagent rail in
 * {@code openjiuwen.harness.rails.subagent.subagent_rail}.
 */
public class SubagentRail extends DeepAgentRail {

    private final List<com.openjiuwen.core.foundation.tool.Tool> tools = new ArrayList<>();
    private String availableAgentsDescription = "";

    public SubagentRail() {
        setPriority(95);
    }

    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent) || !(deepAgent.getConfig() instanceof DeepAgentConfig config)) {
            return;
        }
        if (config.getSubagents().isEmpty()) {
            return;
        }
        availableAgentsDescription = buildAvailableAgentsDescription(config.getSubagents());
        tools.clear();
        tools.add(new TaskTool(deepAgent));
        for (com.openjiuwen.core.foundation.tool.Tool tool : tools) {
            Runner.resourceMgr().addTool(tool, readField(deepAgent.getCard(), "id"));
            deepAgent.getDelegate().getAbilityManager().add(tool.getCard());
        }
    }

    @Override
    public void uninit(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        for (com.openjiuwen.core.foundation.tool.Tool tool : tools) {
            deepAgent.getDelegate().getAbilityManager().remove(readStringField(tool.getCard(), "name"));
            Runner.resourceMgr().removeTool(readField(tool.getCard(), "id"), readField(deepAgent.getCard(), "id"),
                    TagMatchStrategy.ALL, true);
        }
        tools.clear();
        availableAgentsDescription = "";
    }

    public List<com.openjiuwen.core.foundation.tool.Tool> getRegisteredTools() {
        return List.copyOf(tools);
    }

    public String getAvailableAgentsDescription() {
        return availableAgentsDescription;
    }

    public static String buildAvailableAgentsDescription(List<?> subagents) {
        if (subagents == null || subagents.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        boolean hasGeneralPurpose = false;
        for (Object subagent : subagents) {
            AgentMeta meta = extractAgentMeta(subagent);
            if ("general-purpose".equals(meta.name())) {
                if (hasGeneralPurpose) {
                    continue;
                }
                hasGeneralPurpose = true;
            }
            lines.add("\"" + meta.name() + "\": " + meta.description());
        }
        return String.join("\n", lines);
    }

    public static AgentMeta extractAgentMeta(Object subagent) {
        Object card = readField(subagent, "card");
        if (card == null) {
            card = invokeNoArg(subagent, "getCard");
        }
        String name = readStringField(card, "name");
        String description = readStringField(card, "description");
        if (name == null || name.isBlank()) {
            name = "general-purpose";
        }
        if (description == null || description.isBlank()) {
            description = subagent instanceof DeepAgent ? "DeepAgent instance" : "";
        }
        return new AgentMeta(name, description);
    }

    public record AgentMeta(String name, String description) {
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value != null ? String.valueOf(value) : null;
    }
}
