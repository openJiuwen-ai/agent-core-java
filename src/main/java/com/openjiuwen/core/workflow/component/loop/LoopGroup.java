/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.SubWorkflowSession;
import com.openjiuwen.core.workflow.BaseWorkflow;
import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.internal.LegacyWorkflowComponentSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * A group of components that form the body of a loop.
 * Extends BaseWorkflow for graph construction and Executable for invocation.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopGroup}.
 */
public class LoopGroup extends BaseWorkflow {

    private ExecutableGraph<?, ?> compiledGraph;
    private final List<LoopBreakComponent> breakComponents = new ArrayList<>();
    private final List<String> startNodesList = new ArrayList<>();
    private final List<String> endNodesList = new ArrayList<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoopGroup() {
        super();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseWorkflow addWorkflowComp(
            String compId,
            ComponentComposable workflowComp,
            Boolean waitForAll,
            Object inputsSchema,
            Object outputsSchema,
            Object streamInputsSchema,
            Object streamOutputsSchema,
            List<ComponentAbility> compAbility) {

        if (workflowComp instanceof LoopComponentImpl) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_GROUP_PARAM_INVALID,
                    "reason", "cannot add 'LoopComponent' to a loop group.");
        }

        super.addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, outputsSchema,
                streamInputsSchema, streamOutputsSchema, compAbility);

        if (workflowComp instanceof LoopBreakComponent) {
            breakComponents.add((LoopBreakComponent) workflowComp);
        }

        return this;
    }

    /**
     * Compatibility overload for translated tests that omit advanced options.
     */
    public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp) {
        addWorkflowComp(compId, workflowComp, null, null, null, null, null, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that still use legacy POJO nodes.
     */
    public LoopGroup addWorkflowComp(String compId, Object workflowComp) {
        return addWorkflowComp(compId, LegacyWorkflowComponentSupport.adapt(workflowComp));
    }

    /**
     * Compatibility overload for translated tests that omit outputs schema.
     */
    public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema) {
        addWorkflowComp(compId, workflowComp, null, inputsSchema, null, null, null, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that still use legacy POJO nodes.
     */
    public LoopGroup addWorkflowComp(String compId, Object workflowComp, Object inputsSchema) {
        return addWorkflowComp(compId, LegacyWorkflowComponentSupport.adapt(workflowComp), inputsSchema);
    }

    /**
     * Compatibility overload for translated tests that place wait_for_all after schemas.
     */
    public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp,
                                     Object inputsSchema, Boolean waitForAll) {
        addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, null, null, null, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that place wait_for_all after schemas.
     */
    public LoopGroup addWorkflowComp(String compId, Object workflowComp,
                                     Object inputsSchema, Boolean waitForAll) {
        return addWorkflowComp(compId, LegacyWorkflowComponentSupport.adapt(workflowComp), inputsSchema, waitForAll);
    }

    /**
     * Compatibility overload for translated tests that place wait_for_all after schemas.
     */
    public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp,
                                     Object inputsSchema, Object outputsSchema, Boolean waitForAll) {
        addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, outputsSchema, null, null, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that place wait_for_all after schemas.
     */
    public LoopGroup addWorkflowComp(String compId, Object workflowComp,
                                     Object inputsSchema, Object outputsSchema, Boolean waitForAll) {
        return addWorkflowComp(compId, LegacyWorkflowComponentSupport.adapt(workflowComp),
                inputsSchema, outputsSchema, waitForAll);
    }

    /**
     * Compatibility overload for translated tests that still pass explicit abilities.
     */
    public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp,
                                     Object inputsSchema, Boolean waitForAll,
                                     List<ComponentAbility> compAbility) {
        addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, null, null, null, compAbility);
        return this;
    }

    /**
     * Compatibility overload for translated tests that still pass explicit abilities.
     */
    public LoopGroup addWorkflowComp(String compId, Object workflowComp,
                                     Object inputsSchema, Boolean waitForAll,
                                     List<ComponentAbility> compAbility) {
        return addWorkflowComp(compId, LegacyWorkflowComponentSupport.adapt(workflowComp),
                inputsSchema, waitForAll, compAbility);
    }

    /**
     * Compatibility overload for translated tests that still pass explicit abilities.
     */
    public LoopGroup addWorkflowComp(String compId, ComponentComposable workflowComp,
                                     Object inputsSchema, Object outputsSchema, Boolean waitForAll,
                                     List<ComponentAbility> compAbility) {
        addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, outputsSchema, null, null, compAbility);
        return this;
    }

    /**
     * Compatibility overload for translated tests that still pass explicit abilities.
     */
    public LoopGroup addWorkflowComp(String compId, Object workflowComp,
                                     Object inputsSchema, Object outputsSchema, Boolean waitForAll,
                                     List<ComponentAbility> compAbility) {
        return addWorkflowComp(compId, LegacyWorkflowComponentSupport.adapt(workflowComp),
                inputsSchema, outputsSchema, waitForAll, compAbility);
    }

    /**
     * Set the start nodes of the loop group.
     */
    public LoopGroup startNodes(List<String> nodes) {
        for (String node : nodes) {
            startComp(node);
        }
        startNodesList.clear();
        startNodesList.addAll(nodes);
        return this;
    }

    /**
     * Compatibility alias for translated tests that still use snake_case naming.
     */
    public LoopGroup start_nodes(List<String> nodes) {
        return startNodes(nodes);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseWorkflow startComp(String startCompId) {
        super.startComp(startCompId);
        if (!startNodesList.contains(startCompId)) {
            startNodesList.add(startCompId);
        }
        return this;
    }

    /**
     * Set the end nodes of the loop group.
     */
    public LoopGroup endNodes(Object nodes) {
        if (nodes instanceof String) {
            endComp((String) nodes);
            endNodesList.add((String) nodes);
        } else if (nodes instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> nodeList = (List<String>) nodes;
            for (String node : nodeList) {
                endComp(node);
            }
            endNodesList.clear();
            endNodesList.addAll(nodeList);
        }
        return this;
    }

    /**
     * Compatibility alias for translated tests that still use snake_case naming.
     */
    public LoopGroup end_nodes(Object nodes) {
        return endNodes(nodes);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseWorkflow endComp(String endCompId) {
        super.endComp(endCompId);
        if (!endNodesList.contains(endCompId)) {
            endNodesList.add(endCompId);
        }
        return this;
    }

    /**
     * Invoke the loop group graph.
     */
    public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
        autoCompleteAbilities();
        BaseSession parentSession = (session instanceof NodeSession)
                ? ((NodeSession) session).parent() : session;
        String loopNodeId = getConfig().getCard().getId();
        String loopNodeType = getConfig().getCard().getId();
        if (parentSession instanceof NodeSession nodeSession) {
            loopNodeId = nodeSession.nodeId();
            loopNodeType = nodeSession.nodeType();
        }
        SubWorkflowSession loopSession = new SubWorkflowSession(
                parentSession != null ? parentSession : session,
                loopNodeId,
                loopNodeType,
                getConfig().getCard().getId());
        loopSession.setActorManager(buildActorManager(loopSession));
        loopSession.config().addWorkflowConfig(getConfig().getCard().getId(), getConfig());
        compiledGraph = compile(loopSession, kwargs.length > 0 ? kwargs[0] : null);
        @SuppressWarnings("unchecked")
        ExecutableGraph<Object, Object> typedGraph = (ExecutableGraph<Object, Object>) compiledGraph;
        typedGraph.invoke(inputs, loopSession);
        return null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean skipTrace() {
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean graphInvoker() {
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<LoopBreakComponent> getBreakComponents() {
        return breakComponents;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getStartNodesList() {
        return startNodesList;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getEndNodesList() {
        return endNodesList;
    }

    /**
     * Validate the loop group configuration.
     */
    public void checkValidate() {
        if (startNodesList.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_GROUP_PARAM_INVALID,
                    "reason", "missing start_nodes in loop group");
        }
        if (endNodesList.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_GROUP_PARAM_INVALID,
                    "reason", "missing end_nodes in loop group");
        }
        if (getGraph().getNodes().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_LOOP_GROUP_PARAM_INVALID,
                    "reason", "loop group is empty (contains no nodes)");
        }
    }

    private ActorManager buildActorManager(BaseSession session) {
        return new ActorManager(
                getConfig().getSpec().getStreamEdges(),
                getStreamActor(),
                true,
                session,
                compId -> {
                    if (getConfig().getSpec().getCompConfigs().containsKey(compId)) {
                        List<ComponentAbility> abilities =
                                getConfig().getSpec().getCompConfigs().get(compId).getAbilities();
                        return abilities != null ? abilities : List.of();
                    }
                    return List.of();
                });
    }
}
