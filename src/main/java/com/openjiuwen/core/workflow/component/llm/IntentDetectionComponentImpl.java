/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.ComponentComposable;
import java.util.List;

/**
 * Full implementation of IntentDetection workflow component.
 * Classifies user input via LLM and routes to appropriate branches.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.intent_detection_comp.IntentDetectionComponent}.
 */
public class IntentDetectionComponentImpl implements ComponentComposable {

    private IntentDetectionExecutable executable;
    private final IntentDetectionCompConfig config;
    private final BranchRouter router;

    public IntentDetectionComponentImpl(IntentDetectionCompConfig componentConfig) {
        this.config = componentConfig;
        this.router = new BranchRouter();
    }

    public IntentDetectionExecutable getExecutable() {
        if (executable == null) {
            executable = (IntentDetectionExecutable) toExecutable();
        }
        return executable;
    }

    @Override
    public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, toExecutable(), waitForAll);
        graph.addConditionalEdges(nodeId, router);
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return new IntentDetectionExecutable(config).setRouter(router);
    }

    /**
     * Add a branch for intent routing.
     *
     * @param condition branch condition (String expression, BooleanSupplier, or Condition)
     * @param target    target node(s)
     * @param branchId  optional branch identifier
     */
    public void addBranch(Object condition, Object target, String branchId) {
        if (target instanceof String s) {
            router.addBranch(condition, List.of(s), branchId);
        } else {
            router.addBranch(condition, target, branchId);
        }
    }

    /**
     * Add a branch without explicit ID.
     */
    public void addBranch(Object condition, Object target) {
        addBranch(condition, target, null);
    }

    /**
     * Compatibility alias for translated tests that still use snake_case naming.
     */
    public void add_branch(Object condition, Object target, String branchId) {
        addBranch(condition, target, branchId);
    }

    /**
     * Compatibility alias for translated tests that still use snake_case naming.
     */
    public void add_branch(Object condition, Object target) {
        addBranch(condition, target);
    }

    /**
     * Get the branch router.
     */
    public BranchRouter router() {
        return router;
    }
}
