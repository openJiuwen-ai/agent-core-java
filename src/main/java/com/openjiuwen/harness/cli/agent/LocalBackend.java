/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.cli.rails.TokenTrackingRail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Backend that calls the local SDK Runner directly.
 *
 * <p>Mirrors Python's {@code LocalBackend} in
 * {@code openjiuwen/harness/cli/agent/factory.py}.</p>
 */
public class LocalBackend implements AgentBackend {

    private final Map<String, Object> cfg;
    private final String defaultSessionId = "cli-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private final Set<String> loadedExtensions = new LinkedHashSet<>();
    private DeepAgent agent;
    private TokenTrackingRail tracker;

    public LocalBackend(Map<String, Object> cfg) {
        this.cfg = CliAgentFactory.normalizeConfig(cfg);
    }

    @Override
    public CompletionStage<Void> start() {
        CliAgentFactory.AgentBundle bundle = CliAgentFactory.createAgent(cfg);
        agent = bundle.agent();
        tracker = bundle.tracker();
        return Runner.start().thenApply(ignored -> null);
    }

    @Override
    public CompletionStage<Void> stop() {
        return Runner.stop().thenApply(ignored -> null);
    }

    @Override
    public CompletionStage<Iterator<Object>> runStreaming(Object query, String sessionId) {
        if (agent == null) {
            return failedFuture(new IllegalStateException("LocalBackend has not been started."));
        }
        loadRuntimeExtensions();
        String resolvedSession = sessionId == null || sessionId.isBlank() ? defaultSessionId : sessionId;
        return Runner.runAgentStreaming(agent, Map.of("query", query), resolvedSession, null, null, null);
    }

    @Override
    public CompletionStage<Void> abort() {
        if (agent == null) {
            return CompletableFuture.completedFuture(null);
        }
        return agent.abort(null).thenApply(ignored -> null);
    }

    public DeepAgent getAgent() {
        return agent;
    }

    public TokenTrackingRail getTracker() {
        return tracker;
    }

    public Set<String> getLoadedExtensions() {
        return new LinkedHashSet<>(loadedExtensions);
    }

    private void loadRuntimeExtensions() {
        Object workspaceValue = cfg.get("workspace");
        if (workspaceValue == null) {
            return;
        }
        Path extRoot = Path.of(String.valueOf(workspaceValue), "auto_harness", "runtime_extensions");
        if (!Files.isDirectory(extRoot)) {
            return;
        }
        try (var children = Files.list(extRoot)) {
            children.sorted()
                    .filter(Files::isDirectory)
                    .map(path -> path.resolve("harness_config.yaml"))
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .forEach(loadedExtensions::add);
        } catch (Exception ignored) {
            // Python logs extension load failures and continues streaming.
        }
    }

    private static <T> CompletionStage<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
