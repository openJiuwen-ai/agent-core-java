/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.CommitStateLike;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;

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
 */
public class PersistenceCheckpointer extends Checkpointer {

    private final BaseKVStore kvStore;
    private final PersistenceAgentStorage agentStorage;
    private final PersistenceWorkflowStorage workflowStorage;
    private final PersistenceGraphStore graphStoreField;

    public PersistenceCheckpointer(BaseKVStore kvStore) {
        this.kvStore = kvStore;
        this.agentStorage = new PersistenceAgentStorage(kvStore);
        this.workflowStorage = new PersistenceWorkflowStorage(kvStore);
        this.graphStoreField = new PersistenceGraphStore(kvStore);
    }

    @Override
    public void preAgentExecute(BaseSession session, Object inputs) {
        String sessionId = session.sessionId();
        Loggers.SESSION.info("Agent checkpoint restore initiated, sessionId={}", sessionId);
        agentStorage.recover(session);

        if (inputs != null) {
            session.state().update(Map.of(Constant.INTERACTIVE_INPUT, List.of(inputs)));
        }
    }

    @Override
    public void interruptAgentExecute(BaseSession session) {
        String sessionId = session.sessionId();
        Loggers.SESSION.info("Agent checkpoint save on interrupt, sessionId={}", sessionId);
        agentStorage.save(session);
    }

    @Override
    public void postAgentExecute(BaseSession session) {
        String sessionId = session.sessionId();
        Loggers.SESSION.info("Agent checkpoint save on completion, sessionId={}", sessionId);
        agentStorage.save(session);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
        String workflowId = getWorkflowId(session);
        String sessionId = session.sessionId();
        Loggers.SESSION.info("Workflow checkpoint restore initiated, sessionId={}, workflowId={}",
                sessionId, workflowId);

        if (inputs != null) {
            workflowStorage.recover(session, inputs);
        } else {
            if (!workflowStorage.exists(session)) {
                return;
            }
            Object forceDelete = session.config() != null
                    ? session.config().getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false)
                    : false;
            if (Boolean.TRUE.equals(forceDelete)) {
                Loggers.SESSION.info("Force clearing workflow checkpoints, sessionId={}, workflowId={}",
                        sessionId, workflowId);
                graphStoreField.delete(sessionId, workflowId);
                workflowStorage.clear(workflowId, sessionId);
            } else {
                throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_PRE_WORKFLOW_EXECUTION_ERROR,
                        "session_id", sessionId,
                        "workflow", workflowId,
                        "reason", "workflow state exists but non-interactive input and cleanup is disabled");
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void postWorkflowExecute(BaseSession session, Object result, Exception exception) {
        String workflowId = getWorkflowId(session);
        String sessionId = session.sessionId();

        if (exception != null) {
            Loggers.SESSION.info("Workflow checkpoint save on exception, sessionId={}, workflowId={}",
                    sessionId, workflowId);
            workflowStorage.save(session);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(exception);
        }

        if (result instanceof Map<?, ?> resultMap && resultMap.containsKey(PregelConstants.TASK_STATUS_INTERRUPT)) {
            Loggers.SESSION.info("Workflow checkpoint save on interrupt, sessionId={}, workflowId={}",
                    sessionId, workflowId);
            workflowStorage.save(session);
            return;
        }

        // Normal completion — clear checkpoints
        Loggers.SESSION.info("Workflow checkpoint cleared on completion, sessionId={}, workflowId={}",
                sessionId, workflowId);
        graphStoreField.delete(sessionId, workflowId);
        workflowStorage.clear(workflowId, sessionId);
    }

    @Override
    public boolean sessionExists(String sessionId) {
        if (kvStore == null) {
            return false;
        }
        String prefix = sessionId + ":";
        Map<String, Object> keys = kvStore.getByPrefix(prefix);
        return keys != null && !keys.isEmpty();
    }

    @Override
    public void release(String sessionId) {
        if (kvStore == null) {
            Loggers.SESSION.warning("Cannot release resources: KV store is null, sessionId={}", sessionId);
            return;
        }
        Loggers.SESSION.info("Session cleared, sessionId={}", sessionId);
        String prefix = sessionId + ":";
        kvStore.deleteByPrefix(prefix, null);
        Loggers.SESSION.info("All session resources released, sessionId={}", sessionId);
    }

    /**
     * Release resources for a specific agent under a session.
     *
     * @param sessionId the session ID
     * @param agentId   the agent ID
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

    @Override
    public Store graphStore() {
        return graphStoreField;
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

        @Override
        public void save(BaseSession session) {
            Map<String, Object> state = session.state().getState();
            String sessionId = session.sessionId();
            String agentId = getAgentId(session);

            String dumpTypeKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);

            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.set(dumpTypeKey, "java_serialized");
            pipeline.set(blobKey, state);
            pipeline.execute();

            Loggers.SESSION.debug("Agent state saved, sessionId={}, agentId={}", sessionId, agentId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void recover(BaseSession session, InteractiveInput inputs) {
            String sessionId = session.sessionId();
            String agentId = getAgentId(session);

            String dumpTypeKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);

            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.get(dumpTypeKey);
            pipeline.get(blobKey);
            List<Object> results = pipeline.execute();

            if (results == null || results.size() != KEY_NUMS) {
                Loggers.SESSION.debug("No agent state found, sessionId={}, agentId={}", sessionId, agentId);
                return;
            }

            Object blob = results.get(1);
            if (blob instanceof Map) {
                session.state().setState((Map<String, Object>) blob);
                Loggers.SESSION.debug("Agent state recovered, sessionId={}, agentId={}", sessionId, agentId);
            }
        }

        @Override
        public void clear(String id) {
            // id is agentId here — needs sessionId too; use clear(agentId, sessionId)
        }

        public void clear(String agentId, String sessionId) {
            String dumpTypeKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);
            kvStore.delete(dumpTypeKey);
            kvStore.delete(blobKey);
            Loggers.SESSION.debug("Agent checkpoint cleared, sessionId={}, agentId={}",
                    sessionId, agentId);
        }

        @Override
        public boolean exists(BaseSession session) {
            String sessionId = session.sessionId();
            String agentId = getAgentId(session);

            String dumpTypeKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_AGENT, agentId, STATE_BLOBS);

            return kvStore.exists(dumpTypeKey) && kvStore.exists(blobKey);
        }

        private static String getAgentId(BaseSession session) {
            try {
                return (String) session.getClass().getMethod("agentId").invoke(session);
            } catch (Exception e) {
                return session.sessionId();
            }
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

        @Override
        public void save(BaseSession session) {
            Map<String, Object> state = session.state().getState();
            String workflowId = getWorkflowId(session);
            String sessionId = session.sessionId();

            KVStorePipeline pipeline = kvStore.pipeline();

            // Save main state
            String dumpTypeKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String blobKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);
            pipeline.set(dumpTypeKey, "java_serialized");
            pipeline.set(blobKey, state);

            // Save updates if state supports commits
            if (session.state() instanceof CommitStateLike commitState) {
                Map<String, Object> updates = commitState.getUpdates();
                if (updates != null) {
                    String updatesDumpTypeKey = buildKeyWithNamespace(
                            sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS_DUMP_TYPE);
                    String updatesBlobKey = buildKeyWithNamespace(
                            sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS);
                    pipeline.set(updatesDumpTypeKey, "java_serialized");
                    pipeline.set(updatesBlobKey, updates);
                }
            }

            pipeline.execute();
            Loggers.SESSION.debug("Workflow state saved, sessionId={}, workflowId={}", sessionId, workflowId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void recover(BaseSession session, InteractiveInput inputs) {
            String workflowId = getWorkflowId(session);
            String sessionId = session.sessionId();

            KVStorePipeline pipeline = kvStore.pipeline();
            String stateDumpTypeKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String stateBlobKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);
            String updatesDumpTypeKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS_DUMP_TYPE);
            String updatesBlobKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS);

            pipeline.get(stateDumpTypeKey);
            pipeline.get(stateBlobKey);
            pipeline.get(updatesDumpTypeKey);
            pipeline.get(updatesBlobKey);
            List<Object> results = pipeline.execute();

            if (results == null || results.size() != KEY_NUMS) {
                Loggers.SESSION.warning(
                        "Unexpected key count during workflow recovery, sessionId={}, workflowId={}",
                        sessionId, workflowId);
                return;
            }

            // Recover state
            Object stateBlob = results.get(1);
            if (stateBlob instanceof Map) {
                session.state().setState((Map<String, Object>) stateBlob);
            }

            // Process interactive inputs
            if (inputs != null) {
                processInteractiveInputs(session, inputs);
            }

            // Recover updates
            Object updatesBlob = results.get(3);
            if (updatesBlob instanceof Map && session.state() instanceof CommitStateLike commitState) {
                commitState.setUpdates((Map<String, Object>) updatesBlob);
            }
        }

        @Override
        public void clear(String id) {
            // id is workflowId here — needs sessionId too
        }

        public void clear(String workflowId, String sessionId) {
            String stateDumpTypeKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String stateBlobKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);
            String updatesDumpTypeKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS_DUMP_TYPE);
            String updatesBlobKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, UPDATE_BLOBS);

            kvStore.delete(stateDumpTypeKey);
            kvStore.delete(stateBlobKey);
            kvStore.delete(updatesDumpTypeKey);
            kvStore.delete(updatesBlobKey);

            Loggers.SESSION.debug("Workflow checkpoint cleared, sessionId={}, workflowId={}",
                    sessionId, workflowId);
        }

        @Override
        public boolean exists(BaseSession session) {
            String workflowId = getWorkflowId(session);
            String sessionId = session.sessionId();

            String stateDumpTypeKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS_DUMP_TYPE);
            String stateBlobKey = buildKeyWithNamespace(
                    sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, STATE_BLOBS);

            return kvStore.exists(stateDumpTypeKey) && kvStore.exists(stateBlobKey);
        }

        public boolean exists(String workflowId) {
            // Without session context, can't fully determine — check by prefix pattern
            return false;
        }

        @SuppressWarnings("unchecked")
        private void processInteractiveInputs(BaseSession session, InteractiveInput inputs) {
            if (inputs.getRawInputs() != null) {
                if (session.state() instanceof WorkflowCommitState wcs) {
                    wcs.updateAndCommitWorkflowState(
                            Map.of(Constant.INTERACTIVE_INPUT, inputs.getRawInputs()));
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
            if (session.state() instanceof CommitStateLike commitState) {
                commitState.commit();
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

        @Override
        public Optional<GraphStoreState> get(String sessionId, String ns) {
            String keyType = buildKeyWithNamespace(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns, DATA_TYPE);
            String keyValue = buildKeyWithNamespace(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns, DATA_VALUE);

            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.get(keyType);
            pipeline.get(keyValue);
            List<Object> results = pipeline.execute();

            if (results == null || results.size() != 2) {
                return Optional.empty();
            }

            Object typeVal = results.get(0);
            Object valueVal = results.get(1);
            if (typeVal == null || valueVal == null) {
                return Optional.empty();
            }

            if (valueVal instanceof GraphStoreState gs) {
                return Optional.of(gs);
            }
            return Optional.empty();
        }

        @Override
        public void save(String sessionId, String ns, GraphStoreState state) {
            String keyType = buildKeyWithNamespace(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns, DATA_TYPE);
            String keyValue = buildKeyWithNamespace(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns, DATA_VALUE);

            KVStorePipeline pipeline = kvStore.pipeline();
            pipeline.set(keyType, "java_serialized");
            pipeline.set(keyValue, state);
            pipeline.execute();

            Loggers.SESSION.debug("Graph state saved, sessionId={}, ns={}", sessionId, ns);
        }

        @Override
        public void delete(String sessionId, String ns) {
            if (ns == null || ns.isEmpty()) {
                String prefix = buildKey(sessionId, WORKFLOW_NAMESPACE_GRAPH);
                kvStore.deleteByPrefix(prefix, null);
            } else {
                String prefix = buildKeyWithNamespace(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns);
                kvStore.deleteByPrefix(prefix, null);
            }
            Loggers.SESSION.debug("Graph checkpoint cleared, sessionId={}, ns={}", sessionId, ns);
        }
    }
}
