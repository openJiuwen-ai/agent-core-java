/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.SubWorkflowSession;
import com.openjiuwen.core.workflow.BaseWorkflow;
import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.component.ComponentAbility;

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

    public LoopGroup() {
        super();
    }

    @Override
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

    @Override
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

    @Override
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
        loopSession.config().addWorkflowConfig(getConfig().getCard().getId(), getConfig());
        compiledGraph = compile(loopSession, kwargs.length > 0 ? kwargs[0] : null);
        @SuppressWarnings("unchecked")
        ExecutableGraph<Object, Object> typedGraph = (ExecutableGraph<Object, Object>) compiledGraph;
        typedGraph.invoke(inputs, loopSession);
        return null;
    }

    public boolean skipTrace() {
        return true;
    }

    public boolean graphInvoker() {
        return true;
    }

    public List<LoopBreakComponent> getBreakComponents() {
        return breakComponents;
    }

    public List<String> getStartNodesList() {
        return startNodesList;
    }

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
}
