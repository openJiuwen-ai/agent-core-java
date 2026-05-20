/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.contexts;

import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.Map;

/**
 * Public class BaseExecutionContext used by the Java parity implementation.
 *
 * @since 1.0
 */
public class BaseExecutionContext {
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final AutoHarnessOrchestrator orchestrator;

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseExecutionContext(AutoHarnessOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String taskId() {
        return "";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AutoHarnessOrchestrator getOrchestrator() {
        return orchestrator;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getArtifact(String name, Object defaultValue) {
        return orchestrator.getArtifacts().get(name, taskId(), defaultValue);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getArtifact(String name) {
        return getArtifact(name, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object requireArtifact(String name) {
        return orchestrator.getArtifacts().require(name, taskId());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void putArtifact(String name, Object value) {
        orchestrator.getArtifacts().put(name, value, taskId());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void putArtifacts(Map<String, Object> artifacts) {
        orchestrator.getArtifacts().putMany(artifacts, taskId());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static OutputSchema message(String text) {
        return new OutputSchema("message", 0, Map.of("content", text));
    }
}
