/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.contexts;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared execution context surface.
 *
 * <p>Mirrors Python's {@code BaseExecutionContext} in
 * {@code openjiuwen/auto_harness/contexts/execution.py}.</p>
 */
public abstract class BaseExecutionContext {

    private AutoHarnessOrchestrator orchestrator;

    protected BaseExecutionContext(AutoHarnessOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public String getTaskId() {
        return "";
    }

    public Object getArtifact(String name, Object defaultValue) {
        return orchestrator.getArtifacts().get(name, getTaskId(), defaultValue);
    }

    public Object getArtifact(String name) {
        return getArtifact(name, null);
    }

    public Object requireArtifact(String name) {
        return orchestrator.getArtifacts().require(name, getTaskId());
    }

    public void putArtifact(String name, Object value) {
        orchestrator.getArtifacts().put(name, value, getTaskId());
    }

    public void putArtifacts(Map<String, Object> artifacts) {
        orchestrator.getArtifacts().putMany(artifacts, getTaskId());
    }

    public static OutputSchema message(String text) {
        return message(text, "");
    }

    public static OutputSchema message(String text, String stage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", text);
        if (stage != null && !stage.isEmpty()) {
            payload.put("stage", stage);
        }
        return new OutputSchema("message", 0, payload);
    }

    public static OutputSchema stageResultOutput(String stage, String status) {
        return stageResultOutput(stage, status, "", null, null);
    }

    public static OutputSchema stageResultOutput(
            String stage,
            String status,
            String error,
            List<String> messages,
            Map<String, Object> metrics
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", stage);
        payload.put("status", status);
        payload.put("error", error == null ? "" : error);
        payload.put("messages", messages == null ? new ArrayList<>() : new ArrayList<>(messages));
        payload.put("metrics", metrics == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metrics));
        return new OutputSchema("stage_result", 0, payload);
    }

    public AutoHarnessOrchestrator getOrchestrator() {
        return orchestrator;
    }

    public void setOrchestrator(AutoHarnessOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }
}
