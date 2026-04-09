/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.Router;
import com.openjiuwen.core.graph.visualization.DrawableBranchRouter;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;
import com.openjiuwen.core.workflow.component.Branch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Router that evaluates branch conditions and returns target node paths.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.branch_router.BranchRouter}.
 */
public class BranchRouter implements Router {

    private final List<Branch> branches = new ArrayList<>();
    private BaseSession session;
    private final boolean reportTrace;
    private DrawableBranchRouter drawableBranchRouter;

    public BranchRouter(boolean reportTrace) {
        this.reportTrace = reportTrace;
        if (BaseWorkflow.isDrawableEnabled()) {
            this.drawableBranchRouter = new DrawableBranchRouter(new ArrayList<>(), new ArrayList<>());
        }
    }

    public BranchRouter() {
        this(false);
    }

    /**
     * Add a branch with condition and target(s).
     *
     * @param condition a String expression, BooleanSupplier, or Condition
     * @param target    single target string or list of target strings
     * @param branchId  optional branch identifier
     */
    public void addBranch(Object condition, Object target, String branchId) {
        if (condition == null || target == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                    "reason", "condition is None or target is None");
        }
        List<String> targetList;
        if (target instanceof String) {
            targetList = List.of((String) target);
        } else if (target instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) target;
            targetList = list;
        } else {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                    "reason", "target must be a string or list of strings");
        }

        if (drawableBranchRouter != null) {
            String branchData = branchId != null ? branchId : "";
            if (condition instanceof String) {
                branchData = (String) condition;
            }
            for (String t : targetList) {
                drawableBranchRouter.getTargets().add(t);
                drawableBranchRouter.getDatas().add(branchData);
            }
        }

        try {
            branches.add(new Branch(condition, targetList, branchId));
        } catch (IllegalArgumentException e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                    "reason", e.getMessage());
        }
    }

    public DrawableBranchRouter getDrawableBranchRouter() {
        return drawableBranchRouter;
    }

    /**
     * Set the session for condition evaluation.
     */
    public void setSession(Object session) {
        if (session instanceof NodeSessionApi) {
            // Extract inner session via reflection-like access
            this.session = extractInnerSession(session);
        } else if (session instanceof BaseSession) {
            this.session = (BaseSession) session;
        }
    }

    private static BaseSession extractInnerSession(Object sessionApi) {
        // Try to get the inner session through the field
        try {
            java.lang.reflect.Field innerField = sessionApi.getClass().getDeclaredField("inner");
            innerField.setAccessible(true);
            return (BaseSession) innerField.get(sessionApi);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Object apply(Object input) {
        BaseSession currentSession = this.session;
        if (reportTrace && currentSession != null) {
            List<Map<String, Object>> branchesTrace = new ArrayList<>();
            for (Branch branch : branches) {
                Map<String, Object> branchTrace = new LinkedHashMap<>();
                branchTrace.put("branch_id", branch.getBranchId());
                branchTrace.put("condition", branch.traceInfo(currentSession));
                branchesTrace.add(branchTrace);
            }
            TracerWorkflowUtils.traceComponentBegin(currentSession, List.of());
            TracerWorkflowUtils.traceComponentInputs(currentSession, Map.of("branches", branchesTrace), true);
        }

        for (Branch branch : branches) {
            if (branch.evaluate(currentSession)) {
                if (reportTrace && currentSession != null) {
                    Map<String, Object> outputs = new LinkedHashMap<>();
                    outputs.put("branch_id", branch.getBranchId());
                    TracerWorkflowUtils.traceComponentOutputs(currentSession, outputs);
                    TracerWorkflowUtils.traceComponentDone(currentSession);
                }
                return branch.getTarget();
            }
        }
        throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_EXECUTION_ERROR,
                "reason", "branch meeting the condition was not found");
    }
}
