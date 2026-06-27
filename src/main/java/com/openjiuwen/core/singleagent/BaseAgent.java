/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.rail.AgentCallback;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.GitHubTree;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.singleagent.skills.SkillToolRegistry;
import com.openjiuwen.core.singleagent.skills.SkillUtil;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Base class for current single-agent implementations.
 *
 * <p>Mirrors Python's {@code BaseAgent} in
 * {@code openjiuwen/core/single_agent/base.py}.</p>
 */
public abstract class BaseAgent {
    public static final String ACTIVE_SKILL_NAMES_STATE_KEY = "active_skill_names";

    private final AgentCard card;
    private AbilityManager abilityManager;
    private final AgentCallbackManager agentCallbackManager;
    private final SkillToolRegistry skillToolRegistry;
    private SkillUtil skillUtil;
    private Object config;

    protected BaseAgent(AgentCard card) {
        this.card = Objects.requireNonNull(card, "card");
        this.abilityManager = new AbilityManager();
        this.agentCallbackManager = new AgentCallbackManager(card.getId());
        this.skillToolRegistry = new SkillToolRegistry();
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

    public CompletionStage<Boolean> registerSkill(String skillPath, boolean useMetadataName) {
        return registerSkill(List.of(skillPath), useMetadataName);
    }

    public CompletionStage<Boolean> registerSkill(List<String> skillPaths) {
        return registerSkill(skillPaths, false);
    }

    public CompletionStage<Boolean> registerSkill(List<String> skillPaths, boolean useMetadataName) {
        lazyInitSkill();
        if (skillUtil == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("sys_operation_id is required before registering skills")
            );
        }
        try {
            return CompletableFuture.completedFuture(skillUtil.registerSkills(skillPaths, this, null, useMetadataName));
        } catch (IOException | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletionStage<Boolean> register_skill(List<String> skillPaths) {
        return registerSkill(skillPaths);
    }

    public CompletionStage<Boolean> registerSkillTools(SkillToolBinding binding) {
        return registerSkillTools(List.of(binding));
    }

    public CompletionStage<Boolean> registerSkillTools(List<SkillToolBinding> bindings) {
        try {
            skillToolRegistry.registerAll(bindings);
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletionStage<Boolean> register_skill_tools(List<SkillToolBinding> bindings) {
        return registerSkillTools(bindings);
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

    public BaseAgent activateSkill(String skillName, AgentSessionApi session) {
        String normalizedSkillName = normalizeSkillName(skillName);
        if (normalizedSkillName == null) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        requireRegisteredSkill(normalizedSkillName);
        requireSession(session);
        LinkedHashSet<String> activeSkillNames = new LinkedHashSet<>(getActiveSkillNames(session));
        activeSkillNames.add(normalizedSkillName);
        List<String> updated = List.copyOf(activeSkillNames);
        validateEffectiveToolNames(updated);
        session.updateState(Map.of(ACTIVE_SKILL_NAMES_STATE_KEY, updated));
        return this;
    }

    public BaseAgent deactivateSkill(String skillName, AgentSessionApi session) {
        String normalizedSkillName = normalizeSkillName(skillName);
        if (normalizedSkillName == null) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        requireSession(session);
        LinkedHashSet<String> activeSkillNames = new LinkedHashSet<>(getActiveSkillNames(session));
        activeSkillNames.remove(normalizedSkillName);
        session.updateState(Map.of(ACTIVE_SKILL_NAMES_STATE_KEY, List.copyOf(activeSkillNames)));
        return this;
    }

    public List<String> getActiveSkillNames(AgentSessionApi session) {
        if (session == null) {
            return List.of();
        }
        return normalizeActiveSkillNames(session.getState(ACTIVE_SKILL_NAMES_STATE_KEY));
    }

    public List<ToolInfo> listEffectiveToolInfo(AgentSessionApi session) {
        return listEffectiveToolInfo(getActiveSkillNames(session));
    }

    public Optional<Tool> findActiveSkillTool(String toolName, AgentSessionApi session) {
        return skillToolRegistry.findToolForActiveSkills(toolName, getActiveSkillNames(session));
    }

    public Optional<String> findSkillNameByDocumentPath(String documentPath) {
        if (documentPath == null || documentPath.isBlank() || skillUtil == null) {
            return Optional.empty();
        }
        Path normalizedDocumentPath = normalizePath(documentPath).orElse(null);
        if (normalizedDocumentPath == null) {
            return Optional.empty();
        }
        for (Skill skill : skillUtil.getSkillManager().getAll()) {
            Path skillDirectory = normalizePath(skill.getDirectory()).orElse(null);
            if (skillDirectory == null) {
                continue;
            }
            if (normalizedDocumentPath.equals(skillDirectory.resolve("SKILL.md").normalize())
                    || normalizedDocumentPath.equals(skillDirectory.resolve("Skill.md").normalize())
                    || normalizedDocumentPath.equals(skillDirectory.resolve("skill.md").normalize())) {
                return Optional.ofNullable(skill.getName()).filter(name -> !name.isBlank());
            }
        }
        return Optional.empty();
    }

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

    private List<ToolInfo> listEffectiveToolInfo(List<String> activeSkillNames) {
        Map<String, ToolInfo> merged = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        for (ToolInfo toolInfo : abilityManager.listToolInfo()) {
            addToolInfo(merged, sources, toolInfo, "global ability");
        }
        for (Map.Entry<String, List<Tool>> entry : skillToolRegistry.listToolsByActiveSkill(activeSkillNames)
                .entrySet()) {
            String source = "active skill '" + entry.getKey() + "'";
            for (Tool tool : entry.getValue()) {
                if (tool == null || tool.getCard() == null) {
                    continue;
                }
                addToolInfo(merged, sources, tool.getCard().toolInfo(), source);
            }
        }
        return List.copyOf(merged.values());
    }

    private void validateEffectiveToolNames(List<String> activeSkillNames) {
        listEffectiveToolInfo(activeSkillNames);
    }

    private void requireRegisteredSkill(String skillName) {
        if (skillUtil == null || !skillUtil.getSkillManager().has(skillName)) {
            throw new IllegalArgumentException("Skill is not registered: " + skillName);
        }
    }

    private static void addToolInfo(Map<String, ToolInfo> merged,
                                    Map<String, String> sources,
                                    ToolInfo toolInfo,
                                    String source) {
        if (toolInfo == null || toolInfo.getName() == null || toolInfo.getName().isBlank()) {
            return;
        }
        String toolName = toolInfo.getName();
        if (merged.containsKey(toolName)) {
            throw new IllegalStateException("Duplicate effective tool name '" + toolName + "' from " + source
                    + "; conflicts with " + sources.get(toolName));
        }
        merged.put(toolName, toolInfo);
        sources.put(toolName, source);
    }

    private static void requireSession(AgentSessionApi session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
    }

    private static Optional<Path> normalizePath(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(String.valueOf(value)).toAbsolutePath().normalize());
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    private static List<String> normalizeActiveSkillNames(Object value) {
        if (value == null) {
            return List.of();
        }
        Collection<?> values;
        if (value instanceof Collection<?> collection) {
            values = collection;
        } else if (value instanceof String text) {
            values = List.of(text);
        } else if (value instanceof String[] array) {
            values = List.of(array);
        } else {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object item : values) {
            String name = normalizeSkillName(item == null ? null : String.valueOf(item));
            if (name != null) {
                normalized.add(name);
            }
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private static String normalizeSkillName(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return null;
        }
        return skillName;
    }
}
