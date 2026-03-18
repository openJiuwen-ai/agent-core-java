/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Executes a single Pregel node and produces routing messages.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.task.NodeTask}.
 */
public class NodeTask implements Callable<Object> {

    private static final LoggerProtocol logger = Loggers.GRAPH;

    private final PregelNode node;
    private final PregelConfig config;
    private final int version;

    public NodeTask(PregelNode node, PregelConfig config, int version) {
        this.node = node;
        this.config = config;
        this.version = version;
    }

    /**
     * Execute the node function and dispatch routing messages.
     *
     * @return List&lt;Message&gt; on success, or a {@link GraphInterrupt} instance on interrupt
     * @throws Exception on execution failure
     */
    @Override
    public Object call() throws Exception {
        try {
            Object func = node.getFunc();

            // Build kwargs based on function parameters
            Map<String, Object> kwargs = new HashMap<>();

            // Check if function accepts 'config' param
            if (acceptsParameter(func, "config")) {
                PregelConfig innerConfig = PregelConfig.createInnerConfig(config);
                String currentParentNs = innerConfig.getParentNs();
                String currentNodeName = node.getName();
                if (currentParentNs != null) {
                    String newFullNs = currentParentNs + ":" + currentNodeName + ":" + version;
                    innerConfig.setNs(newFullNs);
                    innerConfig.setParentNs(newFullNs);
                }
                kwargs.put("config", innerConfig);
            }
            if (acceptsParameter(func, "state")) {
                kwargs.put("state", null);
            }

            // Invoke the node function
            invokeFunc(func, kwargs);

            // Route messages
            List<Message> messages = new ArrayList<>();
            for (IRouter router : node.getRouters()) {
                messages.addAll(router.dispatch(node.getName()));
            }
            return messages;

        } catch (GraphInterrupt e) {
            // Convert interrupt exception to return value
            return e;
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeFunc(Object func, Map<String, Object> kwargs) throws Exception {
        if (func instanceof Runnable runnable) {
            runnable.run();
        } else if (func instanceof Callable<?> callable) {
            callable.call();
        } else {
            // Try to call via __call__ or call method pattern
            // For Vertex objects, they implement a call(config) method
            try {
                Method callMethod = findCallMethod(func);
                if (callMethod != null) {
                    Object[] args = buildArgs(callMethod, kwargs);
                    callMethod.invoke(func, args);
                } else {
                    // Lambda or simple Runnable-like
                    if (func instanceof java.util.function.Consumer<?>) {
                        ((java.util.function.Consumer<Map<String, Object>>) func).accept(kwargs);
                    }
                }
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof GraphInterrupt gi) {
                    throw gi;
                }
                if (e.getCause() instanceof Exception ex) {
                    throw ex;
                }
                throw new RuntimeException(e.getCause());
            }
        }
    }

    private Method findCallMethod(Object func) {
        // Look for call(GraphNodeState, PregelConfig) or __call__ method
        for (Method method : func.getClass().getMethods()) {
            if ("call".equals(method.getName()) || "__call__".equals(method.getName())) {
                return method;
            }
        }
        return null;
    }

    private Object[] buildArgs(Method method, Map<String, Object> kwargs) {
        java.lang.reflect.Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            String paramName = params[i].getName();
            if (kwargs.containsKey(paramName)) {
                args[i] = kwargs.get(paramName);
            } else if (params[i].getType() == PregelConfig.class) {
                args[i] = kwargs.get("config");
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private boolean acceptsParameter(Object func, String paramName) {
        if (func == null) {
            return false;
        }
        try {
            Method callMethod = findCallMethod(func);
            if (callMethod != null) {
                for (java.lang.reflect.Parameter param : callMethod.getParameters()) {
                    if (param.getName().equals(paramName)) {
                        return true;
                    }
                }
                // Also check by type
                if ("config".equals(paramName)) {
                    for (java.lang.reflect.Parameter param : callMethod.getParameters()) {
                        if (param.getType() == PregelConfig.class) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }
        return false;
    }
}
