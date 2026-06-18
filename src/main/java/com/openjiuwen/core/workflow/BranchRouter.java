/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.visualization.DrawableBranchRouter;
import com.openjiuwen.core.session.BaseSession;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Mirrors Python's {@code BranchRouter} in
 * {@code openjiuwen/core/workflow/components/flow/branch_router.py}.
 */
public class BranchRouter implements Function<BaseSession, List<String>> {

    public static final String WORKFLOW_DRAWABLE = "WORKFLOW_DRAWABLE";

    private final List<Branch> branches = new ArrayList<>();
    private final boolean reportTrace;
    private BaseSession session;
    private final DrawableBranchRouter drawableBranchRouter;

    public BranchRouter() {
        this(false);
    }

    public BranchRouter(boolean reportTrace) {
        this.reportTrace = reportTrace;
        this.drawableBranchRouter = new DrawableBranchRouter(new ArrayList<>(), new ArrayList<>());
    }

    public void addBranch(String condition, String target) {
        addBranch(condition, target, null);
    }

    public void addBranch(String condition, String target, String branchId) {
        addBranch(condition, List.of(target), branchId);
    }

    public void addBranch(String condition, List<String> target) {
        addBranch(condition, target, null);
    }

    public void addBranch(String condition, List<String> target, String branchId) {
        if (condition == null || target == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                    "reason", "condition is None or target is None");
        }
        recordDrawable(condition, target, branchId);
        branches.add(new Branch(condition, target, branchId));
    }

    public void addBranch(BooleanSupplier condition, String target) {
        addBranch(condition, target, null);
    }

    public void addBranch(BooleanSupplier condition, String target, String branchId) {
        addBranch(condition, List.of(target), branchId);
    }

    public void addBranch(BooleanSupplier condition, List<String> target) {
        addBranch(condition, target, null);
    }

    public void addBranch(BooleanSupplier condition, List<String> target, String branchId) {
        if (condition == null || target == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                    "reason", "condition is None or target is None");
        }
        recordDrawable(condition, target, branchId);
        branches.add(new Branch(condition, target, branchId));
    }

    public void addBranch(Branch.BranchCondition condition, String target) {
        addBranch(condition, target, null);
    }

    public void addBranch(Branch.BranchCondition condition, String target, String branchId) {
        addBranch(condition, List.of(target), branchId);
    }

    public void addBranch(Branch.BranchCondition condition, List<String> target) {
        addBranch(condition, target, null);
    }

    public void addBranch(Branch.BranchCondition condition, List<String> target, String branchId) {
        if (condition == null || target == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                    "reason", "condition is None or target is None");
        }
        recordDrawable(branchId, target, branchId);
        branches.add(new Branch(condition, target, branchId));
    }

    public DrawableBranchRouter getDrawableBranchRouter() {
        return drawableBranchRouter;
    }

    public Set<String> allTargets() {
        Set<String> targets = new LinkedHashSet<>();
        for (Branch branch : branches) {
            if (branch.getTarget() != null) {
                targets.addAll(branch.getTarget());
            }
        }
        return targets;
    }

    public void setSession(BaseSession session) {
        this.session = session;
    }

    public void setSession(Object session) {
        if (session instanceof BaseSession baseSession) {
            setSession(baseSession);
            return;
        }
        BaseSession inner = resolveInnerSession(session);
        if (inner != null) {
            setSession(inner);
            return;
        }
        throw new IllegalArgumentException("session type is wrong");
    }

    public List<String> route() {
        return route(session);
    }

    public List<String> call() {
        return route();
    }

    public List<String> call(Object... args) {
        return route();
    }

    @Override
    public List<String> apply(BaseSession session) {
        setSession(session);
        return route(session);
    }

    private List<String> route(BaseSession routeSession) {
        if (reportTrace) {
            traceComponentBegin(routeSession);
            traceComponentInputs(routeSession, branchTracePayload(routeSession));
        }
        for (Branch branch : branches) {
            if (branch.evaluate(routeSession)) {
                if (reportTrace) {
                    Map<String, Object> output = new LinkedHashMap<>();
                    output.put("branch_id", branch.getBranchId());
                    traceComponentOutputs(routeSession, output);
                    traceComponentDone(routeSession);
                }
                return new ArrayList<>(branch.getTarget());
            }
        }
        throw ErrorHelper.buildError(StatusCode.COMPONENT_BRANCH_EXECUTION_ERROR,
                "reason", "branch meeting the condition was not found");
    }

    private void recordDrawable(Object condition, List<String> target, String branchId) {
        if (drawableBranchRouter == null) {
            return;
        }
        String branchData = branchId == null ? "" : branchId;
        if (condition instanceof String conditionText) {
            branchData = conditionText;
        }
        for (String item : target) {
            drawableBranchRouter.getTargets().add(item);
            drawableBranchRouter.getDatas().add(branchData);
        }
    }

    private Map<String, Object> branchTracePayload(BaseSession routeSession) {
        List<Map<String, Object>> traceBranches = new ArrayList<>();
        for (Branch branch : branches) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("branch_id", branch.getBranchId());
            item.put("condition", branch.traceInfo(routeSession));
            traceBranches.add(item);
        }
        return Map.of("branches", traceBranches);
    }

    private BaseSession resolveInnerSession(Object candidate) {
        if (candidate == null) {
            return null;
        }
        for (String methodName : List.of("inner", "getInner", "_inner")) {
            try {
                Method method = candidate.getClass().getMethod(methodName);
                Object value = method.invoke(candidate);
                if (value instanceof BaseSession baseSession) {
                    return baseSession;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next naming convention.
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }
        try {
            Field field = candidate.getClass().getDeclaredField("_inner");
            field.setAccessible(true);
            Object value = field.get(candidate);
            if (value instanceof BaseSession baseSession) {
                return baseSession;
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return null;
        }
        return null;
    }

    private void traceComponentBegin(BaseSession routeSession) {
        invokeTracer(routeSession, "traceComponentBegin", routeSession);
    }

    private void traceComponentInputs(BaseSession routeSession, Map<String, Object> inputs) {
        invokeTracer(routeSession, "traceComponentInputs", routeSession, inputs);
    }

    private void traceComponentOutputs(BaseSession routeSession, Map<String, Object> outputs) {
        invokeTracer(routeSession, "traceComponentOutputs", routeSession, outputs);
    }

    private void traceComponentDone(BaseSession routeSession) {
        invokeTracer(routeSession, "traceComponentDone", routeSession);
    }

    private void invokeTracer(BaseSession routeSession, String methodName, Object... args) {
        if (routeSession == null) {
            return;
        }
        try {
            Method tracerMethod = routeSession.getClass().getMethod("tracer");
            Object tracer = tracerMethod.invoke(routeSession);
            for (Method method : tracer.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() >= args.length) {
                    method.invoke(tracer, args);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Python tracing is best-effort here; routing behavior must not be changed by tracing failures.
        }
    }
}
