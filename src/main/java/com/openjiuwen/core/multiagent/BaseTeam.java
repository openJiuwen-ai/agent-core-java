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
 * <p>
 * The older Java {@link BaseGroup} remains available for legacy callers.
 * This type adds the Python naming surface plus a local {@link TeamRuntime}
 * for point-to-point and publish/subscribe dispatch.
 * </p>
 * 
 * @since 0.1.7
 */
public abstract class BaseTeam extends BaseGroup {
    private TeamConfig teamConfig;
    private final TeamRuntime runtime;

    /**
     * BaseTeam.
     * 
     * @param card card
     * @param config config
     * @param runtime runtime
     * @since 0.1.7
     */
    protected BaseTeam(TeamCard card, TeamConfig config, TeamRuntime runtime) {
        super(card, config);
        this.teamConfig = config != null ? config : new TeamConfig();
        this.runtime = runtime != null ? runtime : new TeamRuntime(card.getId());
    }

    /**
     * BaseTeam.
     * 
     * @param card card
     * @param config config
     * @since 0.1.7
     */
    protected BaseTeam(TeamCard card, TeamConfig config) {
        this(card, config, null);
    }

    /**
     * BaseTeam.
     * 
     * @param card card
     * @since 0.1.7
     */
    protected BaseTeam(TeamCard card) {
        this(card, null, null);
    }

    /**
     * configure.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    @Override
    public BaseTeam configure(GroupConfig config) {
        this.teamConfig = config instanceof TeamConfig team ? team : copyConfig(config);
        super.configure(this.teamConfig);
        return this;
    }

    /**
     * configure.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public BaseTeam configure(TeamConfig config) {
        this.teamConfig = config != null ? config : new TeamConfig();
        super.configure(this.teamConfig);
        return this;
    }

    /**
     * addAgent.
     * 
     * @param card card
     * @param provider provider
     * @return the result
     * @since 0.1.7
     */
    public BaseTeam addAgent(AgentCard card, AgentProvider<? extends BaseAgent> provider) {
        runtime.registerAgent(card, provider);
        getTeamCard().getAgentCards().removeIf(candidate -> candidate.getId().equals(card.getId()));
        getTeamCard().getAgentCards().add(card);
        return this;
    }

    /**
     * removeAgent.
     * 
     * @param agentId agentId
     * @return the result
     * @since 0.1.7
     */
    @Override
    public BaseTeam removeAgent(String agentId) {
        runtime.unregisterAgent(agentId);
        getTeamCard().getAgentCards().removeIf(candidate -> candidate.getId().equals(agentId));
        return this;
    }

    /**
     * getAgentCount.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int getAgentCount() {
        return runtime.getAgentCount();
    }

    /**
     * listAgents.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> listAgents() {
        return runtime.listAgents();
    }

    /**
     * send.
     * 
     * @param message message
     * @param recipient recipient
     * @param sender sender
     * @param sessionId sessionId
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public Object send(Object message, String recipient, String sender, String sessionId,
            AgentGroupSessionApi session) {
        return runtime.send(message, recipient, sender, sessionId, session);
    }

    /**
     * publish.
     * 
     * @param message message
     * @param topicId topicId
     * @param sender sender
     * @param sessionId sessionId
     * @param session session
     * @since 0.1.7
     */
    public void publish(Object message, String topicId, String sender, String sessionId, AgentGroupSessionApi session) {
        runtime.publish(message, topicId, sender, sessionId, session);
    }

    /**
     * subscribe.
     * 
     * @param agentId agentId
     * @param topic topic
     * @since 0.1.7
     */
    public void subscribe(String agentId, String topic) {
        runtime.subscribe(agentId, topic);
    }

    /**
     * unsubscribe.
     * 
     * @param agentId agentId
     * @param topic topic
     * @since 0.1.7
     */
    public void unsubscribe(String agentId, String topic) {
        runtime.unsubscribe(agentId, topic);
    }

    /**
     * getRuntime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamRuntime getRuntime() {
        return runtime;
    }

    /**
     * getTeamConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamConfig getTeamConfig() {
        return teamConfig;
    }

    /**
     * getTeamCard.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamCard getTeamCard() {
        if (getCard() instanceof TeamCard teamCard) {
            return teamCard;
        }
        return TeamCard.class.cast(getCard());
    }

    /**
     * copyConfig.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
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
