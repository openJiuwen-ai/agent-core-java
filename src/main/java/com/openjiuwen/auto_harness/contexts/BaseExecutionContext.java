/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.contexts;

import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared execution context surface.
 *
 * <p>Mirrors Python's {@code BaseExecutionContext} in {@code openjiuwen.auto_harness.contexts.execution}.</p>
 */
public abstract class BaseExecutionContext {

    protected AutoHarnessOrchestrator orchestrator;

    public BaseExecutionContext(AutoHarnessOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Get the task ID for this context.
     *
     * @return empty string by default, overridden by TaskContext
     */
    public String getTaskId() {
        return "";
    }

    /**
     * Get an artifact by name.
     *
     * @param name         the artifact name
     * @param defaultValue the default value if not found
     * @return the artifact value
     */
    public Object getArtifact(String name, Object defaultValue) {
        return orchestrator.getArtifacts().get(name, getTaskId(), defaultValue);
    }

    /**
     * Get an artifact by name with null default.
     *
     * @param name the artifact name
     * @return the artifact value or null
     */
    public Object getArtifact(String name) {
        return getArtifact(name, null);
    }

    /**
     * Require an artifact to exist.
     *
     * @param name the artifact name
     * @return the artifact value
     * @throws IllegalArgumentException if not found
     */
    public Object requireArtifact(String name) {
        return orchestrator.getArtifacts().require(name, getTaskId());
    }

    /**
     * Put an artifact into the store.
     *
     * @param name  the artifact name
     * @param value the artifact value
     */
    public void putArtifact(String name, Object value) {
        orchestrator.getArtifacts().put(name, value, getTaskId());
    }

    /**
     * Put multiple artifacts at once.
     *
     * @param artifacts the artifacts map
     */
    public void putArtifacts(Map<String, Object> artifacts) {
        orchestrator.getArtifacts().putMany(artifacts, getTaskId());
    }

    /**
     * Create a message OutputSchema.
     *
     * @param text the message text
     * @return an OutputSchema with message type
     */
    public static OutputSchema message(String text) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", text);
        return new OutputSchema("message", 0, payload);
    }

    /**
     * Get the orchestrator.
     *
     * @return the orchestrator instance
     */
    public AutoHarnessOrchestrator getOrchestrator() {
        return orchestrator;
    }

    /**
     * Set the orchestrator.
     *
     * @param orchestrator the orchestrator instance
     */
    public void setOrchestrator(AutoHarnessOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }
}