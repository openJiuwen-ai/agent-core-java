/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One audit record for a step boundary. Append-only, never used for resume -
 * the resume path relies on the cursor riding the state snapshot; the journal
 * exists purely for replay and audit.
 *
 * @since 0.1.17
 */
public class StepRecord {
    private String sessionId;
    private String agentId;
    private long seq;
    private String stepType;
    private String stepName;
    private String payloadJson;
    private StepStatus status;
    private long timestamp;

    public StepRecord() {
        this("", "", 0L, "", "", "", StepStatus.RUNNING, 0L);
    }

    /**
     * Create a fully populated record.
     *
     * @param sessionId owning session id
     * @param agentId owning agent id within the session
     * @param seq step sequence number, aligned with {@link StepCursor#getSeq()}
     * @param stepType step category, e.g. TOOL_CALL / LLM_RESPONSE / PHASE
     * @param stepName tool name or phase name
     * @param payloadJson sanitized parameter and result summary, may be empty
     * @param status lifecycle status of the step
     * @param timestamp wall-clock time in milliseconds
     */
    public StepRecord(String sessionId, String agentId, long seq, String stepType, String stepName,
                      String payloadJson, StepStatus status, long timestamp) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.agentId = agentId == null ? "" : agentId;
        this.seq = seq;
        this.stepType = stepType == null ? "" : stepType;
        this.stepName = stepName == null ? "" : stepName;
        this.payloadJson = payloadJson == null ? "" : payloadJson;
        this.status = status == null ? StepStatus.RUNNING : status;
        this.timestamp = timestamp;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId == null ? "" : sessionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId == null ? "" : agentId;
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

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson == null ? "" : payloadJson;
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
     * Serialize to a plain map for journal storage.
     *
     * @return map form, never null
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("session_id", sessionId);
        map.put("agent_id", agentId);
        map.put("seq", seq);
        map.put("step_type", stepType);
        map.put("step_name", stepName);
        map.put("payload_json", payloadJson);
        map.put("status", status.name());
        map.put("timestamp", timestamp);
        return map;
    }

    /**
     * Rebuild a record from its map form.
     *
     * @param map raw map read back from journal storage, may be null
     * @return the restored record, or null when the input is not a record map
     */
    public static StepRecord fromMap(Object map) {
        if (!(map instanceof Map<?, ?> raw)) {
            return null;
        }
        return new StepRecord(
                stringValue(raw.get("session_id")),
                stringValue(raw.get("agent_id")),
                longValue(raw.get("seq")),
                stringValue(raw.get("step_type")),
                stringValue(raw.get("step_name")),
                stringValue(raw.get("payload_json")),
                StepStatus.fromString(raw.get("status")),
                longValue(raw.get("timestamp")));
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
