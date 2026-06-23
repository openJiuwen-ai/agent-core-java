/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.rail.AgentCallback;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.GitHubTree;
import com.openjiuwen.core.singleagent.skills.SkillUtil;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Base class for current single-agent implementations.
 *
 * <p>Mirrors Python's {@code BaseAgent} in
 * {@code openjiuwen/core/single_agent/base.py}.</p>
 */
public abstract class BaseAgent {
    private final AgentCard card;
    private AbilityManager abilityManager;
    private final AgentCallbackManager agentCallbackManager;
    private SkillUtil skillUtil;
    private Object config;

    protected BaseAgent(AgentCard card) {
        this.card = Objects.requireNonNull(card, "card");
        this.abilityManager = new AbilityManager();
        this.agentCallbackManager = new AgentCallbackManager(card.getId());
        lazyInitSkill();
    }

    public void lazyInitSkill() {
        String sysOperationId = readStringProperty(config, "getSysOperationId", "get_sys_operation_id");
        if (sysOperationId == null || sysOperationId.isBlank()) {
            return;
        }
        if (skillUtil == null) {
            skillUtil = createSkillUtil(sysOperationId);
        } else {
            skillUtil.setSysOperationId(sysOperationId);
        }
    }

    protected SkillUtil createSkillUtil(String sysOperationId) {
        return new SkillUtil(sysOperationId);
    }

    public abstract BaseAgent configure(Object config);

    public CompletionStage<Boolean> registerSkill(String skillPath) {
        return registerSkill(List.of(skillPath));
    }

    public CompletionStage<Boolean> registerSkill(List<String> skillPaths) {
        lazyInitSkill();
        if (skillUtil == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("sys_operation_id is required before registering skills")
            );
        }
        try {
            return CompletableFuture.completedFuture(skillUtil.registerSkills(skillPaths, this, null));
        } catch (IOException e) {
            CompletableFuture<Boolean> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    public CompletionStage<Boolean> register_skill(List<String> skillPaths) {
        return registerSkill(skillPaths);
    }

    public CompletionStage<List<java.nio.file.Path>> registerRemoteSkills(String skillsDir,
                                                                          GitHubTree githubTree,
                                                                          String token) {
        lazyInitSkill();
        if (skillUtil == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(skillUtil.registerRemoteSkills(skillsDir, githubTree, token));
    }

    public CompletionStage<List<java.nio.file.Path>> register_remote_skills(String skillsDir,
                                                                            GitHubTree githubTree,
                                                                            String token) {
        return registerRemoteSkills(skillsDir, githubTree, token);
    }

    public CompletionStage<BaseAgent> registerCallback(AgentCallbackEvent event,
                                                       AgentCallback callback,
                                                       int priority) {
        return agentCallbackManager.registerCallback(event, callback, priority).thenApply(ignored -> this);
    }

    public CompletionStage<BaseAgent> register_callback(AgentCallbackEvent event,
                                                        AgentCallback callback,
                                                        int priority) {
        return registerCallback(event, callback, priority);
    }

    public CompletionStage<BaseAgent> registerRail(AgentRail rail) {
        if (rail != null) {
            rail.init(this);
        }
        return agentCallbackManager.registerRail(rail, this).thenApply(ignored -> this);
    }

    public CompletionStage<BaseAgent> register_rail(AgentRail rail) {
        return registerRail(rail);
    }

    public CompletionStage<BaseAgent> unregisterRail(AgentRail rail) {
        return agentCallbackManager.unregisterRail(rail, this)
                .thenApply(ignored -> {
                    if (rail != null) {
                        rail.uninit(this);
                    }
                    return this;
                });
    }

    public CompletionStage<BaseAgent> unregister_rail(AgentRail rail) {
        return unregisterRail(rail);
    }

    public CompletionStage<Void> executeCallbacks(AgentCallbackEvent event,
                                                  Object inputs,
                                                  AgentSessionApi session,
                                                  ModelContext context) {
        AgentCallbackContext callbackContext = new AgentCallbackContext(this);
        callbackContext.setEvent(event);
        callbackContext.setInputs(inputs);
        callbackContext.setSession(session);
        callbackContext.setContext(context);
        return agentCallbackManager.execute(event, callbackContext).thenApply(ignored -> null);
    }

    public CompletionStage<Void> _execute_callbacks(AgentCallbackEvent event,
                                                    Map<String, Object> inputs,
                                                    AgentSessionApi session,
                                                    ModelContext context) {
        return executeCallbacks(event, inputs, session, context);
    }

    public abstract CompletionStage<Object> invoke(Object inputs, AgentSessionApi session);

    public abstract Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes);

    public AgentCard getCard() {
        return card;
    }

    public Object getConfig() {
        return config;
    }

    protected void setConfig(Object config) {
        this.config = config;
        lazyInitSkill();
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public AbilityManager get_ability_manager() {
        return abilityManager;
    }

    public void setAbilityManager(AbilityManager abilityManager) {
        this.abilityManager = abilityManager == null ? new AbilityManager() : abilityManager;
    }

    public AgentCallbackManager getAgentCallbackManager() {
        return agentCallbackManager;
    }

    public AgentCallbackManager get_agent_callback_manager() {
        return agentCallbackManager;
    }

    public SkillUtil getSkillUtil() {
        return skillUtil;
    }

    private static String readStringProperty(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                return value == null ? null : String.valueOf(value);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
