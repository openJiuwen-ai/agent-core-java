/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.dev_tools.agent_builder.builders.WorkflowBuilder;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test WorkflowBuilder functionality.
 * <p>
 * Mirrors Python's {@code test_builder.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_builder.py}.
 */
class TestBuilder {

    @Test
    void testInitSuccess() {
        Object mockModel = new Object();
        HistoryManager historyManager = new HistoryManager();
        WorkflowBuilder builder = new WorkflowBuilder(mockModel, historyManager);

        assertThat(builder.getLlm()).isSameAs(mockModel);
        assertThat(builder.getHistoryManager()).isSameAs(historyManager);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getWorkflowName()).isNull();
        assertThat(builder.getWorkflowNameEn()).isNull();
        assertThat(builder.getWorkflowDesc()).isNull();
        assertThat(builder.getDl()).isNull();
        assertThat(builder.getMermaidCode()).isNull();
    }

    @Test
    void testInitProgressReporterDefaultNone() {
        WorkflowBuilder builder = new WorkflowBuilder(new Object(), new HistoryManager());
        assertThat(builder.getProgressReporter()).isNull();
    }

    @Test
    void testResourceProperty() {
        WorkflowBuilder builder = new WorkflowBuilder(new Object(), new HistoryManager());
        assertThat(builder.getResource()).isEmpty();
    }

    @Test
    void testStateProperty() {
        WorkflowBuilder builder = new WorkflowBuilder(new Object(), new HistoryManager());
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);

        builder.setState(AgentBuilderEnums.BuildState.PROCESSING);

        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);
    }
}
