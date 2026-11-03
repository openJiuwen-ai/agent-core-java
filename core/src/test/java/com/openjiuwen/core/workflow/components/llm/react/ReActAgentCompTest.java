/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm.react;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code ReActAgentComp} in
 * {@code openjiuwen/core/workflow/components/llm/react/react_component.py}.
 */
class ReActAgentCompTest {

    @Test
    void executablePropertyCachesFirstCreatedExecutable() {
        ReActAgentCompConfig config = new ReActAgentCompConfig();
        ReActAgentComp component = new ReActAgentComp(config);

        ReActAgentCompExecutable first = component.getExecutable();
        ReActAgentCompExecutable second = component.getExecutable();

        assertThat(first).isSameAs(second);
        assertThat(first.getConfig()).isSameAs(config);
    }

    @Test
    void toExecutableCreatesFreshExecutableWithSameConfig() {
        ReActAgentCompConfig config = new ReActAgentCompConfig();
        ReActAgentComp component = new ReActAgentComp(config);

        ReActAgentCompExecutable first = (ReActAgentCompExecutable) component.toExecutable();
        ReActAgentCompExecutable second = (ReActAgentCompExecutable) component.toExecutable();

        assertThat(first).isNotSameAs(second);
        assertThat(first.getConfig()).isSameAs(config);
        assertThat(second.getConfig()).isSameAs(config);
    }

    @Test
    void addComponentRegistersCachedExecutableOnGraph() {
        ReActAgentComp component = new ReActAgentComp(new ReActAgentCompConfig());
        RecordingGraph graph = new RecordingGraph();

        component.addComponent(graph, "react", true);

        assertThat(graph.nodeId).isEqualTo("react");
        assertThat(graph.node).isSameAs(component.getExecutable());
        assertThat(graph.waitForAll).isTrue();
    }

    private static final class RecordingGraph extends Graph {
        private String nodeId;
        private Executable<?, ?> node;
        private boolean waitForAll;

        @Override
        public Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll) {
            this.nodeId = nodeId;
            this.node = node;
            this.waitForAll = waitForAll;
            return this;
        }
    }
}
