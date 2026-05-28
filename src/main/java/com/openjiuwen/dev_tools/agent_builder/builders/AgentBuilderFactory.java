/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;

import java.util.*;

/**
 * Agent builder factory — creates the appropriate builder based on agent type.
 * <p>
 * Mirrors Python's {@code factory} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.factory}.
 */
public final class AgentBuilderFactory {

    private AgentBuilderFactory() {
    }

    /** Create a builder for the given agent type. */
    public static BaseAgentBuilder create(AgentBuilderEnums.AgentType type) {
        return create(type, null);
    }

    public static BaseAgentBuilder create(AgentBuilderEnums.AgentType type,
                                           ProgressReporter progressReporter) {
        switch (type) {
            case LLM_AGENT:
                return new LlmAgentBuilder(progressReporter);
            case WORKFLOW:
                return new WorkflowBuilder(progressReporter);
            default:
                throw new IllegalArgumentException("Unknown agent type: " + type);
        }
    }
}
