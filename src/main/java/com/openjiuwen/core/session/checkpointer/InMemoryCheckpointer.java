/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.InMemoryStore;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.state.WorkflowCommitState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory checkpointer implementation.
 *
 * <p>Mirrors Python's {@code InMemoryCheckpointer} in
 * {@code openjiuwen/core/session/checkpointer/inmemory.py}.</p>
 */
public class InMemoryCheckpointer extends Checkpointer {

    private final Map<String, AgentStorage> agentStores = new ConcurrentHashMap<>();
    private final Map<String, AgentTeamStorage> agentTeamStores = new ConcurrentHashMap<>();
    private final Map<String, WorkflowStorage> workflowStores = new ConcurrentHashMap<>();
    private final Store graphStore = new InMemoryStore();
    private final Map<String, Set<String>> sessionToWorkflowIds = new ConcurrentHashMap<>();

    String tenantAwareSessionId(String sessionId) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        if (ctx != null && ctx.isTenantAware()) {
            return ctx.safeTenantId() + ":" + sessionId;
        }
        return sessionId;
    }

    @Override
    public void preAgentExecute(BaseSession session, Object inputs) {
        String sessionId = tenantAwareSessionId(session.sessionId());
        AgentStorage agentStorage = agentStores.computeIfAbsent(sessionId, ignored -> new AgentStorage());
        agentStorage.recover(session);
        if (inputs != null && session.state() != null) {
            session.state().update(mapOf(Constant.INTERACTIVE_INPUT, new ArrayList<>(List.of(inputs))));
        }
    }

    @Override
    public void interruptAgentExecute(BaseSession session) {
        AgentStorage agentStorage = agentStores.get(tenantAwareSessionId(session.sessionId()));
        if (agentStorage == null) {
            throw ErrorHelper.buildError(
                    StatusCode.CHECKPOINTER_INTERRUPT_AGENT_ERROR,
                    "session_id",
                    session.sessionId(),
                    "agent",
                    agentId(session),
                    "reason",
                    "agent store not found"
            );
        }
        agentStorage.save(session);
    }

    @Override
    public void postAgentExecute(BaseSession session) {
        AgentStorage agentStorage = agentStores.get(tenantAwareSessionId(session.sessionId()));
        if (agentStorage == null) {
            throw ErrorHelper.buildError(
                    StatusCode.CHECKPOINTER_POST_AGENT_EXECUTION_ERROR,
                    "session_id",
                    session.sessionId(),
                    "agent",
                    agentId(session),
                    "reason",
                    "agent store not found"
            );
        }
        agentStorage.save(session);
    }

    @Override
    public void preAgentTeamExecute(BaseSession session, Object inputs) {
        String sessionId = tenantAwareSessionId(session.sessionId());
        AgentTeamStorage teamStorage = agentTeamStores.computeIfAbsent(sessionId, ignored -> new AgentTeamStorage());
        teamStorage.recover(session);
        if (inputs != null && session.state() != null) {
            session.state().updateGlobal(mapOf(Constant.INTERACTIVE_INPUT, new ArrayList<>(List.of(inputs))));
        }
    }

    @Override
    public void postAgentTeamExecute(BaseSession session) {
        AgentTeamStorage teamStorage = agentTeamStores.get(tenantAwareSessionId(session.sessionId()));
        if (teamStorage == null) {
            throw ErrorHelper.buildError(
                    StatusCode.CHECKPOINTER_POST_AGENT_EXECUTION_ERROR,
                    "session_id",
                    session.sessionId(),
                    "agent",
                    teamId(session),
                    "reason",
                    "agent team store not found"
            );
        }
        teamStorage.save(session);
    }

    @Override
    public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
        prepareWorkflowExecute(session, inputs);
    }

    @Override
    public void preWorkflowExecute(BaseSession session, Object inputs) {
        prepareWorkflowExecute(session, inputs instanceof InteractiveInput interactiveInput ? interactiveInput : null);
    }

    @Override
    public void postWorkflowExecute(BaseSession session, Object result, Exception exception) {
        String sessionId = tenantAwareSessionId(session.sessionId());
        String workflowId = Checkpointer.workflowId(session);
        WorkflowStorage workflowStorage = workflowStores.get(sessionId);
        if (exception != null) {
            if (workflowStorage == null) {
                throw ErrorHelper.buildError(
                        StatusCode.CHECKPOINTER_POST_WORKFLOW_EXECUTION_ERROR,
                        "session_id",
                        session.sessionId(),
                        "workflow",
                        workflowId,
                        "reason",
                        "workflow store not found"
                );
            }
            saveWorkflowCheckpoint(sessionId, workflowId, session);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(exception);
        }

        if (!containsInterrupt(result)) {
            clearWorkflowSession(sessionId, workflowId);
            if (!(parent(session) instanceof AgentSession)) {
                workflowStores.remove(sessionId);
                sessionToWorkflowIds.remove(sessionId);
            }
            return;
        }

        if (workflowStorage == null) {
            throw ErrorHelper.buildError(
                    StatusCode.CHECKPOINTER_POST_WORKFLOW_EXECUTION_ERROR,
                    "session_id",
                    session.sessionId(),
                    "workflow",
                    workflowId,
                    "reason",
                    "workflow store not found"
            );
        }
        saveWorkflowCheckpoint(sessionId, workflowId, session);
    }

    @Override
    public boolean sessionExists(String sessionId) {
        String tid = tenantAwareSessionId(sessionId);
        return agentStores.containsKey(tid)
                || agentTeamStores.containsKey(tid)
                || workflowStores.containsKey(tid);
    }

    @Override
    public void release(String sessionId) {
        release(sessionId, null);
    }

    public void release(String sessionId, String agentId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        String tid = tenantAwareSessionId(sessionId);
        if (agentId != null) {
            AgentStorage agentStorage = agentStores.get(tid);
            if (agentStorage != null) {
                agentStorage.clear(agentId);
            }
            return;
        }

        Set<String> workflowIds = sessionToWorkflowIds.get(tid);
        if (workflowIds != null) {
            for (String workflowId : new ArrayList<>(workflowIds)) {
                graphStore.delete(tid, workflowId).toCompletableFuture().join();
            }
        }
        sessionToWorkflowIds.remove(tid);
        workflowStores.remove(tid);

        for (String storedSessionId : new ArrayList<>(agentStores.keySet())) {
            if (storedSessionId.startsWith(tid)) {
                agentStores.remove(storedSessionId);
            }
        }
        agentTeamStores.remove(tid);
    }

    @Override
    public Store graphStore() {
        return graphStore;
    }

    private void prepareWorkflowExecute(BaseSession session, InteractiveInput inputs) {
        String sessionId = tenantAwareSessionId(session.sessionId());
        String workflowId = Checkpointer.workflowId(session);
        WorkflowStorage workflowStorage = workflowStores.computeIfAbsent(sessionId, ignored -> new WorkflowStorage());
        sessionToWorkflowIds.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet());

        if (inputs != null) {
            workflowStorage.recover(session, inputs);
            return;
        }
        if (!workflowStorage.exists(session)) {
            return;
        }
        if (Boolean.TRUE.equals(session.config().getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false))) {
            graphStore.delete(sessionId, workflowId).toCompletableFuture().join();
            workflowStorage.clear(workflowId);
            sessionToWorkflowIds.getOrDefault(sessionId, Set.of()).remove(workflowId);
            return;
        }
        // Workflow state exists and input is a non-InteractiveInput query (e.g. String).
        // Recover the saved state without interactive input processing so the workflow
        // can resume with the new query input. This mirrors Python's behavior where
        // a query recovery is treated as a valid resumption path.
        workflowStorage.recover(session, null);
    }

    private void saveWorkflowCheckpoint(String sessionId, String workflowId, BaseSession session) {
        WorkflowStorage workflowStorage = Objects.requireNonNull(workflowStores.get(sessionId), "workflowStorage");
        workflowStorage.save(session);
        sessionToWorkflowIds.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(workflowId);
    }

    private void clearWorkflowSession(String sessionId, String workflowId) {
        graphStore.delete(sessionId, workflowId).toCompletableFuture().join();
        WorkflowStorage workflowStorage = workflowStores.get(sessionId);
        if (workflowStorage != null) {
            workflowStorage.clear(workflowId);
        }
        Set<String> workflowIds = sessionToWorkflowIds.get(sessionId);
        if (workflowIds != null) {
            workflowIds.remove(workflowId);
        }
    }

    private static boolean containsInterrupt(Object result) {
        if (result instanceof Map<?, ?> map) {
            return map.get(PregelConstants.TASK_STATUS_INTERRUPT) != null;
        }
        return false;
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static Map<String, Object> toMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        return (Map<String, Object>) deepCopy(source == null ? new LinkedHashMap<>() : source);
    }

    private static Object deepCopy(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            for (Object item : set) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }

    /**
     * Common in-memory storage for a single entity state.
     *
     * <p>Mirrors Python's {@code BaseSingleStateStorage} in
     * {@code openjiuwen/core/session/checkpointer/inmemory.py}.</p>
     */
    private abstract static class BaseSingleStateStorage {
        private final Map<String, Map<String, Object>> stateBlobs = new ConcurrentHashMap<>();

        abstract String entityId(BaseSession session);

        abstract Map<String, Object> stateToSave(BaseSession session);

        abstract void restoreState(BaseSession session, Map<String, Object> state);

        void save(BaseSession session) {
            stateBlobs.put(entityId(session), deepCopyMap(stateToSave(session)));
        }

        void recover(BaseSession session) {
            Map<String, Object> state = stateBlobs.get(entityId(session));
            if (state != null) {
                restoreState(session, deepCopyMap(state));
            }
        }

        void clear(String entityId) {
            stateBlobs.remove(entityId);
        }
    }

    /**
     * Agent state storage.
     *
     * <p>Mirrors Python's {@code AgentStorage} in
     * {@code openjiuwen/core/session/checkpointer/inmemory.py}.</p>
     */
    private static final class AgentStorage extends BaseSingleStateStorage {
        @Override
        String entityId(BaseSession session) {
            return agentId(session);
        }

        @Override
        Map<String, Object> stateToSave(BaseSession session) {
            return session.state() == null ? new LinkedHashMap<>() : session.state().getState();
        }

        @Override
        void restoreState(BaseSession session, Map<String, Object> state) {
            if (session.state() != null) {
                session.state().setState(state);
            }
        }
    }

    /**
     * Agent-team global state storage.
     *
     * <p>Mirrors Python's {@code AgentTeamStorage} in
     * {@code openjiuwen/core/session/checkpointer/inmemory.py}.</p>
     */
    private static final class AgentTeamStorage extends BaseSingleStateStorage {
        @Override
        String entityId(BaseSession session) {
            return teamId(session);
        }

        @Override
        Map<String, Object> stateToSave(BaseSession session) {
            return session.state() == null ? new LinkedHashMap<>() : toMap(session.state().getGlobal(null));
        }

        @Override
        void restoreState(BaseSession session, Map<String, Object> state) {
            SessionStateAccess stateAccess = session.state();
            if (stateAccess instanceof AgentStateCollection agentStateCollection) {
                agentStateCollection.getGlobalStateLike().setState(state);
                return;
            }
            if (stateAccess != null) {
                stateAccess.updateGlobal(state);
            }
        }
    }

    /**
     * Workflow state and update storage.
     *
     * <p>Mirrors Python's {@code WorkflowStorage} in
     * {@code openjiuwen/core/session/checkpointer/inmemory.py}.</p>
     */
    private static final class WorkflowStorage {
        private final Map<String, Map<String, Object>> stateBlobs = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> stateUpdatesBlobs = new ConcurrentHashMap<>();

        void save(BaseSession session) {
            if (session.state() == null) {
                return;
            }
            String workflowId = Checkpointer.workflowId(session);
            stateBlobs.put(workflowId, deepCopyMap(session.state().getState()));
            if (session.state() instanceof WorkflowCommitState workflowState) {
                stateUpdatesBlobs.put(workflowId, deepCopyMap(workflowState.getUpdates()));
            }
        }

        void recover(BaseSession session, InteractiveInput inputs) {
            if (session.state() == null) {
                return;
            }
            String workflowId = Checkpointer.workflowId(session);
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

        void clear(String workflowId) {
            stateBlobs.remove(workflowId);
            stateUpdatesBlobs.remove(workflowId);
        }

        boolean exists(BaseSession session) {
            return stateBlobs.containsKey(Checkpointer.workflowId(session));
        }

        private void processInteractiveInputs(BaseSession session, InteractiveInput inputs) {
            if (inputs.getRawInputs() != null) {
                if (session.state() instanceof WorkflowCommitState workflowState) {
                    workflowState.updateAndCommitWorkflowState(mapOf(Constant.INTERACTIVE_INPUT, inputs.getRawInputs()));
                    return;
                }
                session.state().update(mapOf(Constant.INTERACTIVE_INPUT, inputs.getRawInputs()));
                return;
            }

            Map<String, Object> userInputs = inputs.getUserInputs();
            if (userInputs == null || userInputs.isEmpty()) {
                return;
            }
            // Also store at workflow level so popWorkflowInteractiveInput can find it.
            // Store userInputs values as a List, matching the rawInputs format.
            if (session.state() instanceof WorkflowCommitState workflowState) {
                Object existing = workflowState.getWorkflowState(Constant.INTERACTIVE_INPUT);
                List<Object> values = existing instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
                values.addAll(userInputs.values());
                workflowState.updateAndCommitWorkflowState(mapOf(Constant.INTERACTIVE_INPUT, values));
            }
            for (Map.Entry<String, Object> entry : userInputs.entrySet()) {
                NodeSession nodeSession = new NodeSession(session, entry.getKey());
                if (nodeSession.state() == null) {
                    continue;
                }
                Object existing = nodeSession.state().get(Constant.INTERACTIVE_INPUT);
                List<Object> values = existing instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
                values.add(entry.getValue());
                nodeSession.state().update(mapOf(Constant.INTERACTIVE_INPUT, values));
            }
            if (session.state() instanceof WorkflowCommitState workflowState) {
                workflowState.commit();
            }
        }
    }
}
