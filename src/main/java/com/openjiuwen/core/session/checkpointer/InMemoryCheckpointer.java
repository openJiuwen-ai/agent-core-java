/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.InMemoryStore;
import com.openjiuwen.core.graph.store.KeyLockedStore;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory checkpointer implementation storing state in local maps.
 * <p>
 * Checkpoints are kept per session so an interrupted execution can resume later.
 * To keep memory bounded in long-running processes, sessions are evicted with a
 * combined TTL + capacity policy: entries not written within the TTL (default
 * 7 days, aligned with the Redis checkpointer's {@code default_ttl}) are removed,
 * and when the number of tracked sessions exceeds the capacity limit (default
 * 100) the least recently written sessions are evicted first.
 * <p>
 * Eviction is lazy: the TTL sweep and capacity check run whenever any session
 * writes a checkpoint, not on a background timer. A fully idle process therefore
 * keeps at most {@code maxSessions} sessions resident — the capacity limit is
 * the hard memory bound, the TTL only reclaims stale entries once traffic resumes.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.inmemory.InMemoryCheckpointer}.
 *
 * @since 0.1.7
 */
public class InMemoryCheckpointer extends Checkpointer {
    /** Default TTL in milliseconds: 7 days, aligned with the Redis checkpointer default. */
    static final long DEFAULT_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000;

    /** Default maximum number of tracked sessions before least-recently-written eviction. */
    static final int DEFAULT_MAX_SESSIONS = 100;

    private final Map<String, InMemoryAgentStorage> agentStores = new ConcurrentHashMap<>();

    private final Map<String, InMemoryWorkflowStorage> workflowStores = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> sessionToWorkflowIds = new ConcurrentHashMap<>();

    /** Graph state store; per-session striped locks via {@link KeyLockedStore}. */
    private final Store graphStore = new KeyLockedStore(new InMemoryStore());

    private final long ttlMillis;

    private final int maxSessions;

    /** Clock source, overridable for tests. */
    private final LongSupplier clock;

    /**
     * Insertion-ordered session registry. Access order == write order (a read
     * does not refresh the TTL, matching the Redis checkpointer's
     * {@code refresh_on_read=false}). Guarded by itself.
     */
    private final LinkedHashMap<String, Long> sessionRegistry = new LinkedHashMap<>(16, 0.75f, false);

    public InMemoryCheckpointer() {
        this(DEFAULT_TTL_MILLIS, DEFAULT_MAX_SESSIONS, System::currentTimeMillis);
    }

    /**
     * Create a checkpointer with a custom eviction policy.
     *
     * @param ttlMillis time-to-live per session in milliseconds; non-positive disables TTL eviction
     * @param maxSessions maximum number of tracked sessions; non-positive disables capacity eviction
     * @param clock wall-clock source in milliseconds
     * @since 0.1.15
     */
    InMemoryCheckpointer(long ttlMillis, int maxSessions, LongSupplier clock) {
        this.ttlMillis = ttlMillis;
        this.maxSessions = maxSessions;
        this.clock = clock != null ? clock : System::currentTimeMillis;
    }

    String tenantAwareSessionId(String sessionId) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        if (ctx != null && ctx.isTenantAware()) {
            return ctx.safeTenantId() + ":" + sessionId;
        }
        return sessionId;
    }

    @Override
    public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
        String sessionId = session.sessionId();
        String workflowId = getWorkflowId(session);
        String tid = tenantAwareSessionId(sessionId);

        boolean isNewStore = !workflowStores.containsKey(tid);
        InMemoryWorkflowStorage workflowStore =
            workflowStores.computeIfAbsent(tid, k -> new InMemoryWorkflowStorage());

        if (isNewStore) {
            Loggers.SESSION.info("Create new workflow checkpointer store, sessionId={}, workflowId={}", sessionId,
                    workflowId);
        }

        sessionToWorkflowIds.computeIfAbsent(tid, k -> ConcurrentHashMap.newKeySet());
        touchSession(tid, sessionId);

        if (inputs != null) {
            Loggers.SESSION.info("Begin to restore workflow session, sessionId={}, workflowId={}", sessionId,
                    workflowId);
            workflowStore.recover(workflowId, session, inputs);
            Loggers.SESSION.info("Succeed to restore workflow session, sessionId={}, workflowId={}", sessionId,
                    workflowId);
        } else {
            if (!workflowStore.isExists(workflowId)) {
                return;
            }
            Object forceDelete = session.config() != null
                    ? session.config().getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false)
                    : false;
            if (Boolean.TRUE.equals(forceDelete)) {
                Loggers.SESSION.info("Force clearing workflow checkpoints, sessionId={}, workflowId={}", sessionId,
                        workflowId);
                workflowStore.clear(workflowId);
                graphStore.delete(sessionId, workflowId);
            } else {
                throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_PRE_WORKFLOW_EXECUTION_ERROR, "session_id",
                        sessionId, "workflow", workflowId, "reason",
                        "workflow state exists but non-interactive input and cleanup is disabled");
            }
        }
    }

    @Override
    public void postWorkflowExecute(BaseSession session, Object result, Exception exception) {
        String sessionId = session.sessionId();
        String workflowId = getWorkflowId(session);
        String tid = tenantAwareSessionId(sessionId);
        InMemoryWorkflowStorage workflowStore = workflowStores.get(tid);

        if (exception != null) {
            if (workflowStore == null) {
                throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_POST_WORKFLOW_EXECUTION_ERROR, "workflow",
                        workflowId, "reason", "workflow store not found");
            }
            saveWorkflowCheckpoint(workflowId, sessionId, session, "workflow exception");
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(exception);
        }

        if (result instanceof Map<?, ?> resultMap && resultMap.containsKey(PregelConstants.TASK_STATUS_INTERRUPT)) {
            saveWorkflowCheckpoint(workflowId, sessionId, session, "workflow interruption");
            return;
        }

        Loggers.SESSION.info("Clear workflow checkpoints on completion, sessionId={}, workflowId={}", sessionId,
                workflowId);
        graphStore.delete(sessionId, workflowId);
        if (workflowStore != null) {
            Set<String> workflowIds = sessionToWorkflowIds.get(tid);
            if (workflowIds != null) {
                workflowIds.remove(workflowId);
                if (workflowIds.isEmpty()) {
                    sessionToWorkflowIds.remove(tid);
                }
            }
            workflowStore.clear(workflowId);
            if (workflowStore.isEmpty()) {
                BaseSession parent = null;
                try {
                    parent = (BaseSession) session.getClass().getMethod("parent").invoke(session);
                } catch (Exception e) {
                    Loggers.SESSION.debug("Unable to resolve parent session, sessionId={}", session.sessionId(), e);
                }
                if (!(parent instanceof AgentSession)) {
                    workflowStores.remove(tid);
                }
            }
        }
    }

    @Override
    public void preAgentExecute(BaseSession session, Object inputs) {
        String sessionId = session.sessionId();
        String tid = tenantAwareSessionId(sessionId);

        boolean isNewStore = !agentStores.containsKey(tid);
        InMemoryAgentStorage agentStore = agentStores.computeIfAbsent(tid, k -> new InMemoryAgentStorage());

        if (isNewStore) {
            Loggers.SESSION.info("Create new agent checkpointer store, sessionId={}", sessionId);
        }
        touchSession(tid, sessionId);

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
        String tid = tenantAwareSessionId(sessionId);
        InMemoryAgentStorage agentStore = agentStores.get(tid);
        if (agentStore == null) {
            throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_INTERRUPT_AGENT_ERROR, "reason",
                    "agent store not found");
        }

        touchSession(tid, sessionId);
        Loggers.SESSION.info("Save agent checkpoint on interruption, sessionId={}", sessionId);
        agentStore.save(session);
        Loggers.SESSION.info("Succeed to save agent checkpoint on interruption, sessionId={}", sessionId);
    }

    @Override
    public void postAgentExecute(BaseSession session) {
        String sessionId = session.sessionId();
        String tid = tenantAwareSessionId(sessionId);
        InMemoryAgentStorage agentStore = agentStores.get(tid);
        if (agentStore == null) {
            throw ErrorHelper.buildError(StatusCode.CHECKPOINTER_POST_AGENT_EXECUTION_ERROR, "reason",
                    "agent store not found");
        }

        touchSession(tid, sessionId);
        Loggers.SESSION.info("Save agent checkpoint on completion, sessionId={}", sessionId);
        agentStore.save(session);
        Loggers.SESSION.info("Succeed to save agent checkpoint on completion, sessionId={}", sessionId);
    }

    @Override
    public boolean sessionExists(String sessionId) {
        String tid = tenantAwareSessionId(sessionId);
        return agentStores.containsKey(tid) || workflowStores.containsKey(tid);
    }

    @Override
    public void release(String sessionId) {
        String tid = tenantAwareSessionId(sessionId);
        unregisterSession(tid);
        Set<String> workflowIds = sessionToWorkflowIds.remove(tid);
        if (workflowIds != null) {
            Loggers.SESSION.info("Clear workflow checkpoints on release, sessionId={}, workflowIds={}", sessionId,
                    workflowIds);
        }
        graphStore.delete(sessionId, null);
        workflowStores.remove(tid);
        agentStores.remove(tid);
        Loggers.SESSION.info("Cleared all checkpoints on release, sessionId={}", sessionId);
    }

    /**
     * Record a write for the session and apply the TTL + capacity eviction policy.
     * A read does not refresh the TTL (matches the Redis checkpointer's
     * {@code refresh_on_read=false}); only writes keep a session alive.
     */
    private void touchSession(String tid, String sessionId) {
        long now = clock.getAsLong();
        List<String> evicted = new ArrayList<>();
        synchronized (sessionRegistry) {
            sessionRegistry.remove(tid);
            sessionRegistry.put(tid, now);
            if (ttlMillis > 0) {
                Iterator<Map.Entry<String, Long>> it = sessionRegistry.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Long> entry = it.next();
                    if (now - entry.getValue() >= ttlMillis) {
                        it.remove();
                        evicted.add(entry.getKey());
                    }
                }
            }
            if (maxSessions > 0) {
                // The iterator starts at the least recently written entry, so this
                // evicts eldest sessions one by one until within the capacity limit
                // (in practice exactly one, since each touch adds at most one entry).
                Iterator<Map.Entry<String, Long>> it = sessionRegistry.entrySet().iterator();
                while (sessionRegistry.size() > maxSessions && it.hasNext()) {
                    evicted.add(it.next().getKey());
                    it.remove();
                }
            }
        }
        for (String evictedTid : evicted) {
            evictSession(evictedTid);
        }
    }

    /** Stop tracking a session without touching its checkpoints (used by {@link #release}). */
    private void unregisterSession(String tid) {
        synchronized (sessionRegistry) {
            sessionRegistry.remove(tid);
        }
    }

    /** Remove all checkpoint state for a session evicted by the TTL or capacity policy. */
    private void evictSession(String tid) {
        sessionToWorkflowIds.remove(tid);
        // tid may carry a tenant prefix ("tenantId:sessionId"); the graph store is keyed
        // by the raw sessionId, so strip the prefix before deleting graph checkpoints.
        graphStore.delete(stripTenantPrefix(tid), null);
        workflowStores.remove(tid);
        agentStores.remove(tid);
        Loggers.SESSION.warning("Evicted in-memory checkpoints for session tid={} (ttl={}ms, maxSessions={})", tid,
                ttlMillis, maxSessions);
    }

    /** Extract the raw sessionId from a tenant-prefixed tid ("tenantId:sessionId"). */
    private static String stripTenantPrefix(String tid) {
        if (tid == null) {
            return null;
        }
        int idx = tid.indexOf(':');
        return idx >= 0 ? tid.substring(idx + 1) : tid;
    }

    @Override
    public Store graphStore() {
        return graphStore;
    }

    private void saveWorkflowCheckpoint(String workflowId, String sessionId, BaseSession session, String reason) {
        String tid = tenantAwareSessionId(sessionId);
        touchSession(tid, sessionId);
        InMemoryWorkflowStorage workflowStore = workflowStores.get(tid);
        Set<String> workflowIds = sessionToWorkflowIds.get(tid);
        Loggers.SESSION.info("Save workflow checkpoint on {}, sessionId={}, workflowId={}", reason, sessionId,
                workflowId);
        if (workflowStore != null) {
            workflowStore.save(workflowId, session);
        }
        if (workflowIds != null) {
            workflowIds.add(workflowId);
        }
        Loggers.SESSION.info("Succeed to save workflow checkpoint on {}, sessionId={}, workflowId={}", reason,
                sessionId, workflowId);
    }

    private static class InMemoryAgentStorage {
        private final Map<String, Map<String, Object>> stateBlobs = new ConcurrentHashMap<>();

        void save(BaseSession session) {
            String agentId = getAgentId(session);
            Map<String, Object> state = session.state().getState();
            if (state != null) {
                stateBlobs.put(agentId, new HashMap<>(state));
            }
        }

        void recover(BaseSession session) {
            String agentId = getAgentId(session);
            Map<String, Object> state = stateBlobs.get(agentId);
            if (state != null) {
                session.state().setState(new HashMap<>(state));
            }
        }

        void clear(String agentId) {
            stateBlobs.remove(agentId);
        }

        private static String getAgentId(BaseSession session) {
            try {
                Object id = session.getClass().getMethod("agentId").invoke(session);
                return id != null ? id.toString() : session.sessionId();
            } catch (Exception e) {
                return session.sessionId();
            }
        }
    }

    private static class InMemoryWorkflowStorage {
        private final Map<String, Map<String, Object>> stateBlobs = new ConcurrentHashMap<>();

        private final Map<String, Map<String, Object>> stateUpdatesBlobs = new ConcurrentHashMap<>();

        void save(String workflowId, BaseSession session) {
            Map<String, Object> state = session.state().getState();
            if (state != null) {
                stateBlobs.put(workflowId, deepCopyMap(state));
            }

            if (session.state() instanceof WorkflowCommitState workflowState) {
                Map<String, Object> updates = workflowState.getUpdates();
                stateUpdatesBlobs.put(workflowId, deepCopyMap(updates));
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
                workflowState.commit();
            }
        }

        void recover(String workflowId, BaseSession session) {
            recover(workflowId, session, null);
        }

        void clear(String workflowId) {
            stateBlobs.remove(workflowId);
            stateUpdatesBlobs.remove(workflowId);
        }

        boolean isExists(String workflowId) {
            return stateBlobs.containsKey(workflowId);
        }

        boolean isEmpty() {
            return stateBlobs.isEmpty() && stateUpdatesBlobs.isEmpty();
        }

        @SuppressWarnings("unchecked")
        private void processInteractiveInputs(BaseSession session, InteractiveInput inputs) {
            if (inputs.getRawInputs() != null) {
                if (session.state() instanceof WorkflowCommitState workflowState) {
                    workflowState
                            .updateAndCommitWorkflowState(Map.of(Constant.INTERACTIVE_INPUT, inputs.getRawInputs()));
                }
                return;
            }

            for (Map.Entry<String, Object> entry : inputs.getUserInputs().entrySet()) {
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
