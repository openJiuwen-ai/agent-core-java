/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Decorator utilities for wrapping objects with trace functionality.
 * 
 * <p>Provides methods to decorate models, tools, and workflows with trace
 * capabilities for monitoring their execution.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class TracerDecorator {
    
    private TracerDecorator() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Checks if an object should be decorated with trace functionality.
     * 
     * @param obj the object to check
     * @param session the agent session
     * @return true if the object should be decorated
     */
    private static boolean shouldDecorate(Object obj, AgentSession session) {
        return obj != null && 
               session != null && 
               session.tracer() != null && 
               session.span() != null;
    }
    
    /**
     * Decorates a model with trace functionality.
     * 
     * <p>Wraps the model's invoke and stream methods to emit trace events.
     * 
     * @param <T> the model type
     * @param model the model to decorate
     * @param agentSession the agent session
     * @return the decorated model wrapper
     */
    public static <T> TracedModel<T> decorateModelWithTrace(T model, AgentSession agentSession) {
        if (!shouldDecorate(model, agentSession)) {
            return new TracedModel<>(model, null, null);
        }
        
        String modelName;
        try {
            // Try to get model name from config
            Object config = model.getClass().getMethod("getConfig").invoke(model);
            Object modelConfig = config.getClass().getMethod("getModelConfig").invoke(config);
            Object nameObj = modelConfig.getClass().getMethod("getModelName").invoke(modelConfig);
            modelName = nameObj != null ? nameObj.toString() : model.getClass().getSimpleName();
        } catch (Exception e) {
            modelName = model.getClass().getSimpleName();
        }
        
        Map<String, Object> instanceInfo = new HashMap<>();
        instanceInfo.put("class_name", modelName);
        instanceInfo.put("type", "llm");
        
        return new TracedModel<>(model, agentSession, instanceInfo);
    }
    
    /**
     * Decorates a tool with trace functionality.
     * 
     * <p>Wraps the tool's invoke method to emit trace events.
     * 
     * @param <T> the tool type
     * @param tool the tool to decorate
     * @param agentSession the agent session
     * @return the decorated tool wrapper
     */
    public static <T> TracedTool<T> decorateToolWithTrace(T tool, AgentSession agentSession) {
        if (!shouldDecorate(tool, agentSession)) {
            return new TracedTool<>(tool, null, null);
        }
        
        String toolName;
        try {
            Object nameObj = tool.getClass().getMethod("getName").invoke(tool);
            toolName = nameObj != null ? nameObj.toString() : tool.getClass().getSimpleName();
        } catch (Exception e) {
            toolName = tool.getClass().getSimpleName();
        }
        
        Map<String, Object> instanceInfo = new HashMap<>();
        instanceInfo.put("class_name", toolName);
        instanceInfo.put("type", "tool");
        
        return new TracedTool<>(tool, agentSession, instanceInfo);
    }
    
    /**
     * Decorates a workflow with trace functionality.
     * 
     * <p>Wraps the workflow's invoke and stream methods to emit trace events.
     * 
     * @param <T> the workflow type
     * @param workflow the workflow to decorate
     * @param agentSession the agent session
     * @return the decorated workflow wrapper
     */
    public static <T> TracedWorkflow<T> decorateWorkflowWithTrace(T workflow, AgentSession agentSession) {
        if (!shouldDecorate(workflow, agentSession)) {
            return new TracedWorkflow<>(workflow, null, null);
        }
        
        Map<String, Object> metadata = new HashMap<>();
        String workflowName;
        try {
            Object card = workflow.getClass().getMethod("getCard").invoke(workflow);
            if (card != null) {
                Object id = card.getClass().getMethod("getId").invoke(card);
                Object name = card.getClass().getMethod("getName").invoke(card);
                Object desc = card.getClass().getMethod("getDescription").invoke(card);
                Object version = card.getClass().getMethod("getVersion").invoke(card);
                
                metadata.put("id", id);
                metadata.put("name", name);
                metadata.put("description", desc);
                metadata.put("version", version);
                
                workflowName = name != null ? name.toString() : workflow.getClass().getSimpleName();
            } else {
                workflowName = workflow.getClass().getSimpleName();
            }
        } catch (Exception e) {
            workflowName = workflow.getClass().getSimpleName();
        }
        
        Map<String, Object> instanceInfo = new HashMap<>();
        instanceInfo.put("class_name", workflowName);
        instanceInfo.put("type", "workflow");
        instanceInfo.put("metadata", metadata);
        
        return new TracedWorkflow<>(workflow, agentSession, instanceInfo);
    }
    
    /**
     * Creates a synchronous trace wrapper for a function.
     * 
     * @param <T> the function return type
     * @param func the function to wrap
     * @param session the agent session
     * @param invokeType the invoke type
     * @param instanceInfo the instance info
     * @param index the argument index for inputs (default 1)
     * @param inputsFieldName the field name for inputs in kwargs (default "inputs")
     * @return the wrapped function
     */
    public static <T> Function<Object[], T> trace(
            Function<Object[], T> func,
            AgentSession session,
            InvokeType invokeType,
            Map<String, Object> instanceInfo,
            int index,
            String inputsFieldName) {
        
        return args -> {
            Tracer tracer = session.tracer();
            TraceAgentSpan span = null;
            
            try {
                TraceAgentSpan agentSpan = session.span();
                span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
                
                // Get inputs
                Object inputs = null;
                if (args != null && args.length > index) {
                    inputs = args[index];
                }
                
                Map<String, Object> inputsMap = new HashMap<>();
                inputsMap.put("inputs", inputs);
                
                // Trigger start event synchronously
                Map<String, Object> startKwargs = new HashMap<>();
                startKwargs.put("span", span);
                startKwargs.put("inputs", inputsMap);
                startKwargs.put("instance_info", instanceInfo);
                
                tracer.syncTrigger("tracer_agent", "on" + capitalize(invokeType.getValue()) + "Start", startKwargs);
                
                // Execute function (skip first argument which is usually 'self')
                Object[] funcArgs = new Object[args.length - 1];
                System.arraycopy(args, 1, funcArgs, 0, args.length - 1);
                T result = func.apply(funcArgs);
                
                // Trigger end event
                Map<String, Object> outputsMap = new HashMap<>();
                outputsMap.put("outputs", result);
                
                Map<String, Object> endKwargs = new HashMap<>();
                endKwargs.put("span", span);
                endKwargs.put("outputs", outputsMap);
                
                tracer.syncTrigger("tracer_agent", "on" + capitalize(invokeType.getValue()) + "End", endKwargs);
                
                return result;
            } catch (Exception error) {
                // Trigger error event
                Map<String, Object> errorKwargs = new HashMap<>();
                errorKwargs.put("span", span);
                errorKwargs.put("error", error);
                
                tracer.syncTrigger("tracer_agent", "on" + capitalize(invokeType.getValue()) + "Error", errorKwargs);
                throw error;
            }
        };
    }
    
    /**
     * Creates an asynchronous trace wrapper for a function.
     * 
     * @param <T> the function return type
     * @param func the function to wrap
     * @param session the agent session
     * @param invokeType the invoke type
     * @param instanceInfo the instance info
     * @param index the argument index for inputs (default 1)
     * @param inputsFieldName the field name for inputs in kwargs (default "inputs")
     * @return the wrapped async function
     */
    public static <T> Function<Object[], CompletableFuture<T>> asyncTrace(
            Function<Object[], CompletableFuture<T>> func,
            AgentSession session,
            InvokeType invokeType,
            Map<String, Object> instanceInfo,
            int index,
            String inputsFieldName) {
        
        return args -> {
            Tracer tracer = session.tracer();
            TraceAgentSpan agentSpan = session.span();
            TraceAgentSpan span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
            
            // Get inputs
            Object inputs = null;
            if (args != null && args.length > index) {
                inputs = args[index];
            }
            
            Map<String, Object> inputsMap = new HashMap<>();
            inputsMap.put("inputs", inputs);
            
            // Trigger start event
            Map<String, Object> startKwargs = new HashMap<>();
            startKwargs.put("span", span);
            startKwargs.put("inputs", inputsMap);
            startKwargs.put("instance_info", instanceInfo);
            
            String eventPrefix = "on" + capitalize(invokeType.getValue());
            
            return tracer.trigger("tracer_agent", eventPrefix + "Start", startKwargs)
                .thenCompose(v -> {
                    // Execute function (skip first argument which is usually 'self')
                    Object[] funcArgs = new Object[args.length - 1];
                    System.arraycopy(args, 1, funcArgs, 0, args.length - 1);
                    
                    // For LLM invoke type, add tracer record data callback
                    if (invokeType == InvokeType.LLM) {
                        // Add tracer_record_data function to kwargs if applicable
                        // This would be passed through the args/kwargs mechanism
                    }
                    
                    return func.apply(funcArgs);
                })
                .thenCompose(result -> {
                    // Trigger end event
                    Map<String, Object> outputsMap = new HashMap<>();
                    outputsMap.put("outputs", result);
                    
                    Map<String, Object> endKwargs = new HashMap<>();
                    endKwargs.put("span", span);
                    endKwargs.put("outputs", outputsMap);
                    
                    return tracer.trigger("tracer_agent", eventPrefix + "End", endKwargs)
                        .thenApply(v -> result);
                })
                .exceptionally(error -> {
                    // Trigger error event
                    Map<String, Object> errorKwargs = new HashMap<>();
                    errorKwargs.put("span", span);
                    errorKwargs.put("error", error);
                    
                    tracer.syncTrigger("tracer_agent", eventPrefix + "Error", errorKwargs);
                    
                    if (error instanceof RuntimeException) {
                        throw (RuntimeException) error;
                    }
                    throw new RuntimeException(error);
                });
        };
    }
    
    /**
     * Creates a synchronous stream trace wrapper for a function.
     * 
     * @param <T> the item type
     * @param func the function to wrap
     * @param session the agent session
     * @param invokeType the invoke type
     * @param instanceInfo the instance info
     * @param index the argument index for inputs (default 1)
     * @param inputsFieldName the field name for inputs in kwargs (default "inputs")
     * @return the wrapped stream function
     */
    public static <T> Function<Object[], Iterator<T>> traceStream(
            Function<Object[], Iterator<T>> func,
            AgentSession session,
            InvokeType invokeType,
            Map<String, Object> instanceInfo,
            int index,
            String inputsFieldName) {
        
        return args -> {
            Tracer tracer = session.tracer();
            TraceAgentSpan agentSpan = session.span();
            TraceAgentSpan span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
            
            // Get inputs
            Object inputs = null;
            if (args != null && args.length > index) {
                inputs = args[index];
            }
            
            Map<String, Object> inputsMap = new HashMap<>();
            inputsMap.put("inputs", inputs);
            
            // Trigger start event
            Map<String, Object> startKwargs = new HashMap<>();
            startKwargs.put("span", span);
            startKwargs.put("inputs", inputsMap);
            startKwargs.put("instance_info", instanceInfo);
            
            String eventPrefix = "on" + capitalize(invokeType.getValue());
            tracer.syncTrigger("tracer_agent", eventPrefix + "Start", startKwargs);
            
            try {
                // Execute function (skip first argument which is usually 'self')
                Object[] funcArgs = new Object[args.length - 1];
                System.arraycopy(args, 1, funcArgs, 0, args.length - 1);
                Iterator<T> result = func.apply(funcArgs);
                
                List<T> results = new ArrayList<>();
                
                // Create a wrapping iterator that collects results
                return new Iterator<T>() {
                    @Override
                    public boolean hasNext() {
                        boolean hasNext = result.hasNext();
                        if (!hasNext) {
                            // Trigger end event when iterator is exhausted
                            Map<String, Object> outputsMap = new HashMap<>();
                            outputsMap.put("outputs", results);
                            
                            Map<String, Object> endKwargs = new HashMap<>();
                            endKwargs.put("span", span);
                            endKwargs.put("outputs", outputsMap);
                            
                            tracer.syncTrigger("tracer_agent", eventPrefix + "End", endKwargs);
                        }
                        return hasNext;
                    }
                    
                    @Override
                    public T next() {
                        T item = result.next();
                        results.add(item);
                        return item;
                    }
                };
            } catch (Exception error) {
                // Trigger error event
                Map<String, Object> errorKwargs = new HashMap<>();
                errorKwargs.put("span", span);
                errorKwargs.put("error", error);
                
                tracer.syncTrigger("tracer_agent", eventPrefix + "Error", errorKwargs);
                throw error;
            }
        };
    }
    
    /**
     * Creates an asynchronous stream trace wrapper for a function.
     * 
     * @param <T> the item type
     * @param func the function to wrap
     * @param session the agent session
     * @param invokeType the invoke type
     * @param instanceInfo the instance info
     * @param index the argument index for inputs (default 1)
     * @param inputsFieldName the field name for inputs in kwargs (default "inputs")
     * @return the wrapped async stream function
     */
    public static <T> Function<Object[], AsyncIterator<T>> asyncTraceStream(
            Function<Object[], AsyncIterator<T>> func,
            AgentSession session,
            InvokeType invokeType,
            Map<String, Object> instanceInfo,
            int index,
            String inputsFieldName) {
        
        return args -> {
            Tracer tracer = session.tracer();
            TraceAgentSpan agentSpan = session.span();
            TraceAgentSpan span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
            
            // Get inputs
            Object inputs = null;
            if (args != null && args.length > index) {
                inputs = args[index];
            }
            
            Map<String, Object> inputsMap = new HashMap<>();
            inputsMap.put("inputs", inputs);
            
            // Trigger start event
            Map<String, Object> startKwargs = new HashMap<>();
            startKwargs.put("span", span);
            startKwargs.put("inputs", inputsMap);
            startKwargs.put("instance_info", instanceInfo);
            
            String eventPrefix = "on" + capitalize(invokeType.getValue());
            
            // Start tracing
            tracer.trigger("tracer_agent", eventPrefix + "Start", startKwargs).join();
            
            // Execute function (skip first argument which is usually 'self')
            Object[] funcArgs = new Object[args.length - 1];
            System.arraycopy(args, 1, funcArgs, 0, args.length - 1);
            
            // For LLM invoke type, add tracer record data callback
            AsyncIterator<T> result = func.apply(funcArgs);
            List<T> results = new ArrayList<>();
            
            // Return a wrapping async iterator
            return new AsyncIterator<T>() {
                @Override
                public CompletableFuture<Boolean> hasNext() {
                    return result.hasNext().thenCompose(hasNext -> {
                        if (!hasNext) {
                            // Trigger end event when iterator is exhausted
                            Map<String, Object> outputsMap = new HashMap<>();
                            outputsMap.put("outputs", results);
                            
                            Map<String, Object> endKwargs = new HashMap<>();
                            endKwargs.put("span", span);
                            endKwargs.put("outputs", outputsMap);
                            
                            return tracer.trigger("tracer_agent", eventPrefix + "End", endKwargs)
                                .thenApply(v -> false);
                        }
                        return CompletableFuture.completedFuture(true);
                    }).exceptionally(error -> {
                        // Trigger error event
                        Map<String, Object> errorKwargs = new HashMap<>();
                        errorKwargs.put("span", span);
                        errorKwargs.put("error", error);
                        
                        tracer.syncTrigger("tracer_agent", eventPrefix + "Error", errorKwargs);
                        
                        if (error instanceof RuntimeException) {
                            throw (RuntimeException) error;
                        }
                        throw new RuntimeException(error);
                    });
                }
                
                @Override
                public CompletableFuture<T> next() {
                    return result.next().thenApply(item -> {
                        results.add(item);
                        return item;
                    });
                }
            };
        };
    }
    
    /**
     * Capitalizes the first letter of a string.
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    
    /**
     * Interface for agent session operations used by tracer decorator.
     */
    public interface AgentSession {
        
        /**
         * Gets the tracer instance.
         * 
         * @return the tracer, or null if tracing is disabled
         */
        Tracer tracer();
        
        /**
         * Gets the current agent span.
         * 
         * @return the current span
         */
        TraceAgentSpan span();
    }
    
    /**
     * Interface for async iteration.
     * 
     * @param <T> the item type
     */
    public interface AsyncIterator<T> {
        
        /**
         * Checks if there are more items.
         * 
         * @return a CompletableFuture resolving to true if more items exist
         */
        CompletableFuture<Boolean> hasNext();
        
        /**
         * Gets the next item.
         * 
         * @return a CompletableFuture resolving to the next item
         */
        CompletableFuture<T> next();
    }
    
    /**
     * Wrapper for traced model operations.
     * 
     * @param <T> the underlying model type
     */
    public static class TracedModel<T> {
        
        private final T model;
        private final AgentSession session;
        private final Map<String, Object> instanceInfo;
        
        /**
         * Creates a new TracedModel.
         * 
         * @param model the underlying model
         * @param session the agent session
         * @param instanceInfo the instance info for tracing
         */
        public TracedModel(T model, AgentSession session, Map<String, Object> instanceInfo) {
            this.model = model;
            this.session = session;
            this.instanceInfo = instanceInfo;
        }
        
        /**
         * Gets the underlying model.
         * 
         * @return the model
         */
        public T getModel() {
            return model;
        }
        
        /**
         * Invokes the model with tracing.
         * 
         * @param messages the messages to send
         * @param kwargs additional arguments
         * @return a CompletableFuture with the result
         */
        @SuppressWarnings("unchecked")
        public CompletableFuture<Object> invoke(Object messages, Map<String, Object> kwargs) {
            if (session == null || instanceInfo == null) {
                // No tracing, just invoke directly
                try {
                    Object result = model.getClass().getMethod("invoke", Object.class, Map.class)
                        .invoke(model, messages, kwargs);
                    if (result instanceof CompletableFuture) {
                        return (CompletableFuture<Object>) result;
                    }
                    return CompletableFuture.completedFuture(result);
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
            
            Tracer tracer = session.tracer();
            TraceAgentSpan agentSpan = session.span();
            TraceAgentSpan span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
            
            Map<String, Object> inputsMap = new HashMap<>();
            inputsMap.put("inputs", messages);
            
            Map<String, Object> startKwargs = new HashMap<>();
            startKwargs.put("span", span);
            startKwargs.put("inputs", inputsMap);
            startKwargs.put("instance_info", instanceInfo);
            
            return tracer.trigger("tracer_agent", "onLlmStart", startKwargs)
                .thenCompose(v -> {
                    try {
                        // Add tracer record callback to kwargs
                        Map<String, Object> callKwargs = kwargs != null ? new HashMap<>(kwargs) : new HashMap<>();
                        
                        Object result = model.getClass().getMethod("invoke", Object.class, Map.class)
                            .invoke(model, messages, callKwargs);
                        
                        if (result instanceof CompletableFuture) {
                            return (CompletableFuture<Object>) result;
                        }
                        return CompletableFuture.completedFuture(result);
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                })
                .thenCompose(result -> {
                    Map<String, Object> outputsMap = new HashMap<>();
                    outputsMap.put("outputs", result);
                    
                    Map<String, Object> endKwargs = new HashMap<>();
                    endKwargs.put("span", span);
                    endKwargs.put("outputs", outputsMap);
                    
                    return tracer.trigger("tracer_agent", "onLlmEnd", endKwargs)
                        .thenApply(v -> result);
                })
                .exceptionally(error -> {
                    Map<String, Object> errorKwargs = new HashMap<>();
                    errorKwargs.put("span", span);
                    errorKwargs.put("error", error);
                    
                    tracer.syncTrigger("tracer_agent", "onLlmError", errorKwargs);
                    
                    if (error instanceof RuntimeException) {
                        throw (RuntimeException) error;
                    }
                    throw new RuntimeException(error);
                });
        }
        
        /**
         * Streams from the model with tracing.
         * 
         * @param messages the messages to send
         * @param kwargs additional arguments
         * @return an iterator over the stream results
         */
        public Iterator<Object> stream(Object messages, Map<String, Object> kwargs) {
            if (session == null || instanceInfo == null) {
                // No tracing, just stream directly
                try {
                    @SuppressWarnings("unchecked")
                    Iterator<Object> result = (Iterator<Object>) model.getClass()
                        .getMethod("stream", Object.class, Map.class)
                        .invoke(model, messages, kwargs);
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            Tracer tracer = session.tracer();
            TraceAgentSpan agentSpan = session.span();
            TraceAgentSpan span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
            
            Map<String, Object> inputsMap = new HashMap<>();
            inputsMap.put("inputs", messages);
            
            Map<String, Object> startKwargs = new HashMap<>();
            startKwargs.put("span", span);
            startKwargs.put("inputs", inputsMap);
            startKwargs.put("instance_info", instanceInfo);
            
            tracer.syncTrigger("tracer_agent", "onLlmStart", startKwargs);
            
            try {
                @SuppressWarnings("unchecked")
                Iterator<Object> result = (Iterator<Object>) model.getClass()
                    .getMethod("stream", Object.class, Map.class)
                    .invoke(model, messages, kwargs);
                
                List<Object> results = new ArrayList<>();
                
                return new Iterator<Object>() {
                    @Override
                    public boolean hasNext() {
                        boolean hasNext = result.hasNext();
                        if (!hasNext) {
                            Map<String, Object> outputsMap = new HashMap<>();
                            outputsMap.put("outputs", results);
                            
                            Map<String, Object> endKwargs = new HashMap<>();
                            endKwargs.put("span", span);
                            endKwargs.put("outputs", outputsMap);
                            
                            tracer.syncTrigger("tracer_agent", "onLlmEnd", endKwargs);
                        }
                        return hasNext;
                    }
                    
                    @Override
                    public Object next() {
                        Object item = result.next();
                        results.add(item);
                        return item;
                    }
                };
            } catch (Exception error) {
                Map<String, Object> errorKwargs = new HashMap<>();
                errorKwargs.put("span", span);
                errorKwargs.put("error", error);
                
                tracer.syncTrigger("tracer_agent", "onLlmError", errorKwargs);
                throw new RuntimeException(error);
            }
        }
    }
    
    /**
     * Wrapper for traced tool operations.
     * 
     * @param <T> the underlying tool type
     */
    public static class TracedTool<T> {
        
        private final T tool;
        private final AgentSession session;
        private final Map<String, Object> instanceInfo;
        
        /**
         * Creates a new TracedTool.
         * 
         * @param tool the underlying tool
         * @param session the agent session
         * @param instanceInfo the instance info for tracing
         */
        public TracedTool(T tool, AgentSession session, Map<String, Object> instanceInfo) {
            this.tool = tool;
            this.session = session;
            this.instanceInfo = instanceInfo;
        }
        
        /**
         * Gets the underlying tool.
         * 
         * @return the tool
         */
        public T getTool() {
            return tool;
        }
        
        /**
         * Invokes the tool with tracing.
         * 
         * @param inputs the tool inputs
         * @return a CompletableFuture with the result
         */
        @SuppressWarnings("unchecked")
        public CompletableFuture<Object> invoke(Object inputs) {
            if (session == null || instanceInfo == null) {
                try {
                    Object result = tool.getClass().getMethod("invoke", Object.class).invoke(tool, inputs);
                    if (result instanceof CompletableFuture) {
                        return (CompletableFuture<Object>) result;
                    }
                    return CompletableFuture.completedFuture(result);
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
            
            Tracer tracer = session.tracer();
            TraceAgentSpan agentSpan = session.span();
            TraceAgentSpan span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
            
            Map<String, Object> inputsMap = new HashMap<>();
            inputsMap.put("inputs", inputs);
            
            Map<String, Object> startKwargs = new HashMap<>();
            startKwargs.put("span", span);
            startKwargs.put("inputs", inputsMap);
            startKwargs.put("instance_info", instanceInfo);
            
            return tracer.trigger("tracer_agent", "onPluginStart", startKwargs)
                .thenCompose(v -> {
                    try {
                        Object result = tool.getClass().getMethod("invoke", Object.class).invoke(tool, inputs);
                        if (result instanceof CompletableFuture) {
                            return (CompletableFuture<Object>) result;
                        }
                        return CompletableFuture.completedFuture(result);
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                })
                .thenCompose(result -> {
                    Map<String, Object> outputsMap = new HashMap<>();
                    outputsMap.put("outputs", result);
                    
                    Map<String, Object> endKwargs = new HashMap<>();
                    endKwargs.put("span", span);
                    endKwargs.put("outputs", outputsMap);
                    
                    return tracer.trigger("tracer_agent", "onPluginEnd", endKwargs)
                        .thenApply(v -> result);
                })
                .exceptionally(error -> {
                    Map<String, Object> errorKwargs = new HashMap<>();
                    errorKwargs.put("span", span);
                    errorKwargs.put("error", error);
                    
                    tracer.syncTrigger("tracer_agent", "onPluginError", errorKwargs);
                    
                    if (error instanceof RuntimeException) {
                        throw (RuntimeException) error;
                    }
                    throw new RuntimeException(error);
                });
        }
    }
    
    /**
     * Wrapper for traced workflow operations.
     * 
     * @param <T> the underlying workflow type
     */
    public static class TracedWorkflow<T> {
        
        private final T workflow;
        private final AgentSession session;
        private final Map<String, Object> instanceInfo;
        
        /**
         * Creates a new TracedWorkflow.
         * 
         * @param workflow the underlying workflow
         * @param session the agent session
         * @param instanceInfo the instance info for tracing
         */
        public TracedWorkflow(T workflow, AgentSession session, Map<String, Object> instanceInfo) {
            this.workflow = workflow;
            this.session = session;
            this.instanceInfo = instanceInfo;
        }
        
        /**
         * Gets the underlying workflow.
         * 
         * @return the workflow
         */
        public T getWorkflow() {
            return workflow;
        }
        
        /**
         * Invokes the workflow with tracing.
         * 
         * @param inputs the workflow inputs
         * @return a CompletableFuture with the result
         */
        @SuppressWarnings("unchecked")
        public CompletableFuture<Object> invoke(Object inputs) {
            if (session == null || instanceInfo == null) {
                try {
                    Object result = workflow.getClass().getMethod("invoke", Object.class).invoke(workflow, inputs);
                    if (result instanceof CompletableFuture) {
                        return (CompletableFuture<Object>) result;
                    }
                    return CompletableFuture.completedFuture(result);
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
            
            Tracer tracer = session.tracer();
            TraceAgentSpan agentSpan = session.span();
            TraceAgentSpan span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
            
            Map<String, Object> inputsMap = new HashMap<>();
            inputsMap.put("inputs", inputs);
            
            Map<String, Object> startKwargs = new HashMap<>();
            startKwargs.put("span", span);
            startKwargs.put("inputs", inputsMap);
            startKwargs.put("instance_info", instanceInfo);
            
            return tracer.trigger("tracer_agent", "onWorkflowStart", startKwargs)
                .thenCompose(v -> {
                    try {
                        Object result = workflow.getClass().getMethod("invoke", Object.class).invoke(workflow, inputs);
                        if (result instanceof CompletableFuture) {
                            return (CompletableFuture<Object>) result;
                        }
                        return CompletableFuture.completedFuture(result);
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                })
                .thenCompose(result -> {
                    Map<String, Object> outputsMap = new HashMap<>();
                    outputsMap.put("outputs", result);
                    
                    Map<String, Object> endKwargs = new HashMap<>();
                    endKwargs.put("span", span);
                    endKwargs.put("outputs", outputsMap);
                    
                    return tracer.trigger("tracer_agent", "onWorkflowEnd", endKwargs)
                        .thenApply(v -> result);
                })
                .exceptionally(error -> {
                    Map<String, Object> errorKwargs = new HashMap<>();
                    errorKwargs.put("span", span);
                    errorKwargs.put("error", error);
                    
                    tracer.syncTrigger("tracer_agent", "onWorkflowError", errorKwargs);
                    
                    if (error instanceof RuntimeException) {
                        throw (RuntimeException) error;
                    }
                    throw new RuntimeException(error);
                });
        }
        
        /**
         * Streams from the workflow with tracing.
         * 
         * @param inputs the workflow inputs
         * @return an iterator over the stream results
         */
        public Iterator<Object> stream(Object inputs) {
            if (session == null || instanceInfo == null) {
                try {
                    @SuppressWarnings("unchecked")
                    Iterator<Object> result = (Iterator<Object>) workflow.getClass()
                        .getMethod("stream", Object.class)
                        .invoke(workflow, inputs);
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            Tracer tracer = session.tracer();
            TraceAgentSpan agentSpan = session.span();
            TraceAgentSpan span = tracer.getTracerAgentSpanManager().createAgentSpan(agentSpan);
            
            Map<String, Object> inputsMap = new HashMap<>();
            inputsMap.put("inputs", inputs);
            
            Map<String, Object> startKwargs = new HashMap<>();
            startKwargs.put("span", span);
            startKwargs.put("inputs", inputsMap);
            startKwargs.put("instance_info", instanceInfo);
            
            tracer.syncTrigger("tracer_agent", "onWorkflowStart", startKwargs);
            
            try {
                @SuppressWarnings("unchecked")
                Iterator<Object> result = (Iterator<Object>) workflow.getClass()
                    .getMethod("stream", Object.class)
                    .invoke(workflow, inputs);
                
                List<Object> results = new ArrayList<>();
                
                return new Iterator<Object>() {
                    @Override
                    public boolean hasNext() {
                        boolean hasNext = result.hasNext();
                        if (!hasNext) {
                            Map<String, Object> outputsMap = new HashMap<>();
                            outputsMap.put("outputs", results);
                            
                            Map<String, Object> endKwargs = new HashMap<>();
                            endKwargs.put("span", span);
                            endKwargs.put("outputs", outputsMap);
                            
                            tracer.syncTrigger("tracer_agent", "onWorkflowEnd", endKwargs);
                        }
                        return hasNext;
                    }
                    
                    @Override
                    public Object next() {
                        Object item = result.next();
                        results.add(item);
                        return item;
                    }
                };
            } catch (Exception error) {
                Map<String, Object> errorKwargs = new HashMap<>();
                errorKwargs.put("span", span);
                errorKwargs.put("error", error);
                
                tracer.syncTrigger("tracer_agent", "onWorkflowError", errorKwargs);
                throw new RuntimeException(error);
            }
        }
    }
}

