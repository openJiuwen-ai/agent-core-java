/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.toolgroup.ToolAvailabilityChecker;
import com.openjiuwen.core.operator.toolgroup.ToolGroup;
import com.openjiuwen.core.operator.toolgroup.ToolGroupManager;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallback;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackFirer;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.GitHubTree;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillDocumentLoader;
import com.openjiuwen.core.singleagent.skills.SkillToolBinding;
import com.openjiuwen.core.singleagent.skills.SkillToolRegistry;
import com.openjiuwen.core.singleagent.skills.SkillUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Base class for current single-agent implementations.
 *
 * @since 0.1.17
 */
public abstract class BaseAgent implements AgentCallbackFirer {
    /**
     * Session state key holding the names of explicitly activated skills.
     */
    public static final String ACTIVE_SKILL_NAMES_STATE_KEY = "active_skill_names";

    /**
     * Session state key holding the names of explicitly activated tool groups.
     */
    public static final String ACTIVE_TOOL_GROUP_NAMES_STATE_KEY = "active_tool_group_names";

    private final AgentCard card;
    private AbilityManager abilityManager;
    private final AgentCallbackManager agentCallbackManager;
    private final SkillToolRegistry skillToolRegistry;
    private final ToolGroupManager toolGroupManager;
    private volatile SkillUtil skillUtil;
    private final Object skillUtilLock = new Object();
    private Object config;
    private volatile boolean isSkillMetaToolRegistered;

    /**
     * Owner token for global resource registration. Generated per instance at creation time and
     * propagated from the harness layer, it is the ownership key the agent uses when claiming
     * shared registry entries, so same-card instances stay distinguishable and one instance's
     * destroy only releases its own claims.
     *
     * @since 0.1.16
     */
    private volatile String ownerToken;

    protected BaseAgent(AgentCard card) {
        this.card = Objects.requireNonNull(card, "card");
        this.abilityManager = new AbilityManager(card.getId());
        this.agentCallbackManager = new AgentCallbackManager(card.getId());
        this.skillToolRegistry = new SkillToolRegistry();
        this.toolGroupManager = new ToolGroupManager();
        this.abilityManager.setToolAvailabilityChecker(createDefaultToolAvailabilityChecker());
        lazyInitSkill();
    }

    /**
     * throwIfAddResourceFailed.
     *
     * <p>Fails fast when a ResourceMgr add result is an error: rethrows the original runtime error,
     * or wraps a checked exception with the resource id for context.
     *
     * @param result add result returned by ResourceMgr
     * @param resourceId resource id used for error context
     * @since 0.1.16
     */
    protected static void throwIfAddResourceFailed(Result<?> result, String resourceId) {
        if (!result.isError()) {
            return;
        }
        Object error = result.getError();
        if (error instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (error instanceof Exception exception) {
            throw ErrorHelper.buildError(
                    StatusCode.RESOURCE_ADD_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("card", resourceId, "reason", String.valueOf(exception.getMessage())));
        }
        throw ErrorHelper.buildError(
                StatusCode.RESOURCE_ADD_ERROR,
                "card",
                resourceId,
                "reason",
                error != null ? String.valueOf(error) : "add resource failed");
    }

    private void registerSkillMetaTool() {
        ToolCard metaToolCard =
                ToolCard.builder()
                        .id("skill_tool")
                        .name("skill_tool")
                        .description(
                                "Read skill documents. Select a skill by skill_name, skill_id, or"
                                    + " skill_path; use relative_path='SKILL.md' for the main"
                                    + " document or a relative path for extra files.")
                        .inputParams(
                                Map.of(
                                        "type", "object",
                                        "properties",
                                                Map.of(
                                                        "skill_name",
                                                                Map.of(
                                                                        "type",
                                                                        "string",
                                                                        "description",
                                                                        "Name of the skill to"
                                                                            + " read"),
                                                        "skill_id",
                                                                Map.of(
                                                                        "type",
                                                                        "string",
                                                                        "description",
                                                                        "Optional stable Skill id."
                                                                            + " Prefer this to"
                                                                            + " select a"
                                                                            + " non-default Skill"
                                                                            + " layer."),
                                                        "skill_path",
                                                                Map.of(
                                                                        "type",
                                                                        "string",
                                                                        "description",
                                                                        "Optional trusted Skill"
                                                                            + " directory or"
                                                                            + " SKILL.md path. Must"
                                                                            + " resolve to a"
                                                                            + " registered"
                                                                            + " repository."),
                                                        "relative_path",
                                                                Map.of(
                                                                        "type",
                                                                        "string",
                                                                        "description",
                                                                        "Relative path within skill"
                                                                            + " directory (default:"
                                                                            + " SKILL.md)")),
                                        "required", List.of()))
                        .stateless(true)
                        .parallelSafe(true)
                        .idempotent(true)
                        .build();
        LocalFunction metaTool =
                new LocalFunction(
                        metaToolCard,
                        inputs -> {
                            Map<String, Object> args = new LinkedHashMap<>();
                            if (inputs != null) {
                                inputs.forEach(args::putIfAbsent);
                            }
                            Optional<String> skillName = stringArgument(args.get("skill_name"));
                            Optional<String> skillId = stringArgument(args.get("skill_id"));
                            Optional<String> skillPath = stringArgument(args.get("skill_path"));
                            String relativePath =
                                    String.valueOf(args.getOrDefault("relative_path", "SKILL.md"));
                            Optional<Skill> skill =
                                    resolveExplicitOrNamedSkill(skillName, skillId, skillPath);
                            if (skill.isEmpty()) {
                                return Map.of("error", "Skill not found: " + skillName);
                            }
                            Skill resolvedSkill = skill.get();
                            com.openjiuwen.core.singleagent.skills.SkillConfig skillConfig =
                                    skillUtil == null
                                            ? com.openjiuwen.core.singleagent.skills.SkillConfig
                                                    .fromEnvironment()
                                            : skillUtil.getSkillConfig();
                            java.util.Optional<String> content;
                            if ("SKILL.md".equalsIgnoreCase(relativePath)) {
                                content =
                                        SkillDocumentLoader.readMainDocument(
                                                resolvedSkill, skillConfig);
                            } else {
                                content =
                                        SkillDocumentLoader.readRelativeDocument(
                                                resolvedSkill, relativePath, skillConfig);
                            }
                            return content.<Object>map(c -> Map.of("content", c))
                                    .orElseGet(
                                            () ->
                                                    Map.of(
                                                            "error",
                                                            "Document not found: " + relativePath));
                        });
        abilityManager.addAbility(metaTool.getCard(), metaTool);
    }

    private void ensureSkillMetaToolRegistered() {
        if (isSkillMetaToolRegistered) {
            return;
        }
        synchronized (skillUtilLock) {
            if (abilityManager.getTools().containsKey("skill_tool")) {
                isSkillMetaToolRegistered = true;
            } else if (!isSkillMetaToolRegistered) {
                registerSkillMetaTool();
                isSkillMetaToolRegistered = true;
            }
        }
    }

    private ToolAvailabilityChecker createDefaultToolAvailabilityChecker() {
        return (toolName, session) -> {
            List<String> activeGroupNames = new ArrayList<>(getActiveToolGroupNames(session));
            for (String skillName : getActiveSkillNames(session)) {
                activeGroupNames.add("skill:" + skillName);
            }
            boolean isToolInRegisteredGroup =
                    toolGroupManager.getGroups().stream()
                            .anyMatch(group -> group.toolNames().contains(toolName));
            if (isToolInRegisteredGroup) {
                return toolGroupManager.isToolEligibleForExecution(toolName, activeGroupNames);
            }
            if (skillToolRegistry.isSkillTool(toolName)) {
                return findActiveSkillTool(toolName, session).isPresent();
            }
            return true;
        };
    }

    private Optional<Skill> resolveExplicitOrNamedSkill(
            Optional<String> skillName, Optional<String> skillId, Optional<String> skillPath) {
        if (skillUtil == null) {
            return Optional.empty();
        }
        if (skillId.isPresent() || skillPath.isPresent()) {
            return skillUtil
                    .getSkillManager()
                    .getExplicitSkill(skillId.orElse(null), skillPath.orElse(null))
                    .filter(
                            skill ->
                                    skillName
                                            .map(name -> name.equals(skill.getName()))
                                            .orElse(true));
        }
        return skillName.flatMap(
                name -> Optional.ofNullable(skillUtil.getSkillManager().get(name)));
    }

    private static Optional<String> stringArgument(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    public void lazyInitSkill() {
        String sysOperationId =
                readStringProperty(getConfig(), "getSysOperationId", "get_sys_operation_id");
        if (sysOperationId == null || sysOperationId.isBlank()) {
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
                skillUtil = createSkillUtil(sysOperationId);
            } else {
                local.setSysOperationId(sysOperationId);
            }
        }
    }

    protected SkillUtil createSkillUtil(String sysOperationId) {
        return new SkillUtil(sysOperationId);
    }

    /**
     * Configures this agent with implementation-specific settings.
     *
     * @param config implementation-specific configuration
     * @return this agent after configuration
     */
    public abstract BaseAgent configure(Object config);

    /**
     * Registers a Skill path without replacing an existing Skill.
     *
     * @param skillPath Skill file or directory path
     * @return future completing with true when registration succeeds
     */
    public CompletionStage<Boolean> registerSkill(String skillPath) {
        return registerSkill(List.of(skillPath));
    }

    /**
     * Registers a Skill path and controls metadata-name use.
     *
     * @param skillPath Skill file or directory path
     * @param shouldUseMetadataNames whether metadata names override directory names
     * @return future completing with true when registration succeeds
     */
    public CompletionStage<Boolean> registerSkill(String skillPath, boolean shouldUseMetadataNames) {
        return registerSkill(List.of(skillPath), shouldUseMetadataNames);
    }

    /**
     * Registers multiple Skill paths without replacing existing Skills.
     *
     * @param skillPaths Skill files or directory paths
     * @return future completing with true when registration succeeds
     */
    public CompletionStage<Boolean> registerSkill(List<String> skillPaths) {
        return registerSkill(skillPaths, false);
    }

    /**
     * getOwnerToken.
     *
     * @return the owner token used for global resource registration, or null when none was
     *     propagated (legacy single-agent mode)
     * @since 0.1.16
     */
    public String getOwnerToken() {
        return ownerToken;
    }

    /**
     * setOwnerToken.
     *
     * <p>Must be set before the first resource registration or invoke: claims made on the global
     * ResourceMgr under this token are released by the instance teardown (per-owner release). The
     * harness construction path assigns the token automatically; direct constructions that register
     * global resources must set it first. The token string must be unique per agent instance (the
     * harness derives it from the agent identity), because teardown releases every claim registered
     * under the same token.
     *
     * @param ownerToken owner token used for global resource registration
     * @since 0.1.16
     */
    public void setOwnerToken(String ownerToken) {
        this.ownerToken = ownerToken;
    }

    /**
     * Registers multiple Skill paths for this agent.
     *
     * @param skillPaths paths to Skill files or directories
     * @param shouldUseMetadataNames whether metadata names override directory names
     * @return future completing with true when registration succeeds
     */
    public CompletionStage<Boolean> registerSkill(
            List<String> skillPaths, boolean shouldUseMetadataNames) {
        lazyInitSkill();
        if (skillUtil == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "sys_operation_id is required before registering skills"));
        }
        try {
            Boolean isRegistered = skillUtil.registerSkills(skillPaths, this, null, shouldUseMetadataNames);
            ensureSkillMetaToolRegistered();
            return CompletableFuture.completedFuture(isRegistered);
        } catch (IOException | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Register skill(s) only when each real path is within a trusted skills root.
     *
     * <p>Absolute paths are accepted when they canonicalize inside {@code skillsRoot}.
     *
     * @param skillPath path or list of paths to register
     * @param skillsRoot trusted root containing loadable skills
     */
    public CompletionStage<Boolean> registerSkill(Object skillPath, Path skillsRoot) {
        return registerSkill(skillPath, skillsRoot, false);
    }

    /**
     * Registers one or more Skill paths inside a trusted root.
     *
     * @param skillPath single path or list of paths to register
     * @param skillsRoot trusted root containing loadable skills
     * @param shouldUseMetadataNames whether metadata names override directory names
     * @return future completing with true when registration succeeds
     */
    public CompletionStage<Boolean> registerSkill(
            Object skillPath, Path skillsRoot, boolean shouldUseMetadataNames) {
        lazyInitSkill();
        if (skillUtil == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "sys_operation_id is required before registering skills"));
        }
        try {
            if (skillPath instanceof String path) {
                Boolean isRegistered =
                        skillUtil.registerSkills(
                                List.of(path), skillsRoot, this, null, shouldUseMetadataNames);
                ensureSkillMetaToolRegistered();
                return CompletableFuture.completedFuture(isRegistered);
            }
            if (skillPath instanceof List<?> paths) {
                List<String> skillPaths = new ArrayList<>();
                for (Object item : paths) {
                    skillPaths.add(String.valueOf(item));
                }
                Boolean isRegistered =
                        skillUtil.registerSkills(
                                skillPaths, skillsRoot, this, null, shouldUseMetadataNames);
                ensureSkillMetaToolRegistered();
                return CompletableFuture.completedFuture(isRegistered);
            }
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unsupported skill path type"));
        } catch (IOException | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletionStage<Boolean> registerSkillTools(SkillToolBinding binding) {
        return registerSkillTools(List.of(binding));
    }

    public CompletionStage<Boolean> registerSkillTools(List<SkillToolBinding> bindings) {
        try {
            skillToolRegistry.registerAll(bindings);
            ensureSkillMetaToolRegistered();
            if (bindings != null) {
                for (SkillToolBinding binding : bindings) {
                    if (binding == null
                            || binding.getSkillName() == null
                            || binding.getSkillName().isBlank()) {
                        continue;
                    }
                    String implicitGroupName = "skill:" + binding.getSkillName();
                    List<String> toolNames = new ArrayList<>();
                    List<Tool> skillTools =
                            skillToolRegistry
                                    .listToolsByActiveSkill(List.of(binding.getSkillName()))
                                    .getOrDefault(binding.getSkillName(), List.of());
                    for (Tool tool : skillTools) {
                        if (tool != null
                                && tool.getCard() != null
                                && tool.getCard().getName() != null
                                && !tool.getCard().getName().isBlank()) {
                            toolNames.add(tool.getCard().getName());
                        }
                    }
                    if (!toolNames.isEmpty()) {
                        toolGroupManager.unregisterToolGroup(implicitGroupName);
                        ToolGroup implicitGroup =
                                new ToolGroup(
                                        implicitGroupName,
                                        "Implicit group for skill " + binding.getSkillName(),
                                        toolNames);
                        toolGroupManager.registerSkillToolGroup(implicitGroup);
                    }
                }
            }
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /**
     * Registers skills supplied by a GitHub tree.
     *
     * @param skillsDir destination directory for downloaded skills
     * @param githubTree GitHub tree containing skill files
     * @param token GitHub access token
     * @return future completing with the downloaded skill paths
     */
    public CompletionStage<List<java.nio.file.Path>> registerRemoteSkills(
            String skillsDir, GitHubTree githubTree, String token) {
        lazyInitSkill();
        if (skillUtil == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(
                skillUtil.registerRemoteSkills(skillsDir, githubTree, token));
    }

    /**
     * Registers a callback for an agent event.
     *
     * @param event event that triggers the callback
     * @param callback callback to register
     * @param priority callback execution priority
     * @return future completing with this agent
     */
    public CompletionStage<BaseAgent> registerCallback(
            AgentCallbackEvent event, AgentCallback callback, int priority) {
        return agentCallbackManager
                .registerCallback(event, callback, priority)
                .thenApply(ignored -> this);
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

    public CompletionStage<BaseAgent> registerInstanceRail(AgentRail rail) {
        if (rail != null) {
            rail.init(this);
        }
        return agentCallbackManager.registerInstanceRail(rail, this).thenApply(ignored -> this);
    }

    public CompletionStage<BaseAgent> unregisterRail(AgentRail rail) {
        return agentCallbackManager
                .unregisterRail(rail, this)
                .thenApply(
                        ignored -> {
                            if (rail != null) {
                                rail.uninit(this);
                            }
                            return this;
                        });
    }

    public CompletionStage<BaseAgent> unregister_rail(AgentRail rail) {
        return unregisterRail(rail);
    }

    public CompletionStage<BaseAgent> unregisterInstanceRail(AgentRail rail) {
        return agentCallbackManager
                .unregisterInstanceRail(rail, this)
                .thenApply(
                        ignored -> {
                            if (rail != null) {
                                rail.uninit(this);
                            }
                            return this;
                        });
    }

    /**
     * Executes callbacks registered for an event.
     *
     * @param event event whose callbacks should run
     * @param inputs agent inputs supplied to callbacks
     * @param session session supplied to callbacks
     * @param context model context supplied to callbacks
     * @return future completing when all callbacks finish
     */
    public CompletionStage<Void> executeCallbacks(
            AgentCallbackEvent event,
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

    /**
     * Fire a callback event through the agent callback manager.
     *
     * @param event the event to fire
     * @param ctx the callback context
     * @since 0.1.7
     */
    @Override
    public void fireCallbackEvent(AgentCallbackEvent event, AgentCallbackContext ctx) {
        try {
            agentCallbackManager.execute(event, ctx).toCompletableFuture().join();
        } catch (CompletionException exception) {
            Throwable cause = exception;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof ToolInterruptException interruption) {
                throw interruption;
            }
            throw exception;
        }
    }

    /**
     * Batch execution. Subclasses implement this {@link AgentSessionApi} entry point.
     *
     * @param inputs agent input
     * @param session session object
     * @return agent output result
     */
    public Object invoke(Object inputs, AgentSessionApi session) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not implement invoke(Object, AgentSessionApi)");
    }

    /**
     * Stream execution. Default wraps {@link #invoke(Object, AgentSessionApi)}.
     *
     * @param inputs agent input
     * @param session session object
     * @param streamModes stream output modes
     * @return iterator of stream output
     */
    public Iterator<Object> stream(
            Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        return List.<Object>of(invoke(inputs, session)).iterator();
    }

    public Object invoke(Object inputs, AgentSession session) {
        AgentSessionApi apiSession = session;
        return invoke(inputs, apiSession);
    }

    /**
     * Streams execution using the legacy session API.
     *
     * @param inputs agent input
     * @param session legacy session object
     * @param streamModes stream output modes
     * @return iterator of stream output
     */
    public Iterator<Object> stream(
            Object inputs, AgentSession session, List<StreamMode> streamModes) {
        AgentSessionApi apiSession = session;
        return stream(inputs, apiSession, streamModes);
    }

    /**
     * Reactive version of {@link #invoke(Object, AgentSession)}.
     *
     * @param inputs agent inputs
     * @param session session context, nullable
     * @return Mono emitting the invocation result
     */
    public Mono<Object> invokeAsync(Object inputs, AgentSession session) {
        return ReactiveAdapters.fromCallable(() -> invoke(inputs, session));
    }

    /**
     * Reactive version of {@link #invoke(Object, AgentSessionApi)}.
     *
     * @param inputs agent inputs
     * @param session agent session
     * @return Mono emitting the invocation result
     */
    public Mono<Object> invokeAsync(Object inputs, AgentSessionApi session) {
        return ReactiveAdapters.fromCallable(() -> invoke(inputs, session));
    }

    /**
     * Reactive version of {@link #stream(Object, AgentSession, List)}.
     *
     * @param inputs agent inputs
     * @param session session context, nullable
     * @param streamModes stream output modes
     * @return Flux emitting stream chunks
     */
    public Flux<Object> streamAsync(
            Object inputs, AgentSession session, List<StreamMode> streamModes) {
        return ReactiveAdapters.fromAutoCloseableIterator(
                () -> stream(inputs, session, streamModes));
    }

    /**
     * Reactive version of {@link #stream(Object, AgentSessionApi, List)}.
     *
     * @param inputs agent inputs
     * @param session agent session
     * @param streamModes stream output modes
     * @return Flux emitting stream chunks
     */
    public Flux<Object> streamAsync(
            Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        return ReactiveAdapters.fromAutoCloseableIterator(
                () -> stream(inputs, session, streamModes));
    }

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

    /**
     * Activates an explicit tool group for the session.
     *
     * @param groupName registered tool-group name
     * @param session session whose state should be updated
     * @return this agent
     */
    public BaseAgent activateToolGroup(String groupName, AgentSessionApi session) {
        String normalizedGroupName = normalizeGroupName(groupName);
        if (normalizedGroupName.startsWith("skill:")) {
            throw new IllegalArgumentException(
                    "Reserved prefix 'skill:' cannot be used for explicit tool groups");
        }
        requireSession(session);
        LinkedHashSet<String> activeGroupNames =
                new LinkedHashSet<>(getActiveToolGroupNames(session));
        activeGroupNames.add(normalizedGroupName);
        session.updateState(
                Map.of(ACTIVE_TOOL_GROUP_NAMES_STATE_KEY, List.copyOf(activeGroupNames)));
        return this;
    }

    /**
     * Deactivates an explicit tool group for the session.
     *
     * @param groupName registered tool-group name
     * @param session session whose state should be updated
     * @return this agent
     */
    public BaseAgent deactivateToolGroup(String groupName, AgentSessionApi session) {
        String normalizedGroupName = normalizeGroupName(groupName);
        requireSession(session);
        LinkedHashSet<String> activeGroupNames =
                new LinkedHashSet<>(getActiveToolGroupNames(session));
        activeGroupNames.remove(normalizedGroupName);
        session.updateState(
                Map.of(ACTIVE_TOOL_GROUP_NAMES_STATE_KEY, List.copyOf(activeGroupNames)));
        return this;
    }

    /**
     * Returns the session explicitly activated tool-group names.
     *
     * @param session session whose state should be inspected
     * @return immutable active tool-group names
     */
    public List<String> getActiveToolGroupNames(AgentSessionApi session) {
        if (session == null) {
            return List.of();
        }
        return normalizeGroupNames(session.getState(ACTIVE_TOOL_GROUP_NAMES_STATE_KEY));
    }

    /**
     * Registers an explicit tool group on this agent.
     *
     * @param group tool group to register
     */
    public void registerToolGroup(ToolGroup group) {
        if (group != null && group.name() != null && group.name().startsWith("skill:")) {
            throw new IllegalArgumentException(
                    "Reserved prefix 'skill:' cannot be used for explicit tool groups");
        }
        toolGroupManager.registerToolGroup(group);
    }

    /**
     * Removes an explicit tool group by name.
     *
     * @param name tool-group name to remove
     */
    public void unregisterToolGroup(String name) {
        toolGroupManager.unregisterToolGroup(name);
    }

    public ToolGroupManager getToolGroupManager() {
        return toolGroupManager;
    }

    public List<ToolInfo> listEffectiveToolInfo(AgentSessionApi session) {
        List<String> activeSkillNames = getActiveSkillNames(session);
        if (toolGroupManager.getGroups().isEmpty()) {
            return listEffectiveToolInfo(activeSkillNames);
        }
        return listEffectiveToolInfoWithGroups(activeSkillNames, getActiveToolGroupNames(session));
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
                    || normalizedDocumentPath.equals(
                            skillDirectory.resolve("skill.md").normalize())) {
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
        this.abilityManager =
                abilityManager == null ? new AbilityManager(card.getId()) : abilityManager;
        this.abilityManager.setToolAvailabilityChecker(createDefaultToolAvailabilityChecker());
        this.isSkillMetaToolRegistered = false;
        ensureSkillMetaToolRegistered();
    }

    public AgentCallbackManager getAgentCallbackManager() {
        return agentCallbackManager;
    }

    public SkillUtil getSkillUtil() {
        return skillUtil;
    }

    public SkillToolRegistry getSkillToolRegistry() {
        return skillToolRegistry;
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
            } catch (ReflectiveOperationException exception) {
                Loggers.AGENT.debug("Fallback tool card introspection failed", exception);
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
        Map<String, List<Tool>> skillTools = skillToolRegistry.listToolsByActiveSkill(activeSkillNames);
        for (Map.Entry<String, List<Tool>> entry : skillTools.entrySet()) {
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

    private List<ToolInfo> listEffectiveToolInfoWithGroups(
            List<String> activeSkillNames, List<String> activeToolGroupNames) {
        List<ToolInfo> merged = listEffectiveToolInfo(activeSkillNames);
        List<String> allActiveGroupNames = new ArrayList<>(activeToolGroupNames);
        for (String skillName : activeSkillNames) {
            allActiveGroupNames.add("skill:" + skillName);
        }
        List<String> visibleToolNames =
                toolGroupManager.getActiveToolNames(
                        allActiveGroupNames, merged.stream().map(ToolInfo::getName).toList());
        Set<String> visibleSet = new LinkedHashSet<>(visibleToolNames);
        List<ToolInfo> visibleTools =
                merged.stream()
                        .filter(toolInfo -> visibleSet.contains(toolInfo.getName()))
                        .toList();
        Loggers.AGENT.debug(
                "Effective tools: count={}, active_toolgroups={}",
                visibleTools.size(),
                activeToolGroupNames);
        return visibleTools;
    }

    private void validateEffectiveToolNames(List<String> activeSkillNames) {
        listEffectiveToolInfo(activeSkillNames);
    }

    private void requireRegisteredSkill(String skillName) {
        if (skillUtil == null || !skillUtil.getSkillManager().has(skillName)) {
            throw new IllegalArgumentException("Skill is not registered: " + skillName);
        }
    }

    private static void addToolInfo(
            Map<String, ToolInfo> merged,
            Map<String, String> sources,
            ToolInfo toolInfo,
            String source) {
        if (toolInfo == null || toolInfo.getName() == null || toolInfo.getName().isBlank()) {
            return;
        }
        String toolName = toolInfo.getName();
        if (merged.containsKey(toolName)) {
            throw new IllegalStateException(
                    "Duplicate effective tool name '"
                            + toolName
                            + "' from "
                            + source
                            + "; conflicts with "
                            + sources.get(toolName));
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

    private static String normalizeGroupName(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("groupName must not be blank");
        }
        return groupName.trim();
    }

    private static List<String> normalizeGroupNames(Object value) {
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
            if (item != null && !String.valueOf(item).isBlank()) {
                normalized.add(String.valueOf(item).trim());
            }
        }
        return List.copyOf(new ArrayList<>(normalized));
    }
}
