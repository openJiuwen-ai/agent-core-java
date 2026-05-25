/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

/**
 * Budget processor state for tracking budget processing status.
 * <p>
 * Mirrors Python's budget processor state from
 * {@code core/context_engine/context/processor_state_recorder.py}.
 */
public class BudgetProcessorState {

    private String operationId;
    private String status;
    private String phase;
    private String trigger;
    private String reason;
    private long startedAt;
    private long endedAt;

    public BudgetProcessorState() {
        this.operationId = "";
        this.status = "initialized";
        this.phase = "start";
        this.trigger = "";
        this.reason = "";
        this.startedAt = System.currentTimeMillis();
    }

    public BudgetProcessorState(String operationId, String status, String phase) {
        this.operationId = operationId;
        this.status = status;
        this.phase = phase;
        this.trigger = "";
        this.reason = "";
        this.startedAt = System.currentTimeMillis();
    }

    // Getters
    public String getOperationId() { return operationId; }
    public String getStatus() { return status; }
    public String getPhase() { return phase; }
    public String getTrigger() { return trigger; }
    public String getReason() { return reason; }
    public long getStartedAt() { return startedAt; }
    public long getEndedAt() { return endedAt; }

    // Setters
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public void setStatus(String status) { this.status = status; }
    public void setPhase(String phase) { this.phase = phase; }
    public void setTrigger(String trigger) { this.trigger = trigger; }
    public void setReason(String reason) { this.reason = reason; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }

    public void complete() {
        this.status = "completed";
        this.endedAt = System.currentTimeMillis();
    }

    public void fail(String errorReason) {
        this.status = "failed";
        this.reason = errorReason;
        this.endedAt = System.currentTimeMillis();
    }
}