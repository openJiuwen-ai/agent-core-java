/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.CommitStateLike;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.component.AdvancedLoopComponent;
import com.openjiuwen.core.workflow.component.WorkflowComponent;
import com.openjiuwen.core.workflow.component.loop.callback.LoopCallback;
import com.openjiuwen.core.workflow.condition.Condition;
import com.openjiuwen.core.workflow.condition.NumberCondition;
import com.openjiuwen.core.workflow.condition.NumberConditionInSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Advanced loop component exposing Python's {@code body} property.
 *
 * <p>Mirrors Python's {@code AdvancedLoopComponent} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public class AdvancedLoopComponentImpl extends WorkflowComponent implements AdvancedLoopComponent, LoopController {

    private final HasDrawable body;
    private final Condition condition;
    private final List<LoopCallback> callbacks = new ArrayList<>();
    private boolean broken;

    public AdvancedLoopComponentImpl(HasDrawable body) {
        this(body, alwaysTrueCondition(), List.of(), List.of());
    }

    public AdvancedLoopComponentImpl(
            HasDrawable body,
            Condition condition,
            List<LoopBreakComponent> breakNodes,
            List<LoopCallback> callbacks
    ) {
        if (body == null) {
            throw new IllegalArgumentException("body is None");
        }
        this.body = body;
        this.condition = condition == null ? alwaysTrueCondition() : condition;
        if (breakNodes != null) {
            for (LoopBreakComponent breakNode : breakNodes) {
                if (breakNode != null) {
                    breakNode.setController(this);
                }
            }
        }
        if (callbacks != null) {
            this.callbacks.addAll(callbacks);
        }
    }

    @Override
    public HasDrawable getBody() {
        return body;
    }

    @Override
    public void registerCallback(LoopCallback callback) {
        if (callback != null) {
            callbacks.add(callback);
        }
    }

    public List<LoopCallback> getCallbacks() {
        return List.copyOf(callbacks);
    }

    public boolean evaluateCondition() {
        return evaluateCondition(null);
    }

    public boolean evaluateCondition(BaseSession session) {
        return !broken && condition.evaluate(session);
    }

    @Override
    public void breakLoop() {
        broken = true;
    }

    @Override
    public boolean isBroken() {
        return broken;
    }

    @Override
    public boolean graphInvoker() {
        return true;
    }

    /**
     * Component type identifier used by Vertex tracing metadata.
     *
     * @return {@code "AdvancedLoopComponent"}
     */
    public String componentType() {
        return "AdvancedLoopComponent";
    }

    public Executable<?, ?> toExecutable() {
        return new AdvancedLoopExecutable(this);
    }

    /**
     * Python-compatible snake_case bridge for reflected callers.
     *
     * @return executable advanced loop component
     */
    public Executable<?, ?> to_executable() {
        return toExecutable();
    }

    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        LoopGroup loopGroup = body instanceof LoopGroup lg ? lg : null;
        Object loopInputs = inputs;
        if (inputs instanceof Map<?, ?> map && map.containsKey(Constant.INPUTS_KEY)) {
            loopInputs = map;
        } else if (inputs instanceof Map<?, ?> map) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put(Constant.INPUTS_KEY, map);
            loopInputs = wrapped;
        }
        Condition effectiveCondition = resolveCondition(inputs, session);
        return LoopRuntime.invoke(effectiveCondition, loopGroup, callbacks, loopInputs, session, context);
    }

    private Condition resolveCondition(Object inputs, BaseSession session) {
        if (!(condition instanceof NumberCondition)) {
            return condition;
        }
        Object schema = condition.getInputSchema();
        // Try resolving from session IO state first
        Object resolved = com.openjiuwen.core.workflow.internal.WorkflowSessionSupport
                .getInputs(session, schema);
        if (resolved instanceof Number number) {
            return new NumberConditionInSession(number.intValue());
        }
        // Try resolving from the component inputs
        if (schema instanceof String strSchema && inputs instanceof Map<?, ?> inputsMap) {
            Object inputsData = inputsMap.get(Constant.INPUTS_KEY);
            if (inputsData instanceof Map<?, ?> dataMap) {
                String key = com.openjiuwen.core.session.utils.SessionUtils.extractOriginKey(strSchema);
                Object value = dataMap.get(key);
                if (value instanceof Number number) {
                    return new NumberConditionInSession(number.intValue());
                }
            }
        }
        // Try resolving from global state
        if (schema instanceof String strSchema) {
            String key = com.openjiuwen.core.session.utils.SessionUtils.extractOriginKey(strSchema);
            Object value = com.openjiuwen.core.workflow.internal.WorkflowSessionSupport
                    .getGlobalState(session, key);
            if (value instanceof Number number) {
                return new NumberConditionInSession(number.intValue());
            }
        }
        return condition;
    }

    private static Condition alwaysTrueCondition() {
        return new Condition() {
            @Override
            public Object doInvoke(Object inputs, BaseSession session) {
                return true;
            }
        };
    }

    private static final class AdvancedLoopExecutable extends Executable<Object, Object> {

        private final AdvancedLoopComponentImpl owner;

        private AdvancedLoopExecutable(AdvancedLoopComponentImpl owner) {
            this.owner = owner;
        }

        @Override
        public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
            prepareLoopTraceContext(session);
            return owner.invoke(inputs, session, null);
        }

        @Override
        public boolean graphInvoker() {
            return true;
        }

        @Override
        public String componentType() {
            return owner.componentType();
        }

        private static void prepareLoopTraceContext(BaseSession session) {
            if (session == null) {
                return;
            }
            String nodeId = WorkflowSessionSupport.componentId(session);
            if (nodeId == null || nodeId.isBlank()) {
                nodeId = session instanceof WorkflowRuntimeSession runtime
                        && runtime.executableId() != null
                        ? runtime.executableId()
                        : "";
            }
            cleanPriorNodeOutputs(session, nodeId);
            WorkflowSessionSupport.setOutputs(session, Map.of(Constant.LOOP_ID, nodeId));
            SessionStateAccess state = session.state();
            if (state != null) {
                state.updateGlobal(Map.of(Constant.LOOP_ID, nodeId));
            }
            WorkflowCommitState commitState = WorkflowSessionSupport.workflowState(session);
            if (commitState != null) {
                commitState.commit();
            }
            TracerWorkflowUtils.registerWorkflowSpanManager(session);
        }

        /**
         * Best-effort cleanup of prior outputs for this loop node (Python AdvancedLoop on_invoke).
         */
        private static void cleanPriorNodeOutputs(BaseSession session, String nodeId) {
            if (nodeId == null || nodeId.isBlank()) {
                return;
            }
            WorkflowCommitState commitState = WorkflowSessionSupport.workflowState(session);
            if (commitState == null) {
                return;
            }
            CommitStateLike ioState = commitState.getIoState();
            if (ioState == null) {
                return;
            }
            Map<String, Object> ioMap = new LinkedHashMap<>(ioState.getState());
            String parentId = session instanceof WorkflowRuntimeSession runtime
                    ? runtime.parentId()
                    : null;
            if (parentId != null && !parentId.isBlank()) {
                Object scoped = ioMap.get(parentId);
                if (scoped instanceof Map<?, ?> scopedMap) {
                    Map<String, Object> mutable = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : scopedMap.entrySet()) {
                        mutable.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    if (mutable.remove(nodeId) != null) {
                        ioMap.put(parentId, mutable);
                        ioState.setState(ioMap);
                    }
                }
            } else if (ioMap.remove(nodeId) != null) {
                ioState.setState(ioMap);
            }
        }
    }
}
