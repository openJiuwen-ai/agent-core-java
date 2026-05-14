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

    private static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value != null ? String.valueOf(value) : null;
    }
}
