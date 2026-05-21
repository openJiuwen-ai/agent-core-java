/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders;

import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * LLM agent builder — creates LLM-based agents from user specifications.
 * <p>
 * Mirrors Python's builder classes in
 * {@code openjiuwen.dev_tools.agent_builder.builders.llm_agent}.
 */
public class LlmAgentBuilder extends BaseAgentBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(LlmAgentBuilder.class);

    public LlmAgentBuilder(ProgressReporter progressReporter) {
        super(progressReporter);
    }

    @Override
    protected Map<String, Object> handleInitial(Map<String, Object> query, List<Map<String, Object>> history) {
        progressReporter.report(AgentBuilderEnums.ProgressStage.CLARIFYING,
                AgentBuilderEnums.ProgressStatus.RUNNING, "Clarifying agent requirements");
        state = AgentBuilderEnums.BuildState.PROCESSING;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "clarifying");
        result.put("state", "processing");
        return result;
    }

    @Override
    protected Map<String, Object> handleProcessing(Map<String, Object> query, List<Map<String, Object>> history) {
        progressReporter.report(AgentBuilderEnums.ProgressStage.GENERATING_CONFIG,
                AgentBuilderEnums.ProgressStatus.RUNNING, "Generating agent config");
        progressReporter.report(AgentBuilderEnums.ProgressStage.TRANSFORMING_DSL,
                AgentBuilderEnums.ProgressStatus.RUNNING, "Transforming DSL");
        state = AgentBuilderEnums.BuildState.COMPLETED;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "completed");
        result.put("state", "completed");
        return result;
    }
}
