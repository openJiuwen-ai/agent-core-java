/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.Branch;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.condition.Condition;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Mirrors Python's {@code BranchComponent} in
 * {@code openjiuwen/core/workflow/components/flow/branch_comp.py}.
 */
public class BranchComponent extends WorkflowComponent<Object, Map<String, Object>> {

    private final BranchRouter router = new BranchRouter(true);

    public BranchComponent() {
    }

    public void addBranch(Object condition, Object target) {
        addBranch(condition, target, null);
    }

    public void addBranch(Object condition, Object target, String branchId) {
        validateCondition(condition);
        validateTargetObject(target);
        router.addBranch(condition, target, branchId);
    }

    public void addBranch(String condition, String target) {
        addBranch(condition, target, null);
    }

    public void addBranch(String condition, String target, String branchId) {
        validateCondition(condition);
        validateTarget(target);
        router.addBranch(condition, target, branchId);
    }

    public void addBranch(String condition, List<String> target) {
        addBranch(condition, target, null);
    }

    public void addBranch(String condition, List<String> target, String branchId) {
        validateCondition(condition);
        validateTarget(target);
        router.addBranch(condition, target, branchId);
    }

    public void addBranch(BooleanSupplier condition, String target) {
        addBranch(condition, target, null);
    }

    public void addBranch(BooleanSupplier condition, String target, String branchId) {
        validateCondition(condition);
        validateTarget(target);
        router.addBranch(condition, target, branchId);
    }

    public void addBranch(BooleanSupplier condition, List<String> target) {
        addBranch(condition, target, null);
    }

    public void addBranch(BooleanSupplier condition, List<String> target, String branchId) {
        validateCondition(condition);
        validateTarget(target);
        router.addBranch(condition, target, branchId);
    }

    public void addBranch(Branch.BranchCondition condition, String target) {
        addBranch(condition, target, null);
    }

    public void addBranch(Branch.BranchCondition condition, String target, String branchId) {
        validateCondition(condition);
        validateTarget(target);
        router.addBranch(condition, target, branchId);
    }

    public void addBranch(Branch.BranchCondition condition, List<String> target) {
        addBranch(condition, target, null);
    }

    public void addBranch(Branch.BranchCondition condition, List<String> target, String branchId) {
        validateCondition(condition);
        validateTarget(target);
        router.addBranch(condition, target, branchId);
    }

    public void addBranch(Condition condition, String target) {
        addBranch(condition, target, null);
    }

    public void addBranch(Condition condition, String target, String branchId) {
        validateCondition(condition);
        validateTarget(target);
        router.addBranch(condition, target, branchId);
    }

    public void addBranch(Condition condition, List<String> target) {
        addBranch(condition, target, null);
    }

    public void addBranch(Condition condition, List<String> target, String branchId) {
        validateCondition(condition);
        validateTarget(target);
        router.addBranch(condition, target, branchId);
    }

    public BranchRouter router() {
        return router;
    }

    public void add_branch(Object condition, Object target) {
        addBranch(condition, target);
    }

    public void add_branch(Object condition, Object target, String branchId) {
        addBranch(condition, target, branchId);
    }

    @Override
    public Map<String, Object> invoke(Object inputs, BaseSession session, ModelContext context) {
        router.setSession(session);
        return new LinkedHashMap<>();
    }

    @Override
    public Map<String, Object> invoke(Object inputs, NodeSessionApi session, com.openjiuwen.core.context.ModelContext context) {
        return invoke(inputs, session.getInner(), context == null ? null : context.unwrap());
    }

    @Override
    public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, toExecutable(), waitForAll);
        graph.addConditionalEdges(nodeId, router());
        Set<String> allTargets = router.allTargets();
        if (allTargets.size() > 1) {
            registerBranchTargets(graph, nodeId, allTargets);
        }
    }

    @Override
    public boolean skipTrace() {
        return true;
    }

    private void validateCondition(Object condition) {
        if (condition == null || (condition instanceof String text && text.isEmpty())) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                    "reason", "condition is None or empty");
        }
    }

    private void validateTarget(String target) {
        if (target == null || target.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                    "reason", "target is None or empty");
        }
    }

    private void validateTarget(List<String> target) {
        if (target == null || target.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                    "reason", "target is None or empty");
        }
        for (int i = 0; i < target.size(); i++) {
            String item = target.get(i);
            if (item == null || item.isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                        "reason", "empty item at index " + i + " in target list");
            }
        }
    }

    private void validateTargetObject(Object target) {
        if (target instanceof String text) {
            validateTarget(text);
            return;
        }
        if (target instanceof List<?> items) {
            if (items.isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                        "reason", "target is None or empty");
            }
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                if (!(item instanceof String text) || text.isEmpty()) {
                    throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                            "reason", "empty item at index " + i + " in target list");
                }
            }
            return;
        }
        if (target == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                    "reason", "target is None or empty");
        }
        throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                "reason", "target must be a string or list of strings");
    }

    private void registerBranchTargets(Graph graph, String nodeId, Set<String> allTargets) {
        try {
            Method method = graph.getClass().getMethod("registerBranchTargets", String.class, Set.class);
            method.setAccessible(true);
            method.invoke(graph, nodeId, allTargets);
        } catch (NoSuchMethodException ignored) {
            // Python uses hasattr(graph, "register_branch_targets"); absence is a no-op.
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("failed to register branch targets", ex);
        }
    }
}
