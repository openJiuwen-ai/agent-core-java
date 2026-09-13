/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackFirer;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.GitHubTree;
import com.openjiuwen.core.singleagent.skills.SkillUtil;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Single Agent Base Class.
 * <p>
 * Design principles:
 * <ul>
 * <li>Card is required (defines what the Agent is)</li>
 * <li>Config is optional (defines how the Agent runs)</li>
 * <li>All configuration methods support chaining</li>
 * </ul>
 * 
 * @since 0.1.7
 */
public abstract class BaseAgent implements AgentCallbackFirer {
    private final AgentCard card;
    private final AbilityManager abilityManager;
    private final AgentCallbackManager agentCallbackManager;
    private volatile SkillUtil skillUtil;
    private final Object skillUtilLock = new Object();

    /**
     * BaseAgent.
     * 
     * @param card card
     * @since 0.1.7
     */
    protected BaseAgent(AgentCard card) {
        this.card = card;
        this.abilityManager = new AbilityManager();
        this.agentCallbackManager = new AgentCallbackManager(card.getId());
        lazyInitSkill();
    }

    /**
     * Lazy init SkillUtil.
     * 
     * @since 0.1.7
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
        SkillUtil local = skillUtil;
        if (local != null) {
            local.setSysOperationId(sysOperationId);
            return;
        }
        synchronized (skillUtilLock) {
            local = skillUtil;
            if (local == null) {
                skillUtil = new SkillUtil(sysOperationId);
            } else {
                local.setSysOperationId(sysOperationId);
            }
        }
    }

    /**
     * Extract sys_operation_id from config via reflection. Override for concrete types.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
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
     * @since 0.1.7
     */
    public abstract BaseAgent configure(Object config);

    /**
     * Get current configuration.
     * 
     * @return current config, or null
     * @since 0.1.7
     */
    public abstract Object getConfig();

    /**
     * getCard.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AgentCard getCard() {
        return card;
    }

    /**
     * getAbilityManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    /**
     * getAgentCallbackManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AgentCallbackManager getAgentCallbackManager() {
        return agentCallbackManager;
    }

    /**
     * getSkillUtil.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SkillUtil getSkillUtil() {
        return skillUtil;
    }

    /**
     * setSkillUtil.
     * 
     * @param skillUtil skillUtil
     * @since 0.1.7
     */
    protected void setSkillUtil(SkillUtil skillUtil) {
        this.skillUtil = skillUtil;
    }

    /**
     * Register a skill from a local path.
     * 
     * @param skillPath path to the skill directory or file (String or List of Strings)
     * @since 0.1.7
     */
    public void registerSkill(Object skillPath) {
        lazyInitSkill();
        if (skillUtil != null) {
            skillUtil.registerSkills(skillPath, this);
        }
    }

    /**
     * Register a skill only when its real path is within a trusted skills root.
     *
     * @param skillPath path to the skill directory or file (String or List of Strings)
     * @param skillsRoot trusted root containing loadable skills
     * @since 0.1.13
     */
    public void registerSkill(Object skillPath, Path skillsRoot) {
        lazyInitSkill();
        if (skillUtil != null) {
            skillUtil.registerSkills(skillPath, skillsRoot, this);
        }
    }

    /**
     * Register remote skills from GitHub.
     * 
     * @param skillsDir local directory for skills
     * @param githubTree the GitHub tree reference
     * @param token GitHub API token (optional, pass empty string if not needed)
     * @since 0.1.7
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
     * @param event event type
     * @param callback callback function
     * @param priority execution priority
     * @return self for chaining
     * @since 0.1.7
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
     * @since 0.1.7
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
     * @since 0.1.7
     */
    public BaseAgent unregisterRail(AgentRail rail) {
        agentCallbackManager.unregisterRail(rail, this);
        return this;
    }

    /**
     * fireCallbackEvent.
     * 
     * @param event event
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void fireCallbackEvent(AgentCallbackEvent event, AgentCallbackContext ctx) {
        agentCallbackManager.execute(event, ctx);
    }

    /**
     * Batch execution.
     * 
     * @param inputs agent input
     * @param session session object
     * @return agent output result
     * @since 0.1.7
     */
    public abstract Object invoke(Object inputs, Session session);

    /**
     * Stream execution.
     * 
     * @param inputs agent input
     * @param session session object
     * @param streamModes stream output modes
     * @return iterator of stream output
     * @since 0.1.7
     */
    public abstract Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes);

    /**
     * Reactive version of {@link #invoke(Object, Session)}.
     * 
     * @param inputs agent inputs
     * @param session session context, nullable
     * @return Mono emitting the invocation result
     * @since 0.1.7
     */
    public Mono<Object> invokeAsync(Object inputs, Session session) {
        return ReactiveAdapters.fromCallable(() -> invoke(inputs, session));
    }

    /**
     * Reactive version of {@link #stream(Object, Session, List)}.
     * 
     * @param inputs agent inputs
     * @param session session context, nullable
     * @param streamModes stream output modes
     * @return Flux emitting stream chunks
     * @since 0.1.7
     */
    public Flux<Object> streamAsync(Object inputs, Session session, List<StreamMode> streamModes) {
        return ReactiveAdapters.fromAutoCloseableIterator(() -> stream(inputs, session, streamModes));
    }
}
