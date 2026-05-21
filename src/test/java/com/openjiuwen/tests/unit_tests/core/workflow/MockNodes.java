/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import java.util.Map;

/**
 * Mock workflow nodes for unit testing.
 * <p>
 * Mirrors Python's {@code mock_nodes.py} in
 * {@code tests/unit_tests/core/workflow/mock_nodes.py}.
 */
public final class MockNodes {

    private MockNodes() {}

    /**
     * Mock start node that passes inputs through as-is.
     */
    public static final class MockStartNode extends Start {
        private final String nodeId;

        public MockStartNode(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    /**
     * Mock end node with configurable response template.
     */
    public static final class MockEndNode extends End {
        private final String nodeId;

        public MockEndNode(String nodeId) {
            super(Map.of("responseTemplate", "hello:{{end_input}}"));
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    /**
     * Simple pass-through node for testing workflow routing.
     */
    public static final class Node1 extends WorkflowComponent {
        private final String nodeId;

        public Node1(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    /**
     * Counter node that tracks invocation count.
     */
    public static final class CountNode extends WorkflowComponent {
        private final String nodeId;
        private int times = 0;

        public CountNode(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public int getTimes() {
            return times;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            times++;
            return Map.of("count", times);
        }
    }
}