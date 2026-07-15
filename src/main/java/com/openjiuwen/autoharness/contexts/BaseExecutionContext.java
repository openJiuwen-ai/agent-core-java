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
 * @since 0.1.7
 */
public class BaseExecutionContext {
    /**
     * orchestrator.
     * 
     * @since 0.1.7
     */
    protected final AutoHarnessOrchestrator orchestrator;

    /**
     * BaseExecutionContext.
     * 
     * @param orchestrator orchestrator
     * @since 0.1.7
     */
    public BaseExecutionContext(AutoHarnessOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * taskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String taskId() {
        return "";
    }

    /**
     * getOrchestrator.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AutoHarnessOrchestrator getOrchestrator() {
        return orchestrator;
    }

    /**
     * getArtifact.
     * 
     * @param name name
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public Object getArtifact(String name, Object defaultValue) {
        return orchestrator.getArtifacts().get(name, taskId(), defaultValue);
    }

    /**
     * getArtifact.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    public Object getArtifact(String name) {
        return getArtifact(name, null);
    }

    /**
     * requireArtifact.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    public Object requireArtifact(String name) {
        return orchestrator.getArtifacts().require(name, taskId());
    }

    /**
     * putArtifact.
     * 
     * @param name name
     * @param value value
     * @since 0.1.7
     */
    public void putArtifact(String name, Object value) {
        orchestrator.getArtifacts().put(name, value, taskId());
    }

    /**
     * putArtifacts.
     * 
     * @param artifacts artifacts
     * @since 0.1.7
     */
    public void putArtifacts(Map<String, Object> artifacts) {
        orchestrator.getArtifacts().putMany(artifacts, taskId());
    }

    /**
     * message.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    public static OutputSchema message(String text) {
        return new OutputSchema("message", 0, Map.of("content", text));
    }
}
