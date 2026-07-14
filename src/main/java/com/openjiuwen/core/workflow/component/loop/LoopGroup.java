/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.graph.pregel.PregelConfig;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.BaseWorkflow;
import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.WorkflowConfig;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.internal.LegacyWorkflowComponentSupport;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Workflow-like group used as a loop body and drawable subgraph owner.
 *
 * <p>Mirrors Python's {@code LoopGroup} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public class LoopGroup extends BaseWorkflow implements HasDrawable {

    private final List<LoopBreakComponent> breakComponents = new ArrayList<>();
    private final List<String> startNodes = new ArrayList<>();
    private final List<String> endNodes = new ArrayList<>();
    private final List<String> nodeIds = new ArrayList<>();
    private final List<String[]> connections = new ArrayList<>();
    private final Set<String> setVariableNodes = new HashSet<>();
    private final Set<String> setVariableDependencyEdges = new HashSet<>();

    public LoopGroup() {
        super(null, new PregelGraph());
    }

    public LoopGroup addWorkflowComp(String componentId, ComponentComposable workflowComponent) {
        return addWorkflowComp(componentId, workflowComponent, null, null, null, null, null, null);
    }

    public LoopGroup addWorkflowComp(String componentId,
                                     ComponentComposable workflowComponent,
                                     Boolean waitForAll,
                                     Object inputsSchema,
                                     Object outputsSchema,
                                     Object streamInputsSchema,
                                     Object streamOutputsSchema,
                                     List<ComponentAbility> compAbility) {
        if (workflowComponent instanceof LoopComponentImpl) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_GROUP_PARAM_INVALID,
                    "reason", "cannot add 'LoopComponent' to a loop group.");
        }
        super.addWorkflowComp(componentId, workflowComponent, waitForAll, inputsSchema, outputsSchema,
                streamInputsSchema, streamOutputsSchema, compAbility);
        if (workflowComponent instanceof LoopBreakComponent breakComponent) {
            breakComponents.add(breakComponent);
            if (getDrawable() != null) {
                getDrawable().setBreakNode(componentId);
            }
        }
        if (workflowComponent instanceof LoopSetVariableComponent) {
            setVariableNodes.add(componentId);
        }
        if (!nodeIds.contains(componentId)) {
            nodeIds.add(componentId);
        }
        return this;
    }

    public LoopGroup addWorkflowComp(String componentId,
                                     ComponentComposable workflowComponent,
                                     Map<String, ?> inputsSchema,
                                     Map<String, ?> streamInputsSchema,
                                     boolean waitForAll,
                                     List<ComponentAbility> compAbility) {
        return addWorkflowComp(componentId, workflowComponent, Boolean.valueOf(waitForAll), inputsSchema, null,
                streamInputsSchema, null, compAbility);
    }

    public LoopGroup addWorkflowComp(String componentId,
                                     Object workflowComponent,
                                     Boolean waitForAll,
                                     Object inputsSchema,
                                     Object outputsSchema,
                                     Object streamInputsSchema,
                                     Object streamOutputsSchema,
                                     Object compAbility) {
        List<ComponentAbility> abilities = compAbility instanceof List<?> list
                ? castAbilities(list)
                : null;
        return addWorkflowComp(componentId, LegacyWorkflowComponentSupport.adapt(workflowComponent), waitForAll,
                inputsSchema, outputsSchema, streamInputsSchema, streamOutputsSchema, abilities);
    }

    public LoopGroup addConnection(String sourceComponentId, String targetComponentId) {
        super.addConnection(sourceComponentId, targetComponentId);
        connections.add(new String[] {sourceComponentId, targetComponentId});
        addSetVariableSiblingDependencies(sourceComponentId, targetComponentId);
        return this;
    }

    private void addSetVariableSiblingDependencies(String sourceComponentId, String targetComponentId) {
        if (setVariableNodes.contains(targetComponentId)) {
            for (String[] connection : connections) {
                if (sourceComponentId.equals(connection[0]) && !targetComponentId.equals(connection[1])) {
                    addSetVariableDependency(connection[1], targetComponentId);
                }
            }
            return;
        }
        for (String[] connection : connections) {
            if (sourceComponentId.equals(connection[0]) && setVariableNodes.contains(connection[1])) {
                addSetVariableDependency(targetComponentId, connection[1]);
            }
        }
    }

    private void addSetVariableDependency(String sourceComponentId, String setVariableComponentId) {
        if (sourceComponentId == null || setVariableComponentId == null
                || sourceComponentId.equals(setVariableComponentId)) {
            return;
        }
        String edgeKey = sourceComponentId + "->" + setVariableComponentId;
        if (setVariableDependencyEdges.add(edgeKey)) {
            super.addConnection(sourceComponentId, setVariableComponentId);
        }
    }

    public LoopGroup addWorkflowComp(String componentId, ComponentComposable workflowComponent,
                                     Map<String, ?> inputsSchema) {
        return addWorkflowComp(componentId, workflowComponent, null, inputsSchema, null, null, null, null);
    }

    public LoopGroup addWorkflowComp(String componentId, Object workflowComponent, Map<String, ?> inputsSchema) {
        return addWorkflowComp(componentId, LegacyWorkflowComponentSupport.adapt(workflowComponent), inputsSchema);
    }

    public LoopGroup addWorkflowComp(String componentId, ComponentComposable workflowComponent,
                                     Map<String, ?> inputsSchema, boolean waitForAll) {
        return addWorkflowComp(componentId, workflowComponent, waitForAll, inputsSchema, null, null, null, null);
    }

    public LoopGroup addWorkflowComp(String componentId, Object workflowComponent, Map<String, ?> inputsSchema,
                                     boolean waitForAll) {
        return addWorkflowComp(componentId, LegacyWorkflowComponentSupport.adapt(workflowComponent),
                inputsSchema, waitForAll);
    }

    public LoopGroup addStreamConnection(String sourceComponentId, String targetComponentId) {
        super.addStreamConnection(sourceComponentId, targetComponentId);
        return this;
    }

    public LoopGroup startNodes(List<String> nodes) {
        startNodes.clear();
        if (nodes != null) {
            for (String node : nodes) {
                startComp(node);
            }
        }
        return this;
    }

    public LoopGroup start_nodes(List<String> nodes) {
        return startNodes(nodes);
    }

    public LoopGroup startComp(String componentId) {
        if (!startNodes.contains(componentId)) {
            startNodes.add(componentId);
        }
        super.startComp(componentId);
        return this;
    }

    public LoopGroup endNodes(List<String> nodes) {
        endNodes.clear();
        if (nodes != null) {
            for (String node : nodes) {
                endComp(node);
            }
        }
        return this;
    }

    public LoopGroup end_nodes(List<String> nodes) {
        return endNodes(nodes);
    }

    public LoopGroup endComp(String componentId) {
        if (!endNodes.contains(componentId)) {
            endNodes.add(componentId);
        }
        super.endComp(componentId);
        return this;
    }

    public void checkValidate() {
        if (startNodes.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_GROUP_PARAM_INVALID,
                    "reason", "missing start_nodes in loop group");
        }
        if (endNodes.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_GROUP_PARAM_INVALID,
                    "reason", "missing end_nodes in loop group");
        }
        if (nodeIds.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_GROUP_PARAM_INVALID,
                    "reason", "loop group is empty (contains no nodes)");
        }
    }

    @SuppressWarnings("unchecked")
    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        if (!(session instanceof WorkflowRuntimeSession runtimeSession)) {
            return null;
        }
        validateConfiguredNodes();
        reset();
        autoCompleteAbilities();
        WorkflowConfig config = getConfig();
        runtimeSession.config().addWorkflowConfig(config.getCard().getId(), config);
        clearFinishedStreamNodes(runtimeSession);
        ActorManager previousActorManager = runtimeSession.runtimeActorManager();
        runtimeSession.setActorManager(new ActorManager(config.getSpec(), getStreamActor(), true, runtimeSession));
        try {
            ExecutableGraph<?, ?> compiled = compile(runtimeSession, context);
            Map<String, Object> envelope = inputs instanceof Map<?, ?> map
                    ? toStringObjectMap(map)
                    : Map.of();
            Map<String, Object> graphInputs = new java.util.LinkedHashMap<>();
            graphInputs.put(Constant.INPUTS_KEY, envelope.getOrDefault(Constant.INPUTS_KEY, Map.of()));
            String graphNamespace = loopGraphNamespace(runtimeSession, config);
            PregelConfig graphConfig = new PregelConfig(
                    runtimeSession.sessionId(),
                    graphNamespace,
                    PregelConstants.MAX_RECURSIVE_LIMIT);
            graphConfig.setParentNs(runtimeSession.workflowId());
            graphInputs.put(Constant.CONFIG_KEY, graphConfig);
            Object result = ((ExecutableGraph<Object, Object>) compiled).invoke(graphInputs, runtimeSession);
            clearLoopGraphCheckpoint(runtimeSession, graphNamespace);
            return result;
        } finally {
            runtimeSession.setActorManager(previousActorManager);
        }
    }

    private void clearFinishedStreamNodes(WorkflowRuntimeSession runtimeSession) {
        Object rawFinished = runtimeSession.state().getWorkflowState("finished_stream_nodes");
        if (!(rawFinished instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<String> finished = new ArrayList<>(list.stream().map(String::valueOf).toList());
        if (finished.removeIf(nodeIds::contains)) {
            runtimeSession.state().updateAndCommitWorkflowState(Map.of("finished_stream_nodes", finished));
        }
    }

    private static String loopGraphNamespace(WorkflowRuntimeSession session, WorkflowConfig config) {
        String executableId = session.executableId();
        if (executableId == null || executableId.isBlank()) {
            executableId = config.getCard().getId();
        }
        String workflowId = session.workflowId();
        if (workflowId == null || workflowId.isBlank()) {
            return executableId + ".loop-body";
        }
        return workflowId + "." + executableId + ".loop-body";
    }

    private static void clearLoopGraphCheckpoint(WorkflowRuntimeSession session, String graphNamespace) {
        if (session == null || graphNamespace == null || graphNamespace.isBlank() || session.checkpointer() == null) {
            return;
        }
        Store graphStore = session.checkpointer().graphStore();
        if (graphStore != null) {
            graphStore.delete(session.sessionId(), graphNamespace).toCompletableFuture().join();
        }
    }

    public List<LoopBreakComponent> getBreakComponents() {
        return Collections.unmodifiableList(breakComponents);
    }

    public List<String> getNodeIds() {
        return Collections.unmodifiableList(nodeIds);
    }

    @SuppressWarnings("unchecked")
    private static List<ComponentAbility> castAbilities(List<?> list) {
        for (Object item : list) {
            if (!(item instanceof ComponentAbility)) {
                return null;
            }
        }
        return (List<ComponentAbility>) list;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private void validateConfiguredNodes() {
        for (String node : startNodes) {
            if (!nodeIds.contains(node)) {
                throw new IllegalStateException("getRouters returned null for start node " + node);
            }
        }
        for (String node : endNodes) {
            if (!nodeIds.contains(node)) {
                throw new IllegalStateException("getRouters returned null for end node " + node);
            }
        }
    }
}
