/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.graph.CompiledGraph;
import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.graph.stream_actor.ActorManager;
import com.openjiuwen.core.graph.stream_actor.ActorManagerSession;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.workflow.CompIOConfig;
import com.openjiuwen.core.workflow.NodeSpec;
import com.openjiuwen.core.workflow.SchemaOrTransformer;
import com.openjiuwen.core.workflow.WorkflowConfig;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Mirrors Python's workflow session runtime used by
 * {@code openjiuwen/core/workflow/workflow.py}.
 */
public class WorkflowRuntimeSession extends Vertex.VertexSession
        implements ActorManagerSession, CompiledGraph.GraphRuntimeSession {

    private final BaseSession parent;
    private final String sessionId;
    private final WorkflowRuntimeState state;
    private final Object callbackManager;
    private final WorkflowRuntimeConfig config = new WorkflowRuntimeConfig();
    private final String parentId;
    private final String executableId;
    private final boolean subGraph;
    private String workflowId;
    private String nodeId;
    private String nodeType;
    private String mainWorkflowId;
    private int workflowNestingDepth;
    private StreamWriterManager streamWriterManager;
    private ActorManager actorManager;
    private CompiledGraph.GraphCheckpointer checkpointer;
    private Vertex.VertexTraceSink tracer = new Vertex.VertexTraceSink() {
    };

    public WorkflowRuntimeSession(String workflowId, BaseSession parent, String sessionId,
                                  WorkflowCommitState state, Object callbackManager) {
        this(workflowId, parent, sessionId, state, callbackManager, "", "", workflowId, workflowId, 0);
    }

    public WorkflowRuntimeSession(String workflowId, BaseSession parent, String sessionId,
                                  WorkflowCommitState state, Object callbackManager,
                                  String parentId, String executableId, String nodeId,
                                  String nodeType, int workflowNestingDepth) {
        this(workflowId, parent, sessionId, state, callbackManager, parentId, executableId,
                nodeId, nodeType, workflowNestingDepth, false);
    }

    public WorkflowRuntimeSession(String workflowId, BaseSession parent, String sessionId,
                                  WorkflowCommitState state, Object callbackManager,
                                  String parentId, String executableId, String nodeId,
                                  String nodeType, int workflowNestingDepth, boolean subGraph) {
        this.workflowId = workflowId != null ? workflowId : "";
        this.parent = parent;
        this.sessionId = sessionId != null && !sessionId.isBlank()
                ? sessionId
                : UUID.randomUUID().toString().replace("-", "");
        this.callbackManager = callbackManager;
        this.parentId = parentId != null ? parentId : "";
        this.executableId = executableId != null ? executableId : "";
        String stateNodeId = this.parentId.isBlank()
                ? WorkflowRuntimeState.DEFAULT_NODE_ID
                : this.executableId.isBlank() ? WorkflowRuntimeState.DEFAULT_NODE_ID : this.executableId;
        this.state = WorkflowRuntimeState.from(state, this.parentId, stateNodeId);
        this.subGraph = subGraph;
        this.nodeId = nodeId != null ? nodeId : "";
        this.nodeType = nodeType != null ? nodeType : "";
        this.mainWorkflowId = this.workflowId;
        this.workflowNestingDepth = workflowNestingDepth;
    }

    public static WorkflowRuntimeSession nodeSession(BaseSession parent, String nodeId) {
        WorkflowRuntimeSession runtimeParent = parent instanceof WorkflowRuntimeSession runtime
                ? runtime
                : new WorkflowRuntimeSession("", parent, null, WorkflowRuntimeState.create(), null);
        String nodeParentId = runtimeParent.executableId();
        String nodeExecutableId = nodeParentId == null || nodeParentId.isBlank()
                ? nodeId
                : nodeParentId + "." + nodeId;
        WorkflowRuntimeSession nodeSession = new WorkflowRuntimeSession(
                runtimeParent.workflowId(),
                runtimeParent,
                runtimeParent.sessionId(),
                runtimeParent.state().createNodeState(nodeExecutableId, nodeParentId),
                runtimeParent.callbackManager(),
                nodeParentId,
                nodeExecutableId,
                nodeId,
                nodeId,
                runtimeParent.workflowNestingDepth(),
                runtimeParent.subGraph());
        nodeSession.config().setEnvs(runtimeParent.config().getEnvs());
        nodeSession.config().addWorkflowConfigs(runtimeParent.config().getWorkflowConfigs());
        nodeSession.setMainWorkflowId(runtimeParent.mainWorkflowId());
        nodeSession.setActorManager(runtimeParent.runtimeActorManager());
        nodeSession.setStreamWriterManager(runtimeParent.runtimeStreamWriterManager());
        nodeSession.setCheckpointer(runtimeParent.checkpointer());
        return nodeSession;
    }

    @Override
    public Vertex.VertexSession nodeSession(String nodeId) {
        return WorkflowRuntimeSession.nodeSession(this, nodeId);
    }

    public BaseSession parent() {
        return parent;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public String workflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId != null ? workflowId : "";
        if (mainWorkflowId == null || mainWorkflowId.isBlank()) {
            this.mainWorkflowId = this.workflowId;
        }
    }

    @Override
    public WorkflowRuntimeState state() {
        return state;
    }

    @Override
    public WorkflowRuntimeConfig config() {
        return config;
    }

    @Override
    public String parentId() {
        return parentId;
    }

    @Override
    public boolean subGraph() {
        return subGraph;
    }

    @Override
    public String executableId() {
        return executableId;
    }

    public String nodeId() {
        return nodeId;
    }

    public String nodeType() {
        return nodeType;
    }

    @Override
    public void setNodeType(String nodeType) {
        this.nodeType = nodeType != null ? nodeType : "";
    }

    public String mainWorkflowId() {
        return mainWorkflowId;
    }

    public void setMainWorkflowId(String mainWorkflowId) {
        this.mainWorkflowId = mainWorkflowId;
    }

    public int workflowNestingDepth() {
        return workflowNestingDepth;
    }

    public Object callbackManager() {
        return callbackManager;
    }

    @Override
    public Vertex.VertexNodeConfig nodeConfig() {
        Object workflowConfig = config.getWorkflowConfig(workflowId);
        if (!(workflowConfig instanceof WorkflowConfig wfConfig)
                || wfConfig.getSpec() == null
                || nodeId == null
                || !wfConfig.getSpec().getCompConfigs().containsKey(nodeId)) {
            return new Vertex.VertexNodeConfig();
        }
        NodeSpec nodeConfig = wfConfig.getSpec().getCompConfigs().get(nodeId);
        CompIOConfig io = nodeConfig.getIoConfigs();
        CompIOConfig streamIo = nodeConfig.getStreamIoConfigs();
        return new Vertex.VertexNodeConfig(
                nodeConfig.getAbilities(),
                new Vertex.VertexIoConfig(
                        io != null ? unwrapSchema(io.getInputsSchema()) : null,
                        io != null ? unwrapSchema(io.getOutputsSchema()) : null),
                new Vertex.VertexIoConfig(
                        streamIo != null ? unwrapSchema(streamIo.getInputsSchema()) : null,
                        streamIo != null ? unwrapSchema(streamIo.getOutputsSchema()) : null),
                nodeConfig.getMaxRetries(),
                nodeConfig.getTimeout(),
                nodeConfig.getExceptionConfig());
    }

    @Override
    public Vertex.VertexActorManager actorManager() {
        return actorManager == null ? null : new ActorManagerAdapter(actorManager);
    }

    public ActorManager runtimeActorManager() {
        return actorManager;
    }

    public void setActorManager(ActorManager actorManager) {
        this.actorManager = actorManager;
    }

    @Override
    public Vertex.VertexStreamWriterManager streamWriterManager() {
        return streamWriterManager == null ? null : new StreamWriterManagerAdapter(streamWriterManager);
    }

    public StreamWriterManager runtimeStreamWriterManager() {
        return streamWriterManager;
    }

    public void setStreamWriterManager(StreamWriterManager streamWriterManager) {
        this.streamWriterManager = streamWriterManager;
        if (!(parent instanceof WorkflowRuntimeSession) && streamWriterManager != null) {
            Tracer runtimeTracer = new Tracer();
            runtimeTracer.init(streamWriterManager);
            this.tracer = runtimeTracer;
        }
    }

    @Override
    public Vertex.VertexTraceSink tracer() {
        if (parent instanceof WorkflowRuntimeSession runtimeSession) {
            return runtimeSession.tracer();
        }
        return tracer;
    }

    @Override
    public CompiledGraph.WorkflowState workflowState() {
        return state;
    }

    @Override
    public CompiledGraph.GraphCheckpointer checkpointer() {
        return checkpointer;
    }

    public void setCheckpointer(CompiledGraph.GraphCheckpointer checkpointer) {
        this.checkpointer = checkpointer;
    }

    public Object getEnv(String key) {
        return config.getEnv(key);
    }

    public Object getGlobalState(String path) {
        return state.getGlobal(path);
    }

    public String getComponentId() {
        return nodeId;
    }

    private static Object unwrapSchema(SchemaOrTransformer schema) {
        if (schema == null) {
            return null;
        }
        if (schema.isSchema()) {
            return schema.getSchema();
        }
        if (schema.isTransformer()) {
            return (Vertex.ValueTransformer) state -> {
                Object value = schema.getTransformer().apply(state);
                if (value instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedMap = (Map<String, Object>) map;
                    return typedMap;
                }
                return Map.of();
            };
        }
        return null;
    }

    private static final class ActorManagerAdapter implements Vertex.VertexActorManager {
        private final ActorManager delegate;

        private ActorManagerAdapter(ActorManager delegate) {
            this.delegate = delegate;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> consume(String nodeId, ComponentAbility ability, Object inputsSchema,
                                           Consumer<Object> streamCallback) {
            Map<String, Object> schema = inputsSchema instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            Consumer<Map<String, Object>> callback = streamCallback == null
                    ? null
                    : item -> streamCallback.accept(item);
            return delegate.consume(nodeId, ability, schema, callback);
        }

        @Override
        public void produce(String nodeId, Object message, ComponentAbility ability, boolean firstFrame) {
            delegate.produce(nodeId, message, ability, firstFrame);
        }

        @Override
        public void endMessage(String nodeId, ComponentAbility ability) {
            delegate.endMessage(nodeId, ability);
        }

        @Override
        public void markProducerDone(String nodeId) {
            delegate.markProducerDone(nodeId);
        }

        @Override
        public StreamEmitter subWorkflowStream() {
            return new StreamEmitter(delegate.subWorkflowStream());
        }

        @Override
        public boolean shouldSanitizeStreamSource(String nodeId, String componentId) {
            return delegate.shouldSanitizeStreamSource(nodeId, componentId);
        }
    }

    private static final class StreamWriterManagerAdapter implements Vertex.VertexStreamWriterManager {
        private final StreamWriterManager delegate;

        private StreamWriterManagerAdapter(StreamWriterManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public Vertex.VertexStreamWriter getOutputWriter() {
            return data -> delegate.getOutputWriter().write(data);
        }

        public Vertex.VertexStreamWriter getCustomWriter() {
            return data -> delegate.getCustomWriter().write(data);
        }
    }
}
