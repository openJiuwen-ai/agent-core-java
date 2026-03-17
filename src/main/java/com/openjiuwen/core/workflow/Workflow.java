/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.SubWorkflowSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main workflow class representing a directed graph of components.
 * Orchestrates execution of connected components, managing data flow and streaming.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.workflow.Workflow}.
 */
public class Workflow {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ExecutorService STREAM_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

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
        return invoke(inputs, session, context, isSub, false);
    }

    @SuppressWarnings("unchecked")
    public WorkflowOutput invoke(Object inputs, Object session, ModelContext context,
                                 boolean isSub, boolean skipInputsValidate) {
        if (isSub) {
            return new WorkflowOutput(invokeSubWorkflow(inputs, session, context), WorkflowExecutionState.COMPLETED);
        }
        validateSession(session);
        Object validatedInputs = validateInputs(inputs, skipInputsValidate);
        WorkflowSession workflowSession = createWorkflowSession(session, List.of(StreamMode.OUTPUT));

        try {
            traceWorkflowStart(workflowSession, validatedInputs);
            Object executionResult;
            try {
                executionResult = executeCompiledGraph(validatedInputs, workflowSession, context, null);
            } finally {
                traceWorkflowDone(workflowSession);
                closeStreamEmitter(workflowSession);
            }
            List<Object> outputChunks = collectOutputChunks(workflowSession);
            if (isInterrupted(executionResult, outputChunks)) {
                return new WorkflowOutput(outputChunks, WorkflowExecutionState.INPUT_REQUIRED);
            }
            Object result = isStreaming
                    ? outputChunks
                    : workflowSession.state() instanceof WorkflowStateCollection
                            ? ((WorkflowStateCollection) workflowSession.state()).getOutputs(endCompId)
                            : null;
            return new WorkflowOutput(result, WorkflowExecutionState.COMPLETED);
        } catch (Exception e) {
            throw wrapWorkflowException(e);
        } finally {
            resetGraphExecutionState();
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
    public Iterator<WorkflowChunk> stream(Object inputs, Object session, ModelContext context, boolean isSub) {
        return stream(inputs, session, context, List.of(StreamMode.OUTPUT), isSub, false);
    }

    public Iterator<WorkflowChunk> stream(Object inputs, Object session, ModelContext context,
                                   List<StreamMode> streamModes) {
        return stream(inputs, session, context,
                streamModes != null ? streamModes : List.of(StreamMode.OUTPUT), false, false);
    }

    public Iterator<WorkflowChunk> stream(Object inputs, Object session, ModelContext context,
                                   List<StreamMode> streamModes, boolean isSub, boolean skipInputsValidate) {
        if (isSub) {
            return streamSubWorkflow(inputs, session, context);
        }
        validateSession(session);
        Object validatedInputs = validateInputs(inputs, skipInputsValidate);
        WorkflowSession workflowSession = createWorkflowSession(session, streamModes);

        @SuppressWarnings("unchecked")
        Iterator<WorkflowChunk> streamIterator = workflowSession.streamWriterManager() != null
                ? (Iterator<WorkflowChunk>) (Iterator<?>) workflowSession.streamWriterManager().streamIterator()
                : Collections.emptyIterator();
        AtomicReference<Object> finalPayload = new AtomicReference<>();
        AtomicReference<RuntimeException> executionError = new AtomicReference<>();

        CompletableFuture<Void> executionFuture = CompletableFuture.runAsync(() -> {
            try {
                traceWorkflowStart(workflowSession, validatedInputs);
                executeCompiledGraph(validatedInputs, workflowSession, context, null);
                finalPayload.set(resolveFinalStreamPayload(workflowSession));
            } catch (Exception e) {
                executionError.set(wrapWorkflowException(e));
            } finally {
                try {
                    traceWorkflowDone(workflowSession);
                } finally {
                    closeStreamEmitter(workflowSession);
                    resetGraphExecutionState();
                    workflowSession.close();
                }
            }
        }, STREAM_EXECUTOR);

        return new Iterator<WorkflowChunk>() {
            private boolean finalChunkEmitted = false;

            @Override
            public boolean hasNext() {
                if (streamIterator.hasNext()) {
                    return true;
                }
                waitForExecution();
                return !finalChunkEmitted && finalPayload.get() != null;
            }

            @Override
            public WorkflowChunk next() {
                if (streamIterator.hasNext()) {
                    return streamIterator.next();
                }
                waitForExecution();
                if (!finalChunkEmitted && finalPayload.get() != null) {
                    finalChunkEmitted = true;
                    return new OutputSchema("workflow_final", 0, finalPayload.get());
                }
                throw new java.util.NoSuchElementException();
            }

            private void waitForExecution() {
                try {
                    executionFuture.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw wrapWorkflowException(e);
                } catch (ExecutionException e) {
                    RuntimeException error = executionError.get();
                    if (error != null) {
                        throw error;
                    }
                    throw wrapWorkflowException(new Exception(e.getCause()));
                }
                RuntimeException error = executionError.get();
                if (error != null) {
                    throw error;
                }
            }
        };
    }

    public Iterator<WorkflowChunk> stream(Object inputs, Object session, ModelContext context) {
        return stream(inputs, session, context, List.of(StreamMode.OUTPUT), false, false);
    }

    /**
     * Generate a Mermaid diagram of the workflow.
     *
     * @param title           diagram title
     * @param outputFormat    "mermaid", "png", or "svg"
     * @param expandSubgraph  subgraph expansion level
     * @return Mermaid syntax string for "mermaid" format; empty string for "png"/"svg" (use drawBytes instead)
     */
    public String draw(String title, String outputFormat, Object expandSubgraph) {
        return draw(title, outputFormat, expandSubgraph, false);
    }

    public String draw(String title, String outputFormat, Object expandSubgraph, boolean enableAnimation) {
        if ("png".equalsIgnoreCase(outputFormat)) {
            throw new UnsupportedOperationException("Use drawBytes() for png output");
        }
        if ("svg".equalsIgnoreCase(outputFormat)) {
            throw new UnsupportedOperationException("Use drawBytes() for svg output");
        }
        return internal.toMermaid(title == null ? "" : title,
                normalizeExpandSubgraph(expandSubgraph),
                enableAnimation);
    }

    /**
     * Generate a binary diagram of the workflow (PNG or SVG).
     *
     * @param title          diagram title
     * @param outputFormat   "png" or "svg"
     * @param expandSubgraph subgraph expansion level
     * @return image binary data
     */
    public byte[] drawBytes(String title, String outputFormat, Object expandSubgraph) {
        if ("png".equalsIgnoreCase(outputFormat)) {
            return internal.toMermaidPng(title == null ? "" : title, normalizeExpandSubgraph(expandSubgraph));
        }
        if ("svg".equalsIgnoreCase(outputFormat)) {
            return internal.toMermaidSvg(title == null ? "" : title, normalizeExpandSubgraph(expandSubgraph));
        }
        throw new IllegalArgumentException("drawBytes only supports 'png' or 'svg' format, got: " + outputFormat);
    }

    public HasDrawable getInternalDrawable() {
        return internal;
    }

    // ======================= Private Methods =======================

    @SuppressWarnings("unchecked")
    public Object invokeSubWorkflow(Object inputs, Object session, ModelContext context) {
        return invokeSubWorkflow(inputs, session, context, null);
    }

    @SuppressWarnings("unchecked")
    public Object invokeSubWorkflow(Object inputs, Object session, ModelContext context, Object config) {
        SubWorkflowSession subSession = createSubWorkflowSession(session);
        try {
            executeCompiledGraph(inputs != null ? inputs : Map.of(), subSession, context, config);
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
            resetGraphExecutionState();
            subSession.close();
        }
    }

    public Iterator<WorkflowChunk> streamSubWorkflow(Object inputs, Object session, ModelContext context) {
        return streamSubWorkflow(inputs, session, context, null);
    }

    @SuppressWarnings("unchecked")
    public Iterator<WorkflowChunk> streamSubWorkflow(Object inputs, Object session, ModelContext context, Object config) {
        Object results = invokeSubWorkflow(inputs, session, context, config);
        if (results instanceof List<?> list) {
            List<WorkflowChunk> chunks = (List<WorkflowChunk>) (List<?>) list;
            return chunks.iterator();
        }
        return Collections.emptyIterator();
    }

    @SuppressWarnings("unchecked")
    private Object executeCompiledGraph(Object inputs, BaseSession session, ModelContext context, Object config) {
        internal.autoCompleteAbilities();
        session.config().addWorkflowConfig(card.getId(), internal.getConfig());
        ExecutableGraph<?, ?> compiled = internal.compile(session, context);
        ExecutableGraph<Object, Object> typedCompiled = (ExecutableGraph<Object, Object>) compiled;
        Map<String, Object> graphInputs = new java.util.HashMap<>();
        graphInputs.put(Constant.INPUTS_KEY, inputs != null ? inputs : Map.of());
        graphInputs.put(Constant.CONFIG_KEY, config);
        return typedCompiled.invoke(graphInputs, session);
    }

    private RuntimeException wrapWorkflowException(Exception e) {
        if (e instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return ErrorHelper.buildError(StatusCode.WORKFLOW_EXECUTION_ERROR,
                "reason", e.getMessage(),
                "workflow", card.str());
    }

    private WorkflowSession createWorkflowSession(Object session, List<StreamMode> streamModes) {
        internal.autoCompleteAbilities();

        BaseSession parent = null;
        String sessionId = null;
        Map<String, Object> envs = null;
        WorkflowSession existingWorkflowSession = null;
        if (session instanceof WorkflowSessionApi sessionApi) {
            sessionApi.setWorkflowCard(card);
            parent = sessionApi.getParent();
            sessionId = sessionApi.getSessionId();
            envs = sessionApi.getEnvs();
        } else if (session instanceof WorkflowSession workflowSession) {
            existingWorkflowSession = workflowSession;
            parent = workflowSession.parent();
            sessionId = workflowSession.sessionId();
            envs = workflowSession.config() != null ? workflowSession.config().getEnvs() : null;
        } else if (session instanceof BaseSession baseSession) {
            parent = baseSession;
            sessionId = baseSession.sessionId();
        } else {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_EXECUTION_ERROR,
                    "reason", "unsupported workflow session type: " + session.getClass().getSimpleName(),
                    "card", card.getId());
        }

        WorkflowSession workflowSession = existingWorkflowSession != null
                ? existingWorkflowSession
                : new WorkflowSession(
                        card.getId(),
                        parent,
                        sessionId,
                        InMemoryState.create(),
                        session instanceof WorkflowSessionApi sessionApi ? sessionApi.getCallbackManager() : null);
        workflowSession.setWorkflowId(card.getId());
        if (envs != null) {
            workflowSession.config().setEnvs(envs);
        }
        workflowSession.config().addWorkflowConfig(card.getId(), internal.getConfig());
        if (workflowSession.streamWriterManager() == null) {
            workflowSession.setStreamWriterManager(new StreamWriterManager(new StreamEmitter(), streamModes));
        }
        workflowSession.setActorManager(buildActorManager(workflowSession, false));
        if (workflowSession.tracer() == null && (streamModes == null || streamModes.contains(StreamMode.TRACE))) {
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

    private List<Object> collectOutputChunks(WorkflowSession workflowSession) {
        if (workflowSession.streamWriterManager() == null) {
            return List.of();
        }
        return workflowSession.streamWriterManager().collectStreamOutput();
    }

    private boolean isInterrupted(Object executionResult, List<Object> outputChunks) {
        if (executionResult instanceof Map<?, ?> resultMap
                && resultMap.containsKey(PregelConstants.TASK_STATUS_INTERRUPT)) {
            return true;
        }
        for (Object chunk : outputChunks) {
            if (chunk instanceof OutputSchema outputSchema
                    && Constant.INTERACTION.equals(outputSchema.getType())) {
                return true;
            }
        }
        return false;
    }

    private void resetGraphExecutionState() {
        internal.reset();
    }

    private void traceWorkflowStart(WorkflowSession workflowSession, Object inputs) {
        if (workflowSession.tracer() == null) {
            return;
        }
        TracerWorkflowUtils.traceWorkflowStart(workflowSession, inputs);
    }

    private void traceWorkflowDone(WorkflowSession workflowSession) {
        if (workflowSession.tracer() == null
                || !(workflowSession.state() instanceof WorkflowStateCollection stateCollection)) {
            return;
        }
        Object outputs = stateCollection.getOutputs(endCompId);
        TracerWorkflowUtils.traceWorkflowDone(workflowSession, outputs);
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

    private int normalizeExpandSubgraph(Object expandSubgraph) {
        if (expandSubgraph instanceof Boolean expand) {
            return expand ? -1 : 0;
        }
        if (expandSubgraph instanceof Number depth) {
            return depth.intValue();
        }
        return 0;
    }

    private void validateSession(Object session) {
        if (session != null) {
            return;
        }
        throw ErrorHelper.buildError(StatusCode.WORKFLOW_EXECUTE_SESSION_INVALID,
                "reason", "session is required for workflow execution",
                "workflow", card.str());
    }

    private Object validateInputs(Object inputs, boolean skipInputsValidate) {
        Object schema = card.getInputParams();
        if (schema == null || inputs instanceof InteractiveInput) {
            return inputs;
        }

        try {
            Map<String, Object> schemaMap = resolveInputSchema(schema);
            Map<String, Object> inputMap = convertInputsToMap(inputs);
            if (inputMap == null) {
                return inputs;
            }
            return SchemaUtils.formatWithSchema(inputMap, schemaMap, skipInputsValidate);
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_EXECUTE_INPUT_INVALID,
                    "inputs", String.valueOf(inputs),
                    "reason", "input validation failed against schema: " + e.getMessage(),
                    "workflow", card.str());
        }
    }

    private Map<String, Object> resolveInputSchema(Object schema) {
        if (schema instanceof Map<?, ?> schemaMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedSchema = (Map<String, Object>) schemaMap;
            return typedSchema;
        }
        if (schema instanceof Class<?> clazz) {
            return SchemaUtils.getSchemaDict(clazz);
        }
        return Map.of();
    }

    private Map<String, Object> convertInputsToMap(Object inputs) {
        if (inputs == null) {
            return null;
        }
        if (inputs instanceof Map<?, ?> inputMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) inputMap;
            return typedMap;
        }
        return OBJECT_MAPPER.convertValue(inputs, new TypeReference<Map<String, Object>>() {
        });
    }

    private Object resolveFinalStreamPayload(WorkflowSession workflowSession) {
        if (!(workflowSession.state() instanceof WorkflowStateCollection stateCollection)) {
            return null;
        }
        return stateCollection.getOutputs(endCompId);
    }
}
