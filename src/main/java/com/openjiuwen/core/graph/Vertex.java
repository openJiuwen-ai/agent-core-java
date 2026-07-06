/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;
import com.openjiuwen.core.common.VirtualThreadSupport;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.stream_actor.StreamConsumer;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.callback.CallbackDecorators;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.WorkflowEvents;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.CommitStateLike;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code Vertex} and module helpers in
 * {@code openjiuwen/core/graph/vertex.py}.
 */
public class Vertex extends AsyncAtomicNode implements StreamConsumer {

    public static final String SUB_WORKFLOW_COMPONENT = "sub_workflow";
    public static final String INTERACTIVE_INPUT = Constant.INTERACTIVE_INPUT;
    public static final String END_NODE_STREAM = Constant.END_NODE_STREAM;
    public static final String INPUTS_KEY = "inputs";
    public static final String CONFIG_KEY = "config";

    private static final Logger LOGGER = LoggerFactory.getLogger(Vertex.class);
    private static final Boolean STREAM_DONE_SUCCESS = Boolean.TRUE;
    private static final Map<String, Object> PYTHON_NONE_MAP = null;
    private static final VertexActorManager PYTHON_NONE_ACTOR_MANAGER = null;
    private static final VertexStreamWriterManager PYTHON_NONE_STREAM_WRITER_MANAGER = null;
    private static final VertexTraceSink PYTHON_NONE_TRACE_SINK = null;
    private static final ErrorRecoveryHandler PYTHON_NONE_ERROR_RECOVERY_HANDLER = null;
    private static final VertexEventSink PYTHON_NONE_EVENT_SINK = null;
    private static final Executor STREAM_EXECUTOR = VirtualThreadSupport.newThreadPerTaskExecutor();

    private final String nodeId;
    private final Executable<Map<String, Object>, Object> executable;
    private final Executor executor;
    private VertexSession session;
    private Object context;
    private int streamCallTimeoutSeconds = 10;
    private CompletableFuture<Object> streamDone = new CompletableFuture<>();
    private int callCount;
    private int streamCallCount;
    private boolean endNode;
    private boolean started;
    private boolean callStarted;
    private VertexNodeConfig nodeConfig = new VertexNodeConfig();
    private List<ComponentAbility> componentAbilities = List.of(ComponentAbility.INVOKE);
    private boolean hasStreamCall;
    private boolean hasCall = true;
    private boolean firstInit = true;

    public Vertex(String nodeId, Executable<Map<String, Object>, ?> executable) {
        this(nodeId, executable, ForkJoinPool.commonPool());
    }

    public Vertex(String nodeId,
                  Executable<Map<String, Object>, ?> executable,
                  Executor executor) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.executable = castExecutable(executable);
        this.executor = executor != null ? executor : ForkJoinPool.commonPool();
    }

    @SuppressWarnings("unchecked")
    public Executable<Object, Object> getExecutable() {
        return (Executable<Object, Object>) (Executable<?, ?>) executable;
    }

    public boolean init(VertexSession session) {
        return init(session, Collections.emptyMap());
    }

    public boolean init(VertexSession session, Map<String, Object> kwargs) {
        VertexSession graphSession = Objects.requireNonNull(session, "session must not be null");
        this.session = graphSession.nodeSession(nodeId);
        this.context = kwargs == null ? null : kwargs.get("context");
        this.streamCallTimeoutSeconds = this.session.streamCallTimeoutSeconds();
        this.nodeConfig = this.session.nodeConfig() != null ? this.session.nodeConfig() : new VertexNodeConfig();
        if (nodeConfig.abilities().isEmpty()) {
            this.componentAbilities = List.of(ComponentAbility.INVOKE);
        } else {
            this.componentAbilities = List.copyOf(nodeConfig.abilities());
        }
        this.hasStreamCall = !streamAbilities().isEmpty();
        this.hasCall = componentAbilities.size() > streamAbilities().size();
        if (firstInit) {
            LOGGER.info("Initialized node [{}], abilities is {}", nodeId,
                    componentAbilities.stream().map(ComponentAbility::name).toList());
            firstInit = false;
        }
        boolean hasStreamInputs = hasStreamCall
                && nodeConfig.streamIoConfigs() != null
                && nodeConfig.streamIoConfigs().inputsSchema() != null;
        if (hasStreamInputs && executable instanceof MixConfigurable mixConfigurable) {
            mixConfigurable.setMix();
        }
        this.started = false;
        return true;
    }

    public CompletionStage<Map<String, List<String>>> invoke(GraphState state, Map<String, Object> config) {
        CompletableFuture<Map<String, List<String>>> future = new CompletableFuture<>();
        try {
            LOGGER.info("Begin to call batch-in node [{}]", nodeId);
            if (executable != null && executable.postCommit()) {
                atomicInvoke(Map.of("config", safeConfig(config), "session", session))
                        .toCompletableFuture().join();
            } else {
                callBlocking(config);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("node_id", nodeId);
            payload.put("ability", componentAbilities.stream().map(ComponentAbility::name).toList());
            payload.put("graph_id", safeWorkflowId());
            payload.put("inputs", state != null ? state : new GraphState());
            payload.put("outputs", session != null ? session.state().get(nodeId) : null);
            emitEvent(WorkflowEvents.NODE_EXECUTED, payload);
            Map<String, List<String>> output = new LinkedHashMap<>();
            output.put("source_node_id", List.of(nodeId));
            future.complete(output);
        } catch (Throwable throwable) {
            Throwable error = unwrapCompletion(throwable);
            if (session != null && session.tracer() != null) {
                traceError(error);
            }
            if (!(error instanceof GraphInterrupt)) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("node_id", nodeId);
                payload.put("graph_id", safeWorkflowId());
                payload.put("error", error);
                emitEvent(WorkflowEvents.NODE_ERROR, payload);
            }
            future.completeExceptionally(error);
        } finally {
            callCount += 1;
            started = false;
            callStarted = false;
        }
        return future;
    }

    public CompletionStage<Void> call(Map<String, Object> config) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                callBlocking(config);
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(unwrapCompletion(throwable));
            }
        });
        return future;
    }

    @Override
    protected CompletionStage<Object> atomicInvokeInternal(Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs != null ? kwargs : Collections.emptyMap();
        return call(castMap(safeKwargs.get("config"))).thenApply(ignored -> null);
    }

    private void callBlocking(Map<String, Object> config) throws Exception {
        if (session == null || executable == null) {
            throw buildError(StatusCode.GRAPH_VERTEX_EXECUTION_ERROR,
                    null,
                    Map.of("reason", "node is not initialized", "node_id", nodeId));
        }
        boolean subgraph = executable.graphInvoker();
        ComponentAbility currentAbility = null;
        try {
            List<ComponentAbility> callAbilities = callAbilities();
            for (ComponentAbility ability : callAbilities) {
                currentAbility = ability;
                runExecutableWithRetry(ability, subgraph, config, null);
            }
            if (callAbilities.isEmpty()) {
                traceComponentBegin();
            }
        } catch (BaseError error) {
            LOGGER.error("Node ability call failed: node={}, ability={}", nodeId,
                    currentAbility != null ? currentAbility.name() : null, error);
            throw error;
        }

        if (streamCalled()) {
            waitForStreamDone();
        } else if (hasStreamCall && !endNode) {
            throw buildError(StatusCode.GRAPH_VERTEX_STREAM_CALL_ERROR,
                    null,
                    Map.of("reason", "no stream data in", "node_id", nodeId));
        }
        if (session.actorManager() != null) {
            session.actorManager().markProducerDone(nodeId);
        }
        traceComponentDone();
    }

    private boolean runExecutableWithRetry(ComponentAbility ability,
                                           boolean subgraph,
                                           Map<String, Object> config,
                                           Runnable readinessSignal) throws Exception {
        int maxRetries = Math.max(0, nodeConfig.maxRetries());
        double timeout = nodeConfig.timeoutSeconds();

        if (maxRetries <= 0 && (timeout < 0.0d || nearlyZero(timeout))) {
            return runExecutable(ability, subgraph, config, readinessSignal);
        }

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (timeout < 0.0d || nearlyZero(timeout)) {
                    return runExecutable(ability, subgraph, config, readinessSignal);
                }
                return runExecutableWithTimeout(ability, subgraph, config, readinessSignal, timeout);
            } catch (GraphInterrupt interrupt) {
                throw interrupt;
            } catch (TimeoutException timeoutException) {
                BaseError wrapped = buildError(StatusCode.WORKFLOW_EXECUTION_TIMEOUT,
                        timeoutException,
                        Map.of("timeout", timeout, "node_id", nodeId));
                if (attempt < maxRetries) {
                    traceInnerError(timeoutException);
                    continue;
                }
                return runErrorRecovery(ability, wrapped);
            } catch (Exception exception) {
                if (attempt < maxRetries) {
                    traceInnerError(exception);
                    continue;
                }
                return runErrorRecovery(ability, exception);
            }
        }
        return false;
    }

    private boolean runExecutableWithTimeout(ComponentAbility ability,
                                             boolean subgraph,
                                             Map<String, Object> config,
                                             Runnable readinessSignal,
                                             double timeoutSeconds)
            throws Exception {
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                return runExecutable(ability, subgraph, config, readinessSignal);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executor);
        try {
            long timeoutMillis = Math.max(1L, Math.round(timeoutSeconds * 1000L));
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = unwrapCompletion(exception);
            GraphInterrupt interrupt = findGraphInterrupt(cause);
            if (interrupt != null) {
                throw interrupt;
            }
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw new RuntimeException(cause);
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            throw timeoutException;
        }
    }

    private boolean runExecutable(ComponentAbility ability,
                                  boolean subgraph,
                                  Map<String, Object> config,
                                  Runnable readinessSignal) throws Exception {
        AtomicBoolean readinessSet = new AtomicBoolean(false);
        try {
            markNodeExecuted();
            LOGGER.info("Begin to call node [{}] ability [{}]", nodeId, ability.name());
            switch (ability) {
                case INVOKE -> runInvoke(subgraph, config);
                case STREAM -> runStream(subgraph, config);
                case COLLECT -> {
                    Map<String, Object> collectInputs = preStream(ComponentAbility.COLLECT);
                    signalReady(readinessSignal, readinessSet);
                    if (endNode) {
                        Object streamSchema = nodeConfig.streamIoConfigs().inputsSchema();
                        if (streamSchema instanceof Map<?, ?> mapSchema) {
                            collectInputs = sanitizeUnexecutedBranchInputs(collectInputs, castSchemaMap(mapSchema));
                        }
                    }
                    Object result = executable.onCollect(collectInputs, session, context);
                    postInvoke(result);
                }
                case TRANSFORM -> {
                    Map<String, Object> transformInputs = preStream(ComponentAbility.TRANSFORM);
                    signalReady(readinessSignal, readinessSet);
                    if (endNode) {
                        Object streamSchema = nodeConfig.streamIoConfigs().inputsSchema();
                        if (streamSchema instanceof Map<?, ?> mapSchema) {
                            transformInputs = sanitizeUnexecutedBranchInputs(transformInputs, castSchemaMap(mapSchema));
                        }
                    }
                    Iterator<Object> iterator = executable.onTransform(transformInputs, session, context);
                    postStream(iterator, ComponentAbility.TRANSFORM);
                }
                default -> throw new IllegalArgumentException("Unsupported ability: " + ability);
            }
            LOGGER.info("Succeed to call node [{}] ability [{}]", nodeId, ability.name());
            return true;
        } catch (GraphInterrupt interrupt) {
            LOGGER.info("Interrupt to call node [{}] ability [{}]", nodeId, ability.name());
            throw interrupt;
        } catch (BaseError error) {
            LOGGER.error("Failed to call node [{}] ability [{}]", nodeId, ability.name(), error);
            throw error;
        } catch (Exception exception) {
            GraphInterrupt interrupt = findGraphInterrupt(exception);
            if (interrupt != null) {
                LOGGER.info("Interrupt to call node [{}] ability [{}]", nodeId, ability.name());
                throw interrupt;
            }
            LOGGER.error("Failed to call node [{}]'s [{}]", nodeId, ability.name(), exception);
            throw buildError(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR,
                    exception,
                    Map.of("ability", ability.name(), "comp", nodeId, "reason", exception, "workflow", safeWorkflowId()));
        } finally {
            signalReady(readinessSignal, readinessSet);
        }
    }

    private void runInvoke(boolean subgraph, Map<String, Object> config) {
        Map<String, Object> batchInputs = preInvoke();
        if (subgraph) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put(INPUTS_KEY, batchInputs);
            wrapped.put(CONFIG_KEY, config);
            batchInputs = wrapped;
        }
        batchInputs = applyComponentInputCallbacks(WorkflowEvents.COMPONENT_BATCH_INPUT, batchInputs);
        Object results = executable.onInvoke(batchInputs, session, context);
        results = applyComponentOutputCallbacks(WorkflowEvents.COMPONENT_BATCH_OUTPUT, results);
        postInvoke(results);
    }

    private void runStream(boolean subgraph, Map<String, Object> config) throws Exception {
        Map<String, Object> batchInputs = preInvoke();
        if (subgraph) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put(INPUTS_KEY, batchInputs);
            wrapped.put(CONFIG_KEY, config);
            batchInputs = wrapped;
        }
        batchInputs = applyComponentInputCallbacks(WorkflowEvents.COMPONENT_BATCH_INPUT, batchInputs);
        Iterator<Object> iterator = executable.onStream(batchInputs, session, context);
        iterator = applyComponentStreamOutputCallbacks(WorkflowEvents.COMPONENT_STREAM_OUTPUT, iterator);
        postStream(iterator, ComponentAbility.STREAM);
    }

    private boolean runErrorRecovery(ComponentAbility ability, Exception error) throws Exception {
        if (ability != ComponentAbility.INVOKE && ability != ComponentAbility.COLLECT) {
            throw error;
        }
        ErrorRecoveryHandler handler = session != null ? session.errorRecoveryHandler() : null;
        if (handler == null) {
            throw error;
        }
        Map<String, Object> result = handler.recover(
                error, session, nodeId, ability, nodeConfig.exceptionConfig());
        if (result != null) {
            postInvoke(result);
            return true;
        }
        throw error;
    }

    private Map<String, Object> preInvoke() {
        traceComponentBegin();
        Object inputsSchema = nodeConfig.ioConfigs().inputsSchema();
        Map<String, Object> inputs = null;
        if (inputsSchema instanceof ValueTransformer transformer) {
            inputs = session.state().getInputsByTransformer(transformer);
        } else if (inputsSchema != null) {
            inputs = castMap(session.state().getInputs(inputsSchema));
        }
        if (endNode && inputs != null && inputsSchema instanceof Map<?, ?> mapSchema) {
            inputs = sanitizeEndNodeInputs(inputs, castSchemaMap(mapSchema));
        }
        traceComponentInputs(inputs);
        return inputs;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyComponentInputCallbacks(String event, Map<String, Object> inputs) {
        DecoratorFramework framework = componentCallbackFramework();
        if (framework == null) {
            return inputs;
        }
        Object[] args = new Object[]{inputs, session, context};
        Map<String, Object> kwargs = componentCallbackKwargs(inputs, args);
        Object transformed = framework.triggerTransform(event, args, kwargs);
        Object[] effectiveArgs = args;
        Map<String, Object> effectiveKwargs = kwargs;
        if (transformed instanceof CallbackDecorators.BoundArgs boundArgs) {
            effectiveArgs = boundArgs.getArgs();
            effectiveKwargs = componentCallbackKwargs(
                    firstMapArg(effectiveArgs, inputs),
                    effectiveArgs);
            effectiveKwargs.putAll(boundArgs.getKwargs());
        } else if (transformed instanceof Map<?, ?> transformedMap) {
            effectiveKwargs = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : transformedMap.entrySet()) {
                effectiveKwargs.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            Object transformedArgs = effectiveKwargs.get("_args");
            if (transformedArgs instanceof Object[] values) {
                effectiveArgs = values;
            }
        }
        trigger(framework, event, effectiveArgs, effectiveKwargs);
        Object transformedInputs = effectiveArgs.length > 0
                ? effectiveArgs[0]
                : effectiveKwargs.getOrDefault("inputs", inputs);
        if (transformedInputs == null) {
            return null;
        }
        if (transformedInputs instanceof Map<?, ?> map) {
            return castMap(map);
        }
        if (effectiveKwargs.get("inputs") instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return (Map<String, Object>) transformedInputs;
    }

    private Object applyComponentOutputCallbacks(String event, Object result) {
        DecoratorFramework framework = componentCallbackFramework();
        if (framework == null) {
            return result;
        }
        Object transformed = triggerComponentOutputTransform(framework, event, result);
        Object effectiveResult = transformed == CallbackDecorators.TRANSFORM_NOOP ? result : transformed;
        trigger(framework, event, new Object[0], resultKwargs(effectiveResult));
        return effectiveResult;
    }

    private Iterator<Object> applyComponentStreamOutputCallbacks(
            String event,
            Iterator<Object> source) {
        DecoratorFramework framework = componentCallbackFramework();
        if (framework == null || source == null) {
            return source;
        }
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return source.hasNext();
            }

            @Override
            public Object next() {
                Object current = source.next();
                Object transformed = triggerComponentOutputTransform(framework, event, current);
                Object effectiveCurrent = transformed == CallbackDecorators.TRANSFORM_NOOP ? current : transformed;
                trigger(framework, event, new Object[0], resultKwargs(effectiveCurrent));
                return effectiveCurrent;
            }
        };
    }

    private Map<String, Object> sanitizeEndNodeInputs(Map<String, Object> inputs, Map<String, Object> inputsSchema) {
        TemplateDataSourceCounter template = executable instanceof TemplateAware templateAware
                ? templateAware.templateDataSourceCounter()
                : null;
        if (template == null) {
            return inputs;
        }
        Map<String, Object> sanitized = sanitizeUnexecutedBranchInputs(inputs, inputsSchema);
        if (!(hasCall && hasStreamCall)) {
            return sanitized;
        }
        Object streamSchema = nodeConfig.streamIoConfigs().inputsSchema();
        if (!(streamSchema instanceof Map<?, ?> streamMap) || streamMap.isEmpty()) {
            return sanitized;
        }
        Set<String> streamSourceIds = collectRefSourceIds(castSchemaMap(streamMap));
        if (!streamSourceIds.isEmpty()
                && streamSourceIds.stream().noneMatch(this::isComponentExecuted)) {
            template.setDataSourceCount(0);
        }
        return sanitized;
    }

    private Map<String, Object> sanitizeUnexecutedBranchInputs(Map<String, Object> inputs,
                                                               Map<String, Object> inputsSchema) {
        sanitizeNode(inputs, inputsSchema);
        return inputs;
    }

    @SuppressWarnings("unchecked")
    private void sanitizeNode(Object inputs, Object inputsSchema) {
        if (inputs instanceof Map<?, ?> mapInputs && inputsSchema instanceof Map<?, ?> mapSchema) {
            Map<Object, Object> writableMap = (Map<Object, Object>) mapInputs;
            for (Object key : new ArrayList<>(writableMap.keySet())) {
                Object value = writableMap.get(key);
                Object schemaValue = mapSchema.containsKey(key) ? mapSchema.get(key) : "";
                if (value instanceof Map<?, ?> && schemaValue instanceof Map<?, ?>) {
                    sanitizeNode(value, schemaValue);
                } else if (value instanceof List<?> && schemaValue instanceof List<?>) {
                    sanitizeNode(value, schemaValue);
                } else {
                    sanitizeLeaf(writableMap, key, value, schemaValue);
                }
            }
            return;
        }
        if (inputs instanceof List<?> inputList && inputsSchema instanceof List<?> schemaList) {
            int size = Math.min(inputList.size(), schemaList.size());
            List<Object> writableList = (List<Object>) inputList;
            for (int index = 0; index < size; index++) {
                Object value = writableList.get(index);
                Object schemaValue = schemaList.get(index);
                if (value instanceof Map<?, ?> || value instanceof List<?>) {
                    sanitizeNode(value, schemaValue);
                } else {
                    sanitizeLeaf(writableList, index, value, schemaValue);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void sanitizeLeaf(Object container, Object key, Object value, Object refPath) {
        if (value != null && !(value instanceof PendingStreamInput)) {
            return;
        }
        if (!(refPath instanceof String path) || !SessionUtils.isRefPath(path)) {
            return;
        }
        String originKey = SessionUtils.extractOriginKey(path);
        if (originKey == null || originKey.isEmpty()) {
            return;
        }
        String componentId = originKey.split("\\.", 2)[0];
        if (isComponentExecuted(componentId)) {
            return;
        }
        if (value instanceof PendingStreamInput && isStreamSourceStillPending(componentId)) {
            return;
        }
        if (value instanceof PendingStreamInput pendingStreamInput) {
            pendingStreamInput.close();
        }
        if (container instanceof Map<?, ?> map) {
            ((Map<Object, Object>) map).put(key, "");
        } else if (container instanceof List<?> list && key instanceof Integer index) {
            ((List<Object>) list).set(index, "");
        }
    }

    private boolean isStreamSourceStillPending(String componentId) {
        VertexActorManager actorManager = session != null ? session.actorManager() : null;
        if (actorManager == null) {
            return false;
        }
        return !actorManager.shouldSanitizeStreamSource(nodeId, componentId);
    }

    private boolean isComponentExecuted(String componentId) {
        Object executedNodes = session.state().getWorkflowState("executed_nodes");
        return executedNodes instanceof List<?> nodes && nodes.contains(componentId);
    }

    @SuppressWarnings("unchecked")
    private void markNodeExecuted() {
        Object executedNodesObject = session.state().getWorkflowState("executed_nodes");
        List<String> executedNodes;
        if (executedNodesObject instanceof List<?> list) {
            executedNodes = new ArrayList<>(list.stream().map(String::valueOf).toList());
        } else {
            executedNodes = new ArrayList<>();
        }
        if (!executedNodes.contains(nodeId)) {
            executedNodes.add(nodeId);
            session.state().updateAndCommitWorkflowState(Map.of("executed_nodes", executedNodes));
        }
    }

    private Map<String, Object> postInvoke(Object results) {
        Object outputsSchema = nodeConfig.ioConfigs().outputsSchema();
        Object normalizedResults = results;
        if (outputsSchema instanceof ValueTransformer transformer) {
            normalizedResults = transformer.apply(outputAsMap(results));
        } else if (outputsSchema instanceof Map<?, ?> schema && results != null) {
            normalizedResults = SessionUtils.getBySchema(schema, outputAsMap(results));
            if (!endNode && normalizedResults instanceof Map<?, ?> selectedMap) {
                normalizedResults = filterNullValues(castMap(selectedMap));
            }
        }
        boolean endMixMode = endNode && hasCall && hasStreamCall;
        if (normalizedResults instanceof Map<?, ?> resultMap && endMixMode) {
            Map<String, Object> normalizedMap = castMap(resultMap);
            Object outputs = normalizedMap.get("output");
            if (outputs != null && !(outputs instanceof List<?>)) {
                normalizedMap.put("output", new ArrayList<>(List.of(outputs)));
            }
            Object oldOutputs = session.state().getOutputs(nodeId);
            if (oldOutputs instanceof Map<?, ?> oldMap
                    && oldMap.get("output") instanceof List<?> oldOutputList
                    && normalizedMap.get("output") instanceof List<?> newOutputList) {
                List<Object> merged = new ArrayList<>(newOutputList);
                merged.addAll(oldOutputList);
                normalizedMap.put("output", merged);
            }
            normalizedResults = normalizedMap;
        }
        if (normalizedResults != null) {
            setOutput(normalizedResults);
        }
        Map<String, Object> traceOutputs = normalizedResults instanceof Map<?, ?> map
                ? castMap(map)
                : outputAsMap(normalizedResults);
        traceComponentOutputs(traceOutputs);
        clearInteractive();
        return traceOutputs;
    }

    private Map<String, Object> preStream(ComponentAbility ability) throws Exception {
        traceComponentBegin();
        VertexActorManager actorManager = session.actorManager();
        if (actorManager == null) {
            throw buildError(StatusCode.GRAPH_VERTEX_STREAM_CALL_ERROR,
                    null,
                    Map.of("reason", "queue manager is not initialized", "node_id", nodeId));
        }
        Object inputsSchema = nodeConfig.streamIoConfigs().inputsSchema();
        if (!(inputsSchema instanceof Map<?, ?>)) {
            inputsSchema = null;
        }
        boolean enableTrace = session.tracer() != null && executable != null && !executable.skipTrace();
        Object finalInputsSchema = inputsSchema;
        return actorManager.consume(nodeId, ability, finalInputsSchema, chunk -> {
            if (enableTrace) {
                session.tracer().traceComponentStreamInput(session, chunk, false);
            }
        });
    }

    private void postStream(Iterator<Object> resultsIterator, ComponentAbility ability) throws Exception {
        boolean subGraph = session.subGraph();
        VertexActorManager actorManager = session.actorManager();
        Object outputSchema = nodeConfig.streamIoConfigs().outputsSchema();
        ValueTransformer outputTransformer = outputSchema instanceof ValueTransformer transformer ? transformer : null;
        int endStreamIndex = 0;
        if (resultsIterator != null) {
            while (resultsIterator.hasNext()) {
                Object chunk = resultsIterator.next();
                Object message = outputTransformer == null
                        ? (outputSchema != null
                        ? actorManager.streamTransform().getByDefaultTransformer(chunk, outputSchema)
                        : chunk)
                        : actorManager.streamTransform().getByDefinedTransformer(chunk, outputTransformer);
                processChunk(message, endNode, endStreamIndex, subGraph, ability);
                endStreamIndex += 1;
            }
        }
        if (endNode && subGraph) {
            actorManager.subWorkflowStream().emit(StreamEmitter.END_FRAME);
        } else {
            actorManager.endMessage(nodeId, ability);
        }
        clearInteractive();
        if (executable instanceof StreamOutputProvider streamOutputProvider) {
            Map<String, Object> result = streamOutputProvider.getStreamOutput();
            if (result != null) {
                session.state().setOutputs(result);
            }
        }
    }

    private void processChunk(Object message,
                              boolean endNodeFlag,
                              int endStreamIndex,
                              boolean subGraph,
                              ComponentAbility ability) throws Exception {
        VertexActorManager actorManager = session.actorManager();
        if (endNodeFlag && !subGraph) {
            Object streamData = message instanceof StreamSchemaMessage
                    ? message
                    : endNodeStreamData(endStreamIndex, message);
            traceComponentStreamOutput(streamData);
            VertexStreamWriterManager writerManager = session.streamWriterManager();
            if (writerManager != null && writerManager.getOutputWriter() != null) {
                writerManager.getOutputWriter().write(streamData);
            }
            return;
        }
        if (endNodeFlag) {
            Object streamData = message instanceof OutputSchemaPayload payload ? payload.payload() : message;
            traceComponentStreamOutput(streamData);
            actorManager.subWorkflowStream().emit(streamData);
            return;
        }
        boolean firstFrame = endStreamIndex == 0;
        traceComponentStreamOutput(message);
        actorManager.produce(nodeId, message, ability, firstFrame);
    }

    private void clearInteractive() {
        if (session.state().get(INTERACTIVE_INPUT) != null) {
            Map<String, Object> update = new LinkedHashMap<>();
            update.put(INTERACTIVE_INPUT, null);
            session.state().update(update);
        }
    }

    private static DecoratorFramework componentCallbackFramework() {
        return Runner.getCallbackFramework();
    }

    private static Object triggerComponentOutputTransform(DecoratorFramework framework, String event, Object result) {
        Object transformed = framework.triggerTransform(event, new Object[0], resultKwargs(result));
        if (transformed == null || transformed == CallbackDecorators.TRANSFORM_NOOP) {
            return result;
        }
        return transformed;
    }

    private static Map<String, Object> componentCallbackKwargs(Object inputs, Object[] args) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("inputs", inputs);
        values.put("session", args.length > 1 ? args[1] : null);
        values.put("context", args.length > 2 ? args[2] : null);
        values.put("_args", args);
        return values;
    }

    private static Map<String, Object> resultKwargs(Object result) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("result", result);
        return values;
    }

    private static Map<String, Object> componentResultAsMap(Object transformed, Map<String, Object> fallback) {
        if (transformed == null || transformed == CallbackDecorators.TRANSFORM_NOOP) {
            return fallback;
        }
        if (transformed instanceof Map<?, ?> map) {
            return castMap(map);
        }
        throw new IllegalArgumentException("component callback output must be a map: "
                + transformed.getClass().getName());
    }

    private static Map<String, Object> firstMapArg(Object[] args, Map<String, Object> fallback) {
        if (args.length == 0 || args[0] == null) {
            return fallback;
        }
        if (args[0] instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return fallback;
    }

    private static void trigger(DecoratorFramework framework, String event, Object[] args, Map<String, Object> kwargs) {
        framework.trigger(event, args != null ? args : new Object[0], kwargs != null ? kwargs : Map.of());
    }

    @Override
    public boolean isDone() {
        return callCount == streamCallCount
                || callCount == streamCallCount + 1
                || streamCallCount == callCount + 1;
    }

    public boolean streamCalled() {
        return streamCallCount == callCount + 1;
    }

    @Override
    public void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback) {
        CountDownLatch readyLatch = latch != null ? latch : new CountDownLatch(0);
        Consumer<Exception> safeErrorCallback = errorCallback != null ? errorCallback : ignored -> { };
        streamCallCount += 1;
        streamDone = new CompletableFuture<>();

        if (session == null || session.actorManager() == null) {
            BaseError error = buildError(StatusCode.GRAPH_VERTEX_STREAM_CALL_ERROR,
                    null,
                    Map.of("reason", "queue manager is not initialized", "node_id", nodeId));
            streamDone.complete(error);
            safeErrorCallback.accept(error);
            readyLatch.countDown();
            return;
        }

        Exception error = null;
        List<CompletableFuture<Boolean>> tasks = new ArrayList<>();
        try {
            List<ComponentAbility> abilities = streamAbilities();
            CountDownLatch abilityReadyLatch = new CountDownLatch(abilities.size());
            for (ComponentAbility ability : abilities) {
                CompletableFuture<Boolean> task = CompletableFuture.supplyAsync(() -> {
                    try {
                        return runExecutableWithRetry(ability, false, null, abilityReadyLatch::countDown);
                    } catch (Exception exception) {
                        throw new CompletionException(exception);
                    }
                }, STREAM_EXECUTOR);
                tasks.add(task);
            }
            abilityReadyLatch.await();
            readyLatch.countDown();
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<Boolean> task : tasks) {
                task.join();
            }
            LOGGER.info("Succeed to call stream-in node [{}]", nodeId);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            error = interruptedException;
            safeErrorCallback.accept(interruptedException);
        } catch (CompletionException completionException) {
            Throwable cause = unwrapCompletion(completionException);
            GraphInterrupt interrupt = findGraphInterrupt(cause);
            error = interrupt != null
                    ? interrupt
                    : cause instanceof Exception exception ? exception : new RuntimeException(cause);
            safeErrorCallback.accept(error);
        } catch (Exception exception) {
            GraphInterrupt interrupt = findGraphInterrupt(exception);
            error = interrupt != null ? interrupt : exception;
            safeErrorCallback.accept(exception);
        } finally {
            if (error == null) {
                streamDone.complete(STREAM_DONE_SUCCESS);
            } else {
                streamDone.complete(error);
            }
            traceComponentStreamInputSend();
        }
    }

    @Override
    public boolean shouldHandleMessage() {
        return !streamAbilities().isEmpty();
    }

    public void reset() {
        callCount = 0;
        streamCallCount = 0;
        streamDone.cancel(true);
        streamDone = new CompletableFuture<>();
    }

    public boolean isEndNode() {
        return endNode;
    }

    public void setEndNode(boolean endNode) {
        this.endNode = endNode;
    }

    public int callCount() {
        return callCount;
    }

    public int streamCallCount() {
        return streamCallCount;
    }

    public List<ComponentAbility> streamAbilitiesForTest() {
        return streamAbilities();
    }

    public static Set<String> collectRefSourceIds(Map<String, Object> schema) {
        Set<String> ids = new LinkedHashSet<>();
        walkSchema(schema, ids);
        return ids;
    }

    private static void walkSchema(Object value, Set<String> ids) {
        if (value instanceof Map<?, ?> map) {
            for (Object nested : map.values()) {
                walkSchema(nested, ids);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (Object nested : list) {
                walkSchema(nested, ids);
            }
            return;
        }
        if (value instanceof String path && SessionUtils.isRefPath(path)) {
            String origin = SessionUtils.extractOriginKey(path);
            if (origin != null && !origin.isEmpty()) {
                ids.add(origin.split("\\.", 2)[0]);
            }
        }
    }

    private List<ComponentAbility> streamAbilities() {
        return componentAbilities.stream()
                .filter(ability -> ability == ComponentAbility.COLLECT || ability == ComponentAbility.TRANSFORM)
                .toList();
    }

    private List<ComponentAbility> callAbilities() {
        return componentAbilities.stream()
                .filter(ability -> ability == ComponentAbility.INVOKE || ability == ComponentAbility.STREAM)
                .toList();
    }

    private void waitForStreamDone() throws Exception {
        Object result;
        try {
            if (streamCallTimeoutSeconds > 0) {
                result = streamDone.get(streamCallTimeoutSeconds, TimeUnit.SECONDS);
            } else {
                result = streamDone.get();
            }
        } catch (TimeoutException timeoutException) {
            throw buildError(StatusCode.GRAPH_VERTEX_STREAM_CALL_TIMEOUT,
                    timeoutException,
                    Map.of("timeout", streamCallTimeoutSeconds, "node_id", nodeId));
        } catch (ExecutionException executionException) {
            Throwable cause = unwrapCompletion(executionException);
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        }
        if (result instanceof Exception exception) {
            GraphInterrupt interrupt = findGraphInterrupt(exception);
            if (interrupt != null) {
                throw interrupt;
            }
            throw exception;
        }
    }

    private void traceComponentInputs(Map<String, Object> inputs) {
        if (skipTrace()) {
            return;
        }
        callStarted = true;
        boolean needSend = !hasStreamCall || streamDone.isDone();
        session.tracer().traceComponentInputs(session, inputs, needSend);
        if (SUB_WORKFLOW_COMPONENT.equals(executable.componentType())) {
            session.tracer().registerWorkflowSpanManager(session.executableId());
        }
    }

    private void traceComponentOutputs(Map<String, Object> outputs) {
        if (!skipTrace()) {
            session.tracer().traceComponentOutputs(session, outputs);
        }
    }

    private void traceComponentBegin() {
        if (skipTrace() || started) {
            return;
        }
        started = true;
        session.tracer().traceComponentBegin(session);
    }

    private void traceComponentDone() {
        if (!skipTrace()) {
            session.tracer().traceComponentDone(session);
        }
    }

    private void traceComponentStreamOutput(Object chunk) {
        if (!skipTrace()) {
            session.tracer().traceComponentStreamOutput(session, chunk);
        }
    }

    private void traceError(Throwable error) {
        if (!skipTrace()) {
            session.tracer().traceError(session, error);
        }
    }

    private void traceInnerError(Throwable error) {
        if (skipTrace()) {
            return;
        }
        Map<String, Object> innerError = new LinkedHashMap<>();
        if (error instanceof BaseError baseError) {
            innerError.put("error_code", baseError.getCode());
            innerError.put("message", baseError.getMessage());
        } else {
            innerError.put("error_code", StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR.getCode());
            innerError.put("message", error.getMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inner_error", innerError);
        data.put("current_time", OffsetDateTime.now(ZoneOffset.UTC).toString());
        session.tracer().trace(session, data);
    }

    private void traceComponentStreamInputSend() {
        if (skipTrace()) {
            return;
        }
        if (!hasCall || callStarted) {
            session.tracer().traceComponentStreamInput(session, Collections.emptyMap(), true);
        }
    }

    private boolean skipTrace() {
        return session == null || session.tracer() == null || executable == null || executable.skipTrace();
    }

    private void emitEvent(String event, Map<String, Object> payload) {
        VertexEventSink eventSink = session != null ? session.eventSink() : null;
        if (eventSink != null) {
            eventSink.emit(event, payload);
        }
    }

    private String safeWorkflowId() {
        return session != null ? session.workflowId() : "";
    }

    private static boolean nearlyZero(double value) {
        return Math.abs(value) <= 1.0e-9d;
    }

    @SuppressWarnings("unchecked")
    private static Executable<Map<String, Object>, Object> castExecutable(Executable<Map<String, Object>, ?> executable) {
        return (Executable<Map<String, Object>, Object>) executable;
    }

    private static void signalReady(Runnable readinessSignal, AtomicBoolean readinessSet) {
        if (readinessSignal != null && readinessSet.compareAndSet(false, true)) {
            readinessSignal.run();
        }
    }

    private static Map<String, Object> filterNullValues(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static Map<String, Object> endNodeStreamData(int index, Object payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", END_NODE_STREAM);
        data.put("index", index);
        data.put("payload", payload);
        return data;
    }

    private static Map<String, Object> safeConfig(Map<String, Object> config) {
        return config == null ? Collections.emptyMap() : config;
    }

    private static BaseError buildError(StatusCode status, Throwable cause, Map<String, Object> params) {
        return ErrorHelper.buildError(status, null, null, cause, params);
    }

    private static Throwable unwrapCompletion(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
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

    private void setOutput(Object output) {
        if (output instanceof Map<?, ?> map) {
            session.state().setOutputs(castMap(map));
            return;
        }
        if (session.state() instanceof WorkflowCommitState commitState) {
            CommitStateLike ioState = commitState.getIoState();
            if (ioState != null) {
                ioState.updateById(nodeId, Map.of(nodeId, output));
                return;
            }
        }
        session.state().setOutputs(outputAsMap(output));
    }

    private static Map<String, Object> outputAsMap(Object value) {
        if (value == null) {
            return PYTHON_NONE_MAP;
        }
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return new LinkedHashMap<>(Map.of("output", value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value == null) {
            return PYTHON_NONE_MAP;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        throw new IllegalArgumentException("value is not a map: " + value.getClass().getName());
    }

    private static Map<String, Object> castSchemaMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * Mirrors Python's {@code NodeSession} interaction surface used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public abstract static class VertexSession extends BaseSession implements GraphSession {
        @Override
        public abstract VertexState state();

        public VertexNodeConfig nodeConfig() {
            return new VertexNodeConfig();
        }

        public VertexSession nodeSession(String nodeId) {
            return this;
        }

        public String workflowId() {
            return "";
        }

        public String parentId() {
            return "";
        }

        public boolean subGraph() {
            return false;
        }

        public String executableId() {
            return "";
        }

        public int streamCallTimeoutSeconds() {
            return 10;
        }

        public VertexActorManager actorManager() {
            return PYTHON_NONE_ACTOR_MANAGER;
        }

        public VertexStreamWriterManager streamWriterManager() {
            return PYTHON_NONE_STREAM_WRITER_MANAGER;
        }

        public VertexTraceSink tracer() {
            return PYTHON_NONE_TRACE_SINK;
        }

        public ErrorRecoveryHandler errorRecoveryHandler() {
            return PYTHON_NONE_ERROR_RECOVERY_HANDLER;
        }

        public VertexEventSink eventSink() {
            return PYTHON_NONE_EVENT_SINK;
        }
    }

    /**
     * Mirrors Python's {@code self._session.state()} calls in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface VertexState extends com.openjiuwen.core.session.state.SessionStateAccess {
        Map<String, Object> getInputs(Object schema);

        Map<String, Object> getInputsByTransformer(ValueTransformer transformer);

        Object getOutputs(String nodeId);

        void setOutputs(Map<String, Object> outputs);

        Object getWorkflowState(String key);

        void updateAndCommitWorkflowState(Map<String, Object> data);

        Object get(String key);

        void update(Map<String, Object> data);
    }

    /**
     * Mirrors Python's {@code actor_manager()} stream coordination used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface VertexActorManager {
        Map<String, Object> consume(String nodeId,
                                    ComponentAbility ability,
                                    Object inputsSchema,
                                    Consumer<Object> streamCallback) throws Exception;

        void produce(String nodeId, Object message, ComponentAbility ability, boolean firstFrame) throws Exception;

        void endMessage(String nodeId, ComponentAbility ability) throws Exception;

        void markProducerDone(String nodeId);

        default boolean shouldSanitizeStreamSource(String nodeId, String componentId) {
            return true;
        }

        default VertexStreamTransform streamTransform() {
            return new VertexStreamTransform() {
            };
        }

        default StreamEmitter subWorkflowStream() {
            return new StreamEmitter();
        }
    }

    /**
     * Mirrors Python's stream transform calls used by {@code Vertex._post_stream} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface VertexStreamTransform {
        default Object getByDefaultTransformer(Object chunk, Object outputSchema) {
            if (outputSchema instanceof Map<?, ?> schema && chunk instanceof Map<?, ?> mapChunk) {
                return SessionUtils.getBySchema(schema, castMap(mapChunk));
            }
            return chunk;
        }

        default Object getByDefinedTransformer(Object chunk, ValueTransformer transformer) {
            return transformer.apply(Vertex.castMap(chunk));
        }
    }

    /**
     * Mirrors Python's {@code stream_writer_manager()} dependency used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface VertexStreamWriterManager {
        VertexStreamWriter getOutputWriter();
    }

    /**
     * Mirrors Python's stream output writer used by {@code Vertex._process_chunk} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface VertexStreamWriter {
        void write(Object streamData) throws Exception;
    }

    /**
     * Mirrors Python's {@code TracerWorkflowUtils} calls used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface VertexTraceSink {
        default void traceComponentInputs(VertexSession session, Map<String, Object> inputs, boolean send) {
        }

        default void traceComponentOutputs(VertexSession session, Map<String, Object> outputs) {
        }

        default void traceComponentBegin(VertexSession session) {
        }

        default void traceComponentDone(VertexSession session) {
        }

        default void traceComponentStreamInput(VertexSession session, Object chunk, boolean send) {
        }

        default void traceComponentStreamOutput(VertexSession session, Object chunk) {
        }

        default void traceError(VertexSession session, Throwable error) {
        }

        default void trace(VertexSession session, Map<String, Object> data) {
        }

        default void registerWorkflowSpanManager(String executableId) {
        }
    }

    /**
     * Mirrors Python's component error recovery callback used by {@code Vertex._run_error_recovery} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface ErrorRecoveryHandler {
        Map<String, Object> recover(Exception error,
                                    VertexSession session,
                                    String nodeId,
                                    ComponentAbility ability,
                                    Object exceptionConfig) throws Exception;
    }

    /**
     * Mirrors Python's callback trigger calls used by {@code Vertex.__call__} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface VertexEventSink {
        void emit(String event, Map<String, Object> payload);
    }

    @FunctionalInterface
    /**
     * Mirrors Python's dynamic input/output transformer boundary used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface ValueTransformer {
        Map<String, Object> apply(Map<String, Object> source);
    }

    /**
     * Mirrors Python's pending {@code AsyncGenerator} stream input branch used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface PendingStreamInput extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Mirrors Python's {@code StreamSchemas} marker used by {@code Vertex._process_chunk} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface StreamSchemaMessage {
    }

    /**
     * Mirrors Python's {@code OutputSchema} payload access used by {@code Vertex._process_chunk} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface OutputSchemaPayload {
        Object payload();
    }

    /**
     * Mirrors Python's optional {@code set_mix} executable hook used by {@code Vertex.init} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface MixConfigurable {
        void setMix();
    }

    /**
     * Mirrors Python's optional executable template access used by {@code Vertex._sanitize_end_node_inputs} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface TemplateAware {
        TemplateDataSourceCounter templateDataSourceCounter();
    }

    /**
     * Mirrors Python's template {@code set_data_source_count} call used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface TemplateDataSourceCounter {
        void setDataSourceCount(int count);
    }

    /**
     * Mirrors Python's optional {@code get_stream_output} hook used by {@code Vertex._post_stream} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public interface StreamOutputProvider {
        Map<String, Object> getStreamOutput();
    }

    /**
     * Mirrors Python's node IO config fields consumed by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public static final class VertexIoConfig {
        private final Object inputsSchema;
        private final Object outputsSchema;

        public VertexIoConfig() {
            this(null, null);
        }

        public VertexIoConfig(Object inputsSchema, Object outputsSchema) {
            this.inputsSchema = inputsSchema;
            this.outputsSchema = outputsSchema;
        }

        public Object inputsSchema() {
            return inputsSchema;
        }

        public Object outputsSchema() {
            return outputsSchema;
        }
    }

    /**
     * Mirrors Python's node config fields consumed by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    public static final class VertexNodeConfig {
        private final List<ComponentAbility> abilities;
        private final VertexIoConfig ioConfigs;
        private final VertexIoConfig streamIoConfigs;
        private final int maxRetries;
        private final double timeoutSeconds;
        private final Object exceptionConfig;

        public VertexNodeConfig() {
            this(List.of(ComponentAbility.INVOKE), new VertexIoConfig(), new VertexIoConfig(), 0, -1.0d, null);
        }

        public VertexNodeConfig(List<ComponentAbility> abilities,
                                VertexIoConfig ioConfigs,
                                VertexIoConfig streamIoConfigs,
                                int maxRetries,
                                double timeoutSeconds,
                                Object exceptionConfig) {
            this.abilities = abilities == null ? List.of() : List.copyOf(abilities);
            this.ioConfigs = ioConfigs == null ? new VertexIoConfig() : ioConfigs;
            this.streamIoConfigs = streamIoConfigs == null ? new VertexIoConfig() : streamIoConfigs;
            this.maxRetries = maxRetries;
            this.timeoutSeconds = timeoutSeconds;
            this.exceptionConfig = exceptionConfig;
        }

        public List<ComponentAbility> abilities() {
            return abilities;
        }

        public VertexIoConfig ioConfigs() {
            return ioConfigs;
        }

        public VertexIoConfig streamIoConfigs() {
            return streamIoConfigs;
        }

        public int maxRetries() {
            return maxRetries;
        }

        public double timeoutSeconds() {
            return timeoutSeconds;
        }

        public Object exceptionConfig() {
            return exceptionConfig;
        }
    }
}
