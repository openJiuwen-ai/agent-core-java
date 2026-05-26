/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
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

    // ========== Checkpoint-enabled nodes (mirrors Python's _4Cp classes) ==========

    /**
     * Mock start node with checkpoint (global state) support.
     * <p>
     * Mirrors Python's {@code MockStartNode4Cp} from mock_nodes.py (lines 100-112).
     * <p>
     * Python implementation:
     * <pre>
     * class MockStartNode4Cp(Start):
     *     def __init__(self, node_id: str):
     *         super().__init__()
     *         self.runtime = 0
     *
     *     async def invoke(self, inputs: Input, session: Session, context: ModelContext) -> Output:
     *         self.runtime += 1
     *         value = session.get_global_state("a")
     *         if value is not None:
     *             raise Exception("value is not None")
     *         print("start: output = " + str(inputs))
     *         session.update_global_state({"a": 10})
     *         return inputs
     * </pre>
     */
    public static final class MockStartNode4Cp extends Start {
        private final String nodeId;
        private int runtime = 0;

        public MockStartNode4Cp(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public int getRuntime() {
            return runtime;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            runtime++;
            Object value = session.getGlobalState("a");
            if (value != null) {
                throw new RuntimeException("value is not None");
            }
            System.out.println("start: output = " + inputs);
            session.updateGlobalState(Map.of("a", 10));
            return inputs;
        }
    }

    /**
     * Interactive node with stream output support.
     * <p>
     * Mirrors Python's {@code InteractiveNode4StreamCp} from mock_nodes.py (lines 154-161).
     * <p>
     * Python implementation:
     * <pre>
     * class InteractiveNode4StreamCp(MockNodeBase):
     *     def __init__(self, node_id):
     *         super().__init__(node_id)
     *
     *     async def invoke(self, inputs: Input, session: Session, context: ModelContext) -> Output:
     *         result = await session.interact("Please enter any key")
     *         await session.write_stream(OutputSchema(type="output", index=0, payload=(self.node_id, result)))
     *         return result
     * </pre>
     */
    public static final class InteractiveNode4StreamCp extends WorkflowComponent {
        private final String nodeId;

        public InteractiveNode4StreamCp(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            // Trigger interaction with user
            Object result = session.interact("Please enter any key");
            // Write stream output (nodeId, result tuple)
            session.writeStream(new OutputSchema("output", 0, new Object[]{nodeId, result}));
            return result;
        }
    }

    /**
     * Interactive node with checkpoint support (two interactions).
     * <p>
     * Mirrors Python's {@code InteractiveNode4Cp} from mock_nodes.py (lines 143-151).
     * <p>
     * Python implementation:
     * <pre>
     * class InteractiveNode4Cp(MockNodeBase):
     *     def __init__(self, node_id: str):
     *         super().__init__(node_id)
     *
     *     async def invoke(self, inputs: Input, session: Session, context: ModelContext) -> Output:
     *         result1 = await session.interact("Please enter any key")
     *         print(result1)
     *         result = await session.interact("Please enter any key")
     *         return result
     * </pre>
     */
    public static final class InteractiveNode4Cp extends WorkflowComponent {
        private final String nodeId;

        public InteractiveNode4Cp(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result1 = session.interact("Please enter any key");
            System.out.println(result1);
            Object result = session.interact("Please enter any key");
            return result;
        }
    }

    /**
     * Node with checkpoint support that validates global state.
     * <p>
     * Mirrors Python's {@code Node4Cp} from mock_nodes.py (lines 115-125).
     * <p>
     * Python implementation:
     * <pre>
     * class Node4Cp(MockNodeBase):
     *     def __init__(self, node_id: str):
     *         super().__init__(node_id)
     *         self.runtime = 0
     *
     *     async def invoke(self, inputs: Input, session: Session, context: ModelContext) -> Output:
     *         self.runtime += 1
     *         value = session.get_global_state("a")
     *         if value < 20:
     *             raise Exception("value < 20")
     *         return inputs
     * </pre>
     */
    public static final class Node4Cp extends WorkflowComponent {
        private final String nodeId;
        private int runtime = 0;

        public Node4Cp(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public int getRuntime() {
            return runtime;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            runtime++;
            Object value = session.getGlobalState("a");
            if (value instanceof Number) {
                if (((Number) value).intValue() < 20) {
                    throw new RuntimeException("value < 20");
                }
            }
            return inputs;
        }
    }

    /**
     * Add-ten node with checkpoint and exception simulation.
     * <p>
     * Mirrors Python's {@code AddTenNode4Cp} from mock_nodes.py (lines 128-140).
     * <p>
     * Python implementation:
     * <pre>
     * class AddTenNode4Cp(WorkflowComponent):
     *     raise_exception = True
     *
     *     def __init__(self, node_id: str):
     *         super().__init__()
     *         self.node_id = node_id
     *
     *     async def invoke(self, inputs: Input, session: Session, context: ModelContext) -> Output:
     *         if self.raise_exception:
     *             self.raise_exception = False
     *             raise Exception("inner error: " + str(inputs["source"]))
     *         self.raise_exception = True
     *         return {"result": inputs["source"] + 10}
     * </pre>
     */
    public static final class AddTenNode4Cp extends WorkflowComponent {
        private final String nodeId;
        private boolean raiseException = true;

        public AddTenNode4Cp(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public boolean isRaiseException() {
            return raiseException;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            if (raiseException) {
                raiseException = false;
                Map<String, Object> inputMap = (Map<String, Object>) inputs;
                throw new RuntimeException("inner error: " + inputMap.get("source"));
            }
            raiseException = true;
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            return Map.of("result", ((Number) inputMap.get("source")).intValue() + 10);
        }
    }
}