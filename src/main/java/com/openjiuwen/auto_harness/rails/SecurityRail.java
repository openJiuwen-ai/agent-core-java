/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.core.common.exception.GuardrailError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Security rail for auto-harness.
 * <p>
 * Mirrors Python's {@code openjiuwen.auto_harness.rails.security_rail.SecurityRail}.
 * <p>
 * Merges immutable-file guarding and prompt/tool sanitization into a single rail.
 */
public class SecurityRail extends DeepAgentRail {

    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile(";\\s*rm\\s+-rf\\s+/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\$\\(.*\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("`.*`")
    );

    public SecurityRail() {
        setPriority(85);
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        Object inputs = ctx.getInputs();
        if (!(inputs instanceof ModelCallInputs modelInputs)) {
            return;
        }

        Object messages = modelInputs.getMessages();
        if (messages == null) {
            return;
        }

        String text = messages.toString();
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(text).find()) {
                throw new GuardrailError(StatusCode.GUARDRAIL_BLOCKED);
            }
        }
    }

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        Object inputs = ctx.getInputs();
        if (!(inputs instanceof ToolCallInputs toolInputs)) {
            return;
        }

        String toolName = toolInputs.getToolName();
        if (toolName == null) {
            return;
        }

        if ("write_file".equals(toolName) || "edit_file".equals(toolName)) {
            ctx.requestRetry(0.0);
        }
    }
}
