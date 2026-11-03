/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.visualization.Drawable;
import com.openjiuwen.core.graph.visualization.DrawableEdge;
import com.openjiuwen.core.graph.visualization.DrawableSubgraphNode;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionComponentImpl;
import com.openjiuwen.core.workflow.component.loop.AdvancedLoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Focused validation for the workflow dependency slice used by Drawable.addNode.
 *
 * <p>Mirrors Python's visualization usage in
 * {@code openjiuwen/core/graph/visualization/drawable.py} and the component
 * accessors in {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py},
 * {@code openjiuwen/core/workflow/components/flow/workflow_comp.py}, and
 * {@code openjiuwen/core/workflow/components/llm/intent_detection_comp.py}.</p>
 */
public final class WorkflowVisualizationDependencySliceTest {

    private WorkflowVisualizationDependencySliceTest() {
    }

    public static void main(String[] args) {
        loopComponentProducesDrawableSubgraph();
        advancedLoopComponentUsesBodyDrawable();
        subWorkflowComponentUsesInternalDrawable();
        branchAndIntentComponentsExposeRouterDrawableData();
        System.out.println("PASS WorkflowVisualizationDependencySliceTest");
    }

    private static void loopComponentProducesDrawableSubgraph() {
        LoopGroup group = loopGroupWithDrawableNode("loop-body");
        LoopComponentImpl component = new LoopComponentImpl(group, Map.of("answer", "value"));

        Drawable drawable = new Drawable();
        drawable.addNode("loop", component);

        require(drawable.getGraph().getNodes().get("loop") instanceof DrawableSubgraphNode,
                "loop component should become a subgraph node");
        require(drawable.getGraph().getEdges().size() == 1, "loop component should add self edge");
        DrawableEdge selfEdge = drawable.getGraph().getEdges().get(0);
        require("loop".equals(selfEdge.getSource()), "self edge source");
        require("loop".equals(selfEdge.getTarget()), "self edge target");
        require(selfEdge.isConditional(), "self edge should be conditional");
        require(group == component.getLoopGroup(), "loop_group accessor should expose original group");
    }

    private static void advancedLoopComponentUsesBodyDrawable() {
        LoopGroup group = loopGroupWithDrawableNode("advanced-body");
        AdvancedLoopComponentImpl component = new AdvancedLoopComponentImpl(group);

        Drawable drawable = new Drawable();
        drawable.addNode("advanced", component);

        require(drawable.getGraph().getNodes().get("advanced") instanceof DrawableSubgraphNode,
                "advanced loop should become a subgraph node");
        require(group == component.getBody(), "body accessor should expose original body");
        require(component.evaluateCondition(), "default condition should continue");
        component.breakLoop();
        require(component.isBroken(), "breakLoop should update broken state");
        require(!component.evaluateCondition(), "broken loop should not continue");
    }

    private static void subWorkflowComponentUsesInternalDrawable() {
        LoopGroup internal = loopGroupWithDrawableNode("sub-body");
        Workflow workflow = new Workflow() {
            @Override
            public HasDrawable getInternalDrawable() {
                return internal;
            }
        };
        SubWorkflowComponentImpl component = new SubWorkflowComponentImpl(workflow, true);

        Drawable drawable = new Drawable();
        drawable.addNode("sub", component);

        require(drawable.getGraph().getNodes().get("sub") instanceof DrawableSubgraphNode,
                "sub workflow should become a subgraph node");
        require(workflow == component.getSubWorkflow(), "sub_workflow accessor should expose original workflow");
        require(internal == component.getSubWorkflowInternal(), "internal drawable accessor");
        require(component.isCacheStream(), "cache_stream accessor");
    }

    private static void branchAndIntentComponentsExposeRouterDrawableData() {
        BranchComponent branch = new BranchComponent();
        branch.addBranch("x > 1", List.of("left", "right"), "fallback");
        require(branch.router().getDrawableBranchRouter().getTargets().equals(List.of("left", "right")),
                "branch router targets");
        require(branch.router().getDrawableBranchRouter().getDatas().equals(List.of("x > 1", "x > 1")),
                "string condition is drawable branch data");

        IntentDetectionComponentImpl intent = new IntentDetectionComponentImpl();
        intent.addBranch((BooleanSupplier) () -> true, "intent-target", "intent-branch");
        Drawable drawable = new Drawable();
        drawable.addNode("intent", intent);

        require(drawable.getGraph().getEdges().size() == 1, "intent component should add router edge");
        DrawableEdge edge = drawable.getGraph().getEdges().get(0);
        require("intent".equals(edge.getSource()), "intent edge source");
        require("intent-target".equals(edge.getTarget()), "intent edge target");
        require("intent-branch".equals(edge.getData().toString()), "intent branch id is edge data");
    }

    private static LoopGroup loopGroupWithDrawableNode(String nodeId) {
        LoopGroup group = new LoopGroup();
        group.getDrawable().addNode(nodeId, new PlainComponent());
        group.getDrawable().setStartNode(nodeId);
        group.getDrawable().setEndNode(nodeId);
        group.startComp(nodeId);
        group.endComp(nodeId);
        group.addWorkflowComp(nodeId, new PlainComponent());
        return group;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Plain component used for Drawable.addNode's default branch.
     *
     * <p>Mirrors Python's {@code ComponentComposable} in
     * {@code openjiuwen/core/workflow/components/component.py}.</p>
     */
    private static final class PlainComponent implements ComponentComposable {
    }
}
