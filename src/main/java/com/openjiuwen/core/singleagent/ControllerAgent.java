/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Controller-based Agent implementation.
 *
 * <p>Agent implementation built on top of Controller, used to handle complex
 * event-driven tasks. Supports advanced features such as task scheduling and
 * event handling.</p>
 */
public class ControllerAgent extends BaseAgent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ControllerConfig config;
    private final Controller controller;
    private ContextEngine contextEngine;

    public ControllerAgent(AgentCard card, Controller controller) {
        this(card, controller, null, null);
    }

    public ControllerAgent(AgentCard card, Controller controller, ControllerConfig config) {
        this(card, controller, config, null);
    }

    protected ControllerAgent(AgentCard card, Controller controller, ControllerConfig config,
                              ContextEngineConfig contextEngineConfig) {
        super(card);
        this.config = config != null ? config : new ControllerConfig();
        this.contextEngine = new ContextEngine(
                contextEngineConfig != null ? contextEngineConfig : ContextEngineConfig.builder().build());
        this.controller = controller;
        initializeController();
    }

    private void initializeController() {
        controller.init(
                getCard(),
                config,
                getAbilityManager(),
                contextEngine
        );
    }

    @Override
    public BaseAgent configure(Object config) {
        if (config instanceof ControllerConfig cc) {
            this.config = cc;
        } else if (config instanceof Map<?, ?> configMap) {
            this.config = mergeControllerConfig(configMap);
        }
        controller.setConfig(this.config);
        return this;
    }

    @Override
    public Object getConfig() {
        return config;
    }

    public Controller getController() {
        return controller;
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    /**
     * Release session resources.
     *
     * @param sessionId session ID
     */
    public void releaseSession(String sessionId) {
        if (controller != null && controller.getEventQueue() != null) {
            controller.getEventQueue().unsubscribe(getCard().getId(), sessionId);
        }
        Runner.release(sessionId);
    }

    /**
     * Convert a Session to AgentSessionApi.
     */
    private AgentSessionApi toAgentSession(Session session) {
        if (session instanceof AgentSessionApi asa) {
            return asa;
        }
        return AgentSessionApi.create(session.getSessionId(), null, getCard());
    }

    @Override
    public ControllerOutput invoke(Object inputs, Session session) {
        if (controller == null) {
            throw new RuntimeException(
                    this.getClass().getSimpleName() + " has no controller, "
                            + "subclass should create controller before invocation"
            );
        }

        if (session == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "session is required"
            );
        }

        InputEvent inputEvent = InputEvent.fromUserInput(inputs);

        try {
            AgentSessionApi agentSession = toAgentSession(session);
            return controller.invoke(inputEvent, agentSession);
        } catch (BaseError e) {
            throw e;
        } catch (RuntimeException e) {
            Loggers.AGENT.error("ControllerAgent invoke error: " + e.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", e.getMessage()
            );
        }
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        if (controller == null) {
            throw new RuntimeException(
                    this.getClass().getSimpleName() + " has no controller"
            );
        }

        if (session == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "session is required"
            );
        }

        InputEvent inputEvent = InputEvent.fromUserInput(inputs);

        try {
            AgentSessionApi agentSession = toAgentSession(session);
            Iterator<Object> controllerStream = controller.stream(inputEvent, agentSession, streamModes);
            return controllerStream;
        } catch (BaseError e) {
            throw e;
        } catch (RuntimeException e) {
            Loggers.AGENT.error("ControllerAgent stream error: " + e.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", e.getMessage()
            );
        }
    }

    private ControllerConfig mergeControllerConfig(Map<?, ?> configMap) {
        ControllerConfig merged = new ControllerConfig();
        copyControllerConfig(this.config, merged);

        try {
            Map<String, PropertyDescriptor> descriptors = new java.util.HashMap<>();
            for (PropertyDescriptor descriptor : Introspector.getBeanInfo(ControllerConfig.class).getPropertyDescriptors()) {
                descriptors.put(descriptor.getName(), descriptor);
            }

            for (Map.Entry<?, ?> entry : configMap.entrySet()) {
                String propertyName = String.valueOf(entry.getKey());
                PropertyDescriptor descriptor = descriptors.get(propertyName);
                if (descriptor == null || descriptor.getWriteMethod() == null) {
                    Loggers.AGENT.warning("Ignoring unknown ControllerConfig property: " + propertyName);
                    continue;
                }

                Object convertedValue = MAPPER.convertValue(entry.getValue(), descriptor.getPropertyType());
                descriptor.getWriteMethod().invoke(merged, convertedValue);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to merge ControllerConfig from map", e);
        }

        return merged;
    }

    private static void copyControllerConfig(ControllerConfig source, ControllerConfig target) {
        if (source == null) {
            return;
        }
        target.setMaxConcurrentTasks(source.getMaxConcurrentTasks());
        target.setScheduleInterval(source.getScheduleInterval());
        target.setTaskTimeout(source.getTaskTimeout());
        target.setDefaultTaskPriority(source.getDefaultTaskPriority());
        target.setEnableTaskPersistence(source.isEnableTaskPersistence());
        target.setEventQueueSize(source.getEventQueueSize());
        target.setEventTimeout(source.getEventTimeout());
        target.setEnableIntentRecognition(source.isEnableIntentRecognition());
        target.setIntentLlmId(source.getIntentLlmId());
        target.setIntentConfidenceThreshold(source.getIntentConfidenceThreshold());
        target.setIntentTypeList(source.getIntentTypeList());
    }
}
