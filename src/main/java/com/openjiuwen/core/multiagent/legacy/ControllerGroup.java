/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

/**
 * Agent Group with Controller (legacy pattern).
 * <p>
 * Design features (similar to ControllerAgent):
 * <ul>
 * <li>Inherits LegacyBaseGroup, reuses agent management capabilities</li>
 * <li>Holds GroupController, fully delegates message routing logic</li>
 * <li>Automatically configures GroupController (via setupFromGroup)</li>
 * <li>invoke/stream fully delegated to groupController</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code ControllerGroup} in {@code multi_agent/legacy/agent_group.py}.
 * 
 * @deprecated Use {@link com.openjiuwen.core.multiagent.BaseGroup} with the new Card + Config pattern.
 * @since 0.1.7
 */
@Deprecated
public class ControllerGroup extends LegacyBaseGroup {
    private final BaseGroupController groupController;

    /**
     * Initialize ControllerGroup.
     * 
     * @param config AgentGroup configuration object
     * @param groupController GroupController instance (will be auto-configured)
     * @since 0.1.7
     */
    public ControllerGroup(AgentGroupConfig config, BaseGroupController groupController) {
        super(config);
        this.groupController = groupController;

        // Auto-configure groupController
        if (this.groupController != null) {
            setupGroupController();
        }
    }

    /**
     * ControllerGroup.
     * 
     * @param config config
     * @since 0.1.7
     */
    public ControllerGroup(AgentGroupConfig config) {
        this(config, null);
    }

    /**
     * setupGroupController.
     * 
     * @since 0.1.7
     */
    private void setupGroupController() {
        groupController.setupFromGroup(this);
    }

    /**
     * Convert dict/map to GroupEvent if needed (backward compatibility).
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private GroupEvent convertMessage(Object message) {
        if (message instanceof GroupEvent ge) {
            return ge;
        }
        if (message instanceof Map<?, ?> map) {
            return GroupEvent.fromMap((Map<String, Object>) map);
        }
        if (message instanceof String s) {
            return GroupEvent.createUserEvent(s, "default_session");
        }
        throw new IllegalArgumentException(
                "Unsupported message type: " + message.getClass().getName() + ". Must be GroupEvent, Map, or String.");
    }

    /**
     * invoke.
     * 
     * @param message message
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object invoke(Object message, AgentGroupSessionApi session) {
        if (groupController == null) {
            throw new RuntimeException(getClass().getSimpleName() + " has no groupController");
        }

        GroupEvent event = convertMessage(message);

        // If session not provided, create a new one
        AgentGroupSessionApi effectiveSession = session;
        if (effectiveSession == null) {
            String sessionId = event.getConversationId() != null ? event.getConversationId() : "default";
            effectiveSession = new AgentGroupSessionApi(sessionId);
        }

        Object result = groupController.invoke(event, effectiveSession);
        return result != null ? result : Map.of("output", "processed");
    }

    /**
     * stream.
     * 
     * @param message message
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
        if (groupController == null) {
            throw new RuntimeException(getClass().getSimpleName() + " has no groupController");
        }

        GroupEvent event = convertMessage(message);

        // If session not provided, create a new one
        AgentGroupSessionApi effectiveSession = session;
        if (effectiveSession == null) {
            String sessionId = event.getConversationId() != null ? event.getConversationId() : "default";
            effectiveSession = new AgentGroupSessionApi(sessionId);
        }

        AgentGroupSessionApi finalSession = effectiveSession;
        CompletableFuture<Void> controllerTask = OpenJiuwenExecutors.runBackgroundAsync(() -> {
            try {
                groupController.invoke(event, finalSession);
            } catch (Exception e) {
                Loggers.MULTI_AGENT.error("ControllerGroup.stream: controller error: {}", e.getMessage());
            } finally {
                finalSession.getInner().streamWriterManager().getStreamEmitter().close();
            }
        });

        Iterator<Object> sessionStream = finalSession.getInner().streamWriterManager().streamIterator();
        return new Iterator<>() {
            private Object next = null;
            private boolean done = false;
            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (next != null) {
                    return true;
                }
                try {
                    if (!sessionStream.hasNext()) {
                        controllerTask.join();
                        done = true;
                        return false;
                    }
                    next = sessionStream.next();
                    return true;
                } catch (RuntimeException e) {
                    controllerTask.join();
                    done = true;
                    throw e;
                }
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Object result = next;
                next = null;
                return result;
            }
        };
    }

    /**
     * getGroupController.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BaseGroupController getGroupController() {
        return groupController;
    }
}
