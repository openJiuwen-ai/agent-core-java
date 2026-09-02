/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.store.Store;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/**
 * Pregel graph execution engine implementing the BSP (Bulk Synchronous Parallel) model.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.engine.Pregel}.
 * 
 * @since 0.1.7
 */
public class Pregel {
    private static final LoggerProtocol logger = Loggers.GRAPH;

    private final Map<String, PregelNode> nodes;
    private final List<Channel> channels;
    private final String initial;
    private final Store store;
    private final Consumer<PregelLoop> afterStep;

    /**
     * Creates a Pregel engine with default initial node.
     * 
     * @param nodes map of node names to PregelNode instances
     * @param channels list of channels for message passing
     * @param store state store for persistence
     * @param afterStep callback invoked after each super-step
     * @since 0.1.7
     */
    public Pregel(Map<String, PregelNode> nodes, List<Channel> channels, Store store, Consumer<PregelLoop> afterStep) {
        this(nodes, channels, PregelConstants.START, store, afterStep);
    }

    /**
     * Creates a Pregel engine with a specified initial node.
     * 
     * @param nodes map of node names to PregelNode instances
     * @param channels list of channels for message passing
     * @param initial the initial node name
     * @param store state store for persistence
     * @param afterStep callback invoked after each super-step
     * @since 0.1.7
     */
    public Pregel(Map<String, PregelNode> nodes, List<Channel> channels, String initial, Store store,
            Consumer<PregelLoop> afterStep) {
        this.nodes = nodes;
        this.channels = channels;
        this.initial = initial;
        this.store = store;
        this.afterStep = afterStep;
    }

    /**
     * Execute the Pregel graph computation.
     * 
     * @param config execution configuration
     * @return execution result map, or interrupt info
     * @throws Exception on execution failure
     * @since 0.1.7
     */
    public Map<String, Object> run(PregelConfig config) throws Exception {
        PregelConfig innerConfig = PregelConfig.createInnerConfig(config != null ? config : PregelConfig.DEFAULT);

        boolean isTopLevel = innerConfig.getParentNs() == null;
        if (isTopLevel && innerConfig.getNs() != null) {
            innerConfig.setParentNs(innerConfig.getNs());
        }

        logger.info("Pregel graph engine execution started, ns={}, sessionId={}, isTopLevel={}", innerConfig.getNs(),
                innerConfig.getSessionId(), isTopLevel);

        PregelLoop loop = new PregelLoop(this, innerConfig);
        try {
            loop.init();
            throwIfInterrupted();
            while (loop.runStep()) {
                throwIfInterrupted();
            }
            logger.info("Pregel graph engine execution completed, ns={}, sessionId={}, totalSteps={}",
                    innerConfig.getNs(), innerConfig.getSessionId(), loop.getStep());
            return new HashMap<>();
        } catch (GraphInterrupt e) {
            logger.info("Pregel graph engine execution interrupted, ns={}, sessionId={}, totalSteps={}",
                    innerConfig.getNs(), innerConfig.getSessionId(), loop.getStep());
            if (isTopLevel) {
                Map<String, Object> result = new HashMap<>();
                result.put(PregelConstants.TASK_STATUS_INTERRUPT, e.getValue());
                return result;
            } else {
                throw e;
            }
        } catch (CancellationException e) {
            throw e;
        } finally {
            loop.shutdown();
        }
    }

    /**
     * Gets the nodes in this Pregel graph.
     * 
     * @return map of node names to PregelNode instances
     * @since 0.1.7
     */
    public Map<String, PregelNode> getNodes() {
        return nodes;
    }

    /**
     * Gets the channels in this Pregel graph.
     * 
     * @return list of channels
     * @since 0.1.7
     */
    public List<Channel> getChannels() {
        return channels;
    }

    /**
     * Gets the initial node name.
     * 
     * @return the initial node name
     * @since 0.1.7
     */
    public String getInitial() {
        return initial;
    }

    /**
     * Gets the state store.
     * 
     * @return the store, or null
     * @since 0.1.7
     */
    public Store getStore() {
        return store;
    }

    /**
     * Gets the after-step callback.
     * 
     * @return the callback, or null
     * @since 0.1.7
     */
    public Consumer<PregelLoop> getAfterStep() {
        return afterStep;
    }

    /**
     * throwIfInterrupted.
     * 
     * @since 0.1.7
     */
    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Pregel execution cancelled");
        }
    }
}
