/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.team_runtime.MessageBusConfig;
import com.openjiuwen.core.multiagent.team_runtime.RuntimeConfig;
import com.openjiuwen.core.multiagent.team_runtime.TeamRuntime;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Abstract base class for agent teams.
 *
 * <p>Mirrors Python's {@code BaseTeam} in
 * {@code openjiuwen/core/multi_agent/team.py}.</p>
 */
public abstract class BaseTeam {

    private final TeamCard card;

    private TeamConfig config;

    private final String teamId;

    private final TeamRuntime runtime;

    public BaseTeam(TeamCard card) {
        this(card, null, null);
    }

    public BaseTeam(TeamCard card, TeamConfig config) {
        this(card, config, null);
    }

    public BaseTeam(TeamCard card, TeamConfig config, TeamRuntime runtime) {
        this.card = card;
        this.config = config != null ? config : createDefaultConfig();
        this.teamId = card.getName();
        this.runtime = runtime != null ? runtime : createDefaultRuntime();
    }

    protected TeamConfig createDefaultConfig() {
        return new TeamConfig();
    }

    protected TeamRuntime createDefaultRuntime() {
        MessageBusConfig messageBusConfig = new MessageBusConfig();
        messageBusConfig.setMaxQueueSize(config.getMaxConcurrentMessages());
        messageBusConfig.setProcessTimeout(config.getMessageTimeout());

        RuntimeConfig runtimeConfig = new RuntimeConfig();
        runtimeConfig.setTeamId(card.getId());
        runtimeConfig.setMessageBus(messageBusConfig);
        return new TeamRuntime(runtimeConfig);
    }

    public BaseTeam configure(TeamConfig config) {
        this.config = config;
        return this;
    }

    public BaseTeam addAgent(AgentCard agentCard, Function<AgentCard, ?> provider) {
        String agentId = agentCard.getId();
        if (runtime.hasAgent(agentId)) {
            Loggers.MULTI_AGENT.warning(
                    "[{}] Agent ID '{}' already exists in team '{}', skipping add",
                    getClass().getSimpleName(),
                    agentId,
                    teamId
            );
            return this;
        }

        if (runtime.getAgentCount() >= config.getMaxAgents()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_ADD_RUNTIME_ERROR,
                    "error_msg",
                    "Agent count exceeds max_agents (" + config.getMaxAgents() + ")"
            );
        }

        runtime.registerAgent(agentCard, provider);
        List<AgentCard> agentCards = new ArrayList<>(card.getAgentCards());
        agentCards.add(agentCard);
        card.setAgentCards(agentCards);
        Loggers.MULTI_AGENT.debug(
                "[{}] Added agent '{}' to team '{}'",
                getClass().getSimpleName(),
                agentId,
                teamId
        );
        return this;
    }

    public BaseTeam removeAgent(String agentId) {
        AgentCard removedCard = runtime.unregisterAgent(agentId);
        if (removedCard != null) {
            List<AgentCard> remainingCards = new ArrayList<>();
            for (AgentCard agentCard : card.getAgentCards()) {
                if (!removedCard.getId().equals(agentCard.getId())) {
                    remainingCards.add(agentCard);
                }
            }
            card.setAgentCards(remainingCards);
            Loggers.MULTI_AGENT.debug(
                    "[{}] Removed agent '{}' from team '{}'",
                    getClass().getSimpleName(),
                    agentId,
                    teamId
            );
        }
        return this;
    }

    public BaseTeam removeAgent(AgentCard agentCard) {
        return removeAgent(agentCard.getId());
    }

    public CompletionStage<Void> subscribe(String agentId, String topic) {
        return runtime.subscribe(agentId, topic);
    }

    public CompletionStage<Void> unsubscribe(String agentId, String topic) {
        return runtime.unsubscribe(agentId, topic);
    }

    public AgentCard getAgentCard(String agentId) {
        return runtime.getAgentCard(agentId);
    }

    public int getAgentCount() {
        return runtime.getAgentCount();
    }

    public List<String> listAgents() {
        return runtime.listAgents();
    }

    public CompletionStage<Object> send(
            Object message,
            String recipient,
            String sender,
            String sessionId,
            Double timeout
    ) {
        if (!runtime.hasAgent(sender)) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_AGENT_NOT_FOUND,
                    "error_msg",
                    "Sender '" + sender + "' not found in team '" + teamId + "'"
            );
        }
        if (!runtime.hasAgent(recipient)) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_AGENT_NOT_FOUND,
                    "error_msg",
                    "Recipient '" + recipient + "' not found in team '" + teamId + "'"
            );
        }
        return runtime.send(message, recipient, sender, sessionId, timeout);
    }

    public CompletionStage<Object> send(Object message, String recipient, String sender) {
        return send(message, recipient, sender, null, null);
    }

    public CompletionStage<Void> publish(Object message, String topicId, String sender, String sessionId) {
        if (!runtime.hasAgent(sender)) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_AGENT_NOT_FOUND,
                    "error_msg",
                    "Sender '" + sender + "' not found in team '" + teamId + "'"
            );
        }
        return runtime.publish(message, topicId, sender, sessionId);
    }

    public CompletionStage<Void> publish(Object message, String topicId, String sender) {
        return publish(message, topicId, sender, null);
    }

    public abstract CompletionStage<Object> invoke(Object message, AgentSessionApi session);

    public abstract Stream<Object> stream(Object message, AgentSessionApi session);

    public CompletionStage<Object> invoke(Object message) {
        return invoke(message, (AgentSessionApi) null);
    }

    public Stream<Object> stream(Object message) {
        return stream(message, (AgentSessionApi) null);
    }

    public TeamCard getCard() {
        return card;
    }

    public TeamConfig getConfig() {
        return config;
    }

    public String getTeamId() {
        return teamId;
    }

    public TeamRuntime getRuntime() {
        return runtime;
    }

    public BaseTeam addAgent(AgentCard agentCard, Supplier<? extends BaseAgent> provider) {
        java.util.Objects.requireNonNull(provider, "provider must not be null");
        return addAgent(agentCard, ignored -> provider.get());
    }
}
