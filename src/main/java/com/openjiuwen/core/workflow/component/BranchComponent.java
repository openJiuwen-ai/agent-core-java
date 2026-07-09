/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.WorkflowComponent;

import java.util.HashMap;
import java.util.List;

/**
 * Conditional routing component that evaluates branches and routes execution.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.branch_comp.BranchComponent}.
 * 
 * @since 0.1.7
 */
public class BranchComponent extends WorkflowComponent {
    private final BranchRouter router;

    /**
     * BranchComponent.
     * 
     * @since 0.1.7
     */
    public BranchComponent() {
        this.router = new BranchRouter(true);
    }

    /**
     * Add a branch with condition and target(s).
     * 
     * @param condition a String expression, BooleanSupplier, or Condition
     * @param target single target string or list of target strings
     * @param branchId optional branch identifier
     * @since 0.1.7
     */
    public void addBranch(Object condition, Object target, String branchId) {
        if (condition == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID, "reason",
                    "condition is None or empty");
        }
        if (target == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID, "reason",
                    "target is None or empty");
        }
        if (target instanceof List) {
            List<?> targetList = (List<?>) target;
            for (int i = 0; i < targetList.size(); i++) {
                if (targetList.get(i) == null || targetList.get(i).toString().isEmpty()) {
                    throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID, "reason",
                            "empty item at index " + i + " in target list");
                }
            }
        }
        router.addBranch(condition, target, branchId);
    }

    /**
     * addBranch.
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
     * Gets the router associated with this branch component.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BranchRouter router() {
        return router;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        router.setSession(session);
        return new HashMap<>();
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
        graph.addNode(nodeId, this.toExecutable(), waitForAll);
        graph.addConditionalEdges(nodeId, router);
    }

    /**
     * skipTrace.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean skipTrace() {
        return true;
    }
}
