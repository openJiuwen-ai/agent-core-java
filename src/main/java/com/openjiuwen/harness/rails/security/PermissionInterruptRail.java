/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.interrupt.BaseInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.security.PermissionCheckResult;
import com.openjiuwen.harness.security.PermissionEngine;
import com.openjiuwen.harness.security.PermissionLevel;
import com.openjiuwen.harness.security.ToolPermissionHost;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class PermissionInterruptRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Getter
public class PermissionInterruptRail extends BaseInterruptRail {
    private final PermissionEngine engine;
    private final ToolPermissionHost host;

    /**
     * PermissionInterruptRail.
     * 
     * @param engine engine
     * @param host host
     * @since 0.1.7
     */
    public PermissionInterruptRail(PermissionEngine engine, ToolPermissionHost host) {
        super(null);
        this.engine = engine;
        this.host = host;
        @SuppressWarnings("unchecked")
        Map<String, Object> tools = (Map<String, Object>) engine.getConfig().getOrDefault("tools", Map.of());
        addTools(tools.keySet());
    }

    /**
     * resolveInterrupt.
     * 
     * @param ctx ctx
     * @param toolCall toolCall
     * @param userInput userInput
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
        String toolName = toolCall != null ? toolCall.getName() : "";
        Object toolArgsObj = ctx.getInputs() instanceof com.openjiuwen.core.singleagent.rail.ToolCallInputs inputs
                ? inputs.getToolArgs()
                : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> toolArgs =
            toolArgsObj instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
        PermissionCheckResult result = engine.checkPermission(toolName, toolArgs);
        if (result.getPermission() == PermissionLevel.ALLOW) {
            return approve();
        }
        if (result.getPermission() == PermissionLevel.DENY) {
            return reject("Permission denied for tool: " + toolName);
        }
        return interrupt(InterruptRequest.builder().message("Permission approval required for tool: " + toolName)
                .context(Map.of("tool_name", toolName, "matched_rule", result.getMatchedRule())).build());
    }
}
