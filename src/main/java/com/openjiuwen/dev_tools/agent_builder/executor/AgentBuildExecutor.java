/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.dev_tools.agent_builder.builders.BaseAgentBuilder;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import com.openjiuwen.dev_tools.agent_builder.utils.ProgressReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Agent build executor — orchestrates the build process.
 * <p>
 * Mirrors Python's {@code executor} in
 * {@code openjiuwen.dev_tools.agent_builder.executor.executor}.
 */
public class AgentBuildExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AgentBuildExecutor.class);

    private final BaseAgentBuilder builder;
    private final HistoryManager historyManager;
    private final ProgressReporter progressReporter;

    public AgentBuildExecutor(BaseAgentBuilder builder, HistoryManager historyManager) {
        this.builder = builder;
        this.historyManager = historyManager;
        this.progressReporter = new ProgressReporter();
    }

    /** Execute a build query. */
    public Map<String, Object> execute(Map<String, Object> query) {
        LOG.info("[AgentBuildExecutor] Executing build query, state={}", builder.getState());

        progressReporter.report(AgentBuilderEnums.ProgressStage.INITIALIZING,
                AgentBuilderEnums.ProgressStatus.RUNNING);

        Map<String, Object> result = builder.build(query, historyManager.getHistory());

        historyManager.addEntry(query);
        LOG.info("[AgentBuildExecutor] Build completed, state={}", builder.getState());
        return result;
    }
}
