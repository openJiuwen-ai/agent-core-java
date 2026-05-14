/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.tools.BashTool;
import com.openjiuwen.harness.tools.CodeTool;
import com.openjiuwen.harness.tools.EditFileTool;
import com.openjiuwen.harness.tools.GlobTool;
import com.openjiuwen.harness.tools.GrepTool;
import com.openjiuwen.harness.tools.ListDirTool;
import com.openjiuwen.harness.tools.ReadFileTool;
import com.openjiuwen.harness.tools.WriteFileTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the first batch of harness filesystem, shell, and code tools.
 *
 * <p>Mirrors Python's sys-operation tool registration flow in
 * {@code openjiuwen.harness.rails.sys_operation_rail}.
 */
public class SysOperationRail extends DeepAgentRail {

    private final boolean withCodeTool;
    private final List<Object> toolInstances = new ArrayList<>();

    public SysOperationRail() {
        this(false);
    }

    public SysOperationRail(boolean withCodeTool) {
        this.withCodeTool = withCodeTool;
        setPriority(100);
    }

    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent) || sysOperation == null) {
            return;
        }
        toolInstances.clear();
        toolInstances.add(new ReadFileTool(sysOperation));
        toolInstances.add(new WriteFileTool(sysOperation));
        toolInstances.add(new EditFileTool(sysOperation));
        toolInstances.add(new GlobTool(sysOperation));
        toolInstances.add(new ListDirTool(sysOperation));
        toolInstances.add(new GrepTool(sysOperation));
        toolInstances.add(new BashTool(sysOperation));
        if (withCodeTool) {
            toolInstances.add(new CodeTool(sysOperation));
        }

        BaseAgent delegate = deepAgent.getDelegate();
        for (Object toolObj : toolInstances) {
            if (toolObj instanceof com.openjiuwen.core.foundation.tool.Tool tool) {
                Runner.resourceMgr().addTool(tool, readField(deepAgent.getCard(), "id"));
                delegate.getAbilityManager().add(tool.getCard());
            }
        }
    }

    @Override
    public void uninit(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        BaseAgent delegate = deepAgent.getDelegate();
        for (Object toolObj : toolInstances) {
            if (toolObj instanceof com.openjiuwen.core.foundation.tool.Tool tool) {
                delegate.getAbilityManager().remove(readStringField(tool.getCard(), "name"));
                Runner.resourceMgr().removeTool(readField(tool.getCard(), "id"), readField(deepAgent.getCard(), "id"),
                        TagMatchStrategy.ALL, true);
            }
        }
        toolInstances.clear();
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
