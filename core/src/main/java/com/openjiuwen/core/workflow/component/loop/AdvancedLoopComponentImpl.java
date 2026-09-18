/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.CommitStateLike;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
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
import java.util.function.Function;

/**
 * Advanced loop component exposing Python's {@code body} property.
 *
 * <p>Mirrors Python's {@code AdvancedLoopComponent} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public class AdvancedLoopComponentImpl extends WorkflowComponent implements AdvancedLoopComponent, LoopController {

    private static final String BROKEN = "_broken";
    private static final String CONDITION_NODE_ID = "condition";
    private static final String BODY_NODE_ID = "body";
    private static final String POST_BODY_NODE_ID = "post_body";

    private final HasDrawable body;
    private final Executable<Object, Object> bodyExecutable;
    private final PostLoopBody postBody = new PostLoopBody();
    private final Condition condition;
    private final List<LoopCallback> callbacks = new ArrayList<>();
    private final PregelGraph graph = new PregelGraph();
    private final List<String> inLoop = List.of(BODY_NODE_ID);
    private final List<String> outLoop = List.of(PregelConstants.END);
    private boolean broken;
    private String nodeId;
    private BaseSession nodeSession;
    private ModelContext loopContext;
    private Condition runtimeCondition;

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
        this.bodyExecutable = wrapBody(body);
        this.condition = condition == null ? alwaysTrueCondition() : condition;
        bindBreakNodes(breakNodes);
        if (callbacks != null) {
            this.callbacks.addAll(callbacks);
        }
        buildLoopGraph();
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
        return !isBroken() && condition.evaluate(session);
    }

    @Override
    public void breakLoop() {
        broken = true;
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(nodeSession);
        if (state != null) {
            state.update(Map.of(BROKEN, true));
        }
    }

    @Override
    public boolean isBroken() {
        if (broken) {
            return true;
        }
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(nodeSession);
        return state != null && Boolean.TRUE.equals(state.get(BROKEN));
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
        this.loopContext = context;
        this.runtimeCondition = resolveCondition(inputs, session);
        bindLoopSession(session);
        prepareLoopState(session);
        if (session != null && session.tracer() != null) {
            TracerWorkflowUtils.registerWorkflowSpanManager(session);
        }
        runLoopGraph(inputs, session);
        return readLoopResult();
    }

    private void bindLoopSession(BaseSession session) {
        this.nodeSession = session;
        if (session instanceof WorkflowRuntimeSession runtimeSession) {
            String runtimeNodeId = runtimeSession.nodeId();
            if (runtimeNodeId != null && !runtimeNodeId.isBlank()) {
                this.nodeId = runtimeNodeId;
            }
        }
        if (this.nodeId == null || this.nodeId.isBlank()) {
            this.nodeId = WorkflowSessionSupport.componentId(session);
        }
    }

    private void prepareLoopState(BaseSession session) {
        String loopNodeId = nodeId == null ? "" : nodeId;
        // Drop prior outputs for this node, then set LOOP_ID.
        // Do not setOutputs(full IO snapshot): that merges sibling nodes (start/inputs)
        // into the loop payload and breaks End("${loop}") output contracts.
        cleanPriorNodeOutputs(session, loopNodeId);
        WorkflowSessionSupport.setOutputs(session, Map.of(Constant.LOOP_ID, loopNodeId));
        SessionStateAccess state = session == null ? null : session.state();
        if (state != null) {
            state.updateGlobal(Map.of(Constant.LOOP_ID, loopNodeId));
        }
        WorkflowCommitState commitState = WorkflowSessionSupport.workflowState(session);
        if (commitState != null) {
            commitState.commit();
        }
    }

    @SuppressWarnings("unchecked")
    private void runLoopGraph(Object inputs, BaseSession session) {
        BaseSession compileSession = session == null ? nodeSession : session;
        ExecutableGraph<Object, Object> compiled =
                (ExecutableGraph<Object, Object>) graph.compile(compileSession);
        compiled.invoke(asInvokeEnvelope(inputs), compileSession);
    }

    private Object readLoopResult() {
        if (nodeSession == null) {
            return null;
        }
        Object result = WorkflowSessionSupport.getOutputs(nodeSession, nodeId);
        WorkflowCommitState commitState = WorkflowSessionSupport.workflowState(nodeSession);
        if (commitState != null && nodeId != null && !nodeId.isBlank()) {
            Map<String, Object> cleanup = new LinkedHashMap<>();
            cleanup.put(nodeId, null);
            commitState.getIoState().updateById(nodeId, cleanup);
        }
        return result;
    }

    private List<String> routeLoop() {
        try {
            return conditionInvoke();
        } catch (BaseError error) {
            throw error;
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_CONDITION_EXECUTION_ERROR,
                    "reason", error.getMessage(),
                    "comp", nodeId != null ? nodeId : "unknown");
        }
    }

    private List<String> conditionInvoke() {
        WorkflowStateCollection state = WorkflowSessionSupport.stateCollection(nodeSession);
        if (state == null) {
            return outLoop;
        }
        int index = ensureLoopIndex(state);
        int finishIndex = alignedFinishIndex(index);
        if (finishIndex == index) {
            incrementLoopIndex(state, index);
        }
        boolean continueLoop = !isBroken() && runtimeCondition().evaluate(nodeSession);
        fireLoopCallbacks(finishIndex, index, continueLoop);
        if (!continueLoop) {
            resetLoopState(state);
        }
        return continueLoop ? inLoop : outLoop;
    }

    private int ensureLoopIndex(WorkflowStateCollection state) {
        Object indexObj = state.get(Constant.INDEX);
        if (indexObj == null) {
            Map<String, Object> initial = new LinkedHashMap<>();
            initial.put(BROKEN, false);
            initial.put(Constant.INDEX, 0);
            state.update(initial);
            Map<String, Object> initialOutputs = new LinkedHashMap<>();
            initialOutputs.put(Constant.INDEX, 0);
            WorkflowSessionSupport.setOutputs(nodeSession, initialOutputs);
            commitNodeSession();
            return 0;
        }
        return ((Number) indexObj).intValue();
    }

    private int alignedFinishIndex(int index) {
        int finishIndex = postBody.getFinishIndex();
        if (finishIndex + 1 < index || finishIndex > index) {
            return index - 1;
        }
        return finishIndex;
    }

    private void incrementLoopIndex(WorkflowStateCollection state, int index) {
        Map<String, Object> nextIndex = new LinkedHashMap<>();
        nextIndex.put(Constant.INDEX, index + 1);
        state.update(nextIndex);
        WorkflowSessionSupport.setOutputs(nodeSession, nextIndex);
        commitNodeSession();
    }

    private void fireLoopCallbacks(int finishIndex, int index, boolean continueLoop) {
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
    }

    private void resetLoopState(WorkflowStateCollection state) {
        Map<String, Object> stateReset = new LinkedHashMap<>();
        stateReset.put(Constant.INDEX, null);
        stateReset.put(BROKEN, false);
        state.update(stateReset);
        postBody.setFinishIndex(-1);
        WorkflowStateCollection parentState = parentStateCollection();
        if (parentState != null) {
            Map<String, Object> postBodyReset = new LinkedHashMap<>();
            postBodyReset.put(POST_BODY_NODE_ID, null);
            parentState.update(postBodyReset);
        }
        // Body nodes write under loop via dotted executableId (loop.bodyNode). Null-clear them
        // together with INDEX in one setOutputs so End("${loop}") sees only callback outputs.
        clearNestedLoopBodyOutputs();
        commitNodeSession();
    }

    private void clearNestedLoopBodyOutputs() {
        Object outputs = WorkflowSessionSupport.getOutputs(nodeSession, nodeId);
        Map<String, Object> cleanup = new LinkedHashMap<>();
        if (outputs instanceof Map<?, ?> outputMap) {
            for (Map.Entry<?, ?> entry : outputMap.entrySet()) {
                cleanup.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        cleanup.put(Constant.INDEX, null);
        if (body instanceof LoopGroup loopGroup) {
            for (String bodyNodeId : loopGroup.getNodeIds()) {
                if (cleanup.containsKey(bodyNodeId)) {
                    cleanup.put(bodyNodeId, null);
                }
            }
        }
        for (String internalKey : List.of(
                BODY_NODE_ID, CONDITION_NODE_ID, POST_BODY_NODE_ID,
                Constant.LOOP_ID, BROKEN, "round", "start")) {
            if (cleanup.containsKey(internalKey)) {
                cleanup.put(internalKey, null);
            }
        }
        WorkflowSessionSupport.setOutputs(nodeSession, cleanup);
    }

    private WorkflowStateCollection parentStateCollection() {
        if (!(nodeSession instanceof WorkflowRuntimeSession runtimeSession)) {
            return null;
        }
        return WorkflowSessionSupport.stateCollection(runtimeSession.parent());
    }

    private void commitNodeSession() {
        WorkflowCommitState commitState = WorkflowSessionSupport.workflowState(nodeSession);
        if (commitState != null) {
            commitState.commit();
        }
    }

    private Condition runtimeCondition() {
        return runtimeCondition == null ? condition : runtimeCondition;
    }

    private void bindBreakNodes(List<LoopBreakComponent> breakNodes) {
        if (breakNodes == null) {
            return;
        }
        for (LoopBreakComponent breakNode : breakNodes) {
            if (breakNode != null) {
                breakNode.setController(this);
            }
        }
    }

    private void buildLoopGraph() {
        graph.addNode(BODY_NODE_ID, bodyExecutable);
        graph.addNode(CONDITION_NODE_ID, new EmptyExecutable());
        graph.addNode(POST_BODY_NODE_ID, postBody);
        graph.addEdge(PregelConstants.START, CONDITION_NODE_ID);
        graph.addEdge(BODY_NODE_ID, POST_BODY_NODE_ID);
        graph.addEdge(POST_BODY_NODE_ID, CONDITION_NODE_ID);
        graph.addConditionalEdges(CONDITION_NODE_ID, (Function<Object, Object>) input -> routeLoop());
    }

    @SuppressWarnings("unchecked")
    private Executable<Object, Object> wrapBody(HasDrawable drawable) {
        if (drawable instanceof Executable<?, ?> executable) {
            return (Executable<Object, Object>) executable;
        }
        if (drawable instanceof LoopGroup loopGroup) {
            return new LoopGroupBodyExecutable(loopGroup);
        }
        throw new IllegalArgumentException("body must be Executable or LoopGroup");
    }

    private Condition resolveCondition(Object inputs, BaseSession session) {
        if (!(condition instanceof NumberCondition)) {
            return condition;
        }
        Object schema = condition.getInputSchema();
        Object resolved = WorkflowSessionSupport.getInputs(session, schema);
        if (resolved instanceof Number number) {
            return new NumberConditionInSession(number.intValue());
        }
        Number fromInputs = numberFromInputs(schema, inputs);
        if (fromInputs != null) {
            return new NumberConditionInSession(fromInputs.intValue());
        }
        if (schema instanceof String strSchema) {
            String key = com.openjiuwen.core.session.utils.SessionUtils.extractOriginKey(strSchema);
            Object value = WorkflowSessionSupport.getGlobalState(session, key);
            if (value instanceof Number number) {
                return new NumberConditionInSession(number.intValue());
            }
        }
        return condition;
    }

    private static Number numberFromInputs(Object schema, Object inputs) {
        if (!(schema instanceof String strSchema) || !(inputs instanceof Map<?, ?> inputsMap)) {
            return null;
        }
        Object inputsData = inputsMap.get(Constant.INPUTS_KEY);
        if (!(inputsData instanceof Map<?, ?> dataMap)) {
            return null;
        }
        String key = com.openjiuwen.core.session.utils.SessionUtils.extractOriginKey(strSchema);
        Object value = dataMap.get(key);
        return value instanceof Number number ? number : null;
    }

    private static Map<String, Object> asInvokeEnvelope(Object inputs) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        if (inputs instanceof Map<?, ?> map && map.containsKey(Constant.INPUTS_KEY)) {
            map.forEach((key, value) -> envelope.put(String.valueOf(key), value));
            return envelope;
        }
        if (inputs instanceof Map<?, ?> map) {
            envelope.put(Constant.INPUTS_KEY, map);
            return envelope;
        }
        envelope.put(Constant.INPUTS_KEY, inputs == null ? Map.of() : inputs);
        return envelope;
    }

    private static Condition alwaysTrueCondition() {
        return new Condition() {
            @Override
            public Object doInvoke(Object inputs, BaseSession session) {
                return true;
            }
        };
    }

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
        String parentId = session instanceof WorkflowRuntimeSession runtime ? runtime.parentId() : null;
        boolean changed;
        if (parentId != null && !parentId.isBlank()) {
            changed = removeScopedOutput(ioMap, parentId, nodeId);
        } else {
            changed = ioMap.remove(nodeId) != null;
        }
        if (changed) {
            ioState.setState(ioMap);
        }
    }

    private static boolean removeScopedOutput(Map<String, Object> ioMap, String parentId, String nodeId) {
        Object scoped = ioMap.get(parentId);
        if (!(scoped instanceof Map<?, ?> scopedMap)) {
            return false;
        }
        Map<String, Object> mutable = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : scopedMap.entrySet()) {
            mutable.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        if (mutable.remove(nodeId) == null) {
            return false;
        }
        ioMap.put(parentId, mutable);
        return true;
    }

    private final class LoopGroupBodyExecutable extends Executable<Object, Object> {

        private final LoopGroup loopGroup;

        private LoopGroupBodyExecutable(LoopGroup loopGroup) {
            this.loopGroup = loopGroup;
        }

        @Override
        public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
            BaseSession loopSession = nodeSession != null ? nodeSession : session;
            return loopGroup.invoke(inputs, loopSession, loopContext);
        }

        @Override
        public boolean graphInvoker() {
            return true;
        }

        @Override
        public boolean skipTrace() {
            return true;
        }
    }

    private static final class AdvancedLoopExecutable extends Executable<Object, Object> {

        private final AdvancedLoopComponentImpl owner;

        private AdvancedLoopExecutable(AdvancedLoopComponentImpl owner) {
            this.owner = owner;
        }

        @Override
        public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
            return owner.invoke(inputs, session, extractContext(kwargs));
        }

        @Override
        public boolean graphInvoker() {
            return true;
        }

        @Override
        public String componentType() {
            return owner.componentType();
        }

        private static ModelContext extractContext(Object... kwargs) {
            if (kwargs == null) {
                return null;
            }
            for (Object kwarg : kwargs) {
                if (kwarg instanceof ModelContext modelContext) {
                    return modelContext;
                }
                if (kwarg instanceof Map<?, ?> map && map.get("context") instanceof ModelContext modelContext) {
                    return modelContext;
                }
            }
            return null;
        }
    }
}
