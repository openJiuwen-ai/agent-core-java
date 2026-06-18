/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.PendingNode;
import com.openjiuwen.core.graph.store.Store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes one Pregel graph through super-step barriers.
 *
 * <p>Mirrors Python's {@code PregelLoop} in
 * {@code openjiuwen/core/graph/pregel/engine.py}.</p>
 */
public class PregelLoop {

    private final Pregel graph;
    private final ChannelManager manager;
    private final PregelConfig config;
    private final Store saver;
    private final Map<String, Integer> nodeVersion = new LinkedHashMap<>();
    private final Map<String, PendingNode> retryPendingNodes = new LinkedHashMap<>();
    private int step;
    private int maxStep;
    private List<String> activeNodes = new ArrayList<>();
    private TaskExecutorPool executor;

    public PregelLoop(Pregel graph, PregelConfig config) {
        this.graph = graph;
        this.manager = new ChannelManager(graph.getChannels());
        this.config = config;
        this.saver = graph.getStore();
    }

    /**
     * Initialize the loop from persisted graph state or by triggering the initial node.
     */
    public void init() {
        executor = new TaskExecutorPool(config);
        maxStep = config.getRecursionLimit();

        GraphStoreState state = null;
        if (hasText(config.getSessionId()) && hasText(config.getNs()) && saver != null) {
            Optional<GraphStoreState> stored = saver.get(config.getSessionId(), config.getNs())
                    .toCompletableFuture()
                    .join();
            state = stored.orElse(null);
        }

        if (isResume(state)) {
            manager.restore(state.getChannelValues());
            nodeVersion.clear();
            nodeVersion.putAll(state.getNodeVersion());
            step = state.getStep();
            maxStep = state.getStep() + config.getRecursionLimit();
            for (Message message : state.getPendingBuffer()) {
                manager.bufferMessage(message);
            }
            retryPendingNodes.clear();
            retryPendingNodes.putAll(state.getPendingNode());
            return;
        }

        manager.bufferMessage(new TriggerMessage(graph.getInitial(), graph.getInitial()));
        manager.flush();
    }

    /**
     * Run a single super-step.
     *
     * @return {@code true} when another step may be available, {@code false} when the graph ended
     * @throws Exception when a node, router, or save operation fails
     */
    public boolean runStep() throws Exception {
        try {
            return doRunStep();
        } catch (Exception error) {
            if (!(error instanceof GraphInterrupt)) {
                Loggers.GRAPH.error("Failed to run graph super-step[{}]", step);
            }
            saveStateOnError(error);
            throw error;
        }
    }

    public int getStep() {
        return step;
    }

    public int getMaxStep() {
        return maxStep;
    }

    public PregelConfig getConfig() {
        return config;
    }

    public ChannelManager getManager() {
        return manager;
    }

    public List<String> getActiveNodes() {
        return new ArrayList<>(activeNodes);
    }

    public Map<String, Integer> getNodeVersion() {
        return new LinkedHashMap<>(nodeVersion);
    }

    TaskExecutorPool getExecutor() {
        return executor;
    }

    private boolean doRunStep() throws Exception {
        Loggers.GRAPH.debug("Start to run graph super-step[{}]", step);
        List<PregelNode> tasksToRun = new ArrayList<>();

        if (!retryPendingNodes.isEmpty()) {
            activeNodes = new ArrayList<>(retryPendingNodes.keySet());
            retryPendingNodes.clear();
        } else {
            List<String> readyNodes = manager.getReadyNodes();
            activeNodes = new ArrayList<>();
            for (String nodeName : readyNodes) {
                if (graph.getNodes().containsKey(nodeName) && !PregelConstants.END.equals(nodeName)) {
                    activeNodes.add(nodeName);
                    nodeVersion.merge(nodeName, 1, Integer::sum);
                }
            }
        }

        if (activeNodes.isEmpty()) {
            if (manager.isEmpty()) {
                return false;
            }

            manager.flush();
            step++;
            return true;
        }

        if (step > maxStep) {
            throw new IllegalStateException("Recursion limit of " + maxStep + " reached at step " + step);
        }

        for (String name : activeNodes) {
            manager.consume(name);
            PregelNode node = graph.getNodes().get(name);
            if (node != null) {
                tasksToRun.add(node);
            }
        }

        for (PregelNode node : tasksToRun) {
            executor.submit(node, nodeVersion.getOrDefault(node.getName(), 0));
        }

        executor.waitAll();

        for (Message message : executor.getSucceedMessages()) {
            manager.bufferMessage(message);
        }
        manager.flush();
        executor.clear();

        if (graph.getAfterStep() != null) {
            graph.getAfterStep().accept(this);
        }
        step++;
        return true;
    }

    private void saveStateOnError(Exception exception) {
        Loggers.GRAPH.debug("Failed to run graph super-step[{}], caused by save state", step);
        if (!hasText(config.getSessionId()) || !hasText(config.getNs()) || saver == null) {
            return;
        }

        List<Message> pendingBuffer = new ArrayList<>(manager.getBuffer());
        Map<String, PendingNode> pendingNode = new LinkedHashMap<>();
        if (executor != null) {
            pendingBuffer.addAll(executor.getSucceedMessages());
            pendingNode.putAll(executor.getFailed());
        }

        GraphStoreState errorState = GraphStoreState.create(
                config.getNs(),
                step,
                manager.snapshot(),
                pendingBuffer,
                pendingNode,
                nodeVersion
        );
        saver.save(config.getSessionId(), config.getNs(), errorState)
                .toCompletableFuture()
                .join();
    }

    private static boolean isResume(GraphStoreState state) {
        return state != null
                && (!state.getPendingNode().isEmpty()
                || !state.getPendingBuffer().isEmpty()
                || !state.getChannelValues().isEmpty());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
