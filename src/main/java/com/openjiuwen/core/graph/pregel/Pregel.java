/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.WorkflowEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pregel graph engine.
 *
 * <p>Mirrors Python's {@code Pregel} in
 * {@code openjiuwen/core/graph/pregel/engine.py}.</p>
 */
public class Pregel {

    private static DecoratorFramework callbackFramework;

    private final Map<String, PregelNode> nodes;
    private final Store store;
    private final List<Channel> channels;
    private final String initial;
    private final Consumer<PregelLoop> afterStep;

    public Pregel(Map<String, PregelNode> nodes, List<Channel> channels) {
        this(nodes, channels, PregelConstants.START, null, null);
    }

    public Pregel(Map<String, PregelNode> nodes, List<Channel> channels, String initial) {
        this(nodes, channels, initial, null, null);
    }

    public Pregel(
            Map<String, PregelNode> nodes,
            List<Channel> channels,
            String initial,
            Store store,
            Consumer<PregelLoop> afterStep
    ) {
        this.nodes = nodes != null ? new LinkedHashMap<>(nodes) : new LinkedHashMap<>();
        this.channels = channels != null ? new ArrayList<>(channels) : new ArrayList<>();
        this.initial = initial != null ? initial : PregelConstants.START;
        this.store = store;
        this.afterStep = afterStep;
    }

    public static void setCallbackFramework(DecoratorFramework framework) {
        callbackFramework = framework;
    }

    public static void clearCallbackFramework() {
        callbackFramework = null;
    }

    /**
     * Run this Pregel graph with default config.
     *
     * @return empty result on normal completion or interrupt payload for top-level interrupts
     * @throws Exception for non-interrupt failures or subgraph interrupts
     */
    public Map<String, Object> run() throws Exception {
        return run(null);
    }

    /**
     * Run this Pregel graph.
     *
     * @param config optional pregel config
     * @return empty result on normal completion or interrupt payload for top-level interrupts
     * @throws Exception for non-interrupt failures or subgraph interrupts
     */
    public Map<String, Object> run(PregelConfig config) throws Exception {
        PregelConfig innerConfig = PregelConfig.createInnerConfig(config != null ? config : PregelConfig.DEFAULT);
        boolean isTopLevel = !hasText(innerConfig.getParentNs());
        if (isTopLevel && hasText(innerConfig.getNs())) {
            innerConfig.setParentNs(innerConfig.getNs());
        }

        Loggers.GRAPH.info("Pregel graph engine execution started");

        PregelLoop loop = new PregelLoop(this, innerConfig);
        try {
            loop.init();
            triggerLoopEvent(WorkflowEvents.LOOP_STARTED, innerConfig, null, false);
            while (loop.runStep()) {
                // Python keeps running super-steps until run_step returns False.
            }
            triggerLoopEvent(WorkflowEvents.LOOP_FINISHED, innerConfig, loop.getStep(), true);
            Loggers.GRAPH.info("Pregel graph engine execution completed");
            return new LinkedHashMap<>();
        } catch (GraphInterrupt interrupt) {
            triggerLoopEvent(WorkflowEvents.LOOP_FINISHED, innerConfig, loop.getStep(), true);
            Loggers.GRAPH.info("Pregel graph engine execution interrupted");
            if (isTopLevel) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put(PregelConstants.TASK_STATUS_INTERRUPT, interrupt.getValue());
                return result;
            }
            throw interrupt;
        }
    }

    public Map<String, PregelNode> getNodes() {
        return new LinkedHashMap<>(nodes);
    }

    public Store getStore() {
        return store;
    }

    public List<Channel> getChannels() {
        return new ArrayList<>(channels);
    }

    public String getInitial() {
        return initial;
    }

    public Consumer<PregelLoop> getAfterStep() {
        return afterStep;
    }

    private static void triggerLoopEvent(String event, PregelConfig config, Integer totalSteps, boolean includeSteps) {
        DecoratorFramework framework = callbackFramework;
        if (framework == null) {
            return;
        }
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("graph_id", config != null ? config.getNs() : null);
        if (includeSteps) {
            kwargs.put("total_steps", totalSteps);
        }
        framework.trigger(event, new Object[0], kwargs);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
