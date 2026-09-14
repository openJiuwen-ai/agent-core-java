/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base evaluator-pipeline agent adapter contract.
 *
 * <p>Mirrors Python's {@code BaseAgentAdapter} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/base.py}.</p>
 */
public abstract class BaseAgentAdapter {

    private final Map<String, Object> config;
    private Path logsDir;
    private int totalInputTokens;
    private int totalOutputTokens;

    protected BaseAgentAdapter() {
        this(null);
    }

    protected BaseAgentAdapter(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
    }

    public abstract String name();

    public abstract List<String> supportedSkillsModes();

    public String defaultModel() {
        return null;
    }

    public List<String> validateConfig() {
        return List.of();
    }

    public Path getLogsDir() {
        if (logsDir == null) {
            throw new RuntimeException("logs_dir not set, call setLogsDir() first");
        }
        return logsDir;
    }

    public void setLogsDir(Path logsDir) {
        if (logsDir == null) {
            throw new IllegalArgumentException("logsDir must not be null");
        }
        try {
            Files.createDirectories(logsDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create logs dir " + logsDir, exception);
        }
        this.logsDir = logsDir;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public int getTotalInputTokens() {
        return totalInputTokens;
    }

    public int getTotalOutputTokens() {
        return totalOutputTokens;
    }

    protected void setTotalInputTokens(int totalInputTokens) {
        this.totalInputTokens = totalInputTokens;
    }

    protected void setTotalOutputTokens(int totalOutputTokens) {
        this.totalOutputTokens = totalOutputTokens;
    }

    public abstract CompletableFuture<Boolean> setup(DockerEnvironment env);

    public abstract CompletableFuture<AgentRunResult> run(
            DockerEnvironment env,
            Task task,
            AgentContext context);

    public CompletableFuture<Integer> loadSkills(
            DockerEnvironment env,
            Map<String, String> skills,
            Map<String, String> evolutions,
            Map<String, Map<String, String>> evolutionFiles) {
        return CompletableFuture.completedFuture(0);
    }

    public void setSkillContext(String resolvedName, List<String> allNames) {
        // Default no-op to mirror the Python base implementation.
    }

    public CompletableFuture<List<String>> loadSkillsFromDir(DockerEnvironment env, Path skillsDir) {
        return CompletableFuture.completedFuture(List.of());
    }

    public Map<String, String> getCapturedEvolutionJson() {
        return Map.of();
    }

    public CompletableFuture<SkillDelta> captureSkills(DockerEnvironment env) {
        return CompletableFuture.completedFuture(new SkillDelta());
    }

    public Map<String, Object> getSourceFiles() {
        return null;
    }
}
