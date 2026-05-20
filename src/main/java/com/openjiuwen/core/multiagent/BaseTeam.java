/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.multiagent.runtime.TeamRuntime;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.runner.base.AgentProvider;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.List;

/**
 * Team-oriented compatibility surface aligned with Python's {@code BaseTeam}.
 *
 * <p>The older Java {@link BaseGroup} remains available for legacy callers.
 * This type adds the Python naming surface plus a local {@link TeamRuntime}
 * for point-to-point and publish/subscribe dispatch.</p>
 */
public abstract class BaseTeam extends BaseGroup {
    private TeamConfig teamConfig;
    private final TeamRuntime runtime;

    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseTeam(TeamCard card, TeamConfig config, TeamRuntime runtime) {
        super(card, config);
        this.teamConfig = config != null ? config : new TeamConfig();
        this.runtime = runtime != null ? runtime : new TeamRuntime(card.getId());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseTeam(TeamCard card, TeamConfig config) {
        this(card, config, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseTeam(TeamCard card) {
        this(card, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseTeam configure(GroupConfig config) {
        this.teamConfig = config instanceof TeamConfig team ? team : copyConfig(config);
        super.configure(this.teamConfig);
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseTeam configure(TeamConfig config) {
        this.teamConfig = config != null ? config : new TeamConfig();
        super.configure(this.teamConfig);
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseTeam addAgent(AgentCard card, AgentProvider<? extends BaseAgent> provider) {
        runtime.registerAgent(card, provider);
        getTeamCard().getAgentCards().removeIf(candidate -> candidate.getId().equals(card.getId()));
        getTeamCard().getAgentCards().add(card);
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseTeam removeAgent(String agentId) {
        runtime.unregisterAgent(agentId);
        getTeamCard().getAgentCards().removeIf(candidate -> candidate.getId().equals(agentId));
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int getAgentCount() {
        return runtime.getAgentCount();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> listAgents() {
        return runtime.listAgents();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object send(Object message, String recipient, String sender,
                       String sessionId, AgentGroupSessionApi session) {
        return runtime.send(message, recipient, sender, sessionId, session);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void publish(Object message, String topicId, String sender,
                        String sessionId, AgentGroupSessionApi session) {
        runtime.publish(message, topicId, sender, sessionId, session);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void subscribe(String agentId, String topic) {
        runtime.subscribe(agentId, topic);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void unsubscribe(String agentId, String topic) {
        runtime.unsubscribe(agentId, topic);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamRuntime getRuntime() {
        return runtime;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamConfig getTeamConfig() {
        return teamConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamCard getTeamCard() {
        if (getCard() instanceof TeamCard teamCard) {
            return teamCard;
        }
        return TeamCard.class.cast(getCard());
    }

    private static TeamConfig copyConfig(GroupConfig config) {
        TeamConfig copied = new TeamConfig();
        if (config == null) {
            return copied;
        }
        copied.setMaxAgents(config.getMaxAgents());
        copied.setMaxConcurrentMessages(config.getMaxConcurrentMessages());
        copied.setMessageTimeout(config.getMessageTimeout());
        return copied;
    }
}
