/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ExecutionError;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.graph.stream_actor.StreamConsumer;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.interaction.WorkflowInteraction;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamSchema;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.NodeConfig;
import com.openjiuwen.core.workflow.component.llm.LLMExecutable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Vertex is the execution wrapper for a graph node. It manages node initialization,
 * execution lifecycle (invoke/stream/collect/transform abilities), stream coordination,
 * tracing, and end-node handling.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.vertex.Vertex}.
 * In Java, async patterns use Virtual Threads and CompletableFuture instead of asyncio.
 *
 * @since 0.1.7
 */
public class Vertex extends AtomicNode implements StreamConsumer {
    private static final LoggerProtocol LOGGER = Loggers.GRAPH;
    private static final String SUB_WORKFLOW_COMPONENT = "SubWorkflowComponent";
    private static final ExecutorService STREAM_EXECUTOR =
            OpenJiuwenExecutors.newBoundedModulePool("vertex-stream", false);

    private final String nodeId;
    private final Executable<Object, Object> executable;

    /**
     * Lock guarding the End mix-mode read-modify-write of
     * {@code session.state().getOutputs(nodeId)}/{@code setOutputs(results)} so that
     * concurrently running INVOKE and COLLECT postInvoke calls do not clobber each other.
     */
    private final Object endMixMergeLock = new Object();

    private Object context;
    private NodeSession session;
    private int streamCalledTimeout = 10;
    private CompletableFuture<Object> streamDone;
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final AtomicInteger streamCallCount = new AtomicInteger(0);
    private boolean isEndNode = false;
    private volatile boolean isStarted = false;
    private volatile boolean isCallStarted = false;
    private NodeConfig nodeConfig;
    private List<ComponentAbility> componentAbility;
    private boolean hasStreamCall = false;
    private boolean hasCall = false;

    /**
     * ArrayList<>.
     *
     * @since 0.1.7
     */
    private List<String> sourceId = new ArrayList<>();

    /**
     * HashMap<>.
     *
     * @since 0.1.7
     */
    private Map<String, Object> logMessage = new ConcurrentHashMap<>();
    private boolean isFirstInit = true;

    /**
     * Abilities whose END_FRAME should be deferred until the current
     * batch-in or stream-in ability group finishes, so a downstream consumer's
     * stream processor does not receive an end frame before sibling abilities
     * (e.g. INVOKE following STREAM on the same node) have completed.
     * Mirrors the natural ordering Python gets for free from asyncio's
     * single-threaded cooperative scheduling.
     *
     * @since 0.1.7
     */
    private final List<ComponentAbility> deferredStreamEndAbilities = new ArrayList<>();

    /**
     * Vertex.
     *
     * @param nodeId nodeId
     * @param executable executable
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Vertex(String nodeId, Executable<?, ?> executable) {
        this.nodeId = nodeId;
        this.executable = (Executable<Object, Object>) executable;
        this.streamDone = new CompletableFuture<>();
    }

    /**
     * Initialize the vertex with a session and optional context.
     *
     * @param session the base session
     * @param kwargs additional arguments (e.g., "context")
     * @return true if initialization succeeded
     * @since 0.1.7
     */
    public boolean init(BaseSession session, Map<String, Object> kwargs) {
        String compType = executable.componentType();
        if (compType == null || compType.isEmpty()) {
            compType = executable.getClass().getSimpleName();
        }
        this.session = new NodeSession(session, this.nodeId, compType);
        this.context = kwargs != null ? kwargs.get("context") : null;

        // Get stream call timeout from config
        if (session.config() != null) {
            Object timeout = session.config().getEnv(SessionConstants.COMP_STREAM_CALL_TIMEOUT_KEY);
            if (timeout instanceof Number) {
                this.streamCalledTimeout = ((Number) timeout).intValue();
            }
        }

        // Get node config and determine abilities
        Object rawConfig = this.session.nodeConfig();
        if (rawConfig instanceof NodeConfig) {
            this.nodeConfig = (NodeConfig) rawConfig;
        }

        if (nodeConfig != null && nodeConfig.getAbilities() != null && !nodeConfig.getAbilities().isEmpty()) {
            this.componentAbility = nodeConfig.getAbilities();
        } else {
            this.componentAbility = List.of(ComponentAbility.INVOKE);
        }

        this.hasStreamCall = !streamAbilities().isEmpty();
        this.hasCall = componentAbility.size() > streamAbilities().size();

        this.logMessage = new ConcurrentHashMap<>();
        logMessage.put("graph_id", this.session.workflowId());
        logMessage.put("node_id", this.nodeId);

        if (isFirstInit) {
            List<String> abilityNames =
                    componentAbility.stream().map(ComponentAbility::name).collect(Collectors.toList());
            LOGGER.info("Initialized node [{}], abilities is {}", this.nodeId, abilityNames);
            isFirstInit = false;
        }

        boolean hasStreamInputs = hasStreamCall && nodeConfig != null && nodeConfig.getStreamIoConfigs() != null
                && nodeConfig.getStreamIoConfigs().getInputsSchema() != null;

        if (hasStreamInputs && executable instanceof MixModeAware) {
            ((MixModeAware) executable).setMix();
        }

        this.isStarted = false;
        return true;
    }

    /**
     * Main entry point - called by the Pregel engine.
     * Mirrors Python's {@code __call__(state, config)}.
     *
     * @param state the graph state
     * @param config execution config
     * @return a map with source_node_id
     * @throws Exception on execution failure
     * @since 0.1.7
     */
    public Map<String, Object> call(GraphNodeState state, Object config) throws Exception {
        LOGGER.info("Begin to call batch-in node [{}]", nodeId);
        try {
            if (executable.postCommit()) {
                Map<String, Object> kwargs = new HashMap<>();
                kwargs.put("config", config);
                kwargs.put("session", session);
                atomicInvoke(kwargs);
            } else {
                doCall(config);
            }
            LOGGER.info("Succeed to call batch-in node [{}]", nodeId);

            Map<String, Object> result = new HashMap<>();
            result.put("source_node_id", List.of(nodeId));
            return result;
        } catch (GraphInterrupt e) {
            if (session.tracer() != null) {
                traceError(e);
            }
            LOGGER.info("Interrupt to call batch-in node [{}]", nodeId);
            throw e;
        } catch (Exception e) {
            // Unwrap GraphInterrupt that may have been wrapped by doAtomicInvoke
            GraphInterrupt interrupt = unwrapGraphInterrupt(e);
            if (interrupt != null) {
                if (session.tracer() != null) {
                    traceError(interrupt);
                }
                LOGGER.info("Interrupt to call batch-in node [{}]", nodeId);
                throw interrupt;
            }
            if (session.tracer() != null) {
                traceError(e);
            }
            if (e instanceof BaseError be) {
                LOGGER.error("Failed to call batch-in node [{}], code={}, msg={}", nodeId, be.getCode(),
                        be.getMessage());
            } else {
                LOGGER.error("Failed to call batch-in node [{}]", nodeId, e);
            }
            throw e;
        } finally {
            callCount.incrementAndGet();
            isStarted = false;
            isCallStarted = false;
        }
    }

    /**
     * doAtomicInvoke.
     *
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected Object doAtomicInvoke(Map<String, Object> kwargs) {
        try {
            return doCall(kwargs.get("config"));
        } catch (GraphInterrupt e) {
            throw new WorkflowInteraction.GraphInterruptRuntimeWrapper(e);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    // ---- Core Execution ----

    /**
     * Execute the node's abilities (invoke/stream, then wait for stream-in).
     *
     * @param config execution configuration
     * @return null
     * @throws Exception on execution failure
     * @since 0.1.7
     */
    private Object doCall(Object config) throws Exception {
        throwIfInterrupted();
        deferredStreamEndAbilities.clear();
        // 1. Check whether the node is initialized
        if (session == null || executable == null) {
            throw ErrorHelper.buildError(StatusCode.GRAPH_VERTEX_EXECUTION_ERROR, "reason", "node is not initialized",
                    "node_id", nodeId);
        }

        // 2. Execute node 'batch-in' abilities (INVOKE and STREAM)
        boolean isSubgraph = executable.graphInvoker();
        ComponentAbility currentAbility = null;
        try {
            List<ComponentAbility> callAbilities = componentAbility.stream()
                    .filter(a -> a == ComponentAbility.INVOKE || a == ComponentAbility.STREAM).toList();

            for (ComponentAbility ability : callAbilities) {
                throwIfInterrupted();
                currentAbility = ability;
                runExecutable(ability, isSubgraph, config, null);
            }
            // Flush END_FRAME messages deferred by batch-in abilities (INVOKE/STREAM)
            // of this node. Only flush when batch-in abilities actually ran, so a
            // node that only has stream-in abilities (COLLECT/TRANSFORM) does not
            // race its own streamCall's deferred flush — those are flushed in
            // streamCall's finally instead.
            if (!callAbilities.isEmpty()) {
                sendDeferredStreamEndMessages();
            }

            if (callAbilities.isEmpty()) {
                traceComponentBegin();
            }
        } catch (ExecutionError e) {
            LOGGER.error("Node ability call failed, node_id={}, ability={}", nodeId,
                    currentAbility != null ? currentAbility.name() : null);
            throw e;
        }

        // 3. Wait for stream-in abilities to complete
        if (streamCalled()) {
            int timeout = streamCalledTimeout > 0 ? streamCalledTimeout : 0;
            try {
                Object result;
                if (timeout > 0) {
                    result = streamDone.get(timeout, TimeUnit.SECONDS);
                } else {
                    result = streamDone.get();
                }
                if (result instanceof Exception) {
                    throw (Exception) result;
                }
            } catch (TimeoutException e) {
                throw ErrorHelper.buildError(StatusCode.GRAPH_VERTEX_STREAM_CALL_TIMEOUT, "timeout",
                        String.valueOf(timeout), "node_id", nodeId);
            }
        } else if (hasStreamCall && !isEndNode) {
            throw ErrorHelper.buildError(StatusCode.GRAPH_VERTEX_STREAM_CALL_ERROR, "reason", "no stream data in",
                    "node_id", nodeId);
        }

        // 4. Send end tracer frame
        traceComponentDone();
        return null;
    }

    /**
     * throwIfInterrupted.
     *
     * @since 0.1.7
     */
    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Vertex execution cancelled");
        }
    }

    // ---- Ability Execution ----

    /**
     * Run the executable with a specific ability.
     *
     * @param ability the component ability to execute
     * @param isSubgraph whether the executable is a subgraph
     * @param config execution config
     * @param latch optional latch to signal when stream-in is ready
     * @throws Exception on execution failure
     * @since 0.1.7
     */
    private void runExecutable(ComponentAbility ability, boolean isSubgraph, Object config, CountDownLatch latch)
            throws Exception {
        try {
            LOGGER.info("Begin to call node [{}] ability [{}]", nodeId, ability.name());

            switch (ability) {
                case INVOKE:
                    executeInvoke(isSubgraph, config);
                    break;
                case STREAM:
                    executeStream(isSubgraph, config);
                    break;
                case COLLECT:
                    executeCollect(isSubgraph, latch);
                    break;
                case TRANSFORM:
                    executeTransform(isSubgraph, latch);
                    break;
                default:
                    break;
            }

            LOGGER.info("Succeed to call node [{}] ability [{}]", nodeId, ability.name());
        } catch (BaseError e) {
            LOGGER.error("Failed to call node [{}] ability [{}], code={}, msg={}", nodeId, ability.name(), e.getCode(),
                    e.getMessage());
            throw e;
        } catch (Exception e) {
            GraphInterrupt interrupt = unwrapGraphInterrupt(e);
            if (interrupt != null) {
                LOGGER.info("Interrupt to call node [{}] ability [{}]", nodeId, ability.name());
                throw interrupt;
            }
            LOGGER.error("Failed to call node [{}]'s '{}'", nodeId, ability.name(), e);
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR, "ability", ability.name(),
                    "comp", nodeId, "reason", e.toString(), "workflow", session.workflowId());
        } finally {
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    /**
     * executeInvoke.
     *
     * @param isSubgraph isSubgraph
     * @param config config
     * @since 0.1.7
     */
    private void executeInvoke(boolean isSubgraph, Object config) {
        Map<String, Object> batchInputs = preInvoke();
        LOGGER.debug("Prepare inputs for [{}] ability [INVOKE]", nodeId);
        Object inputs = batchInputs;
        if (isSubgraph) {
            Map<String, Object> wrappedInputs = new HashMap<>();
            wrappedInputs.put(Constant.INPUTS_KEY, batchInputs);
            wrappedInputs.put(Constant.CONFIG_KEY, config);
            inputs = wrappedInputs;
        }
        Object results = executable.onInvoke(inputs, session, context);
        results = postInvoke(results, ComponentAbility.INVOKE);
        LOGGER.debug("Post-process results for [{}] ability [INVOKE]", nodeId);
    }

    /**
     * executeStream.
     *
     * @param isSubgraph isSubgraph
     * @param config config
     * @since 0.1.7
     */
    private void executeStream(boolean isSubgraph, Object config) {
        Map<String, Object> batchInputs = preInvoke();
        LOGGER.debug("Prepare inputs for [{}] ability [STREAM]", nodeId);
        Object inputs = batchInputs;
        if (isSubgraph) {
            Map<String, Object> wrappedInputs = new HashMap<>();
            wrappedInputs.put(Constant.INPUTS_KEY, batchInputs);
            wrappedInputs.put(Constant.CONFIG_KEY, config);
            inputs = wrappedInputs;
        }
        Iterator<Object> resultIter = executable.onStream(inputs, session, context);
        postStream(resultIter, ComponentAbility.STREAM);
    }

    /**
     * executeCollect.
     *
     * @param isSubgraph isSubgraph
     * @param latch latch
     * @since 0.1.7
     */
    private void executeCollect(boolean isSubgraph, CountDownLatch latch) {
        Object collectInputs = preStream(ComponentAbility.COLLECT);
        if (latch != null) {
            latch.countDown();
        }
        Object batchOutput = executable.onCollect(collectInputs, session, context);
        Object results = postInvoke(batchOutput, ComponentAbility.COLLECT);
        LOGGER.debug("Post-process inputs for [{}] ability [COLLECT]", nodeId);
    }

    /**
     * executeTransform.
     *
     * @param isSubgraph isSubgraph
     * @param latch latch
     * @since 0.1.7
     */
    private void executeTransform(boolean isSubgraph, CountDownLatch latch) {
        Object transformInputs = preStream(ComponentAbility.TRANSFORM);
        if (latch != null) {
            latch.countDown();
        }
        Iterator<Object> outputIter = executable.onTransform(transformInputs, session, context);
        postStream(outputIter, ComponentAbility.TRANSFORM);
    }

    // ---- Pre/Post Processing ----

    @SuppressWarnings("unchecked")
    /**
     * preInvoke.
     *
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> preInvoke() {
        traceComponentBegin();
        Object inputsSchema = null;
        if (nodeConfig != null && nodeConfig.getIoConfigs() != null) {
            inputsSchema = nodeConfig.getIoConfigs().getInputsSchema();
        }

        Map<String, Object> inputs = null;
        if (inputsSchema != null && session.state() instanceof WorkflowStateCollection) {
            WorkflowStateCollection stateCollection = (WorkflowStateCollection) session.state();
            if (inputsSchema instanceof Map) {
                inputs = (Map<String, Object>) stateCollection.getInputs(inputsSchema);
            } else {
                inputs = (Map<String, Object>) stateCollection.getInputsByTransformer(inputsSchema);
            }
        }

        traceComponentInputs(inputs);
        return inputs;
    }

    @SuppressWarnings("unchecked")
    /**
     * postInvoke.
     *
     * @param results results
     * @return the result
     * @since 0.1.7
     */
    private Object postInvoke(Object results, ComponentAbility ability) {
        Object processed = applyOutputsSchema(results);
        boolean isEndMixMode = isEndNode && hasCall && hasStreamCall;
        if (processed instanceof Map && isEndMixMode) {
            processed = mergeEndMixModeOutputs(processed);
        }
        if (processed != null && session.state() instanceof WorkflowStateCollection) {
            ((WorkflowStateCollection) session.state()).setOutputs(processed);
        }
        traceComponentOutputs(processed);
        clearInteractive();
        return processed;
    }

    /**
     * applyOutputsSchema.
     *
     * @param results results
     * @return the result
     */
    @SuppressWarnings("unchecked")
    private Object applyOutputsSchema(Object results) {
        Object outputsSchema = null;
        if (nodeConfig != null && nodeConfig.getIoConfigs() != null) {
            outputsSchema = nodeConfig.getIoConfigs().getOutputsSchema();
        }
        if (outputsSchema == null) {
            return results;
        }
        if (outputsSchema instanceof Map) {
            Object transformed = SessionUtils.getBySchema(outputsSchema, (Map<String, Object>) results);
            if (!isEndNode && transformed instanceof Map) {
                ((Map<String, Object>) transformed).values().removeIf(java.util.Objects::isNull);
            }
            return transformed;
        }
        if (outputsSchema instanceof java.util.function.Function) {
            return ((java.util.function.Function<Object, Object>) outputsSchema).apply(results);
        }
        return results;
    }

    /**
     * mergeEndMixModeOutputs.
     *
     * @param results results
     * @return the result
     */
    @SuppressWarnings("unchecked")
    private Object mergeEndMixModeOutputs(Object results) {
        Map<String, Object> resultMap = (Map<String, Object>) results;
        Object output = results;
        if (!(resultMap instanceof java.util.HashMap)
                && !(resultMap instanceof java.util.LinkedHashMap)
                && !(resultMap instanceof java.util.TreeMap)) {
            Map<String, Object> mutable = new LinkedHashMap<>();
            mutable.putAll(resultMap);
            resultMap = mutable;
            output = mutable;
        }
        Object outputs = resultMap.get("output");
        if (outputs != null && !(outputs instanceof List)) {
            resultMap.put("output", new ArrayList<>(List.of(outputs)));
        }
        synchronized (endMixMergeLock) {
            mergeOldOutputsIfNeeded(resultMap);
        }
        return output;
    }

    /**
     * mergeOldOutputsIfNeeded.
     *
     * @param resultMap resultMap
     */
    @SuppressWarnings("unchecked")
    private void mergeOldOutputsIfNeeded(Map<String, Object> resultMap) {
        if (!(session.state() instanceof WorkflowStateCollection)) {
            return;
        }
        Object oldOutputs = ((WorkflowStateCollection) session.state()).getOutputs(nodeId);
        if (!(oldOutputs instanceof Map)) {
            return;
        }
        Map<String, Object> oldMap = (Map<String, Object>) oldOutputs;
        Object oldOutputValue = oldMap.get("output");
        Object currentOutput = resultMap.get("output");
        if (!(currentOutput instanceof List)) {
            return;
        }
        List<Object> currentList = (List<Object>) currentOutput;
        List<Object> oldList;
        if (oldOutputValue instanceof List) {
            oldList = new ArrayList<>((List<Object>) oldOutputValue);
        } else if (oldOutputValue != null) {
            oldList = new ArrayList<>();
            oldList.add(oldOutputValue);
        } else {
            oldList = new ArrayList<>();
        }
        // Mirror Python's End mix-mode ordering: new (current) chunks first,
        // old (previously stored) chunks second — see vertex.py
        // `_post_invoke`: `results["output"].extend(old_outputs["output"])`.
        // Combined with the batch-first execution order in `call()`
        // (INVOKE runs before STREAM), STREAM is the second call, so its
        // chunks land before the earlier INVOKE chunks — yielding
        // [stream_chunks..., batch_chunk] in 009's expected order.
        if (!oldList.isEmpty()) {
            List<Object> merged = new ArrayList<>(currentList);
            merged.addAll(oldList);
            resultMap.put("output", merged);
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * preStream.
     *
     * @param ability ability
     * @return the result
     * @since 0.1.7
     */
    private Object preStream(ComponentAbility ability) {
        traceComponentBegin();
        ActorManager actorManager = getActorManager();
        Object inputsSchema = null;
        if (nodeConfig != null && nodeConfig.getStreamIoConfigs() != null) {
            inputsSchema = nodeConfig.getStreamIoConfigs().getInputsSchema();
        }
        if (!(inputsSchema instanceof Map)) {
            inputsSchema = null;
        }

        boolean enableTrace = session.tracer() != null && !executable.skipTrace();

        Consumer<Object> streamCallback = chunk -> {
            LOGGER.debug("Consume chunk of {}[{}]", nodeId, ability.name());
            if (enableTrace) {
                TracerWorkflowUtils.traceComponentStreamInput(session, chunk, false);
            }
        };

        if (actorManager != null) {
            return actorManager.consume(nodeId, ability, inputsSchema, streamCallback);
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    /**
     * postStream.
     *
     * @param resultsIter resultsIter
     * @param ability ability
     * @since 0.1.7
     */
    private void postStream(Iterator<Object> resultsIter, ComponentAbility ability) {
        boolean isSubGraph = session.parentId() != null && !session.parentId().isEmpty();
        ActorManager actorManager = getActorManager();

        Object outputSchema = null;
        if (nodeConfig != null && nodeConfig.getStreamIoConfigs() != null) {
            outputSchema = nodeConfig.getStreamIoConfigs().getOutputsSchema();
        }
        Object outputTransformer = null;
        if (!(outputSchema instanceof Map)) {
            outputTransformer = outputSchema;
        }

        int endStreamIndex = 0;
        while (resultsIter != null && resultsIter.hasNext()) {
            Object chunk = resultsIter.next();
            Object message = transformStreamChunk(chunk, outputSchema, outputTransformer, actorManager);
            LOGGER.debug("Produce chunk[{}] from {}[{}]", endStreamIndex, nodeId, ability.name());
            processChunk(message, isEndNode, endStreamIndex, isSubGraph, ability);
            endStreamIndex++;
        }

        sendStreamEndFrame(ability, isEndNode, isSubGraph, actorManager);

        LOGGER.debug("Produce 'END_FRAME' chunk of [{}] ability [{}]", nodeId, ability.name());
        clearInteractive();

        writeLlmStreamOutput();
    }

    /**
     * Transform a raw stream chunk into the message payload to be sent to
     * consumers, applying either the default schema transformer or a custom
     * transformer. Mirrors the inline transformer selection in Python
     * {@code Vertex._post_stream}.
     *
     * @param chunk chunk
     * @param outputSchema outputSchema
     * @param outputTransformer outputTransformer
     * @param actorManager actorManager
     * @return the result
     * @since 0.1.7
     */
    private Object transformStreamChunk(Object chunk, Object outputSchema, Object outputTransformer,
                                        ActorManager actorManager) {
        if (outputTransformer == null) {
            return (outputSchema != null && actorManager != null)
                    ? actorManager.getStreamTransform().getByDefaultTransformer(chunk, outputSchema)
                    : chunk;
        }
        return (actorManager != null)
                ? actorManager.getStreamTransform().getByDefinedTransformer(chunk, outputTransformer)
                : chunk;
    }

    /**
     * Send the END_FRAME for the given ability, deferring it when a sibling
     * ability still needs to run in this batch. Mirrors the end-frame handling
     * in Python {@code Vertex._post_stream}.
     *
     * @param ability ability
     * @param isEnd isEnd
     * @param isSubGraph isSubGraph
     * @param actorManager actorManager
     * @since 0.1.7
     */
    private void sendStreamEndFrame(ComponentAbility ability, boolean isEnd, boolean isSubGraph,
                                    ActorManager actorManager) {
        // Defer it when a sibling ability (INVOKE/COLLECT/TRANSFORM)
        // still needs to run in this batch, so a downstream consumer does not see
        // the end frame before all sibling outputs are produced — mirroring the
        // ordering Python gets from asyncio's cooperative scheduling. The
        // deferred frames are flushed at the end of the batch-in ability loop
        // (for INVOKE/STREAM) and the stream-in ability loop (for COLLECT/TRANSFORM).
        if (isEnd && isSubGraph) {
            ActorManager am = getActorManager();
            if (am != null && am.subWorkflowStream() != null) {
                if (shouldDeferStreamEnd(ability)) {
                    deferredStreamEndAbilities.add(ability);
                } else {
                    sendToSubWorkflowStream(am, StreamEmitter.END_FRAME);
                }
            }
            return;
        }
        if (actorManager != null) {
            if (shouldDeferStreamEnd(ability)) {
                deferredStreamEndAbilities.add(ability);
            } else {
                actorManager.endMessage(nodeId, ability);
            }
        }
    }

    /**
     * Write the LLM stream output back to the workflow state, if this vertex
     * runs an LLMExecutable with collected stream output. Mirrors the
     * writeback step in Python {@code Vertex._post_stream}.
     *
     * @since 0.1.7
     */
    private void writeLlmStreamOutput() {
        if (!(executable instanceof LLMExecutable llmExec)) {
            return;
        }
        Map<String, Object> result = llmExec.getStreamOutput();
        if (result != null && session.state() instanceof WorkflowStateCollection) {
            ((WorkflowStateCollection) session.state()).setOutputs(result);
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * processChunk.
     *
     * @param message message
     * @param isEnd isEnd
     * @param endStreamIndex endStreamIndex
     * @param isSubGraph isSubGraph
     * @param ability ability
     * @since 0.1.7
     */
    private void processChunk(Object message, boolean isEnd, int endStreamIndex, boolean isSubGraph,
                              ComponentAbility ability) {
        if (isEnd && !isSubGraph) {
            Object messageStreamData;
            if (message instanceof StreamSchema) {
                messageStreamData = message;
            } else {
                Map<String, Object> data = new HashMap<>();
                data.put("type", Constant.END_NODE_STREAM);
                data.put("index", endStreamIndex);
                data.put("payload", message);
                messageStreamData = data;
            }
            traceComponentStreamOutput(messageStreamData);
            if (session.streamWriterManager() != null && session.streamWriterManager().getOutputWriter() != null) {
                session.streamWriterManager().getOutputWriter().write(messageStreamData);
            }
        } else if (isEnd) {
            // isEnd && isSubGraph
            Object messageStreamData =
                    (message instanceof OutputSchema) ? ((OutputSchema) message).getPayload() : message;
            traceComponentStreamOutput(messageStreamData);
            ActorManager am = getActorManager();
            if (am != null && am.subWorkflowStream() != null) {
                sendToSubWorkflowStream(am, messageStreamData);
            }
        } else {
            boolean firstFrame = endStreamIndex == 0;
            traceComponentStreamOutput(message);
            ActorManager am = getActorManager();
            if (am != null) {
                am.produce(nodeId, message, ability, firstFrame);
            }
        }
    }

    /**
     * sendToSubWorkflowStream.
     *
     * @param actorManager actorManager
     * @param message message
     * @since 0.1.7
     */
    private void sendToSubWorkflowStream(ActorManager actorManager, Object message) {
        try {
            actorManager.subWorkflowStream().put(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ErrorHelper.buildError(StatusCode.GRAPH_STREAM_ACTOR_EXECUTION_ERROR, "reason",
                    "interrupted while sending sub-workflow stream message", "node_id", nodeId);
        }
    }

    /**
     * Whether the END_FRAME for the given ability should be deferred until the
     * current batch (batch-in INVOKE/STREAM, or stream-in COLLECT/TRANSFORM)
     * has finished all sibling abilities. Mirrors the ordering guarantee Python
     * gets from asyncio's single-threaded cooperative scheduler.
     *
     * @param ability ability
     * @return the result
     * @since 0.1.7
     */
    private boolean shouldDeferStreamEnd(ComponentAbility ability) {
        if (componentAbility == null) {
            return false;
        }
        int currentIndex = componentAbility.indexOf(ability);
        if (currentIndex < 0) {
            return false;
        }
        boolean isAbilityBatchIn = ability == ComponentAbility.INVOKE || ability == ComponentAbility.STREAM;
        for (int i = currentIndex + 1; i < componentAbility.size(); i++) {
            ComponentAbility later = componentAbility.get(i);
            boolean isLaterBatchIn = later == ComponentAbility.INVOKE || later == ComponentAbility.STREAM;
            // Only defer across siblings of the same batch kind. A batch-in ability
            // (INVOKE/STREAM) waits for later batch-in siblings; a stream-in ability
            // (COLLECT/TRANSFORM) waits for later stream-in siblings. This keeps the
            // two batches' deferred queues disjoint so each batch flushes only its
            // own deferred frames, avoiding the double-send race that previously
            // deadlocked Workflow043Test.
            if (isAbilityBatchIn == isLaterBatchIn) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flush any deferred END_FRAME messages for this node, in insertion order.
     *
     * @since 0.1.7
     */
    private void sendDeferredStreamEndMessages() {
        if (deferredStreamEndAbilities.isEmpty()) {
            return;
        }
        ActorManager actorManager = getActorManager();
        boolean isEnd = isEndNode;
        boolean isSubGraph = session.parentId() != null && !session.parentId().isEmpty();
        if (actorManager != null) {
            for (ComponentAbility ability : deferredStreamEndAbilities) {
                if (isEnd && isSubGraph && actorManager.subWorkflowStream() != null) {
                    sendToSubWorkflowStream(actorManager, StreamEmitter.END_FRAME);
                } else {
                    actorManager.endMessage(nodeId, ability);
                }
                LOGGER.debug("Produce deferred 'END_FRAME' chunk of [{}] ability [{}]", nodeId, ability.name());
            }
        }
        deferredStreamEndAbilities.clear();
    }

    /**
     * unwrapGraphInterrupt.
     *
     * @param throwable throwable
     * @return the result
     * @since 0.1.7
     */
    private GraphInterrupt unwrapGraphInterrupt(Throwable throwable) {
        if (throwable instanceof WorkflowInteraction.GraphInterruptRuntimeWrapper wrapper) {
            return wrapper.getGraphInterrupt();
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof GraphInterrupt interrupt) {
                return interrupt;
            }
            current = current.getCause();
        }
        return null;
    }

    // ---- Stream Call ----

    /**
     * streamCall.
     *
     * @param latch latch
     * @param errorCallback errorCallback
     * @since 0.1.7
     */
    @Override
    public void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback) {
        LOGGER.info("Begin to call stream-in node [{}]", nodeId);
        streamCallCount.incrementAndGet();
        streamDone = new CompletableFuture<>();

        ActorManager actorManager = getActorManager();
        if (session == null || actorManager == null) {
            BaseError error = ErrorHelper.buildError(StatusCode.GRAPH_VERTEX_STREAM_CALL_ERROR, "reason",
                    "queue manager is not initialized", "node_id", nodeId);
            streamDone.complete(error);
            LOGGER.warning("Failed to call stream-in node [{}], actor_manager is missing", nodeId);
            errorCallback.accept(error);
            return;
        }

        Exception error = null;
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        try {
            List<ComponentAbility> callAbilities = streamAbilities();
            for (ComponentAbility ability : callAbilities) {
                CountDownLatch abilityLatch = new CountDownLatch(1);
                CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                    try {
                        runExecutable(ability, false, null, abilityLatch);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, STREAM_EXECUTOR);
                tasks.add(task);
                abilityLatch.await();
            }
            latch.countDown();

            // Wait for all tasks to complete
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).get();

            LOGGER.info("Succeed to call stream-in node [{}]", nodeId);
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOGGER.error("Failed to call stream-in node [{}]", nodeId, cause);
            error = (cause instanceof Exception) ? (Exception) cause : e;
            errorCallback.accept(error);
        } finally {
            // Stream-in abilities (COLLECT/TRANSFORM) defer their END_FRAME if a
            // sibling stream-in ability still needs to run, so a downstream
            // consumer's stream processor receives end frames in the right
            // order. Flush them now that the stream-in batch is done.
            sendDeferredStreamEndMessages();
            streamDone.complete(error != null ? error : Boolean.TRUE);
            traceComponentStreamInputSend();
        }
    }

    // ---- Helpers ----

    /**
     * clearInteractive.
     *
     * @since 0.1.7
     */
    private void clearInteractive() {
        if (session.state() instanceof WorkflowStateCollection stateCollection) {
            Object interactiveInput = stateCollection.get(Constant.INTERACTIVE_INPUT);
            if (interactiveInput != null) {
                Map<String, Object> clearMap = new HashMap<>();
                clearMap.put(Constant.INTERACTIVE_INPUT, null);
                stateCollection.update(clearMap);
            }
        }
    }

    /**
     * Get stream abilities (COLLECT, TRANSFORM).
     *
     * @return the result
     * @since 0.1.7
     */
    private List<ComponentAbility> streamAbilities() {
        if (componentAbility == null) {
            return List.of();
        }
        return componentAbility.stream().filter(a -> a == ComponentAbility.COLLECT || a == ComponentAbility.TRANSFORM)
                .collect(Collectors.toList());
    }

    /**
     * streamCalled.
     *
     * @return the result
     * @since 0.1.7
     */
    private boolean streamCalled() {
        return streamCallCount.get() == callCount.get() + 1;
    }

    /**
     * isDone.
     *
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isDone() {
        return callCount.get() == streamCallCount.get() || callCount.get() == streamCallCount.get() + 1;
    }

    /**
     * shouldHandleMessage.
     *
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean shouldHandleMessage() {
        return !streamAbilities().isEmpty();
    }

    /**
     * Try to get ActorManager from the session hierarchy.
     *
     * @return the result
     * @since 0.1.7
     */
    private ActorManager getActorManager() {
        // Check if parent session has actor manager
        BaseSession parentSession = session.parent();
        if (parentSession != null) {
            try {
                java.lang.reflect.Method method = parentSession.getClass().getMethod("actorManager");
                Object am = method.invoke(parentSession);
                if (am instanceof ActorManager) {
                    return (ActorManager) am;
                }
            } catch (Exception e) {
                // Not available on this session type
            }
        }
        return null;
    }

    /**
     * Reset the vertex for reuse.
     *
     * @since 0.1.7
     */
    public void reset() {
        callCount.set(0);
        streamCallCount.set(0);
        streamDone.cancel(true);
        streamDone = new CompletableFuture<>();
    }

    // ---- Tracing Helpers ----

    /**
     * traceComponentBegin.
     *
     * @since 0.1.7
     */
    private void traceComponentBegin() {
        if (session.tracer() == null || executable.skipTrace()) {
            return;
        }
        if (!isStarted) {
            isStarted = true;
            TracerWorkflowUtils.traceComponentBegin(session, sourceId);
        }
    }

    /**
     * traceComponentInputs.
     *
     * @param inputs inputs
     * @since 0.1.7
     */
    private void traceComponentInputs(Map<String, Object> inputs) {
        if (session.tracer() == null || executable.skipTrace()) {
            return;
        }
        isCallStarted = true;
        boolean needSend = !hasStreamCall || streamDone.isDone();
        TracerWorkflowUtils.traceComponentInputs(session, inputs, needSend);
        if (SUB_WORKFLOW_COMPONENT.equals(executable.componentType())) {
            TracerWorkflowUtils.registerWorkflowSpanManager(session);
        }
    }

    /**
     * traceComponentOutputs.
     *
     * @param outputs outputs
     * @since 0.1.7
     */
    private void traceComponentOutputs(Object outputs) {
        if (session.tracer() == null || executable.skipTrace()) {
            return;
        }
        TracerWorkflowUtils.traceComponentOutputs(session, outputs);
    }

    /**
     * traceComponentDone.
     *
     * @since 0.1.7
     */
    private void traceComponentDone() {
        if (session.tracer() == null || executable.skipTrace()) {
            return;
        }
        TracerWorkflowUtils.traceComponentDone(session);
    }

    /**
     * traceComponentStreamOutput.
     *
     * @param chunk chunk
     * @since 0.1.7
     */
    private void traceComponentStreamOutput(Object chunk) {
        if (session.tracer() == null || executable.skipTrace()) {
            return;
        }
        TracerWorkflowUtils.traceComponentStreamOutput(session, chunk);
    }

    /**
     * traceError.
     *
     * @param error error
     * @since 0.1.7
     */
    private void traceError(Exception error) {
        if (session.tracer() == null || executable.skipTrace()) {
            return;
        }
        TracerWorkflowUtils.traceError(session, error);
    }

    /**
     * traceComponentStreamInputSend.
     *
     * @since 0.1.7
     */
    private void traceComponentStreamInputSend() {
        if (session.tracer() == null || executable.skipTrace()) {
            return;
        }
        if (!hasCall || isCallStarted) {
            TracerWorkflowUtils.traceComponentStreamInput(session, new HashMap<>(), true);
        }
    }

    // ---- Getters ----

    /**
     * getNodeId.
     *
     * @return the result
     * @since 0.1.7
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * getExecutable.
     *
     * @return the result
     * @since 0.1.7
     */
    public Executable<Object, Object> getExecutable() {
        return executable;
    }

    /**
     * getSession.
     *
     * @return the result
     * @since 0.1.7
     */
    public NodeSession getSession() {
        return session;
    }

    /**
     * isEndNode.
     *
     * @return the result
     * @since 0.1.7
     */
    public boolean isEndNode() {
        return isEndNode;
    }

    /**
     * setEndNode.
     *
     * @param endNode endNode
     * @since 0.1.7
     */
    public void setEndNode(boolean endNode) {
        isEndNode = endNode;
    }

    /**
     * Marker interface for executables that support mixed mode (stream + batch).
     *
     * @since 0.1.7
     */
    public interface MixModeAware {
        /**
         * setMix.
         *
         * @since 0.1.7
         */
        void setMix();
    }
}
