/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.store.Store;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pregel graph execution engine implementing the BSP (Bulk Synchronous Parallel) model.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.engine.Pregel}.
 */
public class Pregel {

    private static final LoggerProtocol logger = Loggers.GRAPH;

    private final Map<String, PregelNode> nodes;
    private final List<Channel> channels;
    private final String initial;
    private final Store store;
    private final Consumer<PregelLoop> afterStep;

    public Pregel(Map<String, PregelNode> nodes, List<Channel> channels,
                  Store store, Consumer<PregelLoop> afterStep) {
        this(nodes, channels, PregelConstants.START, store, afterStep);
    }

    public Pregel(Map<String, PregelNode> nodes, List<Channel> channels,
                  String initial, Store store, Consumer<PregelLoop> afterStep) {
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
     */
    public Map<String, Object> run(PregelConfig config) throws Exception {
        PregelConfig innerConfig = PregelConfig.createInnerConfig(
                config != null ? config : PregelConfig.DEFAULT);

        boolean isTopLevel = innerConfig.getParentNs() == null;
        if (isTopLevel && innerConfig.getNs() != null) {
            innerConfig.setParentNs(innerConfig.getNs());
        }

        logger.info("Pregel graph engine execution started, ns={}, sessionId={}, isTopLevel={}",
                innerConfig.getNs(), innerConfig.getSessionId(), isTopLevel);

        PregelLoop loop = new PregelLoop(this, innerConfig);
        try {
            loop.init();
            while (loop.runStep()) {
                // Continue executing super-steps
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
        }
    }

    public Map<String, PregelNode> getNodes() {
        return nodes;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    public String getInitial() {
        return initial;
    }

    public Store getStore() {
        return store;
    }

    public Consumer<PregelLoop> getAfterStep() {
        return afterStep;
    }
}
