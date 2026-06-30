/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackFirer;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.GitHubTree;
import com.openjiuwen.core.singleagent.skills.SkillUtil;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Single Agent Base Class.
 *
 * <p>Design principles:
 * <ul>
 *   <li>Card is required (defines what the Agent is)</li>
 *   <li>Config is optional (defines how the Agent runs)</li>
 *   <li>All configuration methods support chaining</li>
 * </ul>
 */
public abstract class BaseAgent implements AgentCallbackFirer {

    private final AgentCard card;
    private final AbilityManager abilityManager;
    private final AgentCallbackManager agentCallbackManager;
    private SkillUtil skillUtil;

    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseAgent(AgentCard card) {
        this.card = card;
        this.abilityManager = new AbilityManager();
        this.agentCallbackManager = new AgentCallbackManager(card.getId());
        lazyInitSkill();
    }

    /**
     * Lazy init SkillUtil.
     */
    protected void lazyInitSkill() {
        Object config = getConfig();
        if (config == null) {
            return;
        }
        String sysOperationId = getSysOperationId(config);
        if (sysOperationId == null) {
            return;
        }
        if (skillUtil == null) {
            skillUtil = new SkillUtil(sysOperationId);
        } else {
            skillUtil.setSysOperationId(sysOperationId);
        }
    }

    /**
     * Extract sys_operation_id from config via reflection. Override for concrete types.
     */
    protected String getSysOperationId(Object config) {
        try {
            var method = config.getClass().getMethod("getSysOperationId");
            return (String) method.invoke(config);
        } catch (Exception e) {
            return null;
        }
    }

    // ========== Configuration Interface ==========

    /**
     * Set configuration.
     *
     * @param config the configuration object
     * @return self for chaining
     */
    public abstract BaseAgent configure(Object config);

    /**
     * Get current configuration.
     *
     * @return current config, or null
     */
    public abstract Object getConfig();

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentCard getCard() {
        return card;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentCallbackManager getAgentCallbackManager() {
        return agentCallbackManager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillUtil getSkillUtil() {
        return skillUtil;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected void setSkillUtil(SkillUtil skillUtil) {
        this.skillUtil = skillUtil;
    }

    /**
     * Register a skill from a local path.
     *
     * @param skillPath path to the skill directory or file (String or List of Strings)
     */
    public void registerSkill(Object skillPath) {
        lazyInitSkill();
        if (skillUtil != null) {
            skillUtil.registerSkills(skillPath, this);
        }
    }

    /**
     * Register remote skills from GitHub.
     *
     * @param skillsDir  local directory for skills
     * @param githubTree the GitHub tree reference
     * @param token      GitHub API token (optional, pass empty string if not needed)
     */
    public void registerRemoteSkills(String skillsDir, GitHubTree githubTree, String token) {
        lazyInitSkill();
        if (skillUtil != null) {
            skillUtil.registerRemoteSkills(skillsDir, githubTree, token);
        }
    }

    /**
     * Register a callback for an event.
     *
     * @param event    event type
     * @param callback callback function
     * @param priority execution priority
     * @return self for chaining
     */
    public BaseAgent registerCallback(AgentCallbackEvent event, Consumer<AgentCallbackContext> callback, int priority) {
        agentCallbackManager.registerCallback(event, callback, priority);
        return this;
    }

    /**
     * Register a rail instance.
     *
     * @param rail the AgentRail to register
     * @return self for chaining
     */
    public BaseAgent registerRail(AgentRail rail) {
        agentCallbackManager.registerRail(rail, this);
        return this;
    }

    /**
     * Unregister a rail instance.
     *
     * @param rail the AgentRail to unregister
     * @return self for chaining
     */
    public BaseAgent unregisterRail(AgentRail rail) {
        agentCallbackManager.unregisterRail(rail, this);
        return this;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void fireCallbackEvent(AgentCallbackEvent event, AgentCallbackContext ctx) {
        agentCallbackManager.execute(event, ctx);
    }

    /**
     * Batch execution.
     *
     * @param inputs  agent input
     * @param session session object
     * @return agent output result
     */
    public abstract Object invoke(Object inputs, Session session);

    /**
     * Stream execution.
     *
     * @param inputs      agent input
     * @param session     session object
     * @param streamModes stream output modes
     * @return iterator of stream output
     */
    public abstract Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes);
}
