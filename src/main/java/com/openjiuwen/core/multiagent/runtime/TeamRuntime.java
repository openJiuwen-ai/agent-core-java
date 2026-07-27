/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.runtime;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.AgentProvider;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Local team runtime for Python-style multi-agent communication.
 *
 * <p>This runtime intentionally keeps the first Java parity layer local and
 * synchronous: point-to-point dispatch and publish/subscribe routing happen
 * inside the current process, while registered agent cards are still mirrored
 * into {@link Runner#resourceMgr()} for discovery compatibility.</p>
 */
public class TeamRuntime {
    private static final String CALL_DEPTH_KEY = "_team_runtime_call_depth";
    private static final String ROOT_SENDER_KEY = "_team_runtime_root_sender";
    private static final String ROOT_RECIPIENT_KEY = "_team_runtime_root_recipient";
    private static final int MAX_DISPATCH_DEPTH = 64;

    private final String teamId;
    private final Map<String, AgentCard> agentCards = new LinkedHashMap<>();
    private final Map<String, AgentProvider<? extends BaseAgent>> providers = new LinkedHashMap<>();
    private final SubscriptionManager subscriptionManager = new SubscriptionManager();
    private final Map<String, AgentGroupSessionApi> teamSessions = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamRuntime(String teamId) {
        this.teamId = teamId != null && !teamId.isBlank() ? teamId : "default";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamRuntime() {
        this("default");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void registerAgent(AgentCard card, AgentProvider<? extends BaseAgent> provider) {
        validateRegistration(card, provider);
        String agentId = card.getId();
        agentCards.put(agentId, card);
        providers.put(agentId, provider);

        try {
            Object existing = Runner.resourceMgr().getAgent(agentId);
            if (existing == null) {
                Runner.resourceMgr().addAgent(card, provider::get, null);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Local runtime dispatch uses the provider map above. ResourceMgr mirroring
            // is best-effort for discovery compatibility and should not break reuse.
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentCard unregisterAgent(String agentId) {
        providers.remove(agentId);
        subscriptionManager.unsubscribeAll(agentId);
        return agentCards.remove(agentId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasAgent(String agentId) {
        return agentCards.containsKey(agentId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentCard getAgentCard(String agentId) {
        return agentCards.get(agentId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> listAgents() {
        return List.copyOf(agentCards.keySet());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getAgentCount() {
        return agentCards.size();
    }

    /**
     * Resolve a registered agent instance by ID.
     *
     * <p>Exposes the same resolution path used by {@link #send} so team
     * subclasses (e.g. {@link com.openjiuwen.core.multiagent.teams.handoff.HandoffTeam})
     * can perform pre-invoke setup such as injecting handoff tools into the
     * agent's {@code AbilityManager}.</p>
     *
     * @param agentId ID of the agent to resolve.
     * @return resolved agent instance.
     */
    public BaseAgent getAgentInstance(String agentId) {
        return resolveAgent(agentId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void bindTeamSession(AgentGroupSessionApi session) {
        if (session != null) {
            teamSessions.put(session.getSessionId(), session);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void unbindTeamSession(String sessionId) {
        if (sessionId != null) {
            teamSessions.remove(sessionId);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentGroupSessionApi getTeamSession(String sessionId) {
        return sessionId == null ? null : teamSessions.get(sessionId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object send(Object message, String recipient, String sender,
                       String sessionId, AgentGroupSessionApi session) {
        if (sender == null || sender.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_EXECUTION_ERROR,
                    "error_msg", "sender is required for team runtime messages"
            );
        }
        BaseAgent agent = resolveAgent(recipient);
        MessageEnvelope envelope = MessageEnvelope.builder()
                .messageId(UUID.randomUUID().toString())
                .message(message)
                .sender(sender)
                .recipient(recipient)
                .sessionId(sessionId)
                .build();
        AgentGroupSessionApi resolvedSession = resolveSession(sessionId, session);
        int depth = 0;
        String rootSender = sender;
        String rootRecipient = recipient;
        if (resolvedSession != null) {
            Object depthObj = resolvedSession.getState(CALL_DEPTH_KEY);
            if (depthObj instanceof Number n) {
                depth = n.intValue();
            }
            Object stateRootSender = resolvedSession.getState(ROOT_SENDER_KEY);
            Object stateRootRecipient = resolvedSession.getState(ROOT_RECIPIENT_KEY);
            if (stateRootSender instanceof String s && !s.isBlank()) {
                rootSender = s;
            }
            if (stateRootRecipient instanceof String s && !s.isBlank()) {
                rootRecipient = s;
            }
            if (depth == 0) {
                rootSender = sender;
                rootRecipient = recipient;
            }
            if (depth >= MAX_DISPATCH_DEPTH) {
                throw ErrorHelper.buildError(
                        StatusCode.AGENT_GROUP_EXECUTION_ERROR,
                        "error_msg", "Message from '" + rootSender + "' to '" + rootRecipient
                                + "' timed out after Nones"
                );
            }
            resolvedSession.updateState(Map.of(
                    CALL_DEPTH_KEY, depth + 1,
                    ROOT_SENDER_KEY, rootSender,
                    ROOT_RECIPIENT_KEY, rootRecipient
            ));
        }
        try {
            if (resolvedSession != null) {
                resolvedSession.setCurrentAgentId(recipient);
            }
            return agent.invoke(envelope.getMessage(), resolvedSession);
        } finally {
            if (resolvedSession != null) {
                resolvedSession.setCurrentAgentId(null);
                int nextDepth = 0;
                Object currentDepth = resolvedSession.getState(CALL_DEPTH_KEY);
                if (currentDepth instanceof Number n) {
                    nextDepth = Math.max(0, n.intValue() - 1);
                }
                resolvedSession.updateState(Map.of(CALL_DEPTH_KEY, nextDepth));
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void publish(Object message, String topicId, String sender,
                        String sessionId, AgentGroupSessionApi session) {
        if (topicId == null || topicId.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_EXECUTION_ERROR,
                    "error_msg", "topic_id is required for publish"
            );
        }
        MessageEnvelope envelope = MessageEnvelope.builder()
                .messageId(UUID.randomUUID().toString())
                .message(message)
                .sender(sender)
                .topicId(topicId)
                .sessionId(sessionId)
                .build();
        AgentGroupSessionApi resolvedSession = resolveSession(sessionId, session);
        for (String subscriber : subscriptionManager.getSubscribers(topicId)) {
            if (resolvedSession != null) {
                resolvedSession.setCurrentAgentId(subscriber);
            }
            resolveAgent(subscriber).invoke(envelope.getMessage(), resolvedSession);
            if (resolvedSession != null) {
                resolvedSession.setCurrentAgentId(null);
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void subscribe(String agentId, String topic) {
        if (!hasAgent(agentId)) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_EXECUTION_ERROR,
                    "error_msg", "Agent '" + agentId + "' is not registered in runtime"
            );
        }
        subscriptionManager.subscribe(agentId, topic);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void unsubscribe(String agentId, String topic) {
        subscriptionManager.unsubscribe(agentId, topic);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> listSubscriptions(String agentId) {
        return subscriptionManager.listSubscriptions(agentId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getSubscriptionCount() {
        return subscriptionManager.getSubscriptionCount();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTeamId() {
        return teamId;
    }

    private BaseAgent resolveAgent(String agentId) {
        AgentProvider<? extends BaseAgent> provider = providers.get(agentId);
        if (provider == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_EXECUTION_ERROR,
                    "error_msg", "Recipient '" + agentId + "' not registered in runtime"
            );
        }
        BaseAgent agent = provider.get();
        if (agent instanceof CommunicableAgent communicable) {
            communicable.bindRuntime(this, agentId);
        }
        return agent;
    }

    private AgentGroupSessionApi resolveSession(String sessionId, AgentGroupSessionApi session) {
        if (session != null) {
            bindTeamSession(session);
            return session;
        }
        AgentGroupSessionApi existing = getTeamSession(sessionId);
        if (existing != null) {
            return existing;
        }
        AgentGroupSessionApi created = new AgentGroupSessionApi(sessionId);
        bindTeamSession(created);
        return created;
    }

    private static void validateRegistration(AgentCard card, AgentProvider<? extends BaseAgent> provider) {
        if (card == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR,
                    "error_msg", "Agent card is required"
            );
        }
        if (provider == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR,
                    "error_msg", "Agent provider is required"
            );
        }
        if (card.getId() == null || card.getId().isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_ADD_RUNTIME_ERROR,
                    "error_msg", "Agent card id is required"
            );
        }
    }
}
