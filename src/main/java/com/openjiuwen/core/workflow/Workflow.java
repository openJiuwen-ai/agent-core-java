/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;
import com.openjiuwen.core.common.VirtualThreadSupport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.CompiledGraph;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.runner.callback.CallbackDecorators;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.WorkflowEvents;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.AsyncStreamQueue;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.internal.LegacyWorkflowComponentSupport;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Main workflow class representing a directed graph of components.
 * Orchestrates execution of connected components, managing data flow and streaming.
 * <p>
 * Mirrors Python's {@code Workflow} in
 * {@code openjiuwen/core/workflow/workflow.py}.
 */
public class Workflow {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ExecutorService STREAM_EXECUTOR = VirtualThreadSupport.newThreadPerTaskExecutor();
    private static final long SUB_WORKFLOW_STREAM_DRAIN_TIMEOUT_MS = 1000L;
    private static final String WORKFLOW_INTERACTION_OUTPUTS_KEY = "__workflow_interaction_outputs__";
    private static DecoratorFramework callbackFramework;

    private final WorkflowCard card;
    private final BaseWorkflow internal;
    private String endCompId = "";
    private boolean isStreaming = false;

    public Workflow(WorkflowCard card) {
        this(card, null);
    }

    public Workflow(int workflowMaxNestingDepth) {
        this(null, workflowMaxNestingDepth);
    }

    private Workflow(WorkflowCard card, Integer workflowMaxNestingDepth) {
        this.card = card != null ? card : defaultWorkflowCard();
        WorkflowConfig workflowConfig = new WorkflowConfig(this.card);
        if (workflowMaxNestingDepth != null) {
            workflowConfig.setWorkflowMaxNestingDepth(workflowMaxNestingDepth);
        }
        this.internal = new BaseWorkflow(workflowConfig, new PregelGraph());
    }

    public Workflow() {
        this(null);
    }

    public static void setCallbackFramework(DecoratorFramework framework) {
        callbackFramework = framework;
    }

    public static void clearCallbackFramework() {
        callbackFramework = null;
    }

    public WorkflowCard getCard() {
        return card;
    }

    private static WorkflowCard defaultWorkflowCard() {
        WorkflowCard workflowCard = new WorkflowCard();
        workflowCard.setId(UUID.randomUUID().toString().replace("-", ""));
        return workflowCard;
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
     * @param session workflow session
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
        DecoratorFramework framework = callbackFramework;
        WorkflowCallInput request = prepareWorkflowCall(
                framework,
                WorkflowEvents.WORKFLOW_INVOKE_INPUT,
                inputs,
                session,
                context,
                null,
                isSub,
                skipInputsValidate);
        inputs = request.inputs();
        session = request.session();
        context = request.context();
        isSub = request.isSub();
        skipInputsValidate = request.skipInputsValidate();
        trigger(framework, WorkflowEvents.WORKFLOW_INVOKE_INPUT, request.args(), request.kwargs());

        if (isSub) {
            WorkflowOutput output = new WorkflowOutput(
                    invokeSubWorkflow(inputs, session, context),
                    WorkflowExecutionState.COMPLETED);
            return finishWorkflowInvoke(framework, output);
        }
        validateSession(session);
        Object validatedInputs = validateInputs(inputs, skipInputsValidate);
        final Object executionInputs = validatedInputs;
        final ModelContext executionContext = context;
        WorkflowRuntimeSession workflowSession = createWorkflowSession(session, List.of(StreamMode.OUTPUT));
        long executeTimeoutMs = resolveTimeoutMillis(workflowSession, SessionConstants.WORKFLOW_EXECUTE_TIMEOUT);

        try {
            return finishWorkflowInvoke(framework, executeWithWorkflowTimeout(() -> {
                try {
                    traceWorkflowStart(workflowSession, executionInputs);
                    Object executionResult;
                    try {
                        executionResult = executeCompiledGraph(executionInputs, workflowSession, executionContext, null);
                        finishStreamActorsAfterGraph(workflowSession, executionResult);
                    } finally {
                        traceWorkflowDone(workflowSession);
                        closeStreamEmitter(workflowSession);
                    }
                    List<Object> outputChunks = collectOutputChunks(workflowSession);
                    if (isInterrupted(executionResult, outputChunks)) {
                        return new WorkflowOutput(
                                resolveInterruptedOutputChunks(workflowSession, executionResult, outputChunks),
                                WorkflowExecutionState.INPUT_REQUIRED);
                    }
                    Object result = isStreaming
                            ? stableCompletedOutputChunks(outputChunks)
                            : WorkflowSessionSupport.getOutputs(workflowSession, endCompId);
                    return new WorkflowOutput(result, WorkflowExecutionState.COMPLETED);
                } catch (Exception e) {
                    throw wrapWorkflowException(e);
                }
            }, executeTimeoutMs));
        } finally {
            closeStreamEmitter(workflowSession);
            resetGraphExecutionState();
        }
    }

    /**
     * Simplified invoke without sub flag.
     */
    public WorkflowOutput invoke(Object inputs, Object session, ModelContext context) {
        return invoke(inputs, session, context, false);
    }

    /**
     * Compatibility bridge for callers that still pass the root context facade.
     */
    public WorkflowOutput invoke(Object inputs, Object session, Object context) {
        return invoke(inputs, session, unwrapContext(context), false);
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

    /**
     * Compatibility bridge for callers that still pass the root context facade.
     */
    public Iterator<WorkflowChunk> stream(Object inputs, Object session, Object context,
                                   List<StreamMode> streamModes) {
        return stream(inputs, session, unwrapContext(context),
                streamModes != null ? streamModes : List.of(StreamMode.OUTPUT), false, false);
    }

    public Iterator<WorkflowChunk> stream(Object inputs, Object session, ModelContext context,
                                   List<StreamMode> streamModes, boolean isSub, boolean skipInputsValidate) {
        DecoratorFramework framework = callbackFramework;
        WorkflowCallInput request = prepareWorkflowCall(
                framework,
                WorkflowEvents.WORKFLOW_STREAM_INPUT,
                inputs,
                session,
                context,
                streamModes,
                isSub,
                skipInputsValidate);
        inputs = request.inputs();
        session = request.session();
        context = request.context();
        streamModes = request.streamModes();
        isSub = request.isSub();
        skipInputsValidate = request.skipInputsValidate();
        trigger(framework, WorkflowEvents.WORKFLOW_STREAM_INPUT, request.args(), request.kwargs());

        if (isSub) {
            return finishWorkflowStream(framework, streamSubWorkflow(inputs, session, context));
        }
        validateSession(session);
        Object validatedInputs = validateInputs(inputs, skipInputsValidate);
        final Object executionInputs = validatedInputs;
        final ModelContext executionContext = context;
        WorkflowRuntimeSession workflowSession = createWorkflowSession(session, streamModes);
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
        AsyncStreamQueue streamQueue = workflowSession.runtimeStreamWriterManager() != null
                ? workflowSession.runtimeStreamWriterManager().getStreamEmitter().getStreamQueue()
                : null;

        CompletableFuture<Void> executionFuture = CompletableFuture.runAsync(() -> {
            try {
                traceWorkflowStart(workflowSession, executionInputs);
                Object graphResult = executeCompiledGraph(executionInputs, workflowSession, executionContext, null);
                finishStreamActorsAfterGraph(workflowSession, graphResult);
                writeInterruptedOutputChunks(workflowSession, graphResult);
                finalPayload.set(resolveFinalStreamPayload(workflowSession));
            } catch (Exception e) {
                executionError.set(wrapWorkflowException(e));
            } finally {
                try {
                    traceWorkflowDone(workflowSession);
                } finally {
                    closeStreamEmitter(workflowSession);
                    resetGraphExecutionState();
                }
            }
        }, STREAM_EXECUTOR);

        Iterator<WorkflowChunk> iterator = new Iterator<>() {
            private boolean finalChunkEmitted = false;
            private boolean firstFrame = true;
            private boolean done = false;
            private boolean streamClosed = false;
            private WorkflowChunk nextChunk;
            private final Set<String> seenInteractionIds = new LinkedHashSet<>();
            private List<WorkflowChunk> rememberedInteractionChunks;
            private int rememberedInteractionIndex = 0;

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
                    WorkflowChunk rememberedChunk = nextRememberedInteractionChunk();
                    if (rememberedChunk != null) {
                        return rememberedChunk;
                    }
                    if (!finalChunkEmitted && finalPayload.get() != null) {
                        finalChunkEmitted = true;
                        return new OutputSchema("workflow_final", 0, finalPayload.get());
                    }
                    return null;
                }
                if (streamClosed) {
                    waitForExecution();
                    WorkflowChunk rememberedChunk = nextRememberedInteractionChunk();
                    if (rememberedChunk != null) {
                        return rememberedChunk;
                    }
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
                    WorkflowChunk rememberedChunk = nextRememberedInteractionChunk();
                    if (rememberedChunk != null) {
                        return rememberedChunk;
                    }
                    if (!finalChunkEmitted && finalPayload.get() != null) {
                        finalChunkEmitted = true;
                        return new OutputSchema("workflow_final", 0, finalPayload.get());
                    }
                    return null;
                }
                Loggers.SESSION.debug("Stream data received, dataType={}", data.getClass().getSimpleName());
                WorkflowChunk chunk = streamDataToWorkflowChunk(data);
                rememberInteractionId(chunk);
                return chunk;
            }

            private WorkflowChunk nextRememberedInteractionChunk() {
                if (rememberedInteractionChunks == null) {
                    rememberedInteractionChunks = rememberedInteractionOutputChunks(workflowSession, seenInteractionIds);
                }
                if (rememberedInteractionIndex >= rememberedInteractionChunks.size()) {
                    return null;
                }
                WorkflowChunk chunk = rememberedInteractionChunks.get(rememberedInteractionIndex++);
                rememberInteractionId(chunk);
                return chunk;
            }

            private void rememberInteractionId(Object chunk) {
                String id = interactionOutputId(chunk);
                if (id != null) {
                    seenInteractionIds.add(id);
                }
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
        return finishWorkflowStream(framework, iterator);
    }

    private void writeInterruptedOutputChunks(WorkflowRuntimeSession workflowSession, Object graphResult) {
        if (!isInterrupted(graphResult, List.of())) {
            return;
        }
        StreamWriterManager writerManager = workflowSession.runtimeStreamWriterManager();
        if (writerManager == null || writerManager.getOutputWriter() == null) {
            return;
        }
        for (Object chunk : resolveInterruptedOutputChunks(workflowSession, graphResult, List.of())) {
            if (chunk instanceof OutputSchema outputSchema) {
                writerManager.getOutputWriter().write(outputSchema);
            }
        }
    }

    public Iterator<WorkflowChunk> stream(Object inputs, Object session, ModelContext context) {
        return stream(inputs, session, context, List.of(StreamMode.OUTPUT), false, false);
    }

    private static WorkflowChunk streamDataToWorkflowChunk(Object data) {
        if (data instanceof CustomSchema customSchema) {
            return new OutputSchema(StreamMode.CUSTOM.name().toLowerCase(Locale.ROOT), 0,
                    customSchema.getProperties());
        }
        return WorkflowChunk.from(data);
    }

    /**
     * Compatibility bridge for callers that still pass the root context facade.
     */
    public Iterator<WorkflowChunk> stream(Object inputs, Object session, Object context) {
        return stream(inputs, session, unwrapContext(context), List.of(StreamMode.OUTPUT), false, false);
    }

    private ModelContext unwrapContext(Object context) {
        if (context == null) {
            return null;
        }
        if (context instanceof ModelContext modelContext) {
            return modelContext;
        }
        if (context instanceof com.openjiuwen.core.context.ModelContext modelContext) {
            return modelContext.unwrap();
        }
        throw new IllegalArgumentException("Unsupported workflow model context type: " + context.getClass());
    }

    private WorkflowCallInput prepareWorkflowCall(
            DecoratorFramework framework,
            String event,
            Object inputs,
            Object session,
            ModelContext context,
            List<StreamMode> streamModes,
            boolean isSub,
            boolean skipInputsValidate) {
        Object[] args = new Object[]{inputs, session, context};
        Map<String, Object> kwargs = workflowCallKwargs(inputs, session, context, streamModes, isSub,
                skipInputsValidate, args);
        if (framework == null) {
            return new WorkflowCallInput(inputs, session, context, streamModes, isSub, skipInputsValidate, args,
                    kwargs);
        }

        Object transformed = framework.triggerTransform(event, args, kwargs);
        if (transformed instanceof CallbackDecorators.BoundArgs boundArgs) {
            return workflowCallFromBoundArgs(boundArgs, inputs, session, context, streamModes, isSub,
                    skipInputsValidate);
        }
        if (transformed instanceof Map<?, ?> map) {
            return workflowCallFromMap(map, inputs, session, context, streamModes, isSub, skipInputsValidate);
        }
        return new WorkflowCallInput(inputs, session, context, streamModes, isSub, skipInputsValidate, args,
                kwargs);
    }

    private WorkflowOutput finishWorkflowInvoke(DecoratorFramework framework, WorkflowOutput output) {
        if (framework == null) {
            return output;
        }
        Object transformed = triggerOutputTransform(framework, WorkflowEvents.WORKFLOW_INVOKE_OUTPUT, output);
        WorkflowOutput effectiveOutput = toWorkflowOutput(transformed, output);
        trigger(framework, WorkflowEvents.WORKFLOW_INVOKE_OUTPUT, new Object[0], resultKwargs(effectiveOutput));
        return effectiveOutput;
    }

    private Iterator<WorkflowChunk> finishWorkflowStream(DecoratorFramework framework, Iterator<WorkflowChunk> source) {
        if (framework == null) {
            return source;
        }
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return source.hasNext();
            }

            @Override
            public WorkflowChunk next() {
                WorkflowChunk current = source.next();
                Object transformed = triggerOutputTransform(framework, WorkflowEvents.WORKFLOW_STREAM_OUTPUT, current);
                WorkflowChunk effectiveChunk = toWorkflowChunk(transformed, current);
                trigger(framework, WorkflowEvents.WORKFLOW_STREAM_OUTPUT, new Object[0], resultKwargs(effectiveChunk));
                return effectiveChunk;
            }
        };
    }

    private static Object triggerOutputTransform(DecoratorFramework framework, String event, Object result) {
        Map<String, Object> kwargs = resultKwargs(result);
        Object transformed = framework.triggerTransform(event, new Object[0], kwargs);
        if (transformed == null || transformed == CallbackDecorators.TRANSFORM_NOOP) {
            return result;
        }
        return transformed;
    }

    private static WorkflowOutput toWorkflowOutput(Object transformed, WorkflowOutput fallback) {
        if (transformed instanceof WorkflowOutput workflowOutput) {
            return workflowOutput;
        }
        return new WorkflowOutput(transformed, fallback == null ? null : fallback.getState());
    }

    private static WorkflowChunk toWorkflowChunk(Object transformed, WorkflowChunk fallback) {
        if (transformed instanceof WorkflowChunk workflowChunk) {
            return workflowChunk;
        }
        if (transformed instanceof OutputSchema outputSchema) {
            return outputSchema;
        }
        return new OutputSchema(fallback.getType(), fallback.getIndex(), transformed);
    }

    private static void trigger(DecoratorFramework framework, String event, Object[] args, Map<String, Object> kwargs) {
        if (framework != null) {
            framework.trigger(event, args != null ? args : new Object[0], kwargs != null ? kwargs : Map.of());
        }
    }

    private static Map<String, Object> resultKwargs(Object result) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("result", result);
        return values;
    }

    private static Map<String, Object> workflowCallKwargs(
            Object inputs,
            Object session,
            ModelContext context,
            List<StreamMode> streamModes,
            boolean isSub,
            boolean skipInputsValidate,
            Object[] args) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("inputs", inputs);
        values.put("session", session);
        values.put("context", context);
        values.put("stream_modes", streamModes);
        values.put("is_sub", isSub);
        values.put("skip_inputs_validate", skipInputsValidate);
        values.put("_args", args);
        return values;
    }

    private static WorkflowCallInput workflowCallFromBoundArgs(
            CallbackDecorators.BoundArgs boundArgs,
            Object fallbackInputs,
            Object fallbackSession,
            ModelContext fallbackContext,
            List<StreamMode> fallbackStreamModes,
            boolean fallbackIsSub,
            boolean fallbackSkipInputsValidate) {
        Object[] args = boundArgs.getArgs();
        Map<String, Object> kwargs = boundArgs.getKwargs();
        Object inputs = args.length > 0 ? args[0] : kwargs.getOrDefault("inputs", fallbackInputs);
        Object session = args.length > 1 ? args[1] : kwargs.getOrDefault("session", fallbackSession);
        ModelContext context = args.length > 2 && args[2] instanceof ModelContext modelContext
                ? modelContext
                : valueAsModelContext(kwargs.get("context"), fallbackContext);
        List<StreamMode> streamModes = valueAsStreamModes(kwargs.get("stream_modes"), fallbackStreamModes);
        boolean isSub = valueAsBoolean(kwargs.get("is_sub"), fallbackIsSub);
        boolean skipInputsValidate = valueAsBoolean(kwargs.get("skip_inputs_validate"), fallbackSkipInputsValidate);
        Object[] resolvedArgs = new Object[]{inputs, session, context};
        return new WorkflowCallInput(inputs, session, context, streamModes, isSub, skipInputsValidate, resolvedArgs,
                workflowCallKwargs(inputs, session, context, streamModes, isSub, skipInputsValidate, resolvedArgs));
    }

    private static WorkflowCallInput workflowCallFromMap(
            Map<?, ?> map,
            Object fallbackInputs,
            Object fallbackSession,
            ModelContext fallbackContext,
            List<StreamMode> fallbackStreamModes,
            boolean fallbackIsSub,
            boolean fallbackSkipInputsValidate) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        map.forEach((key, value) -> kwargs.put(String.valueOf(key), value));
        Object inputs = kwargs.getOrDefault("inputs", fallbackInputs);
        Object session = kwargs.getOrDefault("session", fallbackSession);
        ModelContext context = valueAsModelContext(kwargs.get("context"), fallbackContext);
        List<StreamMode> streamModes = valueAsStreamModes(kwargs.get("stream_modes"), fallbackStreamModes);
        boolean isSub = valueAsBoolean(kwargs.get("is_sub"), fallbackIsSub);
        boolean skipInputsValidate = valueAsBoolean(kwargs.get("skip_inputs_validate"), fallbackSkipInputsValidate);
        Object[] args = new Object[]{inputs, session, context};
        return new WorkflowCallInput(inputs, session, context, streamModes, isSub, skipInputsValidate, args,
                workflowCallKwargs(inputs, session, context, streamModes, isSub, skipInputsValidate, args));
    }

    private static ModelContext valueAsModelContext(Object value, ModelContext fallback) {
        return value instanceof ModelContext modelContext ? modelContext : fallback;
    }

    @SuppressWarnings("unchecked")
    private static List<StreamMode> valueAsStreamModes(Object value, List<StreamMode> fallback) {
        if (value instanceof List<?> list && list.stream().allMatch(StreamMode.class::isInstance)) {
            return (List<StreamMode>) list;
        }
        return fallback;
    }

    private static boolean valueAsBoolean(Object value, boolean fallback) {
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private record WorkflowCallInput(
            Object inputs,
            Object session,
            ModelContext context,
            List<StreamMode> streamModes,
            boolean isSub,
            boolean skipInputsValidate,
            Object[] args,
            Map<String, Object> kwargs) {
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
        WorkflowRuntimeSession subSession = createSubWorkflowSession(session);
        try {
            Object graphResult = executeCompiledGraph(inputs != null ? inputs : Map.of(), subSession, context, config);
            finishStreamActorsAfterGraph(subSession, graphResult);
            if (isStreaming) {
                List<Object> messages = drainSubWorkflowStream(subSession, countEndStreamAbilities());
                if (!messages.isEmpty()) {
                    clearSubWorkflowCheckpoint(subSession, config);
                    return Map.of("stream", messages);
                }
            }
            Object outputs = WorkflowSessionSupport.getOutputs(subSession, endCompId);
            clearSubWorkflowCheckpoint(subSession, config);
            return outputs;
        } catch (Exception e) {
            throw wrapWorkflowException(e);
        } finally {
            resetGraphExecutionState();
        }
    }

    public Iterator<WorkflowChunk> streamSubWorkflow(Object inputs, Object session, ModelContext context) {
        return streamSubWorkflow(inputs, session, context, null);
    }

    @SuppressWarnings("unchecked")
    public Iterator<WorkflowChunk> streamSubWorkflow(Object inputs, Object session, ModelContext context, Object config) {
        Object results = invokeSubWorkflow(inputs, session, context, config);
        if (results instanceof Map<?, ?> resultMap && resultMap.get("stream") instanceof List<?> list) {
            return toWorkflowChunks(list).iterator();
        }
        if (results instanceof List<?> list) {
            return toWorkflowChunks(list).iterator();
        }
        return Collections.emptyIterator();
    }

    private static void clearSubWorkflowCheckpoint(WorkflowRuntimeSession session, Object config) {
        String namespace = graphNamespace(config);
        if (session == null || namespace == null || namespace.isBlank() || session.checkpointer() == null) {
            return;
        }
        var graphStore = session.checkpointer().graphStore();
        if (graphStore != null) {
            graphStore.delete(session.sessionId(), namespace).toCompletableFuture().join();
        }
    }

    private static String graphNamespace(Object config) {
        if (config instanceof com.openjiuwen.core.graph.pregel.PregelConfig pregelConfig) {
            return pregelConfig.getNs();
        }
        if (config instanceof Map<?, ?> map) {
            Object namespace = map.get(PregelConstants.NS);
            return namespace == null ? null : String.valueOf(namespace);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object executeCompiledGraph(Object inputs, WorkflowRuntimeSession session, ModelContext context, Object config) {
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
        Throwable cause = unwrapWorkflowException(e);
        GraphInterrupt interrupt = findGraphInterrupt(cause);
        if (interrupt != null) {
            return new CompletionException(interrupt);
        }
        BaseError baseError = findBaseError(cause);
        if (baseError != null) {
            return baseError;
        }
        String reason = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        return ErrorHelper.buildError(StatusCode.WORKFLOW_EXECUTION_ERROR,
                "reason", reason,
                "workflow", workflowCardString());
    }

    private static Throwable unwrapWorkflowException(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException
                || current instanceof ExecutionException
                || current instanceof InvocationTargetException) {
            Throwable cause = current instanceof InvocationTargetException invocationTargetException
                    ? invocationTargetException.getTargetException()
                    : current.getCause();
            if (cause == null) {
                break;
            }
            current = cause;
        }
        return current;
    }

    private static GraphInterrupt findGraphInterrupt(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof GraphInterrupt interrupt) {
                return interrupt;
            }
            current = current.getCause();
        }
        return null;
    }

    private static BaseError findBaseError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BaseError baseError) {
                return baseError;
            }
            current = current.getCause();
        }
        return null;
    }

    private WorkflowRuntimeSession createWorkflowSession(Object session, List<StreamMode> streamModes) {
        internal.autoCompleteAbilities();

        BaseSession parent = null;
        String sessionId = null;
        Map<String, Object> envs = null;
        WorkflowRuntimeSession existingWorkflowSession = null;
        Object callbackManager = invokeNoArg(session, "getCallbackManager");
        if (session instanceof WorkflowRuntimeSession workflowSession) {
            existingWorkflowSession = workflowSession;
            parent = workflowSession.parent();
            sessionId = workflowSession.sessionId();
            envs = workflowSession.config() != null ? workflowSession.config().getEnvs() : null;
        } else if (session instanceof WorkflowSessionApi workflowSessionApi) {
            parent = workflowSessionApi.getParent();
            sessionId = workflowSessionApi.getSessionId();
            envs = workflowSessionApi.getEnvs();
        } else if (session instanceof BaseSession baseSession) {
            parent = baseSession;
            sessionId = stringValue(invokeNoArg(session, "sessionId"));
            if (sessionId == null) {
                sessionId = stringValue(invokeNoArg(session, "getSessionId"));
            }
            Object envObj = invokeNoArg(session, "getEnvs");
            if (envObj instanceof Map<?, ?> envMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedEnv = (Map<String, Object>) envMap;
                envs = typedEnv;
            }
            if ((envs == null || envs.isEmpty()) && baseSession.config() != null) {
                envs = baseSession.config().getEnvs();
            }
        } else {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_EXECUTION_ERROR,
                    "reason", "unsupported workflow session type: " + session.getClass().getSimpleName(),
                    "card", card.getId());
        }

        WorkflowRuntimeSession workflowSession = existingWorkflowSession != null
                ? existingWorkflowSession
                : new WorkflowRuntimeSession(
                        card.getId(),
                        parent,
                        sessionId,
                        InMemoryState.create(),
                        callbackManager);
        workflowSession.setWorkflowId(card.getId());
        if (envs != null) {
            workflowSession.config().setEnvs(envs);
        }
        if (workflowSession.checkpointer() == null) {
            workflowSession.setCheckpointer(resolveGraphCheckpointer(session, parent));
        }
        workflowSession.config().addWorkflowConfig(card.getId(), internal.getConfig());
        if (workflowSession.runtimeStreamWriterManager() == null) {
            workflowSession.setStreamWriterManager(new StreamWriterManager(new StreamEmitter(), streamModes));
        }
        workflowSession.setActorManager(buildActorManager(workflowSession, false));
        return workflowSession;
    }

    private CompiledGraph.GraphCheckpointer resolveGraphCheckpointer(Object session, BaseSession parent) {
        if (session instanceof WorkflowRuntimeSession workflowSession && workflowSession.checkpointer() != null) {
            return workflowSession.checkpointer();
        }
        Object source = null;
        if (session instanceof BaseSession baseSession) {
            source = baseSession.checkpointer();
        }
        if (source == null && parent != null) {
            source = parent.checkpointer();
        }
        if (source == null) {
            source = CheckpointerFactory.getCheckpointer();
        }
        if (source instanceof CompiledGraph.GraphCheckpointer graphCheckpointer) {
            return graphCheckpointer;
        }
        if (source instanceof Checkpointer checkpointer) {
            return new CheckpointerGraphAdapter(checkpointer);
        }
        return null;
    }

    private record CheckpointerGraphAdapter(Checkpointer delegate) implements CompiledGraph.GraphCheckpointer {

        @Override
        public com.openjiuwen.core.graph.store.Store graphStore() {
            return delegate.graphStore();
        }

        @Override
        public void preWorkflowExecute(BaseSession session, Object inputs) {
            delegate.preWorkflowExecute(session, inputs);
        }

        @Override
        public void postWorkflowExecute(BaseSession session, Map<String, Object> result, Exception exception) {
            delegate.postWorkflowExecute(session, result, exception);
        }
    }

    private WorkflowRuntimeSession createSubWorkflowSession(Object session) {
        internal.autoCompleteAbilities();
        BaseSession innerSession = extractInnerSession(session);
        String subParentId = "";
        String subExecutableId = card.getId();
        String subNodeId = card.getId();
        String subNodeType = card.getId();
        if (innerSession instanceof WorkflowRuntimeSession nodeSession) {
            subParentId = nodeSession.parentId();
            subExecutableId = nodeSession.executableId();
            subNodeId = nodeSession.nodeId();
            subNodeType = nodeSession.nodeType();
        }
        WorkflowRuntimeSession subSession = new WorkflowRuntimeSession(
                card.getId(),
                innerSession,
                innerSession instanceof WorkflowRuntimeSession runtime ? runtime.sessionId() : null,
                innerSession instanceof WorkflowRuntimeSession runtime ? runtime.state() : InMemoryState.create(),
                innerSession instanceof WorkflowRuntimeSession runtime ? runtime.callbackManager() : null,
                subParentId,
                subExecutableId,
                subNodeId,
                subNodeType,
                innerSession instanceof WorkflowRuntimeSession runtime ? runtime.workflowNestingDepth() + 1 : 1,
                true);
        if (innerSession instanceof WorkflowRuntimeSession runtime) {
            subSession.config().setEnvs(runtime.config().getEnvs());
            subSession.config().addWorkflowConfigs(runtime.config().getWorkflowConfigs());
            subSession.setMainWorkflowId(resolveMainWorkflowId(runtime));
        } else {
            subSession.setMainWorkflowId(card.getId());
        }
        subSession.setCheckpointer(resolveGraphCheckpointer(session, innerSession));
        subSession.setActorManager(buildActorManager(subSession, true));
        subSession.config().addWorkflowConfig(card.getId(), internal.getConfig());
        return subSession;
    }

    private static String resolveMainWorkflowId(WorkflowRuntimeSession runtime) {
        String mainWorkflowId = runtime.mainWorkflowId();
        if (mainWorkflowId == null || mainWorkflowId.isBlank()) {
            return runtime.workflowId();
        }
        return mainWorkflowId;
    }

    private ActorManager buildActorManager(WorkflowRuntimeSession session, boolean subGraph) {
        return new ActorManager(internal.getConfig().getSpec(), internal.getStreamActor(), subGraph, session);
    }

    private void closeStreamEmitter(WorkflowRuntimeSession workflowSession) {
        if (workflowSession.runtimeStreamWriterManager() != null
                && !workflowSession.runtimeStreamWriterManager().getStreamEmitter().isClosed()) {
            workflowSession.runtimeStreamWriterManager().getStreamEmitter().close();
        }
    }

    /**
     * After graph execution, stream actors for consumers (e.g. End TRANSFORM) may still be blocked
     * waiting for producers that will not run until user input is returned. When the graph yielded
     * {@link PregelConstants#TASK_STATUS_INTERRUPT}, shut actors down instead of awaiting completion.
     */
    private void finishStreamActorsAfterGraph(WorkflowRuntimeSession session, Object executionResult) {
        if (session == null || session.runtimeActorManager() == null) {
            return;
        }
        ActorManager actorManager = session.runtimeActorManager();
        if (graphYieldedInterrupt(executionResult)) {
            actorManager.shutdown();
        }
    }

    private static boolean graphYieldedInterrupt(Object executionResult) {
        return executionResult instanceof Map<?, ?> resultMap
                && resultMap.containsKey(PregelConstants.TASK_STATUS_INTERRUPT);
    }

    private List<Object> drainSubWorkflowStream(WorkflowRuntimeSession subSession, int expectedEndFrames) {
        List<Object> messages = new ArrayList<>();
        if (subSession.runtimeActorManager() == null) {
            return messages;
        }
        AsyncStreamQueue subWorkflowStream = subSession.runtimeActorManager().subWorkflowStream();
        int remainingEndFrames = Math.max(1, expectedEndFrames);
        long receiveTimeoutMs = resolveSubWorkflowDrainTimeoutMillis(subSession);
        while (true) {
            Object frame = subWorkflowStream.receive(receiveTimeoutMs);
            if (frame == null) {
                break;
            }
            if (StreamEmitter.END_FRAME.equals(frame)) {
                remainingEndFrames--;
                if (remainingEndFrames <= 0) {
                    break;
                }
                continue;
            }
            messages.add(frame);
        }
        return messages;
    }

    private long resolveSubWorkflowDrainTimeoutMillis(WorkflowRuntimeSession subSession) {
        long configuredTimeoutMs = resolveTimeoutMillis(subSession, SessionConstants.WORKFLOW_EXECUTE_TIMEOUT);
        if (configuredTimeoutMs <= 0) {
            return SUB_WORKFLOW_STREAM_DRAIN_TIMEOUT_MS;
        }
        return Math.max(1L, Math.min(configuredTimeoutMs, SUB_WORKFLOW_STREAM_DRAIN_TIMEOUT_MS));
    }

    private int countEndStreamAbilities() {
        internal.autoCompleteAbilities();
        if (internal.getConfig() == null
                || internal.getConfig().getSpec() == null
                || internal.getConfig().getSpec().getCompConfigs() == null
                || !internal.getConfig().getSpec().getCompConfigs().containsKey(endCompId)) {
            return 1;
        }
        List<ComponentAbility> abilities =
                internal.getConfig().getSpec().getCompConfigs().get(endCompId).getAbilities();
        if (abilities == null || abilities.isEmpty()) {
            return 1;
        }
        int count = 0;
        for (ComponentAbility ability : abilities) {
            if (ability == ComponentAbility.STREAM || ability == ComponentAbility.TRANSFORM) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private List<Object> collectOutputChunks(WorkflowRuntimeSession workflowSession) {
        if (workflowSession.runtimeStreamWriterManager() == null) {
            return List.of();
        }
        return workflowSession.runtimeStreamWriterManager().collectStreamOutput();
    }

    private static List<Object> stableCompletedOutputChunks(List<Object> outputChunks) {
        if (outputChunks == null || outputChunks.isEmpty()) {
            return List.of();
        }
        List<Object> sorted = new ArrayList<>(outputChunks);
        sorted.sort(Comparator
                .comparingInt(Workflow::outputChunkIndex)
                .thenComparing(Workflow::outputChunkPayloadKey));
        return sorted;
    }

    private static int outputChunkIndex(Object chunk) {
        return chunk instanceof OutputSchema outputSchema ? outputSchema.getIndex() : 0;
    }

    private static String outputChunkPayloadKey(Object chunk) {
        if (chunk instanceof OutputSchema outputSchema) {
            return String.valueOf(outputSchema.getPayload());
        }
        return String.valueOf(chunk);
    }

    private Double resolveTimeoutSeconds(WorkflowRuntimeSession workflowSession, String configKey) {
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

    private long resolveTimeoutMillis(WorkflowRuntimeSession workflowSession, String configKey) {
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
                "workflow", workflowCardString());
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
    private List<Object> resolveInterruptedOutputChunks(
            WorkflowRuntimeSession workflowSession,
            Object executionResult,
            List<Object> outputChunks) {
        List<Object> interruptChunks = extractInterruptOutputChunks(executionResult);
        if (outputChunks != null && !outputChunks.isEmpty()) {
            if (interruptChunks.isEmpty()) {
                return restoreInteractionOutputValues(workflowSession,
                        appendRememberedInteractionOutputs(workflowSession, outputChunks));
            }
            List<Object> merged = new ArrayList<>(outputChunks);
            for (Object interruptChunk : interruptChunks) {
                if (!merged.contains(interruptChunk)) {
                    merged.add(interruptChunk);
                }
            }
            return restoreInteractionOutputValues(workflowSession,
                    appendRememberedInteractionOutputs(workflowSession, merged));
        }
        if (!interruptChunks.isEmpty()) {
            return restoreInteractionOutputValues(workflowSession,
                    appendRememberedInteractionOutputs(workflowSession, interruptChunks));
        }
        return appendRememberedInteractionOutputs(workflowSession, outputChunks != null ? outputChunks : List.of());
    }

    private List<Object> appendRememberedInteractionOutputs(
            WorkflowRuntimeSession workflowSession,
            List<Object> outputChunks) {
        if (workflowSession == null || workflowSession.state() == null) {
            return outputChunks;
        }
        Object remembered = rememberedInteractionOutputs(workflowSession);
        if (!(remembered instanceof Iterable<?> iterable)) {
            return outputChunks;
        }
        List<Object> merged = new ArrayList<>(outputChunks);
        Set<String> seenIds = new LinkedHashSet<>();
        for (Object chunk : outputChunks) {
            String id = interactionOutputId(chunk);
            if (id != null) {
                seenIds.add(id);
            }
        }
        for (Object item : iterable) {
            String id = interactionOutputId(item);
            if (id == null || seenIds.contains(id)) {
                continue;
            }
            seenIds.add(id);
            merged.add(item);
        }
        return merged;
    }

    private List<WorkflowChunk> rememberedInteractionOutputChunks(
            WorkflowRuntimeSession workflowSession,
            Set<String> seenIds) {
        if (workflowSession == null || workflowSession.state() == null) {
            return List.of();
        }
        Object remembered = rememberedInteractionOutputs(workflowSession);
        if (!(remembered instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<WorkflowChunk> chunks = new ArrayList<>();
        for (Object item : iterable) {
            String id = interactionOutputId(item);
            if (id == null || seenIds.contains(id) || !(item instanceof WorkflowChunk workflowChunk)) {
                continue;
            }
            seenIds.add(id);
            chunks.add(workflowChunk);
        }
        return chunks;
    }

    private Object rememberedInteractionOutputs(WorkflowRuntimeSession workflowSession) {
        if (workflowSession == null || workflowSession.state() == null) {
            return null;
        }
        Object direct = workflowSession.state().getGlobal(WORKFLOW_INTERACTION_OUTPUTS_KEY);
        if (direct instanceof Iterable<?>) {
            return direct;
        }
        return findRememberedInteractionOutputs(workflowSession.state().dump());
    }

    private Object findRememberedInteractionOutputs(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Object direct = map.get(WORKFLOW_INTERACTION_OUTPUTS_KEY);
        if (direct != null) {
            return direct;
        }
        for (Object child : map.values()) {
            Object nested = findRememberedInteractionOutputs(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static String interactionOutputId(Object chunk) {
        if (!(chunk instanceof OutputSchema outputSchema)
                || !Constant.INTERACTION.equals(outputSchema.getType())
                || !(outputSchema.getPayload() instanceof InteractionOutput interactionOutput)
                || interactionOutput.getId() == null) {
            return null;
        }
        return interactionOutput.getId();
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractInterruptOutputChunks(Object executionResult) {
        if (!(executionResult instanceof Map<?, ?> resultMap)) {
            return List.of();
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
            return List.of(outputSchemaFromMap((Map<String, Object>) interruptMap));
        }
        if (interrupt instanceof List<?> interruptList) {
            List<Object> recovered = new ArrayList<>(interruptList.size());
            for (Object item : interruptList) {
                Object value = item instanceof Interrupt itemInterrupt ? itemInterrupt.getValue() : item;
                if (value instanceof OutputSchema outputSchema) {
                    recovered.add(outputSchema);
                } else if (value instanceof Map<?, ?> itemMap
                        && itemMap.containsKey("type")
                        && itemMap.containsKey("payload")) {
                    recovered.add(outputSchemaFromMap((Map<String, Object>) itemMap));
                }
            }
            if (!recovered.isEmpty()) {
                return recovered;
            }
        }
        return List.of();
    }

    private List<Object> restoreInteractionOutputValues(
            WorkflowRuntimeSession workflowSession,
            List<Object> outputChunks) {
        if (workflowSession == null || outputChunks == null || outputChunks.isEmpty()) {
            return outputChunks;
        }
        List<Object> restored = new ArrayList<>(outputChunks.size());
        boolean changed = false;
        for (Object chunk : outputChunks) {
            if (chunk instanceof OutputSchema outputSchema
                    && outputSchema.getPayload() instanceof InteractionOutput interactionOutput) {
                Object recoveredValue = interactionOutput.getValue() == null
                        ? recoverInteractionValue(workflowSession, interactionOutput.getId())
                        : interactionOutput.getValue();
                if (recoveredValue != null || interactionOutput.getClass() != InteractionOutput.class) {
                    InteractionOutput restoredPayload = new InteractionOutput(
                            interactionOutput.getId(),
                            recoveredValue);
                    restoredPayload.getMetadata().putAll(interactionOutput.getMetadata());
                    restored.add(new OutputSchema(outputSchema.getType(), outputSchema.getIndex(), restoredPayload));
                    changed = true;
                    continue;
                }
            }
            restored.add(chunk);
        }
        return changed ? restored : outputChunks;
    }

    private Object recoverInteractionValue(WorkflowRuntimeSession workflowSession, String interactionId) {
        if (interactionId == null || interactionId.isBlank() || workflowSession.state() == null) {
            return null;
        }
        Map<String, Object> stateDump = workflowSession.state().dump();
        Object recovered = findInteractionValue(stateDump, interactionId, "");
        return recovered != null ? recovered : recoverRootCommandValue(stateDump, interactionId);
    }

    private Object findInteractionValue(Object value, String interactionId, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String childPath = path.isBlank() ? key : path + "." + key;
            Object child = entry.getValue();
            if (matchesInteractionPath(childPath, interactionId) && child instanceof Map<?, ?> childMap) {
                Object recovered = valueFromInteractionState(childMap);
                if (recovered != null) {
                    return recovered;
                }
            }
            Object nested = findInteractionValue(child, interactionId, childPath);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static boolean matchesInteractionPath(String path, String interactionId) {
        return path.equals(interactionId) || path.endsWith("." + interactionId);
    }

    private static Object valueFromInteractionState(Map<?, ?> state) {
        for (String key : List.of("cmd", "value", "question")) {
            if (state.containsKey(key)) {
                return state.get(key);
            }
        }
        return null;
    }

    private Object recoverRootCommandValue(Map<String, Object> stateDump, String interactionId) {
        List<String> keys = rootInteractionValueKeys(interactionId);
        for (String partition : List.of("io_state", "global_state")) {
            Object partitionValue = stateDump.get(partition);
            if (partitionValue instanceof Map<?, ?> map) {
                for (String key : keys) {
                    if (map.containsKey(key)) {
                        return map.get(key);
                    }
                }
            }
        }
        return null;
    }

    private static List<String> rootInteractionValueKeys(String interactionId) {
        List<String> keys = new ArrayList<>();
        String nodeName = interactionId.substring(interactionId.lastIndexOf('.') + 1);
        int underscore = nodeName.lastIndexOf('_');
        if (underscore >= 0 && underscore + 1 < nodeName.length()) {
            String suffix = nodeName.substring(underscore + 1);
            if (suffix.chars().allMatch(Character::isDigit)) {
                if ("1".equals(suffix)) {
                    keys.add("cmd");
                } else {
                    keys.add("cmd" + suffix);
                }
            }
        }
        keys.add("cmd");
        keys.add("value");
        keys.add("question");
        return keys;
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

    private void traceWorkflowStart(WorkflowRuntimeSession workflowSession, Object inputs) {
        if (workflowSession.tracer() == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workflow_id", card.getId());
        data.put("workflow_name", card.getName());
        data.put("workflow_version", card.getVersion());
        data.put("inputs", inputs);
        workflowSession.tracer().trace(workflowSession, data);
    }

    private void traceWorkflowDone(WorkflowRuntimeSession workflowSession) {
        WorkflowStateCollection stateCollection = workflowSession.state();
        if (workflowSession.tracer() == null || stateCollection == null) {
            return;
        }
        Object outputs = WorkflowSessionSupport.getOutputs(workflowSession, endCompId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workflow_id", card.getId());
        data.put("workflow_name", card.getName());
        data.put("workflow_version", card.getVersion());
        data.put("outputs", outputs);
        workflowSession.tracer().trace(workflowSession, data);
    }

    private BaseSession extractInnerSession(Object session) {
        if (session instanceof BaseSession) {
            return (BaseSession) session;
        }
        Object parent = invokeNoArg(session, "getParent");
        if (parent instanceof BaseSession baseParent) {
            return baseParent;
        }
        Object inner = invokeNoArg(session, "inner");
        if (inner instanceof BaseSession baseInner) {
            return baseInner;
        }
        throw new IllegalArgumentException("Unsupported session type: " + session.getClass().getSimpleName());
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
                "workflow", workflowCardString());
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
                    "workflow", workflowCardString());
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

    private Object resolveFinalStreamPayload(WorkflowRuntimeSession workflowSession) {
        WorkflowStateCollection stateCollection = workflowSession.state();
        if (stateCollection == null) {
            return null;
        }
        return WorkflowSessionSupport.getOutputs(workflowSession, endCompId);
    }

    private static OutputSchema outputSchemaFromMap(Map<String, Object> value) {
        Object index = value.get("index");
        int resolvedIndex = index instanceof Number number
                ? number.intValue()
                : index == null ? 0 : Integer.parseInt(String.valueOf(index));
        return new OutputSchema(stringValue(value.get("type")), resolvedIndex, value.get("payload"));
    }

    private List<WorkflowChunk> toWorkflowChunks(List<?> values) {
        List<WorkflowChunk> chunks = new ArrayList<>(values.size());
        for (Object value : values) {
            chunks.add(WorkflowChunk.from(value));
        }
        return chunks;
    }

    private String workflowCardString() {
        return String.valueOf(card);
    }
}
