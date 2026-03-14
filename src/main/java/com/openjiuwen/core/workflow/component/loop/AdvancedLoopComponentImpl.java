/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.component.AdvancedLoopComponent;
import com.openjiuwen.core.workflow.component.loop.callback.LoopCallback;
import com.openjiuwen.core.workflow.condition.AlwaysTrue;
import com.openjiuwen.core.workflow.condition.Condition;
import com.openjiuwen.core.workflow.condition.ExpressionCondition;
import com.openjiuwen.core.workflow.condition.FuncCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Full advanced loop component implementation. Acts as the condition evaluator
 * and graph router in the loop graph.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.AdvancedLoopComponent}.
 */
public class AdvancedLoopComponentImpl extends Executable<Object, Object> implements LoopController, AdvancedLoopComponent {

    private static final String BROKEN = "_broken";
    private static final String FIRST_IN_LOOP = "_first_in_loop";
    private static final String CONDITION_NODE_ID = "condition";
    private static final String BODY_NODE_ID = "body";
    private static final String POST_BODY_NODE_ID = "post_body";

    private String nodeId;
    private final Executable<Object, Object> body;
    private final PostLoopBody postBody;
    private final Condition condition;
    private final List<LoopCallback> callbacks;
    private final Graph graph;
    private final List<String> inLoop;
    private final List<String> outLoop;
    private NodeSession nodeSession;

    @SuppressWarnings("unchecked")
    public AdvancedLoopComponentImpl(
            Object body,
            Object conditionParam,
            List<? extends LoopBreakComponent> breakNodes,
            List<LoopCallback> callbacks) {

        // Wrap body: if it's already an Executable, use directly; if LoopGroup, wrap in delegate
        if (body instanceof Executable) {
            this.body = (Executable<Object, Object>) body;
        } else if (body instanceof LoopGroup) {
            LoopGroup loopGroup = (LoopGroup) body;
            this.body = new Executable<Object, Object>() {
                @Override
                public Object onInvoke(Object inputs, BaseSession session, Object... kw) {
                    return loopGroup.onInvoke(inputs, session, kw);
                }

                @Override
                public boolean graphInvoker() {
                    return true;
                }

                @Override
                public boolean skipTrace() {
                    return true;
                }
            };
        } else {
            throw new IllegalArgumentException("body must be Executable or LoopGroup");
        }
        this.postBody = new PostLoopBody();

        // Resolve condition
        if (conditionParam == null) {
            this.condition = new AlwaysTrue();
        } else if (conditionParam instanceof Condition) {
            this.condition = (Condition) conditionParam;
        } else if (conditionParam instanceof BooleanSupplier) {
            this.condition = new FuncCondition((BooleanSupplier) conditionParam);
        } else if (conditionParam instanceof String) {
            this.condition = new ExpressionCondition((String) conditionParam);
        } else {
            this.condition = new AlwaysTrue();
        }

        // Set controller on break nodes
        if (breakNodes != null) {
            for (LoopBreakComponent breakNode : breakNodes) {
                breakNode.setController(this);
            }
        }

        // Register callbacks
        this.callbacks = new ArrayList<>();
        if (callbacks != null) {
            this.callbacks.addAll(callbacks);
        }

        // Build loop graph
        this.graph = new PregelGraph();
        this.graph.addNode(BODY_NODE_ID, this.body);
        this.graph.addNode(CONDITION_NODE_ID, new EmptyExecutable());
        this.graph.addNode(POST_BODY_NODE_ID, this.postBody);
        this.graph.addEdge(PregelConstants.START, CONDITION_NODE_ID);
        this.graph.addEdge(BODY_NODE_ID, POST_BODY_NODE_ID);
        this.graph.addEdge(POST_BODY_NODE_ID, CONDITION_NODE_ID);
        // The loop router (this object as a Function) for conditional edges
        this.graph.addConditionalEdges(CONDITION_NODE_ID, (Function<Object, Object>) input -> routeLoop());

        this.inLoop = List.of(BODY_NODE_ID);
        this.outLoop = List.of(PregelConstants.END);
    }

    /**
     * Route function called by the graph when evaluating the condition node.
     */
    private List<String> routeLoop() {
        Map<String, Object> kwargs = Map.of("session", nodeSession);
        // Use AtomicNode-style invocation with validation and commit
        return doConditionInvoke(kwargs);
    }

    @SuppressWarnings("unchecked")
    private List<String> doConditionInvoke(Map<String, Object> kwargs) {
        try {
            return conditionInvoke();
        } catch (Exception e) {
            if (e instanceof com.openjiuwen.core.common.exception.BaseError) {
                throw (RuntimeException) e;
            }
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_CONDITION_EXECUTION_ERROR,
                    "reason", e.getMessage(),
                    "comp", nodeId != null ? nodeId : "unknown");
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> conditionInvoke() {
        if (!(nodeSession.state() instanceof WorkflowStateCollection)) {
            return outLoop;
        }
        WorkflowStateCollection state = (WorkflowStateCollection) nodeSession.state();

        Object indexObj = state.get(Constant.INDEX);
        int index;
        if (indexObj == null) {
            state.update(Map.of(BROKEN, false, Constant.INDEX, 0));
            state.setOutputs(Map.of(Constant.INDEX, 0));
            state.commitCmp();
            index = 0;
        } else {
            index = ((Number) indexObj).intValue();
        }

        int finishIndex = postBody.getFinishIndex();
        if (finishIndex + 1 < index || finishIndex > index) {
            // Resume from checkpoint
            finishIndex = index - 1;
        }

        if (finishIndex == index) {
            state.update(Map.of(Constant.INDEX, index + 1));
            state.setOutputs(Map.of(Constant.INDEX, index + 1));
            state.commitCmp();
        }

        boolean continueLoop = !isBroken() && condition.evaluate(nodeSession);

        for (LoopCallback callback : callbacks) {
            if (finishIndex < 0) {
                callback.call(LoopCallback.FIRST_LOOP, nodeSession);
            } else if (finishIndex == index) {
                callback.call(LoopCallback.END_ROUND, nodeSession, index + 1);
            }
            if (continueLoop) {
                callback.call(LoopCallback.START_ROUND, nodeSession);
            } else {
                callback.call(LoopCallback.OUT_LOOP, nodeSession);
            }
        }

        if (!continueLoop) {
            Map<String, Object> stateReset = new java.util.HashMap<>();
            stateReset.put(Constant.INDEX, null);
            stateReset.put(BROKEN, false);
            state.update(stateReset);
            postBody.setFinishIndex(-1);
            if (nodeSession.parent() != null && nodeSession.parent().state() instanceof WorkflowStateCollection) {
                Map<String, Object> postBodyReset = new java.util.HashMap<>();
                postBodyReset.put(POST_BODY_NODE_ID, null);
                ((WorkflowStateCollection) nodeSession.parent().state()).update(postBodyReset);
            }
            Map<String, Object> outputReset = new java.util.HashMap<>();
            outputReset.put(Constant.INDEX, null);
            state.setOutputs(outputReset);
        }

        return continueLoop ? inLoop : outLoop;
    }

    @Override
    public boolean isBroken() {
        if (nodeSession == null || !(nodeSession.state() instanceof WorkflowStateCollection)) {
            return false;
        }
        Object broken = ((WorkflowStateCollection) nodeSession.state()).get(BROKEN);
        return broken instanceof Boolean && (Boolean) broken;
    }

    @Override
    public void breakLoop() {
        if (nodeSession != null && nodeSession.state() instanceof WorkflowStateCollection) {
            ((WorkflowStateCollection) nodeSession.state()).update(Map.of(BROKEN, true));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
        BaseSession loopSession = session;
        if (loopSession instanceof NodeSession) {
            this.nodeId = ((NodeSession) loopSession).nodeId();
        }
        this.nodeSession = new NodeSession(loopSession, this.nodeId);

        // Set loop ID in outputs
        if (loopSession.state() instanceof WorkflowCommitState wsState) {
            wsState.setOutputs(Map.of(Constant.LOOP_ID, this.nodeId));
            Map<String, Object> stateSnapshot = wsState.getIoState().getState();
            stateSnapshot.remove(this.nodeId);
            wsState.setOutputs(stateSnapshot);
            wsState.commit();
        }

        // Tracer registration (if tracer is present)
        if (loopSession.tracer() != null) {
            TracerWorkflowUtils.registerWorkflowSpanManager(this.nodeSession);
        }

        Object context = kwargs.length > 0 ? kwargs[0] : null;
        @SuppressWarnings("unchecked")
        ExecutableGraph<Object, Object> compiled = (ExecutableGraph<Object, Object>) graph.compile(loopSession);
        compiled.invoke(inputs, loopSession);

        // Get result from node session
        if (nodeSession.state() instanceof WorkflowStateCollection) {
            Object result = ((WorkflowStateCollection) nodeSession.state()).getOutputs(this.nodeId);
            if (loopSession.state() instanceof WorkflowCommitState wsState) {
                Map<String, Object> cleanup = new java.util.HashMap<>();
                cleanup.put(this.nodeId, null);
                wsState.getIoState().updateById(this.nodeId, cleanup);
            }
            return result;
        }
        return null;
    }

    @Override
    public boolean graphInvoker() {
        return true;
    }

    @Override
    public boolean skipTrace() {
        return false;
    }

    @Override
    public HasDrawable getBody() {
        if (body instanceof HasDrawable) {
            return (HasDrawable) body;
        }
        return null;
    }

    public Executable<Object, Object> getBodyExecutable() {
        return body;
    }

    @Override
    public void registerCallback(LoopCallback callback) {
        if (callback != null) {
            callbacks.add(callback);
        }
    }
}
