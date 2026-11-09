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

    /** Suffix of the KV key holding one agent's state blob. */
    private static final String AGENT_STATE_BLOBS_SUFFIX = ":agent_state_blobs";

    /** Marker separating session id and agent id inside an agent-state blob key. */
    private static final String AGENT_NAMESPACE_MARKER = ":" + SESSION_NAMESPACE_AGENT + ":";

    public static String getThreadId(BaseSession session) {
        return String.join(":", session.sessionId(), workflowId(session));
    }

    public static String workflowId(BaseSession session) {
        return stringIdentity(session, "workflowId");
    }

    public static String agentId(BaseSession session) {
        if (session instanceof CheckpointSnapshotSession snapshot) {
            return snapshot.agentId();
        }
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
        StepCursor owned = cursor.withOwner(session.sessionId(), agentId(session));
        StepCursor previous = lastCompletedStep(session);
        if (!owned.isAfter(previous)) {
            return;
        }
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(StepCursor.STATE_KEY, owned.toMap());
        session.state().update(update);
        boolean isSaved = false;
        try {
            postAgentExecute(session);
            isSaved = true;
        } finally {
            if (!isSaved) {
                Map<String, Object> rollback = new LinkedHashMap<>();
                rollback.put(StepCursor.STATE_KEY, previous == null ? null : previous.toMap());
                session.state().update(rollback);
            }
        }
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
     * Persist failure evidence on the last committed snapshot, without publishing
     * live messages or tool state from an incomplete step.
     *
     * <p>The main cursor under {@link StepCursor#STATE_KEY} keeps pointing at
     * the last completed step, so resume and {@link #lastCompletedStep}
     * semantics are untouched - the failed cursor is an observability record
     * of where the task died, never a resumption point.</p>
     *
     * @param session session whose step failed
     * @param cursor cursor describing the failed step
     */
    public void stepFailed(BaseSession session, StepCursor cursor) {
        if (session == null || cursor == null || session.state() == null) {
            return;
        }
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(StepCursor.FAILED_STATE_KEY,
                cursor.withOwner(session.sessionId(), agentId(session)).toMap());
        CheckpointSnapshotSession snapshot = new CheckpointSnapshotSession(session);
        preAgentExecute(snapshot, null);
        snapshot.state().update(update);
        postAgentExecute(snapshot);
        session.state().update(update);
    }

    /**
     * Return the last persisted failed-step cursor for the session.
     *
     * @param session session to inspect
     * @return the failed cursor, or null when no failed step has been persisted
     */
    public StepCursor lastFailedStep(BaseSession session) {
        if (session == null || session.state() == null) {
            return null;
        }
        Object state = session.state().get(StepCursor.FAILED_STATE_KEY);
        StepCursor cursor = StepCursor.fromMap(state);
        return cursor == null ? null : cursor;
    }

    /**
     * Scan persisted sessions whose last step cursor is still RUNNING, i.e.
     * tasks left behind by a crashed or restarted process. Used by recovery
     * and the runtime dead-step sweeper.
     *
     * <p>The base implementation has no cross-session index and returns an
     * empty list; backends with a queryable store override it. An override
     * must return cursors carrying their owning session/agent identity, because
     * recovery hands it to the business hook.</p>
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
     * <p>Entry keys are the storage keys the snapshots were read from; they are
     * used to fill a missing session/agent identity on the cursor, so snapshots
     * written before the cursor carried its identity still recover with one.</p>
     *
     * @param states snapshot maps keyed by their storage key, values may be anything
     * @param scanPrefix prefix the caller used to scan {@code states}, null treated as empty
     * @return one RUNNING cursor per scanned state blob that has one
     */
    protected static List<StepCursor> runningCursorsFromStates(Map<String, Object> states, String scanPrefix) {
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
                running.add(withOwnerFromBlobKey(entry.getKey(), scanPrefix, cursor));
            }
        }
        return running;
    }

    /**
     * Fill the owning session/agent identity of a scanned cursor from the KV
     * key of its agent-state blob.
     *
     * <p>Snapshots written before the cursor carried its identity only expose
     * it through the key shape
     * {@code <prefix><sessionId>:agent:<agentId>:agent_state_blobs}. The prefix
     * must be the one the caller actually scanned with: it is passed in rather
     * than resolved here, because the tenant context at parse time may differ
     * from the one the scan ran under. A scan performed without a bound tenant
     * uses an empty prefix and also returns tenant-scoped keys; for those the
     * restored session id keeps its {@code <tenantId>:} prefix, which callers
     * must treat as opaque.</p>
     *
     * @param key KV key the cursor was read from, may be null
     * @param scanPrefix prefix the caller used for the scan, null treated as empty
     * @param cursor cursor read from that key, may be null
     * @return the cursor with its identity filled, or the input cursor when it
     *         already carries one or the key is not an agent-state blob key
     */
    public static StepCursor withOwnerFromBlobKey(String key, String scanPrefix, StepCursor cursor) {
        if (cursor == null || key == null || !cursor.getSessionId().isEmpty()) {
            return cursor;
        }
        String prefix = scanPrefix == null ? "" : scanPrefix;
        String raw = key.startsWith(prefix) ? key.substring(prefix.length()) : key;
        if (!raw.endsWith(AGENT_STATE_BLOBS_SUFFIX)) {
            return cursor;
        }
        String body = raw.substring(0, raw.length() - AGENT_STATE_BLOBS_SUFFIX.length());
        int marker = body.lastIndexOf(AGENT_NAMESPACE_MARKER);
        if (marker <= 0) {
            return cursor;
        }
        String ownerSessionId = body.substring(0, marker);
        String ownerAgentId = body.substring(marker + AGENT_NAMESPACE_MARKER.length());
        return cursor.withOwner(ownerSessionId, ownerAgentId);
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
     * Returns the store path actually used by this checkpointer, or {@code null} when the
     * backend has no filesystem path (in-memory, remote stores). Callers can use this to
     * discover silent path rewrites applied during configuration.
     */
    public String actualStorePath() {
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
