/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Base interrupt rail for tool-call confirmation flows.
 *
 * <p>Mirrors Python's {@code BaseInterruptRail} in
 * {@code openjiuwen/harness/rails/interrupt/interrupt_base.py}.</p>
 */
public class BaseInterruptRail extends DeepAgentRail {

    private final Map<String, Object> pendingInterrupts = new LinkedHashMap<>();
    private final Set<String> toolNames = new LinkedHashSet<>();

    public BaseInterruptRail() {
    }

    public BaseInterruptRail(Collection<String> toolNames) {
        addTools(toolNames);
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        Object toolName = ctx.get("tool_name");
        if (toolName != null && toolNames.contains(String.valueOf(toolName))) {
            ctx.put("interrupt_required", true);
        }
        if (Boolean.TRUE.equals(ctx.get("interrupt_required"))) {
            pendingInterrupts.put(String.valueOf(ctx.get("tool_name")), new LinkedHashMap<>(ctx.getValues()));
        }
    }

    public void addTool(String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            toolNames.add(toolName);
        }
    }

    public void addTools(Collection<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            addTool(name);
        }
    }

    public void addPolicy(String toolName, Object ignoredPolicy) {
        addTool(toolName);
    }

    public Set<String> getTools() {
        return new LinkedHashSet<>(toolNames);
    }

    public Map<String, Object> getPendingInterrupts() {
        return new LinkedHashMap<>(pendingInterrupts);
    }

    public void clearPendingInterrupts() {
        pendingInterrupts.clear();
    }
}
