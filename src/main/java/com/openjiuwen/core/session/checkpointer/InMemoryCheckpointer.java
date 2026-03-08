/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.store.InMemoryStore;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.WorkflowCommitState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory checkpointer implementation storing state in local maps.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.inmemory.InMemoryCheckpointer}.
 */
public class InMemoryCheckpointer extends Checkpointer {

    private final Map<String, InMemoryAgentStorage> agentStores = new ConcurrentHashMap<>();
    private final Map<String, InMemoryWorkflowStorage> workflowStores = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionToWorkflowIds = new ConcurrentHashMap<>();

    private final Store graphStore = new InMemoryStore();

    @Override
    public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
        String sessionId = session.sessionId();
        String workflowId = getWorkflowId(session);

        boolean isNewStore = !workflowStores.containsKey(sessionId);
        InMemoryWorkflowStorage workflowStore = workflowStores.computeIfAbsent(sessionId,
                k -> new InMemoryWorkflowStorage());

        if (isNewStore) {
            Loggers.SESSION.info("Create new workflow checkpointer store, sessionId={}, workflowId={}",
                    sessionId, workflowId);
        }

        sessionToWorkflowIds.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());

        if (inputs != null) {
            Loggers.SESSION.info("Begin to restore workflow session, sessionId={}, workflowId={}",
                    sessionId, workflowId);
            workflowStore.recover(workflowId, session, inputs);
            Loggers.SESSION.info("Succeed to restore workflow session, sessionId={}, workflowId={}",
                    sessionId, workflowId);
        } else {
            if (!workflowStore.exists(workflowId)) {
                return;
            }
            Object forceDelete = session.config() != null
                    ? session.config().getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false)
                    : false;
            if (Boolean.TRUE.equals(forceDelete)) {
                Loggers.SESSION.info("Force clearing workflow checkpoints, sessionId={}, workflowId={}",
                        sessionId, workflowId);
                workflowStore.clear(workflowId);
                graphStore.delete(sessionId, workflowId);
            } else {
                throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_PRE_WORKFLOW_EXECUTION_ERROR,
                        "session_id", sessionId,
                        "workflow", workflowId,
                        "reason", "workflow state exists but non-interactive input and cleanup is disabled");
            }
        }
    }

    @Override
    public void postWorkflowExecute(BaseSession session, Object result, Exception exception) {
        String sessionId = session.sessionId();
        String workflowId = getWorkflowId(session);
        InMemoryWorkflowStorage workflowStore = workflowStores.get(sessionId);

        if (exception != null) {
            if (workflowStore == null) {
                throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_POST_WORKFLOW_EXECUTION_ERROR,
                        "workflow", workflowId,
                        "reason", "workflow store not found");
            }
            saveWorkflowCheckpoint(workflowId, sessionId, session, "workflow exception");
            throw new RuntimeException(exception);
        }

        // Normal completion - clear checkpoints
        Loggers.SESSION.info("Clear workflow checkpoints on completion, sessionId={}, workflowId={}",
                sessionId, workflowId);
        graphStore.delete(sessionId, workflowId);
        if (workflowStore != null) {
            Set<String> workflowIds = sessionToWorkflowIds.get(sessionId);
            if (workflowIds != null) {
                workflowIds.remove(workflowId);
                if (workflowIds.isEmpty()) {
                    sessionToWorkflowIds.remove(sessionId);
                }
            }
            workflowStore.clear(workflowId);
            if (workflowStore.isEmpty()) {
                workflowStores.remove(sessionId);
            }
        }
    }

    @Override
    public void preAgentExecute(BaseSession session, Object inputs) {
        String sessionId = session.sessionId();

        boolean isNewStore = !agentStores.containsKey(sessionId);
        InMemoryAgentStorage agentStore = agentStores.computeIfAbsent(sessionId,
                k -> new InMemoryAgentStorage());

        if (isNewStore) {
            Loggers.SESSION.info("Create new agent checkpointer store, sessionId={}", sessionId);
        }

        Loggers.SESSION.info("Begin to restore agent session, sessionId={}", sessionId);
        agentStore.recover(session);
        Loggers.SESSION.info("Succeed to restore agent session, sessionId={}", sessionId);

        if (inputs != null) {
            List<Object> inputList = new ArrayList<>();
            inputList.add(inputs);
            session.state().update(Map.of(Constant.INTERACTIVE_INPUT, inputList));
        }
    }

    @Override
    public void interruptAgentExecute(BaseSession session) {
        String sessionId = session.sessionId();
        InMemoryAgentStorage agentStore = agentStores.get(sessionId);
        if (agentStore == null) {
            throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_INTERRUPT_AGENT_ERROR,
                    "reason", "agent store not found");
        }

        Loggers.SESSION.info("Save agent checkpoint on interruption, sessionId={}", sessionId);
        agentStore.save(session);
        Loggers.SESSION.info("Succeed to save agent checkpoint on interruption, sessionId={}", sessionId);
    }

    @Override
    public void postAgentExecute(BaseSession session) {
        String sessionId = session.sessionId();
        InMemoryAgentStorage agentStore = agentStores.get(sessionId);
        if (agentStore == null) {
            throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_POST_AGENT_EXECUTION_ERROR,
                    "reason", "agent store not found");
        }

        Loggers.SESSION.info("Save agent checkpoint on completion, sessionId={}", sessionId);
        agentStore.save(session);
        Loggers.SESSION.info("Succeed to save agent checkpoint on completion, sessionId={}", sessionId);
    }

    @Override
    public boolean sessionExists(String sessionId) {
        return agentStores.containsKey(sessionId) || workflowStores.containsKey(sessionId);
    }

    @Override
    public void release(String sessionId) {
        Set<String> workflowIds = sessionToWorkflowIds.remove(sessionId);
        if (workflowIds != null) {
            Loggers.SESSION.info("Clear workflow checkpoints on release, sessionId={}, workflowIds={}",
                    sessionId, workflowIds);
            for (String workflowId : workflowIds) {
                graphStore.delete(sessionId, workflowId);
            }
        }
        workflowStores.remove(sessionId);
        agentStores.remove(sessionId);
        Loggers.SESSION.info("Cleared all checkpoints on release, sessionId={}", sessionId);
    }

    @Override
    public Store graphStore() {
        return graphStore;
    }

    private void saveWorkflowCheckpoint(String workflowId, String sessionId,
                                         BaseSession session, String reason) {
        InMemoryWorkflowStorage workflowStore = workflowStores.get(sessionId);
        Set<String> workflowIds = sessionToWorkflowIds.get(sessionId);
        Loggers.SESSION.info("Save workflow checkpoint on {}, sessionId={}, workflowId={}",
                reason, sessionId, workflowId);
        if (workflowStore != null) {
            workflowStore.save(workflowId, session);
        }
        if (workflowIds != null) {
            workflowIds.add(workflowId);
        }
        Loggers.SESSION.info("Succeed to save workflow checkpoint on {}, sessionId={}, workflowId={}",
                reason, sessionId, workflowId);
    }

    // ---- Inner Storage Classes ----

    /**
     * In-memory agent storage.
     */
    private static class InMemoryAgentStorage {
        private final Map<String, Map<String, Object>> stateBlobs = new ConcurrentHashMap<>();

        void save(BaseSession session) {
            String agentId = session.sessionId();
            Map<String, Object> state = session.state().getState();
            if (state != null) {
                stateBlobs.put(agentId, new HashMap<>(state));
            }
        }

        void recover(BaseSession session) {
            String agentId = session.sessionId();
            Map<String, Object> state = stateBlobs.get(agentId);
            if (state != null) {
                session.state().setState(new HashMap<>(state));
            }
        }

        void clear(String agentId) {
            stateBlobs.remove(agentId);
        }
    }

    /**
     * In-memory workflow storage.
     */
    private static class InMemoryWorkflowStorage {
        private final Map<String, Map<String, Object>> stateBlobs = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> stateUpdatesBlobs = new ConcurrentHashMap<>();

        void save(String workflowId, BaseSession session) {
            Map<String, Object> state = session.state().getState();
            if (state != null) {
                stateBlobs.put(workflowId, deepCopyMap(state));
            }

            if (session.state() instanceof WorkflowCommitState workflowState) {
                stateUpdatesBlobs.put(workflowId, deepCopyMap(workflowState.getUpdates()));
            }
        }

        void recover(String workflowId, BaseSession session, InteractiveInput inputs) {
            Map<String, Object> state = stateBlobs.get(workflowId);
            if (state != null) {
                session.state().setState(deepCopyMap(state));
            }

            if (inputs != null) {
                processInteractiveInputs(session, inputs);
            }

            Map<String, Object> updates = stateUpdatesBlobs.get(workflowId);
            if (updates != null && session.state() instanceof WorkflowCommitState workflowState) {
                workflowState.setUpdates(deepCopyMap(updates));
            }
        }

        void recover(String workflowId, BaseSession session) {
            recover(workflowId, session, null);
        }

        void clear(String workflowId) {
            stateBlobs.remove(workflowId);
            stateUpdatesBlobs.remove(workflowId);
        }

        boolean exists(String workflowId) {
            return stateBlobs.containsKey(workflowId);
        }

        boolean isEmpty() {
            return stateBlobs.isEmpty() && stateUpdatesBlobs.isEmpty();
        }

        @SuppressWarnings("unchecked")
        private void processInteractiveInputs(BaseSession session, InteractiveInput inputs) {
            if (inputs.getRawInputs() != null) {
                if (session.state() instanceof WorkflowCommitState workflowState) {
                    workflowState.updateAndCommitWorkflowState(
                            Map.of(Constant.INTERACTIVE_INPUT, inputs.getRawInputs()));
                }
                return;
            }

            for (Map.Entry<String, Object> entry : inputs.getUserInputs().entrySet()) {
                NodeSession nodeSession = new NodeSession(session, entry.getKey());
                Object interactiveInput = nodeSession.state().get(Constant.INTERACTIVE_INPUT);
                List<Object> values = interactiveInput instanceof List<?>
                        ? new ArrayList<>((List<Object>) interactiveInput)
                        : new ArrayList<>();
                values.add(entry.getValue());
                nodeSession.state().update(Map.of(Constant.INTERACTIVE_INPUT, values));
            }

            if (session.state() instanceof WorkflowCommitState workflowState) {
                workflowState.commit();
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> deepCopyMap(Map<String, Object> source) {
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                copy.put(entry.getKey(), deepCopyObject(entry.getValue()));
            }
            return copy;
        }

        @SuppressWarnings("unchecked")
        private Object deepCopyObject(Object value) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> copy = new HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copy.put(String.valueOf(entry.getKey()), deepCopyObject(entry.getValue()));
                }
                return copy;
            }
            if (value instanceof List<?> list) {
                List<Object> copy = new ArrayList<>(list.size());
                for (Object item : list) {
                    copy.add(deepCopyObject(item));
                }
                return copy;
            }
            return value;
        }
    }
}
