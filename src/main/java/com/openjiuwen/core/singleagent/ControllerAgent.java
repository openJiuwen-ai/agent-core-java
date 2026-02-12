// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.contextengine.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ControllerAgent.
 * 
 * <p>Agent implementation built on top of Controller, used to handle complex
 * event-driven tasks. Supports advanced features such as task scheduling and
 * event handling.
 * 
 * <p>Python reference: {@code agent-core/openjiuwen/core/single_agent/agent.py::ControllerAgent}
 */
public class ControllerAgent extends BaseAgent {
    
    private static final LoggerProtocol logger = Loggers.AGENT;
    
    private ControllerConfig config;
    private ContextEngine contextEngine;
    private final Controller controller;
    
    /**
     * Initializes ControllerAgent.
     *
     * @param card the agent card defining the Agent identity and capabilities
     * @param controller the Controller instance responsible for event handling and task scheduling
     */
    public ControllerAgent(AgentCard card, Controller controller) {
        this(card, controller, null);
    }
    
    /**
     * Initializes ControllerAgent.
     *
     * @param card the agent card defining the Agent identity and capabilities
     * @param controller the Controller instance responsible for event handling and task scheduling
     * @param config the controller configuration (optional, defaults will be used if null)
     */
    public ControllerAgent(AgentCard card, Controller controller, ControllerConfig config) {
        super(card);
        this.config = config != null ? config : createDefaultConfig();
        this.contextEngine = new ContextEngine(ContextEngineConfig.defaults());
        this.controller = controller;
        initializeController();
    }
    
    /**
     * Initializes controller.
     * 
     * <p>Pass Agent configuration, abilities, context engine and other
     * information to the Controller to ensure it can access all Agent
     * capabilities.
     */
    private void initializeController() {
        controller.init(
            card,
            config,
            abilityManager,
            contextEngine,
            null  // message queue (optional)
        );
    }
    
    /**
     * Creates default configuration.
     *
     * @return default ControllerConfig
     */
    private ControllerConfig createDefaultConfig() {
        return new ControllerConfig();
    }
    
    /**
     * Sets configuration.
     *
     * @param configObj configuration object (ControllerConfig or Map)
     * @return this agent for chaining
     */
    @Override
    public BaseAgent configure(Object configObj) {
        if (configObj instanceof ControllerConfig controllerConfig) {
            this.config = controllerConfig;
        } else if (configObj instanceof Map<?, ?> configMap) {
            // Merge map config with existing config using builder
            ControllerConfig.Builder builder = ControllerConfig.builder();
            if (configMap.containsKey("maxConcurrentTasks")) {
                builder.maxConcurrentTasks((Integer) configMap.get("maxConcurrentTasks"));
            }
            if (configMap.containsKey("scheduleInterval")) {
                builder.scheduleInterval(((Number) configMap.get("scheduleInterval")).doubleValue());
            }
            if (configMap.containsKey("taskTimeout")) {
                builder.taskTimeout(((Number) configMap.get("taskTimeout")).doubleValue());
            }
            if (configMap.containsKey("intentConfidenceThreshold")) {
                builder.intentConfidenceThreshold(
                    ((Number) configMap.get("intentConfidenceThreshold")).doubleValue());
            }
            this.config = builder.build();
        } else {
            this.config = (ControllerConfig) configObj;
        }
        controller.setConfig(this.config);
        return this;
    }
    
    /**
     * Gets the controller.
     *
     * @return the controller
     */
    public Controller getController() {
        return controller;
    }
    
    /**
     * Gets the context engine.
     *
     * @return the context engine
     */
    public ContextEngine getContextEngine() {
        return contextEngine;
    }
    
    /**
     * Gets the current configuration.
     *
     * @return the controller config
     */
    public ControllerConfig getConfig() {
        return config;
    }
    
    /**
     * Releases session resources.
     *
     * @param sessionId the session ID
     * @return future that completes when resources are released
     */
    public CompletableFuture<Void> releaseSession(String sessionId) {
        if (controller.getEventQueue() != null) {
            controller.getEventQueue().unsubscribe(card.getId(), sessionId);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                Runner.release(sessionId);
            } catch (Exception e) {
                logger.error("Failed to release session " + sessionId + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * Batch execution using controller.
     *
     * @param inputs user input, supports:
     *               - String: used directly as user input text
     *               - Map: dict containing user input
     *               - InputEvent: pre-constructed input event object
     * @param session session object
     * @return future containing ControllerOutput
     * @throws JiuWenBaseException if session is null or controller error occurs
     */
    @Override
    public CompletableFuture<Object> invoke(Object inputs, Session session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (controller == null) {
                    throw new RuntimeException(
                        getClass().getSimpleName() + " has no controller, "
                        + "subclass should create controller before invocation"
                    );
                }
                
                if (session == null) {
                    throw new JiuWenBaseException(
                        StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR.getCode(),
                        "session is required"
                    );
                }
                
                // Convert inputs to InputEvent
                InputEvent inputEvent = convertToInputEvent(inputs);
                
                // Call controller.invoke
                return controller.invoke(inputEvent, session, null).join();
                
            } catch (JiuWenBaseException e) {
                throw e;
            } catch (Exception e) {
                logger.error("ControllerAgent invoke error: " + e.getMessage());
                throw new JiuWenBaseException(
                    StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR.getCode(),
                    e.getMessage()
                );
            }
        });
    }
    
    /**
     * Stream execution using controller.
     *
     * @param inputs user input
     * @param session session object (optional)
     * @param streamModes stream output modes (optional)
     * @return future containing an iterator over ControllerOutputChunk
     * @throws JiuWenBaseException if session is null or controller error occurs
     */
    @Override
    public CompletableFuture<Iterator<Object>> stream(
            Object inputs, Session session, List<StreamMode> streamModes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (controller == null) {
                    throw new RuntimeException(
                        getClass().getSimpleName() + " has no controller, "
                        + "subclass should create controller before invocation"
                    );
                }
                
                if (session == null) {
                    throw new JiuWenBaseException(
                        StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR.getCode(),
                        "session is required"
                    );
                }
                
                // Convert inputs to InputEvent
                InputEvent inputEvent = convertToInputEvent(inputs);
                
                // Forward directly to Controller.stream()
                Iterator<ControllerOutputChunk> chunkIterator = controller.stream(
                    inputEvent, session, streamModes, null
                );
                
                // Wrap as Iterator<Object> for compatibility with BaseAgent interface
                return new Iterator<Object>() {
                    @Override
                    public boolean hasNext() {
                        return chunkIterator.hasNext();
                    }
                    
                    @Override
                    public Object next() {
                        return chunkIterator.next();
                    }
                };
                
            } catch (JiuWenBaseException e) {
                throw e;
            } catch (Exception e) {
                logger.error("ControllerAgent stream error: " + e.getMessage());
                throw new JiuWenBaseException(
                    StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR.getCode(),
                    e.getMessage()
                );
            }
        });
    }
    
    /**
     * Converts raw input to InputEvent.
     *
     * @param inputs the raw input (String, Map, or InputEvent)
     * @return the InputEvent
     */
    private InputEvent convertToInputEvent(Object inputs) {
        if (inputs instanceof InputEvent inputEvent) {
            return inputEvent;
        } else if (inputs instanceof String userInput) {
            return InputEvent.fromUserInput(userInput);
        } else if (inputs instanceof Map<?, ?> inputMap) {
            Object userInput = inputMap.get("user_input");
            if (userInput != null) {
                return InputEvent.fromUserInput(String.valueOf(userInput));
            }
            return InputEvent.fromUserInput(inputMap.toString());
        } else {
            return InputEvent.fromUserInput(String.valueOf(inputs));
        }
    }
}

