/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.core.multitenant.TenantNamespaceFactories;
import com.openjiuwen.core.multitenant.TenantNamespaceFactory;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.step.StepCursor;
import com.openjiuwen.core.session.checkpointer.step.StepStatus;
import com.openjiuwen.core.session.interaction.InteractiveInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base checkpointer for agent, team, workflow, and graph state.
 *
 * <p>Step-level entry points ({@link #stepCompleted}, {@link #lastCompletedStep},
 * {@link #scanRunningSteps()}) reuse the existing full-snapshot storage: the
 * cursor is written into the session state and flushed together with the state
 * by the regular save path, so no additional storage structure exists.</p>
 */
public abstract class Checkpointer implements AutoCloseable {

    public static final String SESSION_NAMESPACE_AGENT = "agent";
    public static final String SESSION_NAMESPACE_AGENT_TEAM = "agent-team";
    public static final String SESSION_NAMESPACE_WORKFLOW = "workflow";
    public static final String WORKFLOW_NAMESPACE_GRAPH = "workflow-graph";

    public static String getThreadId(BaseSession session) {
        return String.join(":", session.sessionId(), workflowId(session));
    }

    public static String workflowId(BaseSession session) {
        return stringIdentity(session, "workflowId");
    }

    public static String agentId(BaseSession session) {
        return stringIdentity(session, "agentId");
    }

    public static String teamId(BaseSession session) {
        return stringIdentity(session, "teamId");
    }

    public static BaseSession parent(BaseSession session) {
        Object value = invokeIdentity(session, "parent");
        return value instanceof BaseSession parent ? parent : null;
    }

    private static String stringIdentity(BaseSession session, String methodName) {
        Object value = invokeIdentity(session, methodName);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object invokeIdentity(BaseSession session, String methodName) {
        if (session == null) {
            return null;
        }
        try {
            return session.getClass().getMethod(methodName).invoke(session);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static String buildKey(String... parts) {
        for (String part : parts) {
            if (part == null) {
                throw new NullPointerException("key part");
            }
        }
        return String.join(":", parts);
    }

    public static String buildKeyWithNamespace(String sessionId, String namespace, String entityId,
                                               String... suffixes) {
        String[] parts = new String[3 + suffixes.length];
        parts[0] = sessionId;
        parts[1] = namespace;
        parts[2] = entityId;
        System.arraycopy(suffixes, 0, parts, 3, suffixes.length);
        return buildKey(parts);
    }

    /**
     * Build a namespaced key and resolve it against the current tenant context.
     */
    public static String resolveNsKey(String sessionId, String namespace, String entityId,
            String... suffixes) {
        return TenantKVStoreKeyResolver.resolveKey(
                buildKeyWithNamespace(sessionId, namespace, entityId, suffixes));
    }

    /**
     * Build a namespaced prefix and resolve it against the current tenant context.
     */
    public static String resolveNsPrefix(String sessionId, String namespace, String entityId,
            String... suffixes) {
        return TenantKVStoreKeyResolver.resolvePrefix(
                buildKeyWithNamespace(sessionId, namespace, entityId, suffixes));
    }

    /**
     * Build a tenant-namespaced key from parts using the default namespace factory.
     */
    public static String buildKeyWithTenant(TenantContext ctx, String... parts) {
        return buildKeyWithTenant(TenantNamespaceFactories.KV_STORE_DEFAULT, ctx, parts);
    }

    /**
     * Build a tenant-namespaced key from parts using the given namespace factory.
     */
    public static String buildKeyWithTenant(TenantNamespaceFactory factory, TenantContext ctx, String... parts) {
        TenantNamespaceFactory nsFactory = factory != null ? factory : TenantNamespaceFactories.KV_STORE_DEFAULT;
        String rawKey = buildKey(parts);
        return nsFactory.namespace(ctx, rawKey);
    }

    public void preAgentExecute(BaseSession session, Object inputs) {
    }

    public void interruptAgentExecute(BaseSession session) {
    }

    public void postAgentExecute(BaseSession session) {
    }

    /**
     * Persist a completed step boundary: writes the cursor into the session
     * state under {@link StepCursor#STATE_KEY} and flushes the full snapshot
     * through the regular agent-state save, keeping cursor and state in the
     * same transaction.
     *
     * <p>The default implementation works on every backend because it only
     * relies on the session state and {@link #postAgentExecute}. Backends may
     * override for cheaper equivalents.</p>
     *
     * @param session session in the middle of a task
     * @param cursor cursor describing the completed step; seq must be strictly
     *               greater than the currently persisted cursor
     */
    public void stepCompleted(BaseSession session, StepCursor cursor) {
        if (session == null || cursor == null || session.state() == null) {
            return;
        }
        StepCursor previous = lastCompletedStep(session);
        if (!cursor.isAfter(previous)) {
            return;
        }
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(StepCursor.STATE_KEY, cursor.toMap());
        session.state().update(update);
        postAgentExecute(session);
    }

    /**
     * Return the last persisted completed-step cursor for the session.
     *
     * <p>The default reads {@link StepCursor#STATE_KEY} out of the live session
     * state, which was restored from the snapshot at execution start and kept
     * fresh by {@link #stepCompleted}.</p>
     *
     * @param session session to inspect
     * @return the last cursor, or null when no step has been persisted yet
     */
    public StepCursor lastCompletedStep(BaseSession session) {
        if (session == null || session.state() == null) {
            return null;
        }
        Object state = session.state().get(StepCursor.STATE_KEY);
        StepCursor cursor = StepCursor.fromMap(state);
        return cursor == null ? null : cursor;
    }

    /**
     * Scan persisted sessions whose last step cursor is still RUNNING, i.e.
     * tasks left behind by a crashed or restarted process. Used by recovery
     * and the runtime dead-step sweeper.
     *
     * <p>The base implementation has no cross-session index and returns an
     * empty list; backends with a queryable store override it.</p>
     *
     * @return cursors of sessions with an unfinished last step, empty when none
     */
    public List<StepCursor> scanRunningSteps() {
        return List.of();
    }

    /**
     * Whether a persisted last-step cursor is in RUNNING state.
     *
     * @param cursor cursor to inspect, may be null
     * @return true when the cursor exists and its status is RUNNING
     */
    protected static boolean isRunningCursor(StepCursor cursor) {
        return cursor != null && cursor.getStatus() == StepStatus.RUNNING;
    }

    /**
     * Deserialize a cursor blob stored under a raw KV key.
     *
     * @param blob value previously written through {@link #writeCursorBlob}, may be null
     * @return the restored cursor, or null when the blob is absent or not a cursor map
     */
    protected static StepCursor readCursorBlob(Object blob) {
        return StepCursor.fromMap(blob);
    }

    /**
     * Build the serialized map form of a cursor for raw KV writes.
     *
     * @param cursor cursor to serialize
     * @return map form, never null
     */
    protected static Map<String, Object> writeCursorBlob(StepCursor cursor) {
        Objects.requireNonNull(cursor, "cursor");
        return cursor.toMap();
    }

    /**
     * Collect cursors in RUNNING state from recovered state snapshots.
     *
     * @param states snapshot maps keyed by session id, values may be anything
     * @return one RUNNING cursor per session that has one
     */
    protected static List<StepCursor> runningCursorsFromStates(Map<String, Object> states) {
        List<StepCursor> running = new ArrayList<>();
        if (states == null) {
            return running;
        }
        for (Map.Entry<String, Object> entry : states.entrySet()) {
            Object snapshot = entry.getValue();
            if (!(snapshot instanceof Map<?, ?> snapshotMap)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            snapshotMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            StepCursor cursor = StepCursor.fromState(normalized);
            if (isRunningCursor(cursor)) {
                running.add(cursor);
            }
        }
        return running;
    }

    public void preAgentTeamExecute(BaseSession session, Object inputs) {
    }

    public void postAgentTeamExecute(BaseSession session) {
    }

    public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
    }

    public void preWorkflowExecute(BaseSession session, Object inputs) {
        preWorkflowExecute(session, inputs instanceof InteractiveInput interactiveInput ? interactiveInput : null);
    }

    public void postWorkflowExecute(BaseSession session, Object result, Exception exception) {
    }

    public boolean sessionExists(String sessionId) {
        return false;
    }

    public void release(String sessionId) {
    }

    public Store graphStore() {
        return null;
    }

    /**
     * Close resources owned by this checkpointer.
     */
    @Override
    public void close() {
        // Default no-op for checkpointers without external resources.
    }
}
