/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.mq.InvokeQueueMessage;
import com.openjiuwen.core.runner.mq.MessageQueueInMemory;
import com.openjiuwen.core.runner.mq.SubscriptionBase;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.BaseAgent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * BaseGroupController.
 * 
 * @since 0.1.7
 */
@Deprecated
public abstract class BaseGroupController {
    private LegacyBaseGroup agentGroup;
    private MessageQueueInMemory msgQueue;
    private volatile boolean queueStarted;

    /**
     * Core data: subscription relationship table (message_type string as key).
     * 
     * @since 0.1.7
     */
    private final Map<String, List<String>> subscriptions = new HashMap<>();

    /**
     * Initialize BaseGroupController.
     * 
     * @param agentGroup associated AgentGroup (nullable, can be injected later)
     * @since 0.1.7
     */
    protected BaseGroupController(LegacyBaseGroup agentGroup) {
        this.agentGroup = agentGroup;
        this.msgQueue = new MessageQueueInMemory();
        this.queueStarted = false;
    }

    /**
     * BaseGroupController.
     * 
     * @since 0.1.7
     */
    protected BaseGroupController() {
        this(null);
    }

    /**
     * Setup controller from group — inject required attributes.
     * Called by ControllerGroup to inject group reference.
     * 
     * @param group ControllerGroup instance
     * @since 0.1.7
     */
    public void setupFromGroup(LegacyBaseGroup group) {
        this.agentGroup = group;
        Loggers.MULTI_AGENT.info("BaseGroupController: Setup from group, group_id={}", group.getGroupId());
    }

    /**
     * Synchronous invocation entry.
     * <p>
     * Process: lazy-start message queue, publish message, wait for result.
     * 
     * @param event GroupEvent object (carries message_type for routing)
     * @param session session context
     * @return processing result
     * @since 0.1.7
     */
    public Object invoke(GroupEvent event, AgentGroupSessionApi session) {
        ensureQueueStarted();

        String topic = "group_messages_" + agentGroup.getGroupId();
        InvokeQueueMessage queueMessage = new InvokeQueueMessage();

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("session", session);
        queueMessage.setPayload(payload);

        msgQueue.produceMessage(topic, queueMessage);

        try {
            Object result = queueMessage.getResponse().get();
            return result != null ? result : Map.of("output", "processed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Group invoke interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Group invoke failed", cause);
        }
    }

    /**
     * ensureQueueStarted.
     * 
     * @since 0.1.7
     */
    private synchronized void ensureQueueStarted() {
        if (!queueStarted) {
            String topic = "group_messages_" + agentGroup.getGroupId();
            SubscriptionBase subscription = msgQueue.subscribe(topic);
            subscription.setMessageHandler(msg -> CompletableFuture.completedFuture(handleMessageWrapper(msg)));
            subscription.activate();
            msgQueue.start();
            queueStarted = true;
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * handleMessageWrapper.
     * 
     * @param payload payload
     * @return the result
     * @since 0.1.7
     */
    private Object handleMessageWrapper(Object payload) {
        Map<String, Object> request = (Map<String, Object>) payload;
        GroupEvent event = (GroupEvent) request.get("event");
        AgentGroupSessionApi session = (AgentGroupSessionApi) request.get("session");
        try {
            Object result = handleEvent(event, session);
            Loggers.MULTI_AGENT.info("BaseGroupController: handleEvent returned: {}",
                    result != null ? result.getClass().getSimpleName() : "null");
            return result;
        } catch (Exception e) {
            Loggers.MULTI_AGENT.error("BaseGroupController: handleEvent raised exception: {}", e.getMessage());
            throw e;
        }
    }

    // ========== Abstract methods (developers must implement) ==========

    /**
     * Core method for message processing (must be implemented).
     * <p>
     * Developers implement message routing logic here:
     * <ul>
     * <li>Route to corresponding Agent based on message type</li>
     * <li>Point-to-point sending or broadcasting</li>
     * <li>Coordinate multiple Agents</li>
     * </ul>
     * 
     * @param event GroupEvent object
     * @param session session context
     * @return processing result
     * @since 0.1.7
     */
    protected abstract Object handleEvent(GroupEvent event, AgentGroupSessionApi session);

    // ========== Subscription management API ==========

    /**
     * Subscribe agents to a message type.
     * 
     * @param messageType message type string identifier
     * @param agentIds list of agent IDs
     * @since 0.1.7
     */
    public void subscribe(String messageType, List<String> agentIds) {
        subscriptions.computeIfAbsent(messageType, k -> new ArrayList<>());
        List<String> subs = subscriptions.get(messageType);
        for (String agentId : agentIds) {
            if (!subs.contains(agentId)) {
                subs.add(agentId);
                Loggers.MULTI_AGENT.info("BaseGroupController: Agent {} subscribed to message_type={}", agentId,
                        messageType);
            }
        }
    }

    /**
     * Unsubscribe agents from a message type.
     * 
     * @param messageType message type string identifier
     * @param agentIds list of agent IDs
     * @since 0.1.7
     */
    public void unsubscribe(String messageType, List<String> agentIds) {
        List<String> subs = subscriptions.get(messageType);
        if (subs != null) {
            for (String agentId : agentIds) {
                if (subs.remove(agentId)) {
                    Loggers.MULTI_AGENT.info("BaseGroupController: Agent {} unsubscribed from message_type={}", agentId,
                            messageType);
                }
            }
        }
    }

    /**
     * Get subscribers for a message type.
     * 
     * @param messageType message type string identifier
     * @return list of subscriber agent IDs
     * @since 0.1.7
     */
    public List<String> getSubscribers(String messageType) {
        return subscriptions.getOrDefault(messageType, List.of());
    }

    // ========== Message sending API ==========

    /**
     * Send message to specified Agent (point-to-point, streaming).
     * <p>
     * Calls agent.stream() and collects results. Checks for interaction interrupts.
     * 
     * @param event GroupEvent object
     * @param agentId target Agent ID
     * @param session session context
     * @return final result (last chunk, or full list for interrupt case)
     * @since 0.1.7
     */
    public Object sendToAgent(GroupEvent event, String agentId, AgentGroupSessionApi session) {
        BaseAgent agent = agentGroup.getAgents().get(agentId);
        if (agent == null) {
            Loggers.MULTI_AGENT.warn("BaseGroupController: Agent {} not found in group", agentId);
            return null;
        }

        Map<String, Object> inputs = new HashMap<>();
        Object queryPayload = event.getQueryPayload() != null ? event.getQueryPayload() : event.getQuery();
        inputs.put("query", queryPayload);
        inputs.put("conversation_id", event.getConversationId());
        inputs.put("user_id", event.getUserId());

        Loggers.MULTI_AGENT.info("BaseGroupController: Streaming message to agent {}", agentId);

        AgentSessionApi childSession = AgentSessionApi.create(event.getConversationId(), null, agent.getCard());
        try {
            childSession.getInner().state().setState(session.getInner().state().getState());
            childSession.preRun(inputs);

            List<Object> chunks = new ArrayList<>();
            Iterator<Object> streamIter = agent.stream(inputs, childSession, null);
            while (streamIter.hasNext()) {
                Object chunk = streamIter.next();
                chunks.add(chunk);
                session.getInner().streamWriterManager().getStreamEmitter().emit(chunk);
            }
            session.getInner().state().setState(childSession.getInner().state().getState());

            if (!chunks.isEmpty()) {
                // Check for interaction interrupt
                boolean hasInteraction = chunks.stream()
                        .anyMatch(c -> c instanceof OutputSchema os && Constant.INTERACTION.equals(os.getType()));
                if (hasInteraction) {
                    return chunks;
                }

                // Normal case: return last result
                Object finalResult = chunks.get(chunks.size() - 1);
                if (finalResult instanceof OutputSchema os) {
                    return os.getPayload();
                }
                return finalResult;
            }

            return Map.of("output", "processed");
        } catch (Exception e) {
            Loggers.MULTI_AGENT.error("BaseGroupController: Failed to stream agent {}: {}", agentId, e.getMessage());
            throw e;
        } finally {
            childSession.postRun();
        }
    }

    /**
     * publish.
     * 
     * @param event event
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public List<Object> publish(GroupEvent event, AgentGroupSessionApi session) {
        String messageType = event.getCustomEventType();

        if (messageType == null || messageType.isEmpty()) {
            Loggers.MULTI_AGENT.warn("BaseGroupController: Message has no message_type, cannot route to subscribers");
            return List.of();
        }

        List<String> subscribers = this.subscriptions.getOrDefault(messageType, List.of());

        if (subscribers.isEmpty()) {
            Loggers.MULTI_AGENT.info("BaseGroupController: No subscribers for message_type={}", messageType);
            return List.of();
        }

        Loggers.MULTI_AGENT.info("BaseGroupController: Publishing message to {} subscribers for message_type={}",
                subscribers.size(), messageType);

        // Concurrently call all subscribers on the shared background executor.
        List<CompletableFuture<Object>> futures = subscribers.stream()
                .map(agentId -> OpenJiuwenExecutors.supplyBackgroundAsync(() -> sendToAgent(event, agentId, session)))
                .toList();

        List<Object> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.add(futures.get(i).join());
            } catch (Exception e) {
                Loggers.MULTI_AGENT.error("BaseGroupController: Subscriber {} raised exception: {}", subscribers.get(i),
                        e.getMessage());
                results.add(e);
            }
        }

        return results;
    }

    /**
     * Stop group controller — clean up all resources.
     * 
     * @since 0.1.7
     */
    public void stop() {
        Loggers.MULTI_AGENT.info("BaseGroupController: Stopping");
        msgQueue.stop();
        queueStarted = false;
    }

    /**
     * getAgentGroup.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LegacyBaseGroup getAgentGroup() {
        return agentGroup;
    }

    /**
     * getSubscriptionsMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<String>> getSubscriptionsMap() {
        return subscriptions;
    }
}
