/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.workflow;

import com.openjiuwen.core.workflow.Workflow;

import java.util.List;
import java.util.Map;

/**
 * Mock workflow nodes for unit testing.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.workflow.mock_nodes}.
 */
public final class MockNodes {

    private MockNodes() {
        throw new AssertionError("No instances");
    }

    /**
     * Mirrors Python's {@code MockNodeBase}.
     */
    public static class MockNodeBase extends com.openjiuwen.core.workflow.MockNodes.MockNodeBase {
        public MockNodeBase() {
            super();
        }

        public MockNodeBase(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code MockStartNode}.
     */
    public static class MockStartNode extends com.openjiuwen.core.workflow.MockNodes.MockStartNode {
        public MockStartNode(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code MockEndNode}.
     */
    public static class MockEndNode extends com.openjiuwen.core.workflow.MockNodes.MockEndNode {
        public MockEndNode(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code Node1}.
     */
    public static class Node1 extends com.openjiuwen.core.workflow.MockNodes.Node1 {
        public Node1(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code CountNode}.
     */
    public static class CountNode extends com.openjiuwen.core.workflow.MockNodes.CountNode {
        public CountNode(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code SlowNode}.
     */
    public static class SlowNode extends com.openjiuwen.core.workflow.MockNodes.SlowNode {
        public SlowNode(String nodeId, int waitSeconds) {
            super(nodeId, waitSeconds);
        }
    }

    /**
     * Mirrors Python's {@code StreamNode}.
     */
    public static class StreamNode extends com.openjiuwen.core.workflow.MockNodes.StreamNode {
        public StreamNode(String nodeId, List<Map<String, Object>> datas) {
            super(nodeId, datas);
        }
    }

    /**
     * Mirrors Python's {@code StreamNodeWithSubWorkflow}.
     */
    public static class StreamNodeWithSubWorkflow
            extends com.openjiuwen.core.workflow.MockNodes.StreamNodeWithSubWorkflow {
        public StreamNodeWithSubWorkflow(String nodeId, Workflow subWorkflow) {
            super(nodeId, subWorkflow);
        }
    }

    /**
     * Mirrors Python's {@code MockStartNode4Cp}.
     */
    public static class MockStartNode4Cp extends com.openjiuwen.core.workflow.MockNodes.MockStartNode4Cp {
        public MockStartNode4Cp(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code Node4Cp}.
     */
    public static class Node4Cp extends com.openjiuwen.core.workflow.MockNodes.Node4Cp {
        public Node4Cp(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code AddTenNode4Cp}.
     */
    public static class AddTenNode4Cp extends com.openjiuwen.core.workflow.MockNodes.AddTenNode4Cp {
        public AddTenNode4Cp(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code InteractiveNode4Cp}.
     */
    public static class InteractiveNode4Cp extends com.openjiuwen.core.workflow.MockNodes.InteractiveNode4Cp {
        public InteractiveNode4Cp(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code InteractiveNode4StreamCp}.
     */
    public static class InteractiveNode4StreamCp
            extends com.openjiuwen.core.workflow.MockNodes.InteractiveNode4StreamCp {
        public InteractiveNode4StreamCp(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code InteractiveNode4Collect}.
     */
    public static class InteractiveNode4Collect
            extends com.openjiuwen.core.workflow.MockNodes.InteractiveNode4Collect {
        public InteractiveNode4Collect(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code StreamCompNode}.
     */
    public static class StreamCompNode extends com.openjiuwen.core.workflow.MockNodes.StreamCompNode {
        public StreamCompNode(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code CollectCompNode}.
     */
    public static class CollectCompNode extends com.openjiuwen.core.workflow.MockNodes.CollectCompNode {
        public CollectCompNode(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code TransformCompNode}.
     */
    public static class TransformCompNode extends com.openjiuwen.core.workflow.MockNodes.TransformCompNode {
        public TransformCompNode(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code MultiCollectCompNode}.
     */
    public static class MultiCollectCompNode extends com.openjiuwen.core.workflow.MockNodes.MultiCollectCompNode {
        public MultiCollectCompNode(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code CommonNode}.
     */
    public static class CommonNode extends com.openjiuwen.core.workflow.MockNodes.CommonNode {
        public CommonNode(String nodeId) {
            super(nodeId);
        }
    }

    /**
     * Mirrors Python's {@code AddTenNode}.
     */
    public static class AddTenNode extends com.openjiuwen.core.workflow.MockNodes.AddTenNode {
        public AddTenNode(String nodeId) {
            super(nodeId);
        }

        public AddTenNode(String nodeId, Map<String, Object> checkMap) {
            super(nodeId, checkMap);
        }
    }

    /**
     * Mirrors Python's {@code MockStreamNode}.
     */
    public static class MockStreamNode extends com.openjiuwen.core.workflow.MockNodes.MockStreamNode {
        public MockStreamNode() {
            super();
        }
    }

    /**
     * Mirrors Python's {@code ComputeComponent2}.
     */
    public static class ComputeComponent2 extends com.openjiuwen.core.workflow.MockNodes.ComputeComponent2 {
        public ComputeComponent2() {
            super();
        }
    }

    /**
     * Mirrors Python's {@code ComputeExecutor2}.
     */
    public static class ComputeExecutor2 extends com.openjiuwen.core.workflow.MockNodes.ComputeExecutor2 {
        public ComputeExecutor2() {
            super();
        }
    }

    /**
     * Mirrors Python's {@code DualAbilityWithErrorComponent}.
     */
    public static class DualAbilityWithErrorComponent
            extends com.openjiuwen.core.workflow.MockNodes.DualAbilityWithErrorComponent {
        public DualAbilityWithErrorComponent() {
            super();
        }

        public DualAbilityWithErrorComponent(boolean errorInStream) {
            super(errorInStream);
        }

        public DualAbilityWithErrorComponent(boolean errorInStream, boolean errorInTransform) {
            super(errorInStream, errorInTransform);
        }
    }

    /**
     * Mirrors Python's {@code DualAbilityWithErrorExecutor}.
     */
    public static class DualAbilityWithErrorExecutor
            extends com.openjiuwen.core.workflow.MockNodes.DualAbilityWithErrorExecutor {
        public DualAbilityWithErrorExecutor() {
            super();
        }

        public DualAbilityWithErrorExecutor(boolean errorInStream, boolean errorInTransform) {
            super(errorInStream, errorInTransform);
        }
    }

    /**
     * Mirrors Python's {@code MockNodeWithAllAbility}.
     */
    public static class MockNodeWithAllAbility
            extends com.openjiuwen.core.workflow.MockNodes.MockNodeWithAllAbility {
        public MockNodeWithAllAbility() {
            super();
        }
    }

    /**
     * Mirrors Python's {@code MockIntentNode}.
     */
    public static class MockIntentNode extends com.openjiuwen.core.workflow.MockNodes.MockIntentNode {
        public MockIntentNode(Object classificationId) {
            super(classificationId);
        }
    }
}
