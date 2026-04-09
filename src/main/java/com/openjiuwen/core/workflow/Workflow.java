/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.SubWorkflowSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.AsyncStreamQueue;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.internal.LegacyWorkflowComponentSupport;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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
     * Compatibility overload for translated tests that omit outputs schema.
     */
    public Workflow setStartComp(String startCompId, ComponentComposable component, Object inputsSchema) {
        return setStartComp(startCompId, component, inputsSchema, null);
    }

    /**
     * Compatibility overload for translated tests that still use legacy POJO nodes.
     */
    public Workflow setStartComp(String startCompId, Object component, Object inputsSchema) {
        return setStartComp(startCompId, LegacyWorkflowComponentSupport.adapt(component), inputsSchema, null);
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
     * Compatibility overload for translated tests that still use legacy POJO nodes.
     */
    public Workflow addWorkflowComp(String compId, Object workflowComp,
                                    Object inputsSchema, Object outputsSchema) {
        internal.addWorkflowComp(compId, LegacyWorkflowComponentSupport.adapt(workflowComp), null,
                inputsSchema, outputsSchema, null, null, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that place wait_for_all after schemas.
     */
    public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp,
                                    Object inputsSchema, Boolean waitForAll) {
        internal.addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, null, null, null, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that place wait_for_all after schemas.
     */
    public Workflow addWorkflowComp(String compId, Object workflowComp,
                                    Object inputsSchema, Boolean waitForAll) {
        internal.addWorkflowComp(compId, LegacyWorkflowComponentSupport.adapt(workflowComp), waitForAll,
                inputsSchema, null, null, null, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that place wait_for_all after schemas.
     */
    public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp,
                                    Object inputsSchema, Object outputsSchema, Boolean waitForAll) {
        internal.addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, outputsSchema, null, null, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that place wait_for_all after schemas.
     */
    public Workflow addWorkflowComp(String compId, Object workflowComp,
                                    Object inputsSchema, Object outputsSchema, Boolean waitForAll) {
        ComponentComposable adapted = LegacyWorkflowComponentSupport.adapt(workflowComp);
        internal.addWorkflowComp(compId, adapted, waitForAll, inputsSchema, outputsSchema, null, null, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that place wait_for_all after both schemas
     * while still passing stream schemas.
     */
    public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp,
                                    Object inputsSchema, Object outputsSchema, Boolean waitForAll,
                                    Object streamInputsSchema, Object streamOutputsSchema) {
        internal.addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, outputsSchema,
                streamInputsSchema, streamOutputsSchema, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that place wait_for_all after both schemas
     * while still passing stream schemas.
     */
    public Workflow addWorkflowComp(String compId, Object workflowComp,
                                    Object inputsSchema, Object outputsSchema, Boolean waitForAll,
                                    Object streamInputsSchema, Object streamOutputsSchema) {
        ComponentComposable adapted = LegacyWorkflowComponentSupport.adapt(workflowComp);
        internal.addWorkflowComp(compId, adapted, waitForAll, inputsSchema, outputsSchema,
                streamInputsSchema, streamOutputsSchema, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that still pass explicit abilities.
     */
    public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp,
                                    Object inputsSchema, Boolean waitForAll,
                                    List<ComponentAbility> compAbility) {
        internal.addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, null, null, null, compAbility);
        return this;
    }

    /**
     * Compatibility overload for translated tests that still pass explicit abilities.
     */
    public Workflow addWorkflowComp(String compId, Object workflowComp,
                                    Object inputsSchema, Boolean waitForAll,
                                    List<ComponentAbility> compAbility) {
        ComponentComposable adapted = LegacyWorkflowComponentSupport.adapt(workflowComp);
        internal.addWorkflowComp(compId, adapted, waitForAll, inputsSchema, null, null, null, compAbility);
        return this;
    }

    /**
     * Compatibility overload for translated tests that still pass explicit abilities.
     */
    public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp,
                                    Object inputsSchema, Object outputsSchema, Boolean waitForAll,
                                    List<ComponentAbility> compAbility) {
        internal.addWorkflowComp(compId, workflowComp, waitForAll, inputsSchema, outputsSchema, null, null, compAbility);
        return this;
    }

    /**
     * Compatibility overload for translated tests that still pass explicit abilities.
     */
    public Workflow addWorkflowComp(String compId, Object workflowComp,
                                    Object inputsSchema, Object outputsSchema, Boolean waitForAll,
                                    List<ComponentAbility> compAbility) {
        ComponentComposable adapted = LegacyWorkflowComponentSupport.adapt(workflowComp);
        internal.addWorkflowComp(compId, adapted, waitForAll, inputsSchema, outputsSchema, null, null, compAbility);
        return this;
    }

    /**
     * Compatibility overload for translated tests that omit outputs schema.
     */
    public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp, Object inputsSchema) {
        return addWorkflowComp(compId, workflowComp, inputsSchema, null);
    }

    /**
     * Compatibility overload for translated tests that still use legacy POJO nodes.
     */
    public Workflow addWorkflowComp(String compId, Object workflowComp, Object inputsSchema) {
        internal.addWorkflowComp(compId, LegacyWorkflowComponentSupport.adapt(workflowComp), null,
                inputsSchema, null, null, null, null);
        return this;
    }

    /**
     * Minimal addWorkflowComp with just ID and component.
     */
    public Workflow addWorkflowComp(String compId, ComponentComposable workflowComp) {
        internal.addWorkflowComp(compId, workflowComp, null, null, null, null, null, null);
        return this;
    }

    /**
     * Compatibility overload for translated tests that still use legacy POJO nodes.
     */
    public Workflow addWorkflowComp(String compId, Object workflowComp) {
        internal.addWorkflowComp(compId, LegacyWorkflowComponentSupport.adapt(workflowComp), null,
                null, null, null, null, null);
        return this;
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
     * Compatibility overload for translated tests that still use legacy POJO nodes
     * with explicit stream schemas and response mode.
     */
    public Workflow setEndComp(String endCompId, Object component,
                               Object inputsSchema, Object outputsSchema,
                               Object streamInputsSchema, Object streamOutputsSchema,
                               String responseMode) {
        return setEndComp(endCompId, LegacyWorkflowComponentSupport.adapt(component),
                inputsSchema, outputsSchema, streamInputsSchema, streamOutputsSchema, responseMode);
    }

    /**
     * Compatibility overload for translated tests that still pass {@code responseMode}
     * before the input schema.
     */
    public Workflow setEndComp(String endCompId, ComponentComposable component,
                               String responseMode, Object inputsSchema) {
        return setEndComp(endCompId, component, inputsSchema, null, null, null, responseMode);
    }

    /**
     * Compatibility overload for translated tests that omit outputs schema.
     */
    public Workflow setEndComp(String endCompId, ComponentComposable component, Object inputsSchema) {
        return setEndComp(endCompId, component, inputsSchema, null);
    }

    /**
     * Compatibility overload for translated tests that still pass {@code responseMode}
     * before the input schema.
     */
    public Workflow setEndComp(String endCompId, Object component,
                               String responseMode, Object inputsSchema) {
        return setEndComp(endCompId, LegacyWorkflowComponentSupport.adapt(component), responseMode, inputsSchema);
    }

    /**
     * Compatibility overload for translated tests that still use legacy POJO nodes.
     */
    public Workflow setEndComp(String endCompId, Object component, Object inputsSchema) {
        return setEndComp(endCompId, LegacyWorkflowComponentSupport.adapt(component), inputsSchema, null);
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
     * Compatibility overload so translated tests can pass lambdas without
     * explicit casts to {@code Object}.
     */
    public Workflow addConditionalConnection(String srcCompId, Function<Object, Object> router) {
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
        long executeTimeoutMs = resolveTimeoutMillis(workflowSession, SessionConstants.WORKFLOW_EXECUTE_TIMEOUT);

        try {
            return executeWithWorkflowTimeout(() -> {
                try {
                    traceWorkflowStart(workflowSession, validatedInputs);
                    Object executionResult;
                    try {
                        executionResult = executeCompiledGraph(validatedInputs, workflowSession, context, null);
                        finishStreamActorsAfterGraph(workflowSession, executionResult);
                    } finally {
                        traceWorkflowDone(workflowSession);
                        closeStreamEmitter(workflowSession);
                    }
                    List<Object> outputChunks = collectOutputChunks(workflowSession);
                    if (isInterrupted(executionResult, outputChunks)) {
                        return new WorkflowOutput(
                                resolveInterruptedOutputChunks(executionResult, outputChunks),
                                WorkflowExecutionState.INPUT_REQUIRED);
                    }
                    Object result = isStreaming
                            ? outputChunks
                            : workflowSession.state() instanceof WorkflowStateCollection
                                    ? ((WorkflowStateCollection) workflowSession.state()).getOutputs(endCompId)
                                    : null;
                    return new WorkflowOutput(result, WorkflowExecutionState.COMPLETED);
                } catch (Exception e) {
                    throw wrapWorkflowException(e);
                }
            }, executeTimeoutMs);
        } finally {
            closeStreamEmitter(workflowSession);
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
        long firstFrameTimeoutMs = resolveTimeoutMillis(
                workflowSession, SessionConstants.WORKFLOW_STREAM_FIRST_FRAME_TIMEOUT);
        long frameTimeoutMs = resolveTimeoutMillis(
                workflowSession, SessionConstants.WORKFLOW_STREAM_FRAME_TIMEOUT);
        long executeTimeoutMs = resolveTimeoutMillis(
                workflowSession, SessionConstants.WORKFLOW_EXECUTE_TIMEOUT);
        long executionDeadlineNanos = executeTimeoutMs > 0
                ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(executeTimeoutMs)
                : -1L;
        String executeTimeoutText = formatTimeoutSeconds(executeTimeoutMs);
        AtomicReference<Object> finalPayload = new AtomicReference<>();
        AtomicReference<RuntimeException> executionError = new AtomicReference<>();
        AtomicBoolean executionTimedOut = new AtomicBoolean(false);
        AtomicBoolean terminated = new AtomicBoolean(false);
        AsyncStreamQueue streamQueue = workflowSession.streamWriterManager() != null
                ? workflowSession.streamWriterManager().getStreamEmitter().getStreamQueue()
                : null;

        CompletableFuture<Void> executionFuture = CompletableFuture.runAsync(() -> {
            try {
                traceWorkflowStart(workflowSession, validatedInputs);
                Object graphResult = executeCompiledGraph(validatedInputs, workflowSession, context, null);
                finishStreamActorsAfterGraph(workflowSession, graphResult);
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
            private boolean firstFrame = true;
            private boolean done = false;
            private boolean streamClosed = false;
            private WorkflowChunk nextChunk;

            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (nextChunk != null) {
                    return true;
                }
                nextChunk = fetchNextChunk();
                if (nextChunk == null) {
                    done = true;
                    return false;
                }
                return true;
            }

            @Override
            public WorkflowChunk next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                WorkflowChunk current = nextChunk;
                nextChunk = null;
                return current;
            }

            private WorkflowChunk fetchNextChunk() {
                if (streamQueue == null) {
                    waitForExecution();
                    if (!finalChunkEmitted && finalPayload.get() != null) {
                        finalChunkEmitted = true;
                        return new OutputSchema("workflow_final", 0, finalPayload.get());
                    }
                    return null;
                }
                if (streamClosed) {
                    waitForExecution();
                    if (!finalChunkEmitted && finalPayload.get() != null) {
                        finalChunkEmitted = true;
                        return new OutputSchema("workflow_final", 0, finalPayload.get());
                    }
                    return null;
                }

                Object data = receiveNextChunk();
                if (StreamEmitter.END_FRAME.equals(data)) {
                    closeStreamQueue();
                    waitForExecution();
                    if (!finalChunkEmitted && finalPayload.get() != null) {
                        finalChunkEmitted = true;
                        return new OutputSchema("workflow_final", 0, finalPayload.get());
                    }
                    return null;
                }
                Loggers.SESSION.debug("Stream data received, dataType={}", data.getClass().getSimpleName());
                return (WorkflowChunk) data;
            }

            private Object receiveNextChunk() {
                boolean currentFirstFrame = firstFrame;
                long configuredTimeoutMs = currentFirstFrame ? firstFrameTimeoutMs : frameTimeoutMs;
                long receiveTimeoutMs = resolveReceiveTimeoutMillis(configuredTimeoutMs, executionDeadlineNanos);
                Object data = streamQueue.receive(receiveTimeoutMs);
                firstFrame = false;
                if (data != null) {
                    return data;
                }
                RuntimeException timeoutError = buildReceiveTimeoutError(
                        currentFirstFrame, configuredTimeoutMs, receiveTimeoutMs);
                terminateStream(timeoutError);
                throw timeoutError;
            }

            private RuntimeException buildReceiveTimeoutError(
                    boolean currentFirstFrame, long configuredTimeoutMs, long receiveTimeoutMs) {
                boolean deadlineReached = isExecutionDeadlineReached(executionDeadlineNanos);
                if (currentFirstFrame) {
                    if (configuredTimeoutMs > 0) {
                        return ErrorHelper.buildError(
                                StatusCode.STREAM_OUTPUT_FIRST_CHUNK_INTERVAL_TIMEOUT,
                                "timeout", formatTimeoutSeconds(configuredTimeoutMs),
                                "reason", "");
                    }
                    if (deadlineReached) {
                        executionTimedOut.set(true);
                        return buildWorkflowExecutionTimeout(executeTimeoutText);
                    }
                    return ErrorHelper.buildError(
                            StatusCode.STREAM_OUTPUT_FIRST_CHUNK_INTERVAL_TIMEOUT,
                            "timeout", formatTimeoutSeconds(receiveTimeoutMs),
                            "reason", "");
                }

                if (configuredTimeoutMs > 0
                        && (executeTimeoutMs <= 0 || executeTimeoutMs > configuredTimeoutMs)) {
                    return ErrorHelper.buildError(
                            StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT,
                            "timeout", formatTimeoutSeconds(configuredTimeoutMs),
                            "reason", "");
                }
                if (deadlineReached) {
                    executionTimedOut.set(true);
                    return buildWorkflowExecutionTimeout(executeTimeoutText);
                }
                if (configuredTimeoutMs > 0) {
                    return ErrorHelper.buildError(
                            StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT,
                            "timeout", formatTimeoutSeconds(configuredTimeoutMs),
                            "reason", "");
                }
                executionTimedOut.set(true);
                return buildWorkflowExecutionTimeout(executeTimeoutText);
            }

            private void terminateStream(RuntimeException terminalError) {
                if (!terminated.compareAndSet(false, true)) {
                    return;
                }
                if (terminalError instanceof BaseError baseError
                        && baseError.getStatus() == StatusCode.WORKFLOW_EXECUTION_TIMEOUT) {
                    executionTimedOut.set(true);
                }
                executionFuture.cancel(true);
                closeStreamEmitter(workflowSession);
                workflowSession.close();
            }

            private void waitForExecution() {
                try {
                    executionFuture.get();
                } catch (CancellationException e) {
                    RuntimeException error = executionError.get();
                    if (error != null) {
                        throw error;
                    }
                    if (executionTimedOut.get()) {
                        throw buildWorkflowExecutionTimeout(executeTimeoutText);
                    }
                    throw wrapWorkflowException(new Exception(e));
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

            private void closeStreamQueue() {
                if (streamClosed) {
                    return;
                }
                streamClosed = true;
                streamQueue.close();
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
    public String draw() {
        return draw("", "mermaid", false, false);
    }

    public String draw(String title) {
        return draw(title, "mermaid", false, false);
    }

    public String draw(Object title, String outputFormat, Object expandSubgraph) {
        return draw(title, outputFormat, expandSubgraph, false);
    }

    public String draw(Object title, String outputFormat, Object expandSubgraph, Object enableAnimation) {
        if ("png".equalsIgnoreCase(outputFormat)) {
            throw new UnsupportedOperationException("Use drawBytes() for png output");
        }
        if ("svg".equalsIgnoreCase(outputFormat)) {
            throw new UnsupportedOperationException("Use drawBytes() for svg output");
        }
        return internal.toMermaid(normalizeTitle(title),
                normalizeExpandSubgraph(expandSubgraph),
                normalizeEnableAnimation(enableAnimation));
    }

    /**
     * Generate a binary diagram of the workflow (PNG or SVG).
     *
     * @param title          diagram title
     * @param outputFormat   "png" or "svg"
     * @param expandSubgraph subgraph expansion level
     * @return image binary data
     */
    public byte[] drawBytes(Object title, String outputFormat, Object expandSubgraph) {
        return drawBytes(normalizeTitle(title), outputFormat, expandSubgraph);
    }

    public byte[] drawBytes(String title, String outputFormat, Object expandSubgraph) {
        if ("png".equalsIgnoreCase(outputFormat)) {
            return internal.toMermaidPng(normalizeTitle(title), normalizeExpandSubgraph(expandSubgraph));
        }
        if ("svg".equalsIgnoreCase(outputFormat)) {
            return internal.toMermaidSvg(normalizeTitle(title), normalizeExpandSubgraph(expandSubgraph));
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
            Object graphResult = executeCompiledGraph(inputs != null ? inputs : Map.of(), subSession, context, config);
            finishStreamActorsAfterGraph(subSession, graphResult);
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
        java.util.Map<String, Object> graphInputs = new java.util.HashMap<>();
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

    /**
     * After graph execution, stream actors for consumers (e.g. End TRANSFORM) may still be blocked
     * waiting for producers that will not run until user input is returned. When the graph yielded
     * {@link PregelConstants#TASK_STATUS_INTERRUPT}, shut actors down instead of awaiting completion.
     */
    private void finishStreamActorsAfterGraph(BaseSession session, Object executionResult) {
        if (session == null || !(session.actorManager() instanceof ActorManager actorManager)) {
            return;
        }
        if (graphYieldedInterrupt(executionResult)) {
            actorManager.shutdown();
        } else {
            actorManager.awaitCompletion();
        }
    }

    private static boolean graphYieldedInterrupt(Object executionResult) {
        return executionResult instanceof Map<?, ?> resultMap
                && resultMap.containsKey(PregelConstants.TASK_STATUS_INTERRUPT);
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

    private Double resolveTimeoutSeconds(WorkflowSession workflowSession, String configKey) {
        if (workflowSession == null || workflowSession.config() == null || configKey == null) {
            return null;
        }
        Object raw = workflowSession.config().getEnv(configKey);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private long resolveTimeoutMillis(WorkflowSession workflowSession, String configKey) {
        Double seconds = resolveTimeoutSeconds(workflowSession, configKey);
        if (seconds == null || seconds < 0) {
            return -1L;
        }
        return Math.round(seconds * 1000);
    }

    private long resolveReceiveTimeoutMillis(long configuredTimeoutMs, long executionDeadlineNanos) {
        long remainingExecutionMs = remainingExecutionMillis(executionDeadlineNanos);
        if (remainingExecutionMs < 0) {
            return configuredTimeoutMs;
        }
        long cappedExecutionMs = Math.max(1L, remainingExecutionMs);
        if (configuredTimeoutMs <= 0) {
            return cappedExecutionMs;
        }
        return Math.max(1L, Math.min(configuredTimeoutMs, cappedExecutionMs));
    }

    private long remainingExecutionMillis(long executionDeadlineNanos) {
        if (executionDeadlineNanos < 0) {
            return -1L;
        }
        long remainingNanos = executionDeadlineNanos - System.nanoTime();
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, remainingNanos));
    }

    private boolean isExecutionDeadlineReached(long executionDeadlineNanos) {
        return executionDeadlineNanos >= 0 && System.nanoTime() >= executionDeadlineNanos;
    }

    private String formatTimeoutSeconds(long timeoutMs) {
        if (timeoutMs < 0) {
            return String.valueOf(timeoutMs);
        }
        return BigDecimal.valueOf(timeoutMs)
                .movePointLeft(3)
                .stripTrailingZeros()
                .toPlainString();
    }

    private RuntimeException buildWorkflowExecutionTimeout(String timeoutText) {
        return ErrorHelper.buildError(
                StatusCode.WORKFLOW_EXECUTION_TIMEOUT,
                "timeout", timeoutText,
                "workflow", card.str());
    }

    private <T> T executeWithWorkflowTimeout(Callable<T> task, long timeoutMs) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, STREAM_EXECUTOR);
        try {
            if (timeoutMs > 0) {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (TimeoutException e) {
            future.cancel(true);
            throw buildWorkflowExecutionTimeout(formatTimeoutSeconds(timeoutMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw wrapWorkflowException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Exception exception) {
                throw wrapWorkflowException(exception);
            }
            throw wrapWorkflowException(new Exception(cause));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> resolveInterruptedOutputChunks(Object executionResult, List<Object> outputChunks) {
        if (outputChunks != null && !outputChunks.isEmpty()) {
            return outputChunks;
        }
        if (!(executionResult instanceof Map<?, ?> resultMap)) {
            return outputChunks;
        }

        Object interrupt = resultMap.get(PregelConstants.TASK_STATUS_INTERRUPT);
        if (interrupt instanceof Interrupt graphInterrupt) {
            interrupt = graphInterrupt.getValue();
        }

        if (interrupt instanceof OutputSchema outputSchema) {
            return List.of(outputSchema);
        }
        if (interrupt instanceof Map<?, ?> interruptMap
                && interruptMap.containsKey("type")
                && interruptMap.containsKey("payload")) {
            return List.of(OutputSchema.fromMap((Map<String, Object>) interruptMap));
        }
        if (interrupt instanceof List<?> interruptList) {
            List<Object> recovered = new ArrayList<>(interruptList.size());
            for (Object item : interruptList) {
                if (item instanceof OutputSchema outputSchema) {
                    recovered.add(outputSchema);
                } else if (item instanceof Map<?, ?> itemMap
                        && itemMap.containsKey("type")
                        && itemMap.containsKey("payload")) {
                    recovered.add(OutputSchema.fromMap((Map<String, Object>) itemMap));
                }
            }
            if (!recovered.isEmpty()) {
                return recovered;
            }
        }
        return outputChunks;
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

    private String normalizeTitle(Object title) {
        if (title == null) {
            return "";
        }
        if (title instanceof String titleValue) {
            return titleValue;
        }
        throw ErrorHelper.buildError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                "reason", "'title' type is not str");
    }

    private boolean normalizeEnableAnimation(Object enableAnimation) {
        if (enableAnimation instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw ErrorHelper.buildError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                "reason", "'enable_animation' type is not bool");
    }

    private int normalizeExpandSubgraph(Object expandSubgraph) {
        if (expandSubgraph == null) {
            return 0;
        }
        if (expandSubgraph instanceof Boolean expand) {
            return expand ? -1 : 0;
        }
        if (expandSubgraph instanceof Number depth) {
            int value = depth.intValue();
            if (value >= 0) {
                return value;
            }
            throw ErrorHelper.buildError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    "reason", "'expand_subgraph' type is not bool");
        }
        throw ErrorHelper.buildError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                "reason", "'expand_subgraph' type is not bool");
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
            validateTopLevelInputSchema(schemaMap);
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

    private void validateTopLevelInputSchema(Map<String, Object> schemaMap) {
        if (schemaMap == null || schemaMap.isEmpty()) {
            return;
        }
        Object type = schemaMap.get("type");
        if (type == null) {
            return;
        }
        if (!"object".equals(type)) {
            throw new IllegalArgumentException("'" + type + "' is not valid under any of the given schemas");
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
