/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Per-team stream consumer task and runtime-ready barrier.
 *
 * <p>Mirrors Python's {@code StreamHandle} in
 * {@code openjiuwen/agent_teams/cli/state.py}.</p>
 */
public class StreamHandle {

    private String teamName;
    private String sessionId;
    private CompletableFuture<Map<String, Object>> runtimeReady;
    private CompletableFuture<Void> task;
    private boolean cancelled;

    public StreamHandle(
            String teamName,
            String sessionId,
            CompletableFuture<Map<String, Object>> runtimeReady,
            CompletableFuture<Void> task
    ) {
        this(teamName, sessionId, runtimeReady, task, false);
    }

    public StreamHandle(
            String teamName,
            String sessionId,
            CompletableFuture<Map<String, Object>> runtimeReady,
            CompletableFuture<Void> task,
            boolean cancelled
    ) {
        this.teamName = teamName;
        this.sessionId = sessionId;
        this.runtimeReady = runtimeReady;
        this.task = task;
        this.cancelled = cancelled;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public CompletableFuture<Map<String, Object>> getRuntimeReady() {
        return runtimeReady;
    }

    public void setRuntimeReady(CompletableFuture<Map<String, Object>> runtimeReady) {
        this.runtimeReady = runtimeReady;
    }

    public CompletableFuture<Void> getTask() {
        return task;
    }

    public void setTask(CompletableFuture<Void> task) {
        this.task = task;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
