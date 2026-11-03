/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import com.openjiuwen.core.session.state.State;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Step cursor metadata persisted together with the full state snapshot.
 *
 * <p>Every completed step boundary writes one cursor into the session state
 * under {@link #STATE_KEY}. Because the cursor and the state snapshot are
 * flushed by the same checkpointer save, recovery reads the cursor to know
 * "which step was reached" and restores the state of that exact moment -
 * there is no window where the cursor and the state disagree.</p>
 *
 * @since 0.1.17
 */
public class StepCursor {
    /** Session state key under which the cursor map is stored. */
    public static final String STATE_KEY = "step_cursor";

    /** Session state key under which the latest failed-step cursor is stored. */
    public static final String FAILED_STATE_KEY = "step_cursor_failed";

    private long seq;
    private String stepType;
    private String stepName;
    private StepStatus status;
    private long timestamp;

    public StepCursor() {
        this(0L, "", "", StepStatus.RUNNING, 0L);
    }

    /**
     * Create a cursor with explicit fields.
     *
     * @param seq monotonically increasing step sequence number within a task
     * @param stepType step category, e.g. TOOL_CALL / LLM_RESPONSE / PHASE
     * @param stepName tool name or phase name
     * @param status lifecycle status of the step
     * @param timestamp wall-clock time in milliseconds
     */
    public StepCursor(long seq, String stepType, String stepName, StepStatus status, long timestamp) {
        this.seq = seq;
        this.stepType = stepType == null ? "" : stepType;
        this.stepName = stepName == null ? "" : stepName;
        this.status = status == null ? StepStatus.RUNNING : status;
        this.timestamp = timestamp;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType == null ? "" : stepType;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName == null ? "" : stepName;
    }

    public StepStatus getStatus() {
        return status;
    }

    public void setStatus(StepStatus status) {
        this.status = status == null ? StepStatus.RUNNING : status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Serialize to a plain map so the cursor rides inside the state snapshot
     * without requiring a dedicated serializer.
     *
     * @return map form of this cursor, never null
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("seq", seq);
        map.put("step_type", stepType);
        map.put("step_name", stepName);
        map.put("status", status.name());
        map.put("timestamp", timestamp);
        return map;
    }

    /**
     * Rebuild a cursor from its map form.
     *
     * @param map raw map read back from a state snapshot, may be null
     * @return the restored cursor, or null when the input is not a cursor map
     *         (a map without a numeric {@code seq} marker is never a cursor)
     */
    public static StepCursor fromMap(Object map) {
        if (!(map instanceof Map<?, ?> raw) || !hasSeqMarker(raw)) {
            return null;
        }
        return new StepCursor(
                longValue(raw.get("seq")),
                stringValue(raw.get("step_type")),
                stringValue(raw.get("step_name")),
                StepStatus.fromString(raw.get("status")),
                longValue(raw.get("timestamp")));
    }

    private static boolean hasSeqMarker(Map<?, ?> raw) {
        return raw.get("seq") instanceof Number || raw.get("seq") instanceof String;
    }

    /**
     * Read the current cursor from a session state snapshot.
     *
     * <p>The cursor written through {@code session.state().update(...)} lands
     * in the agent-state partition, so a snapshot taken by
     * {@code session.state().getState()} wraps it under the
     * {@code agent_state} key. This lookup therefore checks the top level
     * first and falls back to the agent-state partition, covering both raw
     * partition maps and wrapped snapshots.</p>
     *
     * @param state full state map as returned by {@code session.state().getState()}, may be null
     * @return the cursor stored under {@link #STATE_KEY}, or null when absent or malformed
     */
    public static StepCursor fromState(Map<String, Object> state) {
        if (state == null) {
            return null;
        }
        StepCursor cursor = fromMap(state.get(STATE_KEY));
        if (cursor != null) {
            return cursor;
        }
        Object agentPartition = state.get(State.AGENT_STATE_KEY);
        if (agentPartition instanceof Map<?, ?> agentMap) {
            return fromMap(agentMap.get(STATE_KEY));
        }
        return null;
    }

    /**
     * Read the latest failed-step cursor from a session state snapshot, using
     * the same top-level-then-agent-partition lookup as {@link #fromState}.
     *
     * @param state full state map as returned by {@code session.state().getState()}, may be null
     * @return the failed cursor stored under {@link #FAILED_STATE_KEY},
     *         or null when absent or malformed
     */
    public static StepCursor failedFromState(Map<String, Object> state) {
        if (state == null) {
            return null;
        }
        StepCursor cursor = fromMap(state.get(FAILED_STATE_KEY));
        if (cursor != null) {
            return cursor;
        }
        Object agentPartition = state.get(State.AGENT_STATE_KEY);
        if (agentPartition instanceof Map<?, ?> agentMap) {
            return fromMap(agentMap.get(FAILED_STATE_KEY));
        }
        return null;
    }

    /**
     * Whether this cursor advanced beyond the given one, used by recovery to
     * reject stale or out-of-order writes.
     *
     * @param other cursor to compare against, may be null
     * @return true when this cursor has a strictly greater sequence number
     */
    public boolean isAfter(StepCursor other) {
        return other == null || seq > other.seq;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StepCursor cursor)) {
            return false;
        }
        return seq == cursor.seq
                && timestamp == cursor.timestamp
                && stepType.equals(cursor.stepType)
                && stepName.equals(cursor.stepName)
                && status == cursor.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seq, stepType, stepName, status, timestamp);
    }

    @Override
    public String toString() {
        return "StepCursor{seq=" + seq + ", stepType=" + stepType
                + ", stepName=" + stepName + ", status=" + status + ", timestamp=" + timestamp + "}";
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
