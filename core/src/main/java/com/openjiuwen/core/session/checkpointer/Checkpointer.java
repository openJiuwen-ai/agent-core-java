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
import com.openjiuwen.core.session.state.State;

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

    /**
     * Session state key under which pause metadata (operator, reason, timestamps)
     * is stored alongside the paused cursor.
     */
    public static final String SESSION_PAUSE_STATE_KEY = "session_pause";

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
        StepCursor previous = lastCompletedStep(session);
        if (!cursor.isAfter(previous)) {
            return;
        }
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(StepCursor.STATE_KEY, cursor.toMap());
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
        update.put(StepCursor.FAILED_STATE_KEY, cursor.toMap());
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
     * Persist a user-requested pause point: writes the PAUSED cursor under
     * {@link StepCursor#PAUSED_STATE_KEY} plus the pause metadata under
     * {@link #SESSION_PAUSE_STATE_KEY}, then flushes the full snapshot through
     * the regular save path so cursor, metadata, and live business state land
     * in the same transaction.
     *
     * <p>The paused cursor marks user intent: recovery scans skip it, and only
     * the explicit resume entry point may continue the session. The main
     * cursor under {@link StepCursor#STATE_KEY} keeps pointing at the last
     * completed step, so {@link #lastCompletedStep} semantics are untouched.
     * The cursor carries the owning session id so whole-store resume matching
     * never has to rely on seq alone, and a stale seq is rejected like in
     * {@link #stepCompleted} so an old pause cannot roll the resume point
     * back.</p>
     *
     * @param session session being paused, carrying the live state to freeze
     * @param cursor cursor describing the boundary where the execution stopped;
     *               its status is forced to {@link StepStatus#PAUSED} and its
     *               session id to the owning session
     * @param pauseInfo pause metadata map (operator, reason, timestamps), may
     *                  be null to persist the cursor only
     * @return true when the paused evidence was persisted; false when the
     *         inputs were absent or the stale-seq guard skipped the write,
     *         leaving the previous pause evidence untouched
     */
    public boolean stepPaused(BaseSession session, StepCursor cursor, Map<String, Object> pauseInfo) {
        if (session == null || cursor == null || session.state() == null) {
            return false;
        }
        StepCursor pausedCursor = new StepCursor(cursor.getSeq(), cursor.getStepType(), cursor.getStepName(),
                StepStatus.PAUSED, cursor.getTimestamp(), session.sessionId());
        if (!shouldPersistPausedCursor(session, pausedCursor)) {
            return false;
        }
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(StepCursor.PAUSED_STATE_KEY, pausedCursor.toMap());
        if (pauseInfo != null && !pauseInfo.isEmpty()) {
            update.put(SESSION_PAUSE_STATE_KEY, new LinkedHashMap<>(pauseInfo));
        }
        session.state().update(update);
        boolean isSaved = false;
        try {
            postAgentExecute(session);
            isSaved = true;
        } finally {
            if (!isSaved) {
                rollbackPausedUpdate(session, update);
            }
        }
        return isSaved;
    }

    /**
     * Whether the paused cursor may overwrite the persisted pause evidence:
     * the seq must be strictly greater than both the stored paused cursor and
     * the main resume point, mirroring the monotonic guard of
     * {@link #stepCompleted}. Pauses repeat legitimately at different
     * boundaries, so unlike completion a newer paused cursor always wins.
     *
     * @param session session being paused
     * @param pausedCursor pause-bound cursor already carrying the session id
     * @return true when the cursor advances beyond the persisted cursors
     */
    private boolean shouldPersistPausedCursor(BaseSession session, StepCursor pausedCursor) {
        StepCursor storedPaused = lastPausedStep(session);
        if (storedPaused != null && !pausedCursor.isAfter(storedPaused)) {
            return false;
        }
        return pausedCursor.isAfter(lastCompletedStep(session));
    }

    /**
     * Undo a failed pause save by deleting the paused keys from the live
     * state, mirroring the {@link #stepCompleted} rollback: the state keeps
     * its pre-pause shape and the next save cannot resurrect stale evidence.
     *
     * @param session session whose pause save failed
     * @param update the update map previously applied to the live state
     */
    private void rollbackPausedUpdate(BaseSession session, Map<String, Object> update) {
        Map<String, Object> rollback = new LinkedHashMap<>();
        rollback.put(StepCursor.PAUSED_STATE_KEY, null);
        if (update.containsKey(SESSION_PAUSE_STATE_KEY)) {
            rollback.put(SESSION_PAUSE_STATE_KEY, null);
        }
        session.state().update(rollback);
    }

    /**
     * Return the last persisted paused-step cursor for the session.
     *
     * @param session session to inspect
     * @return the paused cursor, or null when the session is not paused
     */
    public StepCursor lastPausedStep(BaseSession session) {
        if (session == null || session.state() == null) {
            return null;
        }
        Object state = session.state().get(StepCursor.PAUSED_STATE_KEY);
        StepCursor cursor = StepCursor.fromMap(state);
        return cursor == null ? null : cursor;
    }

    /**
     * Return the last persisted paused-step cursor of one session looked up
     * by id, without requiring a live session object. Backends with a
     * queryable store deserialize the paused cursor from the session's
     * persisted agent-state blob; the session-id entry point serves the
     * explicit resume API where only the id is known (issue #117).
     *
     * <p>The base implementation has no cross-session index and returns
     * null; backends override it.</p>
     *
     * @param sessionId id of the session to inspect, never null or blank
     * @return the paused cursor, or null when the session has none
     */
    public StepCursor lastPausedStep(String sessionId) {
        return null;
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
     * Scan persisted sessions whose paused-step cursor exists, i.e. sessions
     * a user explicitly suspended. Unlike {@link #scanRunningSteps} this scan
     * reports user intent, never crash leftovers, and must not feed the
     * automatic recovery pipeline.
     *
     * <p>The base implementation has no cross-session index and returns an
     * empty list; backends with a queryable store override it.</p>
     *
     * @return cursors of paused sessions, empty when none
     */
    public List<StepCursor> scanPausedSteps() {
        return List.of();
    }

    /**
     * Resume one user-paused session: copies the paused cursor back onto the
     * main resume point as RUNNING (keeping its seq so the continuation
     * starts from the exact suspended boundary) and clears the paused
     * evidence from the state, all inside the regular save transaction.
     *
     * <p>The default implementation is a no-op; backends with a queryable
     * store override it.</p>
     *
     * @param cursor paused cursor of the session to resume, never null
     */
    public void resumePaused(StepCursor cursor) {
        // Default no-op: the base class has no cross-session index to find
        // the session behind the cursor; persistent backends override this.
    }

    /**
     * Resume one user-paused session identified by id: the paused cursor
     * flips back to RUNNING on the main resume point and the paused evidence
     * is cleared, all inside the regular save transaction. The id-based
     * entry point serves the explicit resume API where only the session id
     * is known (issue #117).
     *
     * <p>The default implementation is a no-op; backends with a queryable
     * store override it.</p>
     *
     * @param sessionId id of the session to resume, never null or blank
     */
    public void resumePaused(String sessionId) {
        // Default no-op: the base class has no cross-session index to find
        // the paused state behind the id; persistent backends override this.
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
     * Apply an explicit resume to one persisted full-state snapshot: flip the
     * main cursor back to RUNNING at the paused seq and clear the paused
     * evidence. Cursor keys live inside the {@code agent_state} partition of
     * the snapshot (the {@code getState()} form), so the mutation targets that
     * partition; legacy flat snapshots holding the paused key at the top level
     * are edited in place. Callers persist the mutated map afterwards.
     *
     * @param state mutable full-state snapshot map, never null
     * @param paused paused cursor of the session to resume, never null
     */
    public static void applyResumeToState(Map<String, Object> state, StepCursor paused) {
        Map<String, Object> partition = pausedKeysPartition(state);
        partition.put(StepCursor.STATE_KEY, new StepCursor(paused.getSeq(), paused.getStepType(),
                paused.getStepName(), StepStatus.RUNNING, System.currentTimeMillis(), paused.getSessionId()).toMap());
        partition.remove(StepCursor.PAUSED_STATE_KEY);
        partition.remove(SESSION_PAUSE_STATE_KEY);
    }

    /**
     * Resolve the map holding the paused-cursor keys of a persisted snapshot:
     * the top level for legacy flat snapshots, otherwise the
     * {@code agent_state} partition, which is created when absent.
     *
     * @param state mutable full-state snapshot map, never null
     * @return the mutable map to edit, never null
     */
    private static Map<String, Object> pausedKeysPartition(Map<String, Object> state) {
        if (state.containsKey(StepCursor.PAUSED_STATE_KEY)) {
            return state;
        }
        Object partition = state.get(State.AGENT_STATE_KEY);
        Map<String, Object> mutable = new LinkedHashMap<>();
        if (partition instanceof Map<?, ?> agentMap) {
            agentMap.forEach((key, value) -> mutable.put(String.valueOf(key), value));
        }
        state.put(State.AGENT_STATE_KEY, mutable);
        return mutable;
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
     * <p>Snapshots that still carry paused evidence are skipped: a paused
     * session records user intent, not a crash leftover, so it must never
     * feed the automatic recovery pipeline even when its main cursor stayed
     * RUNNING (e.g. the interrupted boundary persisted before the pause).</p>
     *
     * @param states snapshot maps keyed by session id, values may be anything
     * @return one RUNNING cursor per session that has one and holds no pause evidence
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
            if (isRunningCursor(cursor) && StepCursor.pausedFromState(normalized) == null) {
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
