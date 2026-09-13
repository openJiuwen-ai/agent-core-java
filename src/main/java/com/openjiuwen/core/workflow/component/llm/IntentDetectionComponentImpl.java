/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

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
 * 
 * @since 0.1.7
 */
public class IntentDetectionComponentImpl implements ComponentComposable {
    private IntentDetectionExecutable executable;
    private final IntentDetectionCompConfig config;
    private final BranchRouter router;

    /**
     * IntentDetectionComponentImpl.
     * 
     * @param componentConfig componentConfig
     * @since 0.1.7
     */
    public IntentDetectionComponentImpl(IntentDetectionCompConfig componentConfig) {
        this.config = componentConfig;
        this.router = new BranchRouter();
    }

    /**
     * getExecutable.
     * 
     * @return the result
     * @since 0.1.7
     */
    public IntentDetectionExecutable getExecutable() {
        if (executable == null) {
            executable = (IntentDetectionExecutable) toExecutable();
        }
        return executable;
    }

    /**
     * addComponent.
     * 
     * @param graph graph
     * @param nodeId nodeId
     * @param waitForAll waitForAll
     * @since 0.1.7
     */
    @Override
    public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, toExecutable(), waitForAll);
        graph.addConditionalEdges(nodeId, router);
    }

    /**
     * toExecutable.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Executable<?, ?> toExecutable() {
        return new IntentDetectionExecutable(config).setRouter(router);
    }

    /**
     * Add a branch for intent routing.
     * 
     * @param condition branch condition (String expression, BooleanSupplier, or Condition)
     * @param target target node(s)
     * @param branchId optional branch identifier
     * @since 0.1.7
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
     * 
     * @param condition condition
     * @param target target
     * @since 0.1.7
     */
    public void addBranch(Object condition, Object target) {
        addBranch(condition, target, null);
    }

    /**
     * Compatibility alias for translated tests that still use snake_case naming.
     * 
     * @param condition condition
     * @param target target
     * @param branchId branchId
     * @since 0.1.7
     */
    public void add_branch(Object condition, Object target, String branchId) {
        addBranch(condition, target, branchId);
    }

    /**
     * Compatibility alias for translated tests that still use snake_case naming.
     * 
     * @param condition condition
     * @param target target
     * @since 0.1.7
     */
    public void add_branch(Object condition, Object target) {
        addBranch(condition, target);
    }

    /**
     * Get the branch router.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BranchRouter router() {
        return router;
    }
}
