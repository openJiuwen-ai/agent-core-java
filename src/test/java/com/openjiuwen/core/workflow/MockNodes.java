/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.workflow.components.Session;

import java.util.Map;

/**
 * Mock nodes for workflow testing.
 * <p>
 * Mirrors Python's {@code mock_nodes.py} in
 * {@code tests/unit_tests/core/workflow/mock_nodes.py}.
 */
public class MockNodes {

    /**
     * Base class for mock workflow nodes.
     */
    public static abstract class MockNodeBase implements WorkflowComponent {
        protected String nodeId;

        public MockNodeBase(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }
    }

    /**
     * Mock start node.
     */
    public static class MockStartNode extends Start {
        private String nodeId;

        public MockStartNode(String nodeId) {
            super();
            this.nodeId = nodeId;
        }

        @Override
        public Object invoke(Object inputs, Session session, ModelContext context) {
            return inputs;
        }
    }

    /**
     * Mock end node.
     */
    public static class MockEndNode extends End {
        private String nodeId;

        public MockEndNode(String nodeId) {
            super(EndConfig.builder().responseTemplate("hello:{{end_input}}").build());
            this.nodeId = nodeId;
        }

        @Override
        public Object invoke(Object inputs, Session session, ModelContext context) {
            return inputs;
        }
    }

    /**
     * Simple node that passes inputs through.
     */
    public static class Node1 extends MockNodeBase {
        public Node1(String nodeId) {
            super(nodeId);
        }

        @Override
        public Object invoke(Object inputs, Session session, ModelContext context) {
            return inputs;
        }
    }

    /**
     * Node that counts invocations.
     */
    public static class CountNode extends MockNodeBase {
        private int times = 0;

        public CountNode(String nodeId) {
            super(nodeId);
        }

        @Override
        public Object invoke(Object inputs, Session session, ModelContext context) {
            times++;
            Map<String, Object> result = Map.of("count", times);
            return result;
        }

        public int getTimes() {
            return times;
        }
    }

    /**
     * Node that delays execution.
     */
    public static class SlowNode extends MockNodeBase {
        private final int waitMillis;

        public SlowNode(String nodeId, int waitMillis) {
            super(nodeId);
            this.waitMillis = waitMillis;
        }

        @Override
        public Object invoke(Object inputs, Session session, ModelContext context) throws Exception {
            Thread.sleep(waitMillis);
            return inputs;
        }
    }

    /**
     * Node for testing checkpoint recovery.
     */
    public static class MockStartNode4Cp extends Start {
        private int runtime = 0;

        public MockStartNode4Cp(String nodeId) {
            super();
        }

        @Override
        public Object invoke(Object inputs, Session session, ModelContext context) {
            runtime++;
            Object value = session.getGlobalState("a");
            if (value != null) {
                throw new RuntimeException("value is not None");
            }
            session.updateGlobalState(Map.of("a", 10));
            return inputs;
        }

        public int getRuntime() {
            return runtime;
        }
    }

    /**
     * Node for testing checkpoint state.
     */
    public static class Node4Cp extends MockNodeBase {
        private int runtime = 0;

        public Node4Cp(String nodeId) {
            super(nodeId);
        }

        @Override
        public Object invoke(Object inputs, Session session, ModelContext context) {
            runtime++;
            Object value = session.getGlobalState("a");
            if (value instanceof Integer v && v < 20) {
                throw new RuntimeException("value < 20");
            }
            return inputs;
        }

        public int getRuntime() {
            return runtime;
        }
    }

    /**
     * Node that adds 10 to input (for checkpoint testing).
     */
    public static class AddTenNode4Cp implements WorkflowComponent {
        private boolean raiseException = true;

        @Override
        public Object invoke(Object inputs, Session session, ModelContext context) throws Exception {
            if (raiseException) {
                raiseException = false;
                throw new RuntimeException("inner error: " + ((Map<?, ?>) inputs).get("source"));
            }
            raiseException = true;
            int source = (Integer) ((Map<?, ?>) inputs).get("source");
            return Map.of("result", source + 10);
        }
    }
}