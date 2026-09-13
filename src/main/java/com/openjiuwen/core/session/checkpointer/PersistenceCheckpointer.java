/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.Serializer;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence-based checkpointer implementation using BaseKVStore.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.persistence.PersistenceCheckpointer}.
 * <p>
 * This checkpointer delegates to {@link PersistenceAgentStorage}, {@link PersistenceWorkflowStorage},
 * and {@link PersistenceGraphStore} for saving/recovering agent, workflow, and graph state respectively.
 * 
 * @since 0.1.7
 */
public class PersistenceCheckpointer extends Checkpointer {
    private static final String JAVA_SERIALIZATION_TYPE = "java";
    private static final String LEGACY_JAVA_SERIALIZATION_TYPE = "java_serialized";
    private static final Serializer STATE_SERIALIZER = Serializer.create(JAVA_SERIALIZATION_TYPE);

    private final BaseKVStore kvStore;
    private final PersistenceAgentStorage agentStorage;
    private final PersistenceWorkflowStorage workflowStorage;
    private final PersistenceGraphStore graphStoreField;

    /**
     * PersistenceCheckpointer.
     * 
     * @param kvStore kvStore
     * @since 0.1.7
     */
    public PersistenceCheckpointer(BaseKVStore kvStore) {
        this.kvStore = kvStore;
        this.agentStorage = new PersistenceAgentStorage(kvStore);
        this.workflowStorage = new PersistenceWorkflowStorage(kvStore);
        this.graphStoreField = new PersistenceGraphStore(kvStore);
    }

    /**
     * preAgentExecute.
     * 
     * @param session session
     * @param inputs inputs
     * @since 0.1.7
     */
    @Override
    public void preAgentExecute(BaseSession session, Object inputs) {
        String sessionId = session.sessionId();
        Loggers.SESSION.info("Agent checkpoint restore initiated, sessionId={}", sessionId);
        agentStorage.recover(session);

        if (inputs != null) {
            session.state().update(Map.of(Constant.INTERACTIVE_INPUT, List.of(inputs)));
        }
    }

    /**
     * interruptAgentExecute.
     * 
     * @param session session
     * @since 0.1.7
     */
    @Override
    public void interruptAgentExecute(BaseSession session) {
        String sessionId = session.sessionId();
        Loggers.SESSION.info("Agent checkpoint save on interrupt, sessionId={}", sessionId);
        agentStorage.save(session);
    }

    /**
     * postAgentExecute.
     * 
     * @param session session
     * @since 0.1.7
     */
    @Override
    public void postAgentExecute(BaseSession session) {
        String sessionId = session.sessionId();
        Loggers.SESSION.info("Agent checkpoint save on completion, sessionId={}", sessionId);
        agentStorage.save(session);
    }

    /**
     * preWorkflowExecute.
     * 
     * @param session session
     * @param inputs inputs
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
        String workflowId = getWorkflowId(session);
        String sessionId = session.sessionId();
        Loggers.SESSION.info("Workflow checkpoint restore initiated, sessionId={}, workflowId={}", sessionId,
                workflowId);

        if (inputs != null) {
            workflowStorage.recover(session, inputs);
        } else {
            if (!workflowStorage.isExists(session)) {
                return;
            }
            Object forceDelete = session.config() != null
                    ? session.config().getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false)
                    : false;
            if (Boolean.TRUE.equals(forceDelete)) {
                Loggers.SESSION.info("Force clearing workflow checkpoints, sessionId={}, workflowId={}", sessionId,
                        workflowId);
                graphStoreField.delete(sessionId, workflowId);
                workflowStorage.clear(workflowId, sessionId);
            } else {
                throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_PRE_WORKFLOW_EXECUTION_ERROR, "session_id",
                        sessionId, "workflow", workflowId, "reason",
                        "workflow state exists but non-interactive input and cleanup is disabled");
            }
        }
    }

    /**
     * postWorkflowExecute.
     * 
     * @param session session
     * @param result result
     * @param exception exception
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public void postWorkflowExecute(BaseSession session, Object result, Exception exception) {
        String workflowId = getWorkflowId(session);
        String sessionId = session.sessionId();

        if (exception != null) {
            Loggers.SESSION.info("Workflow checkpoint save on exception, sessionId={}, workflowId={}", sessionId,
                    workflowId);
            workflowStorage.save(session);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(exception);
        }

        if (result instanceof Map<?, ?> resultMap && resultMap.containsKey(PregelConstants.TASK_STATUS_INTERRUPT)) {
            Loggers.SESSION.info("Workflow checkpoint save on interrupt, sessionId={}, workflowId={}", sessionId,
                    workflowId);
            workflowStorage.save(session);
            return;
        }

        // Normal completion — clear checkpoints
        Loggers.SESSION.info("Workflow checkpoint cleared on completion, sessionId={}, workflowId={}", sessionId,
                workflowId);
        graphStoreField.delete(sessionId, workflowId);
        workflowStorage.clear(workflowId, sessionId);
    }

    /**
     * sessionExists.
     * 
     * @param sessionId sessionId
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean sessionExists(String sessionId) {
        if (kvStore == null) {
            return false;
        }
        String prefix = TenantKVStoreKeyResolver.resolvePrefix(sessionId + ":");
        Map<String, Object> keys = kvStore.getByPrefix(prefix);
        return keys != null && !keys.isEmpty();
    }

    /**
     * release.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    @Override
    public void release(String sessionId) {
        if (kvStore == null) {
            Loggers.SESSION.warning("Cannot release resources: KV store is null, sessionId={}", sessionId);
            return;
        }
        Loggers.SESSION.info("Session cleared, sessionId={}", sessionId);
        String prefix = TenantKVStoreKeyResolver.resolvePrefix(sessionId + ":");
        kvStore.deleteByPrefix(prefix, null);
        Loggers.SESSION.info("All session resources released, sessionId={}", sessionId);
    }

    /**
     * Release resources for a specific agent under a session.
     * 
     * @param sessionId the session ID
     * @param agentId the agent ID
     * @since 0.1.7
     */
    public void release(String sessionId, String agentId) {
        if (kvStore == null) {
            return;
        }
        if (agentId != null) {
            Loggers.SESSION.info("Agent checkpoint cleared, sessionId={}, agentId={}", sessionId, agentId);
            agentStorage.clear(agentId, sessionId);
        } else {
            release(sessionId);
        }
    }

    /**
     * graphStore.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Store graphStore() {
        return graphStoreField;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        kvStore.close();
    }

    /**
     * Persistence-based agent state storage.
     */
    static class PersistenceAgentStorage extends Storage {
        private static final String STATE_BLOBS = "agent_state_blobs";
        private static final String STATE_BLOBS_DUMP_TYPE = "agent_state_blobs_dump_type";
        private static final int KEY_NUMS = 2;

        private final BaseKVStore kvStore;

        PersistenceAgentStorage(BaseKVStore kvStore) {
            this.kvStore = kvStore;
        }

        /**
         * save.
         * 
         * @param session session
         * @since 0.1.7
         */
        @Override
        public void save(BaseSession session) {
            Map<String, Object> state = session.state().getState();
            Serializer.TypedBytes serializedState = serializeState(state);
            String sessionId = session.sessionId();
            String agentId = getAgentId(session);

            String dumpTypeKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);

            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.set(dumpTypeKey, serializedState.type());
            pipeline.set(blobKey, serializedState.data());
            pipeline.execute();

            Loggers.SESSION.debug("Agent state saved, sessionId={}, agentId={}", sessionId, agentId);
        }

        /**
         * recover.
         * 
         * @param session session
         * @param inputs inputs
         * @since 0.1.7
         */
        @Override
        @SuppressWarnings("unchecked")
        public void recover(BaseSession session, InteractiveInput inputs) {
            String sessionId = session.sessionId();
            String agentId = getAgentId(session);

            String dumpTypeKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);

            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.get(dumpTypeKey);
            pipeline.get(blobKey);
            List<Object> results = pipeline.execute();

            if (results == null || results.size() != KEY_NUMS) {
                Loggers.SESSION.debug("No agent state found, sessionId={}, agentId={}", sessionId, agentId);
                return;
            }

            deserializeState(results.get(0), results.get(1)).filter(Map.class::isInstance).ifPresent(state -> {
                session.state().setState((Map<String, Object>) state);
                Loggers.SESSION.debug("Agent state recovered, sessionId={}, agentId={}", sessionId, agentId);
            });
        }

        /**
         * clear.
         * 
         * @param id id
         * @since 0.1.7
         */
        @Override
        public void clear(String id) {
            // id is agentId here — needs sessionId too; use clear(agentId, sessionId)
        }

        /**
         * clear.
         * 
         * @param agentId agentId
         * @param sessionId sessionId
         * @since 0.1.7
         */
        public void clear(String agentId, String sessionId) {
            String dumpTypeKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);
            kvStore.delete(dumpTypeKey);
            kvStore.delete(blobKey);
            Loggers.SESSION.debug("Agent checkpoint cleared, sessionId={}, agentId={}", sessionId, agentId);
        }

        /**
         * isExists.
         * 
         * @param session session
         * @return the result
         * @since 0.1.7
         */
        @Override
        public boolean isExists(BaseSession session) {
            String sessionId = session.sessionId();
            String agentId = getAgentId(session);

            String dumpTypeKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);

            return kvStore.isExists(dumpTypeKey) && kvStore.isExists(blobKey);
        }

        /**
         * getAgentId.
         * 
         * @param session session
         * @return the result
         * @since 0.1.7
         */
        private static String getAgentId(BaseSession session) {
            try {
                Object agentId = session.getClass().getMethod("agentId").invoke(session);
                if (agentId instanceof String text && !text.isBlank()) {
                    return text;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall back to the session ID when the session does not expose agentId().
            }
            return session.sessionId();
        }
    }

    /**
     * Persistence-based workflow state storage.
     */
    static class PersistenceWorkflowStorage extends Storage {
        private static final String STATE_BLOBS = "workflow_state_blobs";
        private static final String STATE_BLOBS_DUMP_TYPE = "workflow_state_blobs_dump_type";
        private static final String UPDATE_BLOBS = "workflow_update_blobs";
        private static final String UPDATE_BLOBS_DUMP_TYPE = "workflow_update_blobs_dump_type";
        private static final int KEY_NUMS = 4;

        private final BaseKVStore kvStore;

        PersistenceWorkflowStorage(BaseKVStore kvStore) {
            this.kvStore = kvStore;
        }

        /**
         * save.
         * 
         * @param session session
         * @since 0.1.7
         */
        @Override
        public void save(BaseSession session) {
            Map<String, Object> state = session.state().getState();
            Serializer.TypedBytes serializedState = serializeState(state);
            String workflowId = getWorkflowId(session);
            String sessionId = session.sessionId();

            KVStorePipeline pipeline = kvStore.pipeline();

            // Save main state
            String dumpTypeKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String blobKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);
            pipeline.set(dumpTypeKey, serializedState.type());
            pipeline.set(blobKey, serializedState.data());

            // Save updates if state supports commits
            if (session.state() instanceof WorkflowCommitState workflowState) {
                Map<String, Object> updates = workflowState.getUpdates();
                if (updates != null) {
                    Serializer.TypedBytes serializedUpdates = serializeState(updates);
                    String updatesDumpTypeKey =
                        resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS_DUMP_TYPE);
                    String updatesBlobKey =
                        resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS);
                    pipeline.set(updatesDumpTypeKey, serializedUpdates.type());
                    pipeline.set(updatesBlobKey, serializedUpdates.data());
                }
            }

            pipeline.execute();
            Loggers.SESSION.debug("Workflow state saved, sessionId={}, workflowId={}", sessionId, workflowId);
        }

        /**
         * recover.
         * 
         * @param session session
         * @param inputs inputs
         * @since 0.1.7
         */
        @Override
        @SuppressWarnings("unchecked")
        public void recover(BaseSession session, InteractiveInput inputs) {
            String workflowId = getWorkflowId(session);
            String sessionId = session.sessionId();

            KVStorePipeline pipeline = kvStore.pipeline();
            String stateDumpTypeKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String stateBlobKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);
            String updatesDumpTypeKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS_DUMP_TYPE);
            String updatesBlobKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS);

            pipeline.get(stateDumpTypeKey);
            pipeline.get(stateBlobKey);
            pipeline.get(updatesDumpTypeKey);
            pipeline.get(updatesBlobKey);
            List<Object> results = pipeline.execute();

            if (results == null || results.size() != KEY_NUMS) {
                Loggers.SESSION.warning("Unexpected key count during workflow recovery, sessionId={}, workflowId={}",
                        sessionId, workflowId);
                return;
            }

            // Recover state
            deserializeState(results.get(0), results.get(1)).filter(Map.class::isInstance)
                    .ifPresent(state -> session.state().setState((Map<String, Object>) state));

            // Process interactive inputs
            if (inputs != null) {
                processInteractiveInputs(session, inputs);
            }

            // Recover updates
            if (session.state() instanceof WorkflowCommitState workflowState) {
                deserializeState(results.get(2), results.get(3)).filter(Map.class::isInstance).ifPresent(updates -> {
                    workflowState.setUpdates((Map<String, Object>) updates);
                    workflowState.commit();
                });
            }
        }

        /**
         * clear.
         * 
         * @param id id
         * @since 0.1.7
         */
        @Override
        public void clear(String id) {
            // id is workflowId here — needs sessionId too
        }

        /**
         * clear.
         * 
         * @param workflowId workflowId
         * @param sessionId sessionId
         * @since 0.1.7
         */
        public void clear(String workflowId, String sessionId) {
            String stateDumpTypeKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String stateBlobKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);
            String updatesDumpTypeKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS_DUMP_TYPE);
            String updatesBlobKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS);

            kvStore.delete(stateDumpTypeKey);
            kvStore.delete(stateBlobKey);
            kvStore.delete(updatesDumpTypeKey);
            kvStore.delete(updatesBlobKey);

            Loggers.SESSION.debug("Workflow checkpoint cleared, sessionId={}, workflowId={}", sessionId, workflowId);
        }

        /**
         * isExists.
         * 
         * @param session session
         * @return the result
         * @since 0.1.7
         */
        @Override
        public boolean isExists(BaseSession session) {
            String workflowId = getWorkflowId(session);
            String sessionId = session.sessionId();

            String stateDumpTypeKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String stateBlobKey =
                resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);

            return kvStore.isExists(stateDumpTypeKey) && kvStore.isExists(stateBlobKey);
        }

        /**
         * isExists.
         * 
         * @param workflowId workflowId
         * @return the result
         * @since 0.1.7
         */
        public boolean isExists(String workflowId) {
            // Without session context, can't fully determine — check by prefix pattern
            return false;
        }

        @SuppressWarnings("unchecked")
        /**
         * processInteractiveInputs.
         * 
         * @param session session
         * @param inputs inputs
         * @since 0.1.7
         */
        private void processInteractiveInputs(BaseSession session, InteractiveInput inputs) {
            if (inputs.getRawInputs() != null) {
                if (session.state() instanceof WorkflowCommitState wcs) {
                    wcs.updateAndCommitWorkflowState(Map.of(Constant.INTERACTIVE_INPUT, inputs.getRawInputs()));
                }
                return;
            }

            Map<String, Object> userInputs = inputs.getUserInputs();
            if (userInputs == null || userInputs.isEmpty()) {
                return;
            }

            for (Map.Entry<String, Object> entry : userInputs.entrySet()) {
                NodeSession nodeSession = new NodeSession(session, entry.getKey());
                Object interactiveInput = nodeSession.state().get(Constant.INTERACTIVE_INPUT);
                List<Object> inputList;
                if (interactiveInput instanceof List<?> existingInputs) {
                    inputList = new java.util.ArrayList<>(existingInputs.size() + 1);
                    inputList.addAll((List<Object>) existingInputs);
                    inputList.add(entry.getValue());
                } else {
                    inputList = List.of(entry.getValue());
                }
                nodeSession.state().update(Map.of(Constant.INTERACTIVE_INPUT, inputList));
            }
            if (session.state() instanceof WorkflowCommitState workflowState) {
                workflowState.commit();
            }
        }
    }

    /**
     * Graph state store implementation using BaseKVStore.
     */
    static class PersistenceGraphStore implements Store {
        private static final String DATA_TYPE = "checkpoint_data_type";
        private static final String DATA_VALUE = "checkpoint_data_value";

        private final BaseKVStore kvStore;

        PersistenceGraphStore(BaseKVStore kvStore) {
            this.kvStore = kvStore;
        }

        /**
         * get.
         * 
         * @param sessionId sessionId
         * @param ns ns
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Optional<GraphStoreState> get(String sessionId, String ns) {
            String keyType =
                resolveNsKey(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns, DATA_TYPE);
            String keyValue =
                resolveNsKey(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns, DATA_VALUE);

            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.get(keyType);
            pipeline.get(keyValue);
            List<Object> results = pipeline.execute();

            if (results == null || results.size() != 2) {
                return Optional.empty();
            }

            return deserializeState(results.get(0), results.get(1)).filter(GraphStoreState.class::isInstance)
                    .map(GraphStoreState.class::cast);
        }

        /**
         * save.
         * 
         * @param sessionId sessionId
         * @param ns ns
         * @param state state
         * @since 0.1.7
         */
        @Override
        public void save(String sessionId, String ns, GraphStoreState state) {
            Serializer.TypedBytes serializedState = serializeState(state);
            String keyType =
                resolveNsKey(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns, DATA_TYPE);
            String keyValue =
                resolveNsKey(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns, DATA_VALUE);

            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.set(keyType, serializedState.type());
            pipeline.set(keyValue, serializedState.data());
            pipeline.execute();

            Loggers.SESSION.debug("Graph state saved, sessionId={}, ns={}", sessionId, ns);
        }

        /**
         * delete.
         * 
         * @param sessionId sessionId
         * @param ns ns
         * @since 0.1.7
         */
        @Override
        public void delete(String sessionId, String ns) {
            if (ns == null || ns.isEmpty()) {
                String prefix =
                    TenantKVStoreKeyResolver.resolvePrefix(buildKey(sessionId, WORKFLOW_NAMESPACE_GRAPH));
                kvStore.deleteByPrefix(prefix, null);
            } else {
                String prefix = resolveNsPrefix(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns);
                kvStore.deleteByPrefix(prefix, null);
            }
            Loggers.SESSION.debug("Graph checkpoint cleared, sessionId={}, ns={}", sessionId, ns);
        }
    }

    private static Serializer.TypedBytes serializeState(Object state) {
        return STATE_SERIALIZER.dumpsTyped(state);
    }

    private static Optional<Object> deserializeState(Object dumpType, Object blob) {
        if (dumpType == null || blob == null) {
            return Optional.empty();
        }
        String dumpTypeText = decodeDumpType(dumpType);
        if (!(blob instanceof byte[] bytes)) {
            return LEGACY_JAVA_SERIALIZATION_TYPE.equals(dumpTypeText) ? Optional.of(blob) : Optional.empty();
        }
        String serializerType = LEGACY_JAVA_SERIALIZATION_TYPE.equals(dumpTypeText)
                ? JAVA_SERIALIZATION_TYPE
                : dumpTypeText;
        if (!JAVA_SERIALIZATION_TYPE.equals(serializerType)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(STATE_SERIALIZER.loadsTyped(new Serializer.TypedBytes(serializerType, bytes)));
        } catch (Serializer.SerializationException exception) {
            Loggers.SESSION.warning("Failed to deserialize persistence state, dumpType={}, reason={}",
                    dumpTypeText, exception.getMessage());
            return Optional.empty();
        }
    }

    private static String decodeDumpType(Object dumpType) {
        if (dumpType instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(dumpType);
    }
}
