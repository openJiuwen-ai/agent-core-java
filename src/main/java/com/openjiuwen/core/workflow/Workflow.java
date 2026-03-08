/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.SubWorkflowSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main workflow class representing a directed graph of components.
 * Orchestrates execution of connected components, managing data flow and streaming.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.workflow.Workflow}.
 */
public class Workflow {

    private final WorkflowCard card;
    private final BaseWorkflow internal;
    private String endCompId = "";
    private boolean isStreaming = false;

    public Workflow(WorkflowCard card) {
        this.card = card != null ? card
                : WorkflowCard.builder().id(UUID.randomUUID().toString().replace("-", "")).build();
        this.internal = new BaseWorkflow(new WorkflowConfig(this.card), new PregelGraph());
    }

    public Workflow() {
        this(null);
    }

    public WorkflowCard getCard() {
        return card;
    }

    /**
     * Set the starting component of the workflow.
     */
    public Workflow setStartComp(
            String startCompId,
            ComponentComposable component,
            Object inputsSchema,
            Object outputsSchema) {
        internal.addWorkflowComp(startCompId, component, false, inputsSchema, outputsSchema,
                null, null, null);
        internal.startComp(startCompId);
        return this;
    }

    /**
     * Add a component to the workflow graph.
     */
    public Workflow addWorkflowComp(
            String compId,
            ComponentComposable workflowComp,
            Boolean waitForAll,
            Object inputsSchema,
            Object outputsSchema,
            Object streamInputsSchema,
            Object streamOutputsSchema,
            List<ComponentAbility> compAbility) {
        internal.addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, outputsSchema,
                streamInputsSchema, streamOutputsSchema, compAbility);
        return this;
    }

    /**
     * Simplified addWorkflowComp with just ID, component, and schemas.
     */
    public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp,
                                     Object inputsSchema, Object outputsSchema) {
        return addWorkflowComp(compId, workflowComp, null, inputsSchema, outputsSchema, null, null, null);
    }

    /**
     * Minimal addWorkflowComp with just ID and component.
     */
    public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp) {
        return addWorkflowComp(compId, workflowComp, null, null, null, null, null, null);
    }

    /**
     * Set the ending component of the workflow.
     */
    public Workflow setEndComp(
            String endCompId,
            ComponentComposable component,
            Object inputsSchema,
            Object outputsSchema,
            Object streamInputsSchema,
            Object streamOutputsSchema,
            String responseMode) {

        List<ComponentAbility> compAbility = new ArrayList<>();
        boolean waitForAll = false;

        if ("streaming".equals(responseMode)) {
            this.isStreaming = true;
            if (inputsSchema != null) {
                compAbility.add(ComponentAbility.STREAM);
            }
            if (streamInputsSchema != null) {
                compAbility.add(ComponentAbility.TRANSFORM);
            }
            if (compAbility.isEmpty()) {
                compAbility.add(ComponentAbility.STREAM);
            }
        } else {
            compAbility.add(ComponentAbility.INVOKE);
            if (streamInputsSchema != null) {
                compAbility.add(ComponentAbility.COLLECT);
            }
        }

        waitForAll = compAbility.contains(ComponentAbility.COLLECT)
                || compAbility.contains(ComponentAbility.TRANSFORM);

        internal.addWorkflowComp(endCompId, component, waitForAll, inputsSchema, outputsSchema,
                streamInputsSchema, streamOutputsSchema, compAbility);
        internal.endComp(endCompId);
        this.endCompId = endCompId;
        return this;
    }

    /**
     * Simplified setEndComp.
     */
    public Workflow setEndComp(String endCompId, ComponentComposable component,
                                Object inputsSchema, Object outputsSchema) {
        return setEndComp(endCompId, component, inputsSchema, outputsSchema, null, null, null);
    }

    /**
     * Add a data connection between components.
     */
    public Workflow addConnection(Object srcCompId, String targetCompId) {
        internal.addConnection(srcCompId, targetCompId);
        return this;
    }

    /**
     * Add a streaming connection between components.
     */
    public Workflow addStreamConnection(String srcCompId, String targetCompId) {
        internal.addStreamConnection(srcCompId, targetCompId);
        return this;
    }

    /**
     * Add a conditional connection with routing logic.
     */
    public Workflow addConditionalConnection(String srcCompId, Object router) {
        internal.addConditionalConnection(srcCompId, router);
        return this;
    }

    /**
     * Execute the workflow synchronously.
     *
     * @param inputs  input data
     * @param session workflow session (NodeSessionApi for user-facing, BaseSession for sub)
     * @param context model context
     * @param isSub   whether this is a sub-workflow execution
     * @return WorkflowOutput containing results and metadata
     */
    @SuppressWarnings("unchecked")
    public WorkflowOutput invoke(Object inputs, Object session, ModelContext context, boolean isSub) {
        if (isSub) {
            return new WorkflowOutput(invokeSubWorkflow(inputs, session, context), WorkflowExecutionState.COMPLETED);
        }
        WorkflowSession workflowSession = createWorkflowSession(session, List.of(StreamMode.OUTPUT));

        try {
            executeCompiledGraph(inputs, workflowSession, context, Map.of());
            closeStreamEmitter(workflowSession);
            Object result;
            if (isStreaming) {
                result = workflowSession.streamWriterManager() != null
                        ? workflowSession.streamWriterManager().collectStreamOutput()
                        : List.of();
            } else {
                result = workflowSession.state() instanceof WorkflowStateCollection
                        ? ((WorkflowStateCollection) workflowSession.state()).getOutputs(endCompId)
                        : null;
            }
            return new WorkflowOutput(result, WorkflowExecutionState.COMPLETED);
        } catch (Exception e) {
            throw wrapWorkflowException(e);
        } finally {
            workflowSession.close();
        }
    }

    /**
     * Simplified invoke without sub flag.
     */
    public WorkflowOutput invoke(Object inputs, Object session, ModelContext context) {
        return invoke(inputs, session, context, false);
    }

    /**
     * Execute the workflow with streaming output.
     */
    public Iterator<Object> stream(Object inputs, Object session, ModelContext context, boolean isSub) {
        if (isSub) {
            return streamSubWorkflow(inputs, session, context);
        }
        WorkflowSession workflowSession = createWorkflowSession(session, List.of(StreamMode.OUTPUT));
        try {
            executeCompiledGraph(inputs, workflowSession, context, Map.of());
            closeStreamEmitter(workflowSession);
            List<Object> chunks = workflowSession.streamWriterManager() != null
                    ? workflowSession.streamWriterManager().collectStreamOutput()
                    : List.of();
            return chunks.iterator();
        } catch (Exception e) {
            throw wrapWorkflowException(e);
        } finally {
            workflowSession.close();
        }
    }

    public Iterator<Object> stream(Object inputs, Object session, ModelContext context) {
        return stream(inputs, session, context, false);
    }

    /**
     * Generate a Mermaid diagram of the workflow.
     */
    public String draw(String title, String outputFormat, Object expandSubgraph) {
        return "";
    }

    // ======================= Private Methods =======================

    @SuppressWarnings("unchecked")
    public Object invokeSubWorkflow(Object inputs, Object session, ModelContext context) {
        SubWorkflowSession subSession = createSubWorkflowSession(session);
        try {
            executeCompiledGraph(inputs != null ? inputs : Map.of(), subSession, context, Map.of());
            if (isStreaming) {
                return drainSubWorkflowStream(subSession);
            }
            NodeSession nodeSession = new NodeSession(subSession, endCompId);
            if (nodeSession.state() instanceof WorkflowStateCollection) {
                return ((WorkflowStateCollection) nodeSession.state()).getOutputs(endCompId);
            }
            return null;
        } catch (Exception e) {
            throw wrapWorkflowException(e);
        } finally {
            subSession.close();
        }
    }

    private Iterator<Object> streamSubWorkflow(Object inputs, Object session, ModelContext context) {
        Object results = invokeSubWorkflow(inputs, session, context);
        if (results instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> chunks = (List<Object>) list;
            return chunks.iterator();
        }
        return Collections.emptyIterator();
    }

    @SuppressWarnings("unchecked")
    private void executeCompiledGraph(Object inputs, BaseSession session, ModelContext context, Map<String, Object> config) {
        internal.autoCompleteAbilities();
        session.config().addWorkflowConfig(card.getId(), internal.getConfig());
        ExecutableGraph<?, ?> compiled = internal.compile(session, context);
        ExecutableGraph<Object, Object> typedCompiled = (ExecutableGraph<Object, Object>) compiled;
        typedCompiled.invoke(Map.of(
                Constant.INPUTS_KEY, inputs != null ? inputs : Map.of(),
                Constant.CONFIG_KEY, config != null ? config : Map.of()), session);
    }

    private RuntimeException wrapWorkflowException(Exception e) {
        if (e instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return ErrorHelper.buildError(StatusCode.WORKFLOW_EXECUTION_ERROR,
                "reason", e.getMessage(),
                "card", card.getId());
    }

    private WorkflowSession createWorkflowSession(Object session, List<StreamMode> streamModes) {
        internal.autoCompleteAbilities();

        BaseSession parent = null;
        String sessionId = null;
        Map<String, Object> envs = null;
        if (session instanceof WorkflowSessionApi sessionApi) {
            parent = sessionApi.getParent();
            sessionId = sessionApi.getSessionId();
            envs = sessionApi.getEnvs();
        } else if (session instanceof BaseSession baseSession) {
            parent = baseSession;
            sessionId = baseSession.sessionId();
        } else {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_EXECUTION_ERROR,
                    "reason", "unsupported workflow session type: " + session.getClass().getSimpleName(),
                    "card", card.getId());
        }

        WorkflowSession workflowSession = new WorkflowSession(
                card.getId(),
                parent,
                sessionId,
                InMemoryState.create(),
                session instanceof WorkflowSessionApi sessionApi ? sessionApi.getCallbackManager() : null);
        if (envs != null) {
            workflowSession.config().setEnvs(envs);
        }
        workflowSession.setStreamWriterManager(new StreamWriterManager(new StreamEmitter(), streamModes));
        workflowSession.setActorManager(buildActorManager(workflowSession, false));
        if (workflowSession.tracer() == null) {
            Tracer tracer = new Tracer();
            tracer.init(workflowSession.streamWriterManager(), workflowSession.callbackManager());
            workflowSession.setTracer(tracer);
        }
        return workflowSession;
    }

    private SubWorkflowSession createSubWorkflowSession(Object session) {
        internal.autoCompleteAbilities();
        BaseSession innerSession = extractInnerSession(session);
        String subNodeId = card.getId();
        String subNodeType = card.getId();
        if (innerSession instanceof NodeSession nodeSession) {
            subNodeId = nodeSession.nodeId();
            subNodeType = nodeSession.nodeType();
        }
        SubWorkflowSession subSession = new SubWorkflowSession(innerSession, subNodeId, subNodeType, card.getId());
        subSession.setActorManager(buildActorManager(subSession, true));
        subSession.config().addWorkflowConfig(card.getId(), internal.getConfig());
        return subSession;
    }

    private ActorManager buildActorManager(BaseSession session, boolean subGraph) {
        return new ActorManager(
                internal.getConfig().getSpec().getStreamEdges(),
                internal.getStreamActor(),
                subGraph,
                session,
                compId -> {
                    if (internal.getConfig().getSpec().getCompConfigs().containsKey(compId)) {
                        List<ComponentAbility> abilities =
                                internal.getConfig().getSpec().getCompConfigs().get(compId).getAbilities();
                        return abilities != null ? abilities : List.of();
                    }
                    return List.of();
                });
    }

    private void closeStreamEmitter(WorkflowSession workflowSession) {
        if (workflowSession.streamWriterManager() != null
                && !workflowSession.streamWriterManager().getStreamEmitter().isClosed()) {
            workflowSession.streamWriterManager().getStreamEmitter().close();
        }
    }

    private List<Object> drainSubWorkflowStream(SubWorkflowSession subSession) {
        List<Object> messages = new ArrayList<>();
        if (subSession.actorManager() == null || subSession.actorManager().subWorkflowStream() == null) {
            return messages;
        }
        while (true) {
            Object frame = subSession.actorManager().subWorkflowStream().poll();
            if (frame == null || StreamEmitter.END_FRAME.equals(frame)) {
                break;
            }
            messages.add(frame);
        }
        return messages;
    }

    private BaseSession extractInnerSession(Object session) {
        if (session instanceof BaseSession) {
            return (BaseSession) session;
        }
        if (session instanceof WorkflowSessionApi sessionApi) {
            if (sessionApi.getParent() != null) {
                return sessionApi.getParent();
            }
            WorkflowSession workflowSession = new WorkflowSession(card.getId(), null, sessionApi.getSessionId(),
                    InMemoryState.create(), sessionApi.getCallbackManager());
            if (sessionApi.getEnvs() != null) {
                workflowSession.config().setEnvs(sessionApi.getEnvs());
            }
            return workflowSession;
        }
        if (session instanceof NodeSessionApi) {
            try {
                java.lang.reflect.Field inner = session.getClass().getDeclaredField("inner");
                inner.setAccessible(true);
                return (BaseSession) inner.get(session);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot extract inner session from NodeSessionApi", e);
            }
        }
        throw new IllegalArgumentException("Unsupported session type: " + session.getClass().getSimpleName());
    }
}
