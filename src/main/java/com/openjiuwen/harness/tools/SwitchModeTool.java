/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.AgentMode;

import java.util.Locale;
import java.util.Map;

/**
 * Public class SwitchModeTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SwitchModeTool {
    private final DeepAgent agent;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SwitchModeTool(DeepAgent agent) {
        this.agent = agent;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput switchMode(String mode) {
        if (mode == null) {
            return ToolOutput.builder().success(false).error("mode is required").build();
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        AgentMode nextMode = switch (normalized) {
            case "plan" -> AgentMode.PLAN;
            case "normal" -> AgentMode.NORMAL;
            default -> null;
        };
        if (nextMode == null) {
            return ToolOutput.builder().success(false).error("Unsupported mode: " + mode).build();
        }
        AgentMode previous = agent.getCurrentMode();
        agent.setMode(nextMode);
        return ToolOutput.builder()
                .success(true)
                .data(Map.of(
                        "previous_mode", previous.name().toLowerCase(Locale.ROOT),
                        "current_mode", nextMode.name().toLowerCase(Locale.ROOT)
                ))
                .build();
    }
}
