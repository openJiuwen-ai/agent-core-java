/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.WorkflowEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes one Pregel node and dispatches its route messages.
 *
 * <p>Mirrors Python's {@code NodeTask} in
 * {@code openjiuwen/core/graph/pregel/task.py}.</p>
 */
public final class NodeTask {

    private static DecoratorFramework callbackFramework;

    private final PregelNode node;
    private final PregelConfig config;
    private final int version;
    private final List<Message> messages = new ArrayList<>();

    public NodeTask(PregelNode node, PregelConfig config, int version) {
        this.node = node;
        this.config = config;
        this.version = version;
    }

    public static void setCallbackFramework(DecoratorFramework framework) {
        callbackFramework = framework;
    }

    public static void clearCallbackFramework() {
        callbackFramework = null;
    }

    /**
     * Run the node function and return route messages or a graph interrupt.
     *
     * @return list of routed messages, or a {@link GraphInterrupt} value
     * @throws Exception for non-interrupt node or router failures
     */
    public Object run() throws Exception {
        try {
            Object result = node.getFunc().apply(buildInvocation());
            if (result instanceof GraphInterrupt graphInterrupt) {
                return graphInterrupt;
            }
            messages.clear();
            for (IRouter router : node.getRouters()) {
                List<Message> routedMessages = router.dispatch(node.getName());
                if (routedMessages != null) {
                    messages.addAll(routedMessages);
                }
            }
            triggerEdgeTraversed();
            return new ArrayList<>(messages);
        } catch (RuntimeException error) {
            if (error.getCause() instanceof GraphInterrupt graphInterrupt) {
                return graphInterrupt;
            }
            throw error;
        }
    }

    /**
     * Invocation payload used by translated node functions.
     *
     * <p>Python inspects the callable signature and supplies {@code config} and
     * {@code state} only when requested. Java translated callables receive this
     * map and can read the entries they need.</p>
     *
     * @return invocation map
     */
    public Map<String, Object> buildInvocation() {
        PregelConfig innerConfig = PregelConfig.createInnerConfig(config);
        Object parentNs = innerConfig.get(PregelConstants.PARENT_NS);
        if (parentNs != null && !String.valueOf(parentNs).isEmpty()) {
            String newFullNs = parentNs + PregelConstants.NS_SEPARATOR + node.getName()
                    + PregelConstants.NS_SEPARATOR + version;
            innerConfig.setNs(newFullNs);
            innerConfig.setParentNs(newFullNs);
        }
        Map<String, Object> invocation = new LinkedHashMap<>();
        invocation.put("config", innerConfig);
        invocation.put("state", null);
        invocation.put("version", version);
        invocation.put("node", node);
        return invocation;
    }

    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    private void triggerEdgeTraversed() {
        DecoratorFramework framework = callbackFramework;
        if (framework == null || messages.isEmpty()) {
            return;
        }
        Object graphId = config != null ? config.get(PregelConstants.NS) : null;
        for (Message message : messages) {
            Map<String, Object> kwargs = new LinkedHashMap<>();
            kwargs.put("source_node", message.getSender());
            kwargs.put("target_node", message.getTarget());
            kwargs.put("graph_id", graphId);
            framework.trigger(WorkflowEvents.EDGE_TRAVERSED, new Object[0], kwargs);
        }
    }
}
