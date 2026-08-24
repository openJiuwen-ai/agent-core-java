/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.PendingNode;
import com.openjiuwen.core.graph.store.Store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;

/**
 * Pregel execution loop implementing the BSP (Bulk Synchronous Parallel) model.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.engine.PregelLoop}.
 * 
 * @since 0.1.7
 */
public class PregelLoop {
    private static final LoggerProtocol logger = Loggers.GRAPH;

    private final Pregel graph;
    private final ChannelManager manager;
    private final PregelConfig config;
    private final Store saver;

    private int step = 0;
    private int maxStep = 0;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> activeNodes = new ArrayList<>();
    private TaskExecutorPool executor;

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, PendingNode> retryPendingNodes = new HashMap<>();

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Integer> nodeVersion = new HashMap<>();

    /**
     * PregelLoop.
     * 
     * @param graph graph
     * @param config config
     * @since 0.1.7
     */
    public PregelLoop(Pregel graph, PregelConfig config) {
        this.graph = graph;
        this.manager = new ChannelManager(graph.getChannels());
        this.config = config;
        this.saver = graph.getStore();
    }

    /**
     * Initialize the Pregel loop, restoring state if available.
     * 
     * @since 0.1.7
     */
    public void init() {
        executor = new TaskExecutorPool(config);
        maxStep = config.getRecursionLimit();

        GraphStoreState state = null;
        if (config.getSessionId() != null && config.getNs() != null && saver != null) {
            Optional<GraphStoreState> opt = saver.get(config.getSessionId(), config.getNs());
            state = opt.orElse(null);
        }

        if (isResume(state)) {
            // Restore barrier channel
            manager.restore(state.getChannelValues());
            // Restore loop node version
            nodeVersion.putAll(state.getNodeVersion());
            // Restore step
            step = state.getStep();
            maxStep = state.getStep() + config.getRecursionLimit();
            // Restore pending buffer messages
            for (Message msg : state.getPendingBuffer()) {
                manager.bufferMessage(msg);
            }
            // Pending nodes for retry
            if (state.getPendingNode() != null) {
                retryPendingNodes = new HashMap<>(state.getPendingNode());
            }
        } else {
            // Trigger start node
            manager.bufferMessage(new TriggerMessage(graph.getInitial(), graph.getInitial()));
            manager.flush();
        }
    }

    /**
     * Execute one super-step of the Pregel computation.
     * 
     * @return true if more steps should follow, false if done
     * @throws Exception on execution failure
     * @since 0.1.7
     */
    public boolean runStep() throws Exception {
        try {
            return doRunStep();
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to run graph super-step[{}], ns={}, sessionId={}", step, config.getNs(),
                    config.getSessionId(), e);
            saveStateOnError(e);
            throw e;
        }
    }

    /**
     * getStep.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getStep() {
        return step;
    }

    /**
     * Releases the node-task executor owned by this loop.
     */
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public PregelConfig getConfig() {
        return config;
    }

    /**
     * getActiveNodes.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getActiveNodes() {
        return activeNodes;
    }

    /**
     * doRunStep.
     * 
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private boolean doRunStep() throws Exception {
        throwIfInterrupted();
        logger.debug("Start to run graph super-step[{}], ns={}, sessionId={}", step, config.getNs(),
                config.getSessionId());

        // 1. Determine tasks for this round
        if (!retryPendingNodes.isEmpty()) {
            activeNodes = new ArrayList<>(retryPendingNodes.keySet());
            retryPendingNodes.clear();
        } else {
            List<String> readyNodes = manager.getReadyNodes();
            activeNodes = new ArrayList<>();
            for (String n : readyNodes) {
                if (graph.getNodes().containsKey(n) && !PregelConstants.END.equals(n)) {
                    activeNodes.add(n);
                    nodeVersion.merge(n, 1, Integer::sum);
                }
            }
        }

        if (activeNodes.isEmpty()) {
            if (manager.isEmpty()) {
                return false; // End
            }
            manager.flush();
            step++;
            return true;
        }

        if (step > maxStep) {
            throw new StackOverflowError(
                    "Recursion limit of " + maxStep + " reached at step " + step + " ns: " + config.getNs() + ".");
        }

        List<PregelNode> tasksToRun = new ArrayList<>();
        for (String name : activeNodes) {
            manager.consume(name);
            tasksToRun.add(graph.getNodes().get(name));
        }

        // 2. Execute tasks
        for (PregelNode node : tasksToRun) {
            executor.submit(node, nodeVersion.getOrDefault(node.getName(), 0));
        }

        try {
            executor.waitAll();
        } catch (java.util.concurrent.CancellationException e) {
            executor.cancelAll();
            throw e;
        }
        throwIfInterrupted();

        // 3. Summarize results
        for (Message msg : executor.getSucceedMessages()) {
            manager.bufferMessage(msg);
        }
        manager.flush();
        executor.clear();

        // Hook: after-step callback
        if (graph.getAfterStep() != null) {
            graph.getAfterStep().accept(this);
        }

        step++;
        return true;
    }

    /**
     * throwIfInterrupted.
     * 
     * @since 0.1.7
     */
    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Pregel loop cancelled");
        }
    }

    /**
     * saveStateOnError.
     * 
     * @param exception exception
     * @since 0.1.7
     */
    private void saveStateOnError(Exception exception) {
        if (config.getSessionId() == null || config.getNs() == null || saver == null) {
            return;
        }
        List<Message> pendingBuffer = new ArrayList<>(manager.getBuffer());
        Map<String, PendingNode> pendingNode = new HashMap<>();
        if (executor != null) {
            pendingBuffer.addAll(executor.getSucceedMessages());
            pendingNode = executor.getFailed();
        }
        GraphStoreState errorState = GraphStoreState.create(config.getNs(), step, manager.snapshot(), pendingBuffer,
                pendingNode, new HashMap<>(nodeVersion));
        saver.save(config.getSessionId(), config.getNs(), errorState);
    }

    /**
     * isResume.
     * 
     * @param state state
     * @return the result
     * @since 0.1.7
     */
    private static boolean isResume(GraphStoreState state) {
        return state != null && ((state.getPendingNode() != null && !state.getPendingNode().isEmpty())
                || (state.getPendingBuffer() != null && !state.getPendingBuffer().isEmpty())
                || (state.getChannelValues() != null && !state.getChannelValues().isEmpty()));
    }
}
