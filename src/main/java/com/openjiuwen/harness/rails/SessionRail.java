/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.tools.SessionsListTool;
import com.openjiuwen.harness.tools.SessionsSpawnTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers lightweight session management tools for delegated subagents.
 *
 * <p>Mirrors Python's session rail in
 * {@code openjiuwen.harness.rails.subagent.session_rail}.
 */
public class SessionRail extends DeepAgentRail {

    private final List<com.openjiuwen.core.foundation.tool.Tool> tools = new ArrayList<>();

    public SessionRail() {
        setPriority(95);
    }

    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent) || !(deepAgent.getConfig() instanceof DeepAgentConfig config)) {
            return;
        }
        DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
        config.setSessionToolkit(toolkit);

        tools.clear();
        tools.add(new SessionsListTool(toolkit));
        tools.add(new SessionsSpawnTool(deepAgent, toolkit));

        for (com.openjiuwen.core.foundation.tool.Tool tool : tools) {
            Runner.resourceMgr().addTool(tool, readField(deepAgent.getCard(), "id"));
            deepAgent.getDelegate().getAbilityManager().add(tool.getCard());
        }
    }

    @Override
    public void uninit(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent) || !(deepAgent.getConfig() instanceof DeepAgentConfig config)) {
            return;
        }
        for (com.openjiuwen.core.foundation.tool.Tool tool : tools) {
            deepAgent.getDelegate().getAbilityManager().remove(readStringField(tool.getCard(), "name"));
            Runner.resourceMgr().removeTool(readField(tool.getCard(), "id"), readField(deepAgent.getCard(), "id"),
                    TagMatchStrategy.ALL, true);
        }
        tools.clear();
        config.setSessionToolkit(null);
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
