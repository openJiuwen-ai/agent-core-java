/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.GraphNodeState;
import com.openjiuwen.core.session.interaction.WorkflowInteraction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

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
            throwIfInterrupted();
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
            throwIfInterrupted();

            // Route messages
            List<Message> messages = new ArrayList<>();
            for (IRouter router : node.getRouters()) {
                messages.addAll(router.dispatch(node.getName()));
            }
            return messages;

        } catch (GraphInterrupt e) {
            // Convert interrupt exception to return value
            return e;
        } catch (RuntimeException e) {
            GraphInterrupt interrupt = unwrapGraphInterrupt(e);
            if (interrupt != null) {
                return interrupt;
            }
            throw e;
        }
    }

    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Node task cancelled");
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
                GraphInterrupt interrupt = unwrapGraphInterrupt(e.getCause());
                if (interrupt != null) {
                    throw interrupt;
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
            } else if (GraphNodeState.class.isAssignableFrom(params[i].getType())) {
                args[i] = kwargs.get("state");
            } else if (PregelConfig.class.isAssignableFrom(params[i].getType())) {
                args[i] = kwargs.get("config");
            } else if (i == 0 && kwargs.containsKey("state")) {
                args[i] = kwargs.get("state");
            } else if (i == 1 && kwargs.containsKey("config")) {
                args[i] = kwargs.get("config");
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
                java.lang.reflect.Parameter[] parameters = callMethod.getParameters();
                for (java.lang.reflect.Parameter param : callMethod.getParameters()) {
                    if (param.getName().equals(paramName)) {
                        return true;
                    }
                }
                // Also check by type
                if ("config".equals(paramName)) {
                    for (java.lang.reflect.Parameter param : parameters) {
                        if (param.getType() == PregelConfig.class) {
                            return true;
                        }
                    }
                    return parameters.length >= 2;
                }
                if ("state".equals(paramName)) {
                    for (java.lang.reflect.Parameter param : parameters) {
                        if (GraphNodeState.class.isAssignableFrom(param.getType())) {
                            return true;
                        }
                    }
                    return parameters.length >= 1;
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }
        return false;
    }

    private static GraphInterrupt unwrapGraphInterrupt(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof GraphInterrupt interrupt) {
                return interrupt;
            }
            if (current instanceof WorkflowInteraction.GraphInterruptRuntimeWrapper wrapper) {
                return wrapper.getGraphInterrupt();
            }
            current = current.getCause();
        }
        return null;
    }
}
