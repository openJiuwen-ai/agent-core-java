/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.messager.Messagers;
import com.openjiuwen.agent_teams.models.ModelAllocators;
import com.openjiuwen.agent_teams.models.ModelPoolEntry;
import com.openjiuwen.agent_teams.models.ModelPoolSupport;
import com.openjiuwen.agent_teams.rails.TeamPlanModeRail;
import com.openjiuwen.agent_teams.rails.TeamPolicyRail;
import com.openjiuwen.agent_teams.rails.TeamToolApprovalRail;
import com.openjiuwen.agent_teams.rails.TeamToolRail;
import com.openjiuwen.agent_teams.runtime.TeamPlan;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeManager;
import com.openjiuwen.agent_teams.team_workspace.TeamWorkspaceConfig;
import com.openjiuwen.agent_teams.team_workspace.TeamWorkspaceManager;
import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import com.openjiuwen.harness.tools.worktree.WorktreeConfig;
import com.openjiuwen.harness.tools.worktree.WorktreeCreatedEvent;
import com.openjiuwen.harness.tools.worktree.WorktreeEvent;
import com.openjiuwen.harness.tools.worktree.WorktreeEventHandler;
import com.openjiuwen.harness.tools.worktree.WorktreeManager;
import com.openjiuwen.harness.tools.worktree.WorktreeRemovedEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Handles TeamAgent configuration, setup, and initialization.
 *
 * <p>Mirrors Python's {@code AgentConfigurator} and module function
 * {@code _resolve_team_mode} in
 * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
 *
 * <p>The surrounding TeamAgent SCC is translated over several batch tasks. This
 * class keeps the configurator's observable orchestration behavior concrete
 * while narrow support models in this file preserve the Python data contracts
 * used directly by the configurator.</p>
 */
public class AgentConfigurator {

    private static final String DEFAULT_JOIN_MESSAGE = "Join the team and wait for your first assignment.";

    private final AgentCard card;
    private TeamAgentBlueprint blueprint;
    private SpawnPayloadBuilder spawnPayloadBuilder;
    private final TeamInfra infra = new TeamInfra();
    private final PrivateAgentResources resources = new PrivateAgentResources();
    private Allocation leaderAllocation;
    private Object onTeammateCreated;

    public AgentConfigurator(AgentCard card) {
        this.card = Objects.requireNonNull(card, "card");
    }

    public static String _resolveTeamMode(TeamAgentSpec spec) {
        return resolveTeamMode(spec);
    }

    public static String resolveTeamMode(TeamAgentSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (spec.getTeamMode() != null) {
            return spec.getTeamMode();
        }
        EnumSet<TeamRole> avatarRoles = EnumSet.of(TeamRole.HUMAN_AGENT, TeamRole.BRIDGE_AGENT);
        for (TeamMemberSpec member : spec.getPredefinedMembers()) {
            if (!avatarRoles.contains(member.getRoleType())) {
                return "hybrid";
            }
        }
        return "default";
    }

    public TeamInfra getInfra() {
        return infra;
    }

    public PrivateAgentResources getResources() {
        return resources;
    }

    public Messager getMessager() {
        return infra.getMessager();
    }

    public void setMessager(Messager messager) {
        infra.setMessager(messager);
    }

    public ConfiguredTeamBackend getTeamBackend() {
        return infra.getTeamBackend();
    }

    public void setTeamBackend(ConfiguredTeamBackend teamBackend) {
        infra.setTeamBackend(teamBackend);
    }

    public TeamWorkspaceManager getWorkspaceManager() {
        return infra.getWorkspaceManager();
    }

    public void setWorkspaceManager(TeamWorkspaceManager workspaceManager) {
        infra.setWorkspaceManager(workspaceManager);
    }

    public boolean isWorkspaceInitialized() {
        return infra.isWorkspaceInitialized();
    }

    public void setWorkspaceInitialized(boolean workspaceInitialized) {
        infra.setWorkspaceInitialized(workspaceInitialized);
    }

    public Object getTaskManager() {
        return infra.getTaskManager();
    }

    public void setTaskManager(Object taskManager) {
        infra.setTaskManager(taskManager);
    }

    public Object getMessageManager() {
        return infra.getMessageManager();
    }

    public void setMessageManager(Object messageManager) {
        infra.setMessageManager(messageManager);
    }

    public MemberRuntime getHarness() {
        return resources.getHarness();
    }

    public void setHarness(MemberRuntime harness) {
        resources.setHarness(harness);
    }

    public WorktreeManager getWorktreeManager() {
        return resources.getWorktreeManager();
    }

    public void setWorktreeManager(WorktreeManager worktreeManager) {
        resources.setWorktreeManager(worktreeManager);
    }

    public Object getMemoryManager() {
        return resources.getMemoryManager();
    }

    public void setMemoryManager(Object memoryManager) {
        resources.setMemoryManager(memoryManager);
    }

    public Object getFirstIterGate() {
        return resources.getFirstIterGate();
    }

    public void setFirstIterGate(Object firstIterGate) {
        resources.setFirstIterGate(firstIterGate);
    }

    public ModelAllocator getModelAllocator() {
        return resources.getModelAllocator();
    }

    public void setModelAllocator(ModelAllocator modelAllocator) {
        resources.setModelAllocator(modelAllocator);
    }

    public MemberRuntime configure(
            com.openjiuwen.agent_teams.schema.TeamAgentSpec spec,
            TeamRuntimeContext ctx
    ) {
        return configure(spec == null ? null : spec.toConfiguratorSpec(), ctx);
    }

    public MemberRuntime configure(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx
    ) {
        setupInfra(spec, ctx);
        return setupAgent(spec, ctx, null);
    }

    public void setupInfra(
            com.openjiuwen.agent_teams.schema.TeamAgentSpec spec,
            TeamRuntimeContext ctx
    ) {
        setupInfra(spec == null ? null : spec.toConfiguratorSpec(), ctx);
    }

    public void setupInfra(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx
    ) {
        setupInfra(spec, ctx, null, null, null);
    }

    public void setupInfra(
            com.openjiuwen.agent_teams.schema.TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            Object onTeammateCreated,
            Object onTeamCleaned,
            Object onTeamBuilt
    ) {
        setupInfra(
                spec == null ? null : spec.toConfiguratorSpec(),
                ctx,
                onTeammateCreated,
                onTeamCleaned,
                onTeamBuilt
        );
    }

    public void setupInfra(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            Object onTeammateCreated,
            Object onTeamCleaned,
            Object onTeamBuilt
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        DeepAgentSpec agentSpec = resolveAgentSpec(spec, ctx.getRole(), ctx.getMemberName());
        String resolvedLanguage = resolveLanguage(agentSpec.getLanguage());
        this.blueprint = new TeamAgentBlueprint(
                card,
                spec,
                ctx,
                rolePolicy(ctx.getRole(), resolvedLanguage),
                resolvedLanguage
        );
        this.spawnPayloadBuilder = new SpawnPayloadBuilder(spec, ctx);
        this.onTeammateCreated = onTeammateCreated;

        MessagerTransportConfig messagerConfig = ctx.getMessagerConfig();
        String memberName = ctx.getMemberName();
        if (memberName != null
                && messagerConfig != null
                && !Objects.equals(messagerConfig.getNodeId(), memberName)) {
            messagerConfig = copyMessagerConfig(messagerConfig);
            messagerConfig.setNodeId(memberName);
        }
        setMessager(messagerConfig == null ? null : Messagers.createMessager(messagerConfig));

        TeamWorkspaceConfig workspace = spec.getWorkspace();
        if (workspace != null && workspace.isEnabled()) {
            setWorkspaceManager(createWorkspaceManager(spec, ctx));
        }

        setupTeamBackend(spec, ctx, getMessager(), onTeamCleaned, onTeamBuilt);

        WorktreeConfig worktree = spec.getWorktree();
        if (ctx.getRole() != TeamRole.LEADER && worktree != null && worktree.isEnabled()) {
            setWorktreeManager(createWorktreeManager(spec));
        }
    }

    public static TeamWorkspaceManager createWorkspaceManager(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx
    ) {
        TeamWorkspaceConfig workspaceConfig = Objects.requireNonNull(spec.getWorkspace(), "workspace");
        String teamName = ctx.getTeamSpec() != null && isNonBlank(ctx.getTeamSpec().getTeamName())
                ? ctx.getTeamSpec().getTeamName()
                : spec.getTeamName();
        String workspacePath = isNonBlank(workspaceConfig.getRootPath())
                ? workspaceConfig.getRootPath()
                : teamHome(teamName).resolve("team-workspace").toString();
        try {
            Files.createDirectories(Path.of(workspacePath));
        } catch (IOException exc) {
            throw new IllegalStateException("Unable to ensure team workspace directory: " + workspacePath, exc);
        }
        return new TeamWorkspaceManager(workspaceConfig, workspacePath, teamName);
    }

    public WorktreeManager createWorktreeManager(TeamAgentSpec spec) {
        TeamWorkspaceManager workspaceManager = getWorkspaceManager();
        WorktreeEventHandler eventHandler = null;
        if (workspaceManager != null) {
            eventHandler = event -> mirrorWorktreeIntoWorkspace(workspaceManager, event);
        }
        if (eventHandler == null) {
            return new WorktreeManager(spec.getWorktree());
        }
        return new WorktreeManager(spec.getWorktree(), null, eventHandler, null);
    }

    public MemberRuntime setupAgent(
            com.openjiuwen.agent_teams.schema.TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            MemberRuntime memberRuntime
    ) {
        return setupAgent(spec == null ? null : spec.toConfiguratorSpec(), ctx, memberRuntime);
    }

    public MemberRuntime setupAgent(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            MemberRuntime memberRuntime
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        if (memberRuntime != null) {
            setHarness(memberRuntime);
            setMemoryManager(null);
            return memberRuntime;
        }

        DeepAgentSpec agentSpec = resolveAgentSpec(spec, ctx.getRole(), ctx.getMemberName());
        String resolvedLanguage = blueprint != null ? blueprint.getLanguage() : resolveLanguage(agentSpec.getLanguage());
        String memberName = ctx.getMemberName();

        WorkspaceSpec workspaceSpec = agentSpec.getWorkspace();
        DeepAgentSpec leaderSpec = spec.getAgents().getOrDefault("leader", agentSpec);
        if (workspaceSpec == null) {
            workspaceSpec = leaderSpec.getWorkspace();
        }
        if (workspaceSpec != null && workspaceSpec.isStableBase()) {
            String teamName = ctx.getTeamSpec() != null && isNonBlank(ctx.getTeamSpec().getTeamName())
                    ? ctx.getTeamSpec().getTeamName()
                    : spec.getTeamName();
            Path base = teamHome(teamName).resolve("workspaces");
            Path teamWorkspacePath = base.resolve((memberName == null ? "" : memberName) + "_workspace");
            Path independentWorkspace = independentMemberWorkspace(memberName);
            if (Files.isDirectory(independentWorkspace) && !Files.exists(teamWorkspacePath)) {
                try {
                    Files.createDirectories(base);
                    Files.createSymbolicLink(teamWorkspacePath, independentWorkspace);
                } catch (RuntimeException | IOException exc) {
                    throw new IllegalStateException("Unable to link stable member workspace", exc);
                }
            }
            workspaceSpec = workspaceSpec.copyWithRootPath(teamWorkspacePath.toString());
        }

        if (workspaceSpec != null && isNonBlank(workspaceSpec.getRootPath()) && getTeamBackend() != null) {
            getTeamBackend().registerCleanupPath(workspaceSpec.getRootPath());
        }
        if (getWorkspaceManager() != null && workspaceSpec != null && isNonBlank(workspaceSpec.getRootPath())) {
            try {
                getWorkspaceManager().mountIntoWorkspace(workspaceSpec.getRootPath());
            } catch (IOException exc) {
                throw new IllegalStateException("Unable to mount member workspace", exc);
            }
        }

        if (ctx.getRole() != TeamRole.HUMAN_AGENT) {
            setFirstIterGate(new FirstIterationGateHandle());
        }

        Object memoryManager = buildMemoryManager(spec, ctx, agentSpec, resolvedLanguage, memberName);
        String resolvedTeamName = ctx.getTeamSpec() != null && isNonBlank(ctx.getTeamSpec().getTeamName())
                ? ctx.getTeamSpec().getTeamName()
                : spec.getTeamName();
        String teamWorkspaceMount = getWorkspaceManager() == null ? null : ".team/" + resolvedTeamName + "/";
        String teamWorkspacePath = getWorkspaceManager() == null ? null : getWorkspaceManager().getWorkspacePath();
        TeamPolicyRail teamPolicyRail = new TeamPolicyRail(new TeamPolicyRail.Config(
                ctx.getRole(),
                ctx.getPersona(),
                memberName,
                spec.getLifecycle(),
                spec.getTeammateMode(),
                resolvedLanguage,
                resolveTeamMode(spec),
                agentSpec.getSystemPrompt(),
                teamWorkspaceMount,
                teamWorkspacePath,
                getTeamBackend(),
                spec.isExposeHumanAgentsToTeammates()
        ));
        TeamPlanModeRail teamPlanModeRail = ctx.getRole() == TeamRole.LEADER && TeamPlan.isTeamPlanEnabled(spec)
                ? new TeamPlanModeRail(resolvedLanguage)
                : null;
        TeamToolRail teamToolRail = buildTeamToolRail(spec, ctx, resolvedLanguage, memberName, resolvedTeamName);
        TeamToolApprovalRail toolApprovalRail = buildToolApprovalRail(agentSpec, ctx, resolvedTeamName, memberName);
        ConfiguredMemberRuntime runtime = new ConfiguredMemberRuntime(
                agentSpec,
                ctx,
                workspaceSpec,
                agentSpec.getSysOperation(),
                teamToolRail,
                getFirstIterGate(),
                teamPolicyRail,
                toolApprovalRail,
                teamPlanModeRail
        );
        setHarness(runtime);
        setMemoryManager(memoryManager);
        if (spec.getAgentCustomizer() != null) {
            runtime.runAgentCustomizer(spec.getAgentCustomizer());
        }
        return runtime;
    }

    private TeamToolRail buildTeamToolRail(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            String resolvedLanguage,
            String memberName,
            String resolvedTeamName
    ) {
        return new TeamToolRail(new TeamToolRail.Config(
                getTeamBackend(),
                ctx.getRole() == null ? null : ctx.getRole().value(),
                spec.getTeammateMode(),
                spec.getLifecycle(),
                resolvedLanguage,
                null,
                null,
                Set.of(),
                getWorkspaceManager(),
                getWorktreeManager(),
                false,
                resolvedTeamName,
                memberName,
                null,
                null,
                null
        ));
    }

    private TeamToolApprovalRail buildToolApprovalRail(
            DeepAgentSpec agentSpec,
            TeamRuntimeContext ctx,
            String resolvedTeamName,
            String memberName
    ) {
        if (ctx.getRole() == TeamRole.HUMAN_AGENT || agentSpec.getApprovalRequiredTools().isEmpty()) {
            return null;
        }
        if (!(getTeamBackend().getMessageManager()
                instanceof ConfiguredTeamBackend.ConfiguredMessageManager messageManager)) {
            return null;
        }
        String leaderMemberName = ctx.getTeamSpec() == null ? null : ctx.getTeamSpec().getLeaderMemberName();
        return new TeamToolApprovalRail(new TeamToolApprovalRail.Config(
                resolvedTeamName,
                memberName,
                (content, toMemberName) -> messageManager.sendMessage(content, toMemberName, memberName),
                leaderMemberName,
                agentSpec.getApprovalRequiredTools()
        ));
    }

    public Object buildMemoryManager(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            DeepAgentSpec agentSpec,
            String resolvedLanguage,
            String memberName
    ) {
        TeamMemoryConfig memory = spec.getMemory();
        if (memory == null || !memory.isEnabled()) {
            return null;
        }
        String resolvedTeamName = ctx.getTeamSpec() != null && isNonBlank(ctx.getTeamSpec().getTeamName())
                ? ctx.getTeamSpec().getTeamName()
                : spec.getTeamName();
        String teamMemoryDir = null;
        if (memory.isSharedMemory() && "persistent".equals(spec.getLifecycle())) {
            teamMemoryDir = isNonBlank(memory.getTeamMemoryDir())
                    ? memory.getTeamMemoryDir()
                    : teamMemoryDir(resolvedTeamName).toString();
        }
        return new TeamMemoryManagerDescriptor(
                memberName == null ? "" : memberName,
                resolvedTeamName,
                ctx.getRole().value(),
                spec.getLifecycle(),
                memory.getScenario(),
                teamMemoryDir,
                resolvedLanguage,
                memory.getMemberMemoryPromptMode(),
                memory.isAutoExtract() && "persistent".equals(spec.getLifecycle()),
                memory.getParentWorkspacePath(),
                getTaskManager(),
                memory.getTimezoneOffsetHours()
        );
    }

    public static DeepAgentSpec resolveAgentSpec(
            TeamAgentSpec spec,
            TeamRole role,
            String memberName
    ) {
        if (memberName != null && spec.getAgents().containsKey(memberName)) {
            return spec.getAgents().get(memberName);
        }
        DeepAgentSpec roleSpec = spec.getAgents().get(role.value());
        if (roleSpec != null) {
            return roleSpec;
        }
        DeepAgentSpec teammateSpec = spec.getAgents().get("teammate");
        if (teammateSpec != null) {
            return teammateSpec;
        }
        DeepAgentSpec leaderSpec = spec.getAgents().get("leader");
        if (leaderSpec == null) {
            throw new IllegalArgumentException("agents dict must contain a 'leader' key");
        }
        return leaderSpec;
    }

    public ConfiguredTeamBackend setupTeamBackend(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            Messager messager,
            Object onTeamCleaned,
            Object onTeamBuilt
    ) {
        String teamName = ctx.getTeamSpec() != null && isNonBlank(ctx.getTeamSpec().getTeamName())
                ? ctx.getTeamSpec().getTeamName()
                : "default";
        boolean leader = ctx.getRole() == TeamRole.LEADER;
        String currentMemberName = ctx.getMemberName();
        if (!isNonBlank(currentMemberName) && ctx.getTeamSpec() != null) {
            currentMemberName = ctx.getTeamSpec().getLeaderMemberName();
        }
        ConfiguredTeamBackend backend = new ConfiguredTeamBackend(
                teamName,
                currentMemberName == null ? "" : currentMemberName,
                leader,
                ctx.getDbConfig(),
                messager,
                spec.getTeammateMode(),
                spec.getPredefinedMembers(),
                getModelAllocator(),
                leader ? leaderAllocation : null,
                spec.isEnableHitt(),
                spec.isEnableBridge(),
                spec.getExternalCliAgents(),
                onTeamCleaned,
                onTeamBuilt,
                ctx.getTeamSpec() == null ? null : ctx.getTeamSpec().getLeaderMemberName()
        );
        setTeamBackend(backend);
        setTaskManager(backend.getTaskManager());
        setMessageManager(backend.getMessageManager());

        if (getWorkspaceManager() != null) {
            backend.registerCleanupPath(getWorkspaceManager().getWorkspacePath());
        }
        backend.registerCleanupPath(teamHome(teamName).toString());
        return backend;
    }

    public void updateModelPool(List<?> newPool) {
        if (getCtx() == null || getCtx().getTeamSpec() == null) {
            return;
        }
        TeamSpec teamSpec = getCtx().getTeamSpec();
        List<ModelPoolEntry> currentPool = modelPoolEntries(teamSpec.getModelPool());
        List<ModelPoolEntry> incomingPool = modelPoolEntries(newPool);
        List<ModelPoolEntry> inheritedPool = ModelPoolSupport.inheritPoolIds(currentPool, incomingPool);
        teamSpec.setModelPool(inheritedPool);
        setModelAllocator(ModelAllocators.buildModelAllocator(getSpec(), teamSpec));
    }

    public void attachModelAllocator(
            ModelAllocator allocator,
            Allocation leaderAllocation
    ) {
        setModelAllocator(allocator);
        this.leaderAllocation = leaderAllocation;
    }

    public void restoreAllocatorState(Map<String, Object> state) {
        if (getModelAllocator() != null) {
            getModelAllocator().loadStateDict(state == null ? Map.of() : state);
        }
    }

    public Map<String, Object> buildSpawnPayload(
            TeamRuntimeContext ctx,
            String initialMessage
    ) {
        ensureSpawnPayloadBuilder();
        return spawnPayloadBuilder.buildSpawnPayload(ctx, initialMessage);
    }

    public TeamRuntimeContext buildMemberContext(TeamMemberSpec memberSpec) {
        ensureSpawnPayloadBuilder();
        return spawnPayloadBuilder.buildMemberContext(memberSpec);
    }

    public MessagerTransportConfig buildMemberMessagerConfig(String memberName) {
        ensureSpawnPayloadBuilder();
        return spawnPayloadBuilder.buildMemberMessagerConfig(memberName);
    }

    public SpawnAgentConfig buildSpawnConfig(TeamRuntimeContext ctx) {
        ensureSpawnPayloadBuilder();
        return spawnPayloadBuilder.buildSpawnConfig(ctx);
    }

    public TeamAgentBlueprint getBlueprint() {
        return blueprint;
    }

    public TeamAgentSpec getSpec() {
        return blueprint == null ? null : blueprint.getSpec();
    }

    public TeamRuntimeContext getCtx() {
        return blueprint == null ? null : blueprint.getCtx();
    }

    public String getRolePolicy() {
        return blueprint == null ? "" : blueprint.getRolePolicy();
    }

    public TeamSpec getTeamSpec() {
        return getCtx() == null ? null : getCtx().getTeamSpec();
    }

    public TeamRole getRole() {
        return getCtx() == null ? TeamRole.LEADER : getCtx().getRole();
    }

    public String getLifecycle() {
        return getSpec() == null ? "temporary" : getSpec().getLifecycle();
    }

    public String getMemberName() {
        return getCtx() == null ? null : getCtx().getMemberName();
    }

    public String getTeamName() {
        if (getCtx() != null && getCtx().getTeamSpec() != null) {
            return getCtx().getTeamSpec().getTeamName();
        }
        return null;
    }

    private void ensureSpawnPayloadBuilder() {
        if (spawnPayloadBuilder == null) {
            throw new IllegalStateException("configure or setupInfra must run before building spawn payloads");
        }
    }

    private static List<ModelPoolEntry> modelPoolEntries(List<?> rawPool) {
        List<ModelPoolEntry> entries = new ArrayList<>();
        if (rawPool == null) {
            return entries;
        }
        for (Object item : rawPool) {
            if (item instanceof ModelPoolEntry entry) {
                entries.add(entry);
            } else if (item instanceof Map<?, ?> map) {
                entries.add(modelPoolEntryFromMap(map));
            } else {
                throw new IllegalArgumentException(
                        "model_pool entries must be ModelPoolEntry or map values, got "
                                + item.getClass().getName());
            }
        }
        return entries;
    }

    private static ModelPoolEntry modelPoolEntryFromMap(Map<?, ?> map) {
        Object metadata = valueFromMap(map, "metadata");
        Map<String, Object> metadataMap = metadata instanceof Map<?, ?> raw ? stringMap(raw) : Map.of();
        return new ModelPoolEntry(
                stringValue(valueFromMap(map, "model_name", "modelName")),
                stringValue(valueFromMap(map, "api_key", "apiKey")),
                stringValue(valueFromMap(map, "api_base_url", "apiBaseUrl")),
                stringValue(valueFromMap(map, "api_provider", "apiProvider")),
                stringValue(valueFromMap(map, "description")),
                stringValue(valueFromMap(map, "model_id", "modelId")),
                metadataMap
        );
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), copyDynamicValue(entry.getValue()));
        }
        return result;
    }

    private static Object copyDynamicValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return stringMap(mapValue);
        }
        if (value instanceof List<?> listValue) {
            List<Object> copy = new ArrayList<>();
            for (Object item : listValue) {
                copy.add(copyDynamicValue(item));
            }
            return copy;
        }
        return value;
    }

    private static Object valueFromMap(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static CompletableFuture<Void> mirrorWorktreeIntoWorkspace(
            TeamWorkspaceManager workspaceManager,
            WorktreeEvent event
    ) {
        try {
            if (event instanceof WorktreeCreatedEvent created) {
                workspaceManager.mountWorktree(created.getWorktreeName(), created.getWorktreePath());
            } else if (event instanceof WorktreeRemovedEvent removed) {
                workspaceManager.unmountWorktree(removed.getWorktreeName());
            }
            return CompletableFuture.completedFuture(null);
        } catch (IOException exc) {
            return CompletableFuture.failedFuture(exc);
        }
    }

    private static MessagerTransportConfig copyMessagerConfig(MessagerTransportConfig source) {
        MessagerTransportConfig copy = new MessagerTransportConfig();
        copy.setBackend(source.getBackend());
        copy.setTeamName(source.getTeamName());
        copy.setNodeId(source.getNodeId());
        copy.setDirectAddr(source.getDirectAddr());
        copy.setPubsubPublishAddr(source.getPubsubPublishAddr());
        copy.setPubsubSubscribeAddr(source.getPubsubSubscribeAddr());
        copy.setListenAddrs(source.getListenAddrs());
        copy.setBootstrapPeers(source.getBootstrapPeers());
        copy.setKnownPeers(source.getKnownPeers());
        copy.setRequestTimeout(source.getRequestTimeout());
        copy.setMetadata(source.getMetadata());
        return copy;
    }

    private static String resolveLanguage(String language) {
        if ("en".equalsIgnoreCase(language)) {
            return "en";
        }
        return "cn";
    }

    private static String rolePolicy(TeamRole role, String language) {
        return role.value() + ":" + language;
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static Path teamHome(String teamName) {
        return agentTeamsHome().resolve(teamName == null || teamName.isBlank() ? "default" : teamName);
    }

    private static Path independentMemberWorkspace(String memberName) {
        return openjiuwenHome().resolve((memberName == null ? "" : memberName) + "_workspace");
    }

    private static Path teamMemoryDir(String teamName) {
        return teamHome(teamName).resolve("team-workspace").resolve("team-memory");
    }

    private static Path agentTeamsHome() {
        return openjiuwenHome().resolve(".agent_teams");
    }

    private static Path openjiuwenHome() {
        return Path.of(System.getProperty("user.home"), ".openjiuwen");
    }

    /**
     * Team role values consumed by the configurator.
     *
     * <p>Mirrors Python's {@code TeamRole} use sites in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public enum TeamRole {
        LEADER("leader"),
        TEAMMATE("teammate"),
        HUMAN_AGENT("human_agent"),
        BRIDGE_AGENT("bridge_agent");

        private final String value;

        TeamRole(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static TeamRole fromValue(String value) {
            for (TeamRole role : values()) {
                if (role.value.equals(value)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("Unknown team role: " + value);
        }
    }

    /**
     * Lightweight agent card used by configurator construction.
     *
     * <p>Mirrors Python's {@code AgentCard} parameter use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class AgentCard {
        private String id;
        private String name;
        private String description;

        public AgentCard() {
        }

        public AgentCard(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * Runtime spec for one configured team.
     *
     * <p>Mirrors Python's {@code TeamAgentSpec} fields used by
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class TeamAgentSpec {
        private Map<String, DeepAgentSpec> agents = new LinkedHashMap<>();
        private String teamName = "agent_team";
        private String lifecycle = "temporary";
        private String teammateMode = "build_mode";
        private String spawnMode = "process";
        private String teamMode;
        private List<TeamMemberSpec> predefinedMembers = new ArrayList<>();
        private List<Object> externalCliAgents = new ArrayList<>();
        private TeamWorkspaceConfig workspace;
        private WorktreeConfig worktree;
        private TeamMemoryConfig memory;
        private Map<String, Object> metadata = new LinkedHashMap<>();
        private boolean enableHitt;
        private boolean enableBridge;
        private boolean exposeHumanAgentsToTeammates;
        private AgentCustomizer agentCustomizer;

        public Map<String, DeepAgentSpec> getAgents() {
            return agents;
        }

        public void setAgents(Map<String, DeepAgentSpec> agents) {
            this.agents = agents == null ? new LinkedHashMap<>() : new LinkedHashMap<>(agents);
        }

        public String getTeamName() {
            return teamName;
        }

        public void setTeamName(String teamName) {
            this.teamName = teamName;
        }

        public String getLifecycle() {
            return lifecycle;
        }

        public void setLifecycle(String lifecycle) {
            this.lifecycle = lifecycle;
        }

        public String getTeammateMode() {
            return teammateMode;
        }

        public void setTeammateMode(String teammateMode) {
            this.teammateMode = teammateMode;
        }

        public String getSpawnMode() {
            return spawnMode;
        }

        public void setSpawnMode(String spawnMode) {
            this.spawnMode = spawnMode;
        }

        public String getTeamMode() {
            return teamMode;
        }

        public void setTeamMode(String teamMode) {
            this.teamMode = teamMode;
        }

        public List<TeamMemberSpec> getPredefinedMembers() {
            return predefinedMembers;
        }

        public void setPredefinedMembers(List<TeamMemberSpec> predefinedMembers) {
            this.predefinedMembers = predefinedMembers == null ? new ArrayList<>() : new ArrayList<>(predefinedMembers);
        }

        public List<Object> getExternalCliAgents() {
            return externalCliAgents;
        }

        public void setExternalCliAgents(List<Object> externalCliAgents) {
            this.externalCliAgents = externalCliAgents == null ? new ArrayList<>() : new ArrayList<>(externalCliAgents);
        }

        public TeamWorkspaceConfig getWorkspace() {
            return workspace;
        }

        public void setWorkspace(TeamWorkspaceConfig workspace) {
            this.workspace = workspace;
        }

        public WorktreeConfig getWorktree() {
            return worktree;
        }

        public void setWorktree(WorktreeConfig worktree) {
            this.worktree = worktree;
        }

        public TeamMemoryConfig getMemory() {
            return memory;
        }

        public void setMemory(TeamMemoryConfig memory) {
            this.memory = memory;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }

        public boolean isEnableHitt() {
            return enableHitt;
        }

        public void setEnableHitt(boolean enableHitt) {
            this.enableHitt = enableHitt;
        }

        public boolean isEnableBridge() {
            return enableBridge;
        }

        public void setEnableBridge(boolean enableBridge) {
            this.enableBridge = enableBridge;
        }

        public boolean isExposeHumanAgentsToTeammates() {
            return exposeHumanAgentsToTeammates;
        }

        public void setExposeHumanAgentsToTeammates(boolean exposeHumanAgentsToTeammates) {
            this.exposeHumanAgentsToTeammates = exposeHumanAgentsToTeammates;
        }

        public AgentCustomizer getAgentCustomizer() {
            return agentCustomizer;
        }

        public void setAgentCustomizer(AgentCustomizer agentCustomizer) {
            this.agentCustomizer = agentCustomizer;
        }
    }

    /**
     * Per-role agent spec surface consumed during configurator setup.
     *
     * <p>Mirrors Python's {@code DeepAgentSpec} use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class DeepAgentSpec {
        private String language;
        private WorkspaceSpec workspace;
        private Object model;
        private SysOperationSpec sysOperation;
        private List<Object> tools = new ArrayList<>();
        private List<String> approvalRequiredTools = new ArrayList<>();
        private String systemPrompt;

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public WorkspaceSpec getWorkspace() {
            return workspace;
        }

        public void setWorkspace(WorkspaceSpec workspace) {
            this.workspace = workspace;
        }

        public Object getModel() {
            return model;
        }

        public void setModel(Object model) {
            this.model = model;
        }

        public SysOperationSpec getSysOperation() {
            return sysOperation;
        }

        public void setSysOperation(SysOperationSpec sysOperation) {
            this.sysOperation = sysOperation;
        }

        public List<Object> getTools() {
            return tools;
        }

        public void setTools(List<Object> tools) {
            this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
        }

        public List<String> getApprovalRequiredTools() {
            return approvalRequiredTools;
        }

        public void setApprovalRequiredTools(List<String> approvalRequiredTools) {
            this.approvalRequiredTools =
                    approvalRequiredTools == null ? new ArrayList<>() : new ArrayList<>(approvalRequiredTools);
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }
    }

    /**
     * Member workspace declaration used by the configurator.
     *
     * <p>Mirrors Python's workspace spec access in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class WorkspaceSpec {
        private String rootPath = "./";
        private String language = "cn";
        private boolean stableBase;

        public WorkspaceSpec() {
        }

        public WorkspaceSpec(String rootPath, String language, boolean stableBase) {
            this.rootPath = rootPath;
            this.language = language;
            this.stableBase = stableBase;
        }

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public boolean isStableBase() {
            return stableBase;
        }

        public void setStableBase(boolean stableBase) {
            this.stableBase = stableBase;
        }

        public WorkspaceSpec copyWithRootPath(String newRootPath) {
            return new WorkspaceSpec(newRootPath, language, stableBase);
        }
    }

    /**
     * System operation descriptor used as a Java-safe value object.
     *
     * <p>Mirrors Python's {@code SysOperationSpec} use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class SysOperationSpec {
        private String id;
        private String mode = "local";
        private Object workConfig;

        public SysOperationSpec() {
        }

        public SysOperationSpec(String id, String mode, Object workConfig) {
            this.id = id;
            this.mode = mode;
            this.workConfig = workConfig;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Object getWorkConfig() {
            return workConfig;
        }

        public void setWorkConfig(Object workConfig) {
            this.workConfig = workConfig;
        }
    }

    /**
     * Declarative member specification used by predefined rosters.
     *
     * <p>Mirrors Python's {@code TeamMemberSpec} use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class TeamMemberSpec {
        private String memberName;
        private String displayName;
        private TeamRole roleType = TeamRole.TEAMMATE;
        private String persona = "";
        private String promptHint;
        private String modelName;

        public TeamMemberSpec() {
        }

        public TeamMemberSpec(String memberName, TeamRole roleType, String persona) {
            this.memberName = memberName;
            this.displayName = memberName;
            this.roleType = roleType == null ? TeamRole.TEAMMATE : roleType;
            this.persona = persona == null ? "" : persona;
        }

        public String getMemberName() {
            return memberName;
        }

        public void setMemberName(String memberName) {
            this.memberName = memberName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public TeamRole getRoleType() {
            return roleType;
        }

        public void setRoleType(TeamRole roleType) {
            this.roleType = roleType;
        }

        public String getPersona() {
            return persona;
        }

        public void setPersona(String persona) {
            this.persona = persona;
        }

        public String getPromptHint() {
            return promptHint;
        }

        public void setPromptHint(String promptHint) {
            this.promptHint = promptHint;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }

    /**
     * Team identity and model-pool context.
     *
     * <p>Mirrors Python's {@code TeamSpec} use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class TeamSpec {
        private String teamName;
        private String displayName;
        private String leaderMemberName;
        private String language;
        private Map<String, Object> metadata = new LinkedHashMap<>();
        private List<?> modelPool = new ArrayList<>();
        private String modelPoolStrategy = "round_robin";

        public TeamSpec() {
        }

        public TeamSpec(String teamName, String displayName, String leaderMemberName) {
            this.teamName = teamName;
            this.displayName = displayName;
            this.leaderMemberName = leaderMemberName;
        }

        public String getTeamName() {
            return teamName;
        }

        public void setTeamName(String teamName) {
            this.teamName = teamName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getLeaderMemberName() {
            return leaderMemberName;
        }

        public void setLeaderMemberName(String leaderMemberName) {
            this.leaderMemberName = leaderMemberName;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }

        public List<?> getModelPool() {
            return modelPool;
        }

        public void setModelPool(List<?> modelPool) {
            this.modelPool = modelPool == null ? new ArrayList<>() : new ArrayList<>(modelPool);
        }

        public String getModelPoolStrategy() {
            return modelPoolStrategy;
        }

        public void setModelPoolStrategy(String modelPoolStrategy) {
            this.modelPoolStrategy = modelPoolStrategy;
        }
    }

    /**
     * Runtime identity and resolved infra config for one member.
     *
     * <p>Mirrors Python's {@code TeamRuntimeContext} use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class TeamRuntimeContext {
        private TeamRole role = TeamRole.LEADER;
        private String memberName;
        private String persona = "";
        private TeamSpec teamSpec;
        private MessagerTransportConfig messagerConfig;
        private Map<String, Object> dbConfig = new LinkedHashMap<>();
        private Object memberModel;
        private String cliAgent;

        public TeamRole getRole() {
            return role;
        }

        public void setRole(TeamRole role) {
            this.role = role;
        }

        public String getMemberName() {
            return memberName;
        }

        public void setMemberName(String memberName) {
            this.memberName = memberName;
        }

        public String getPersona() {
            return persona;
        }

        public void setPersona(String persona) {
            this.persona = persona;
        }

        public TeamSpec getTeamSpec() {
            return teamSpec;
        }

        public void setTeamSpec(TeamSpec teamSpec) {
            this.teamSpec = teamSpec;
        }

        public MessagerTransportConfig getMessagerConfig() {
            return messagerConfig;
        }

        public void setMessagerConfig(MessagerTransportConfig messagerConfig) {
            this.messagerConfig = messagerConfig;
        }

        public Map<String, Object> getDbConfig() {
            return dbConfig;
        }

        public void setDbConfig(Map<String, Object> dbConfig) {
            this.dbConfig = dbConfig == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dbConfig);
        }

        public Object getMemberModel() {
            return memberModel;
        }

        public void setMemberModel(Object memberModel) {
            this.memberModel = memberModel;
        }

        public String getCliAgent() {
            return cliAgent;
        }

        public void setCliAgent(String cliAgent) {
            this.cliAgent = cliAgent;
        }
    }

    /**
     * Immutable assembly blueprint for the configured agent.
     *
     * <p>Mirrors Python's {@code TeamAgentBlueprint} use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class TeamAgentBlueprint {
        private final AgentCard card;
        private final TeamAgentSpec spec;
        private final TeamRuntimeContext ctx;
        private final String rolePolicy;
        private final String language;

        public TeamAgentBlueprint(
                AgentCard card,
                TeamAgentSpec spec,
                TeamRuntimeContext ctx,
                String rolePolicy,
                String language
        ) {
            this.card = card;
            this.spec = spec;
            this.ctx = ctx;
            this.rolePolicy = rolePolicy;
            this.language = language;
        }

        public AgentCard getCard() {
            return card;
        }

        public TeamAgentSpec getSpec() {
            return spec;
        }

        public TeamRuntimeContext getCtx() {
            return ctx;
        }

        public String getRolePolicy() {
            return rolePolicy;
        }

        public String getLanguage() {
            return language;
        }

        public TeamRole getRole() {
            return ctx.getRole();
        }

        public String getMemberName() {
            return ctx.getMemberName();
        }

        public String getLifecycle() {
            return spec.getLifecycle();
        }

        public TeamSpec getTeamSpec() {
            return ctx.getTeamSpec();
        }
    }

    /**
     * Per-process infrastructure container.
     *
     * <p>Mirrors Python's {@code TeamInfra} in
     * {@code openjiuwen/agent_teams/agent/infra.py}, and the configurator
     * field forwarding in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class TeamInfra {
        private Messager messager;
        private ConfiguredTeamBackend teamBackend;
        private TeamWorkspaceManager workspaceManager;
        private boolean workspaceInitialized;
        private Object taskManager;
        private Object messageManager;

        public Messager getMessager() {
            return messager;
        }

        public void setMessager(Messager messager) {
            this.messager = messager;
        }

        public ConfiguredTeamBackend getTeamBackend() {
            return teamBackend;
        }

        public void setTeamBackend(ConfiguredTeamBackend teamBackend) {
            this.teamBackend = teamBackend;
        }

        public TeamWorkspaceManager getWorkspaceManager() {
            return workspaceManager;
        }

        public void setWorkspaceManager(TeamWorkspaceManager workspaceManager) {
            this.workspaceManager = workspaceManager;
        }

        public boolean isWorkspaceInitialized() {
            return workspaceInitialized;
        }

        public void setWorkspaceInitialized(boolean workspaceInitialized) {
            this.workspaceInitialized = workspaceInitialized;
        }

        public Object getTaskManager() {
            return taskManager;
        }

        public void setTaskManager(Object taskManager) {
            this.taskManager = taskManager;
        }

        public Object getMessageManager() {
            return messageManager;
        }

        public void setMessageManager(Object messageManager) {
            this.messageManager = messageManager;
        }
    }

    /**
     * Per-instance runtime resource container.
     *
     * <p>Mirrors Python's {@code PrivateAgentResources} in
     * {@code openjiuwen/agent_teams/agent/resources.py}.</p>
     *
     * <p>Java keeps this resource dataclass as an {@link AgentConfigurator}
     * nested type because the current translation unit is the sole owner of
     * the private runtime resource container.</p>
     */
    public static class PrivateAgentResources {
        private MemberRuntime harness;
        private WorktreeManager worktreeManager;
        private Object memoryManager;
        private Object firstIterGate;
        private ModelAllocator modelAllocator;

        public MemberRuntime getHarness() {
            return harness;
        }

        public void setHarness(MemberRuntime harness) {
            this.harness = harness;
        }

        public WorktreeManager getWorktreeManager() {
            return worktreeManager;
        }

        public void setWorktreeManager(WorktreeManager worktreeManager) {
            this.worktreeManager = worktreeManager;
        }

        public Object getMemoryManager() {
            return memoryManager;
        }

        public void setMemoryManager(Object memoryManager) {
            this.memoryManager = memoryManager;
        }

        public Object getFirstIterGate() {
            return firstIterGate;
        }

        public void setFirstIterGate(Object firstIterGate) {
            this.firstIterGate = firstIterGate;
        }

        public ModelAllocator getModelAllocator() {
            return modelAllocator;
        }

        public void setModelAllocator(ModelAllocator modelAllocator) {
            this.modelAllocator = modelAllocator;
        }
    }

    /**
     * Model allocation abstraction used by leader and teammate setup.
     *
     * <p>Mirrors Python's allocator use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public interface ModelAllocator {
        Allocation allocate(String modelName);

        void loadStateDict(Map<String, Object> state);
    }

    /**
     * Allocated model handle preserved for backend wiring.
     *
     * <p>Mirrors Python's leader allocation use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public interface Allocation {
        Object toTeamModelConfig();
    }

    /**
     * Team backend data assembled by the configurator.
     *
     * <p>Mirrors Python's {@code TeamBackend} construction use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     *
     * <p>Mirrors Python's {@code create_member_handle} backend access in
     * {@code openjiuwen/agent_teams/agent/member_factory.py}.</p>
     */
    public static class ConfiguredTeamBackend
            implements TeamPolicyRail.TeamBackend,
            TeamToolRail.TeamBackend,
            TeamRuntimeManager.TeamBackendRuntime,
            SpawnManager.TeamBackendView {
        private final String teamName;
        private final String memberName;
        private final boolean leader;
        private final Map<String, Object> dbConfig;
        private final Messager messager;
        private final String teammateMode;
        private final List<TeamMemberSpec> predefinedMembers;
        private final ModelAllocator modelConfigAllocator;
        private final Allocation leaderAllocation;
        private final boolean enableHitt;
        private final boolean enableBridge;
        private final List<Object> externalCliAgents;
        private final Object onTeamCleaned;
        private final Object onTeamBuilt;
        private final String leaderMemberName;
        private final TeamMember.MemberStore memberStore;
        private final List<String> cleanupPaths = new ArrayList<>();
        private final Object taskManager = new Object();
        private final ConfiguredMessageManager messageManager = new ConfiguredMessageManager();

        public ConfiguredTeamBackend(
                String teamName,
                String memberName,
                boolean leader,
                Map<String, Object> dbConfig,
                Messager messager,
                String teammateMode,
                List<TeamMemberSpec> predefinedMembers,
                ModelAllocator modelConfigAllocator,
                Allocation leaderAllocation,
                boolean enableHitt,
                boolean enableBridge,
                List<Object> externalCliAgents,
                Object onTeamCleaned,
                Object onTeamBuilt,
                String leaderMemberName
        ) {
            this(
                    teamName,
                    memberName,
                    leader,
                    dbConfig,
                    messager,
                    teammateMode,
                    predefinedMembers,
                    modelConfigAllocator,
                    leaderAllocation,
                    enableHitt,
                    enableBridge,
                    externalCliAgents,
                    onTeamCleaned,
                    onTeamBuilt,
                    leaderMemberName,
                    null
            );
        }

        public ConfiguredTeamBackend(
                String teamName,
                String memberName,
                boolean leader,
                Map<String, Object> dbConfig,
                Messager messager,
                String teammateMode,
                List<TeamMemberSpec> predefinedMembers,
                ModelAllocator modelConfigAllocator,
                Allocation leaderAllocation,
                boolean enableHitt,
                boolean enableBridge,
                List<Object> externalCliAgents,
                Object onTeamCleaned,
                Object onTeamBuilt,
                String leaderMemberName,
                TeamMember.MemberStore memberStore
        ) {
            this.teamName = teamName;
            this.memberName = memberName;
            this.leader = leader;
            this.dbConfig = dbConfig == null ? Map.of() : new LinkedHashMap<>(dbConfig);
            this.messager = messager;
            this.teammateMode = teammateMode;
            this.predefinedMembers = predefinedMembers == null ? List.of() : new ArrayList<>(predefinedMembers);
            this.modelConfigAllocator = modelConfigAllocator;
            this.leaderAllocation = leaderAllocation;
            this.enableHitt = enableHitt;
            this.enableBridge = enableBridge;
            this.externalCliAgents = externalCliAgents == null ? List.of() : new ArrayList<>(externalCliAgents);
            this.onTeamCleaned = onTeamCleaned;
            this.onTeamBuilt = onTeamBuilt;
            this.leaderMemberName = leaderMemberName;
            this.memberStore = memberStore;
        }

        public void registerCleanupPath(String path) {
            if (isNonBlank(path)) {
                cleanupPaths.add(path);
            }
        }

        public String getTeamName() {
            return teamName;
        }

        public String getMemberName() {
            return memberName;
        }

        public boolean isLeader() {
            return leader;
        }

        public Map<String, Object> getDbConfig() {
            return dbConfig;
        }

        public Messager getMessager() {
            return messager;
        }

        public String getTeammateMode() {
            return teammateMode;
        }

        public List<TeamMemberSpec> getPredefinedMembers() {
            return predefinedMembers;
        }

        public ModelAllocator getModelConfigAllocator() {
            return modelConfigAllocator;
        }

        public Allocation getLeaderAllocation() {
            return leaderAllocation;
        }

        public boolean isEnableHitt() {
            return enableHitt;
        }

        public boolean isEnableBridge() {
            return enableBridge;
        }

        public List<Object> getExternalCliAgents() {
            return externalCliAgents;
        }

        public Object getOnTeamCleaned() {
            return onTeamCleaned;
        }

        public Object getOnTeamBuilt() {
            return onTeamBuilt;
        }

        public String getLeaderMemberName() {
            return leaderMemberName;
        }

        public TeamMember.MemberStore getMemberStore() {
            return memberStore;
        }

        public List<String> getCleanupPaths() {
            return cleanupPaths;
        }

        public Object getTaskManager() {
            return taskManager;
        }

        public Object getMessageManager() {
            return messageManager;
        }

        @Override
        public TeamRuntimeManager.TeamMessageManagerRuntime messageManager() {
            return messageManager;
        }

        @Override
        public Collection<String> bridgeAgentNames() {
            if (!enableBridge) {
                return List.of();
            }
            return predefinedMembers.stream()
                    .filter(member -> member.getRoleType() == TeamRole.BRIDGE_AGENT)
                    .map(TeamMemberSpec::getMemberName)
                    .filter(AgentConfigurator::isNonBlank)
                    .toList();
        }

        @Override
        public CompletionStage<Long> getTeamUpdatedAt() {
            return CompletableFuture.completedFuture(0L);
        }

        @Override
        public CompletionStage<TeamPolicyRail.TeamInfoSnapshot> getTeamInfo() {
            return CompletableFuture.completedFuture(new TeamPolicyRail.TeamInfoSnapshot(teamName, teamName, ""));
        }

        @Override
        public CompletionStage<Long> getMembersMaxUpdatedAt() {
            return CompletableFuture.completedFuture(0L);
        }

        @Override
        public CompletionStage<List<String>> humanAgentNames() {
            if (!enableHitt) {
                return CompletableFuture.completedFuture(List.of());
            }
            return CompletableFuture.completedFuture(predefinedMembers.stream()
                    .filter(member -> member.getRoleType() == TeamRole.HUMAN_AGENT)
                    .map(TeamMemberSpec::getMemberName)
                    .filter(AgentConfigurator::isNonBlank)
                    .sorted()
                    .toList());
        }

        @Override
        public CompletionStage<List<TeamPolicyRail.TeamMemberSnapshot>> listMembers() {
            List<TeamPolicyRail.TeamMemberSnapshot> members = predefinedMembers.stream()
                    .map(member -> new TeamPolicyRail.TeamMemberSnapshot(
                            member.getMemberName(),
                            isNonBlank(member.getDisplayName()) ? member.getDisplayName() : member.getMemberName(),
                            member.getPersona()
                    ))
                    .toList();
            return CompletableFuture.completedFuture(members);
        }

        @Override
        public CompletionStage<Object> getMember(String name) {
            if (memberStore != null) {
                return memberStore.getMember(name, teamName).thenApply(snapshot -> memberRow(name, snapshot));
            }
            if (Objects.equals(name, memberName) || Objects.equals(name, leaderMemberName)) {
                return CompletableFuture.completedFuture(new Object());
            }
            return CompletableFuture.completedFuture(predefinedMembers.stream()
                    .filter(member -> Objects.equals(member.getMemberName(), name))
                    .findFirst()
                    .map(member -> (Object) member)
                    .orElse(null));
        }

        @Override
        public CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
            if (memberStore == null) {
                return CompletableFuture.completedFuture(false);
            }
            return memberStore.updateMemberStatus(memberName, teamName, status);
        }

        private static Object memberRow(String requestedName, TeamMember.MemberSnapshot snapshot) {
            if (snapshot == null) {
                return null;
            }
            String rowMemberName = isNonBlank(snapshot.memberName()) ? snapshot.memberName() : requestedName;
            String rowRole = isNonBlank(snapshot.role()) ? snapshot.role() : TeamRole.TEAMMATE.value();
            return new SpawnManager.MemberRow(
                    rowMemberName,
                    rowRole,
                    snapshot.desc(),
                    snapshot.prompt(),
                    snapshot.modelRefJson()
            );
        }

        /**
         * In-memory message surface used by lightweight configured team backends.
         *
         * <p>Mirrors Python's {@code TeamMessageManager} send/broadcast calls in
         * {@code openjiuwen/agent_teams/tools/message_manager.py}.</p>
         */
        public static class ConfiguredMessageManager implements TeamRuntimeManager.TeamMessageManagerRuntime {
            private final List<SentMessage> sentMessages = new ArrayList<>();
            private int nextId;

            public List<SentMessage> getSentMessages() {
                return List.copyOf(sentMessages);
            }

            @Override
            public CompletionStage<String> broadcastMessage(String content, String fromMemberName) {
                return record(content, null, fromMemberName, true);
            }

            @Override
            public CompletionStage<String> sendMessage(String content, String toMemberName, String fromMemberName) {
                return record(content, toMemberName, fromMemberName, false);
            }

            private CompletionStage<String> record(
                    String content,
                    String toMemberName,
                    String fromMemberName,
                    boolean broadcast
            ) {
                String id = "msg-" + (++nextId);
                sentMessages.add(new SentMessage(id, content, toMemberName, fromMemberName, broadcast));
                return CompletableFuture.completedFuture(id);
            }
        }

        /**
         * Recorded outbound team message.
         *
         * <p>Mirrors Python message-manager records used by team routing tests in
         * {@code tests/unit_tests/agent_teams/test_team_agent.py}.</p>
         */
        public record SentMessage(
                String messageId,
                String content,
                String toMemberName,
                String fromMemberName,
                boolean broadcast
        ) {
        }
    }

    /**
     * Runtime adapter created by the configurator when no external runtime is supplied.
     *
     * <p>Mirrors Python's default {@code TeamHarness.build(...)} path in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class ConfiguredMemberRuntime implements MemberRuntime {
        private final DeepAgentSpec agentSpec;
        private final TeamRuntimeContext context;
        private final WorkspaceSpec workspace;
        private final SysOperationSpec sysOperation;
        private final List<Object> rails = new ArrayList<>();

        public ConfiguredMemberRuntime(
                DeepAgentSpec agentSpec,
                TeamRuntimeContext context,
                WorkspaceSpec workspace,
                SysOperationSpec sysOperation,
                Object teamToolRail,
                Object firstIterationGate,
                Object teamPolicyRail,
                Object toolApprovalRail,
                Object teamPlanModeRail
        ) {
            this.agentSpec = agentSpec;
            this.context = context;
            this.workspace = workspace;
            this.sysOperation = sysOperation;
            if (teamToolRail != null) {
                rails.add(teamToolRail);
            }
            if (teamPolicyRail != null) {
                rails.add(teamPolicyRail);
            }
            if (firstIterationGate != null) {
                rails.add(firstIterationGate);
            }
            if (toolApprovalRail != null) {
                rails.add(toolApprovalRail);
            }
            if (teamPlanModeRail != null) {
                rails.add(teamPlanModeRail);
            }
        }

        @Override
        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
            return Collections.emptyIterator();
        }

        @Override
        public CompletionStage<Void> steer(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> followUp(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> abort() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void initCwdForRound() {
        }

        @Override
        public boolean hasPendingInterrupt() {
            return false;
        }

        @Override
        public boolean isPendingInterruptResumeValid(Object userInput) {
            return false;
        }

        @Override
        public List<Object> findRails(Class<?> railType) {
            return rails.stream().filter(railType::isInstance).map(Object.class::cast).toList();
        }

        @Override
        public CompletionStage<Void> registerRail(Object rail) {
            rails.add(rail);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterRail(Object rail) {
            rails.remove(rail);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void registerMemberTools(Object memoryManager) {
        }

        @Override
        public CompletionStage<Void> injectMemberMemory(Object memoryManager, String query) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void runAgentCustomizer(AgentCustomizer customizer) {
            customizer.customize(this, context.getMemberName(), context.getRole().value());
        }

        @Override
        public Object workspace() {
            return workspace;
        }

        @Override
        public Object sysOperation() {
            return sysOperation;
        }

        public DeepAgentSpec getAgentSpec() {
            return agentSpec;
        }

        public TeamRuntimeContext getContext() {
            return context;
        }
    }

    /**
     * Spawn payload construction helper.
     *
     * <p>Mirrors Python's {@code SpawnPayloadBuilder} use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     *
     * <p>Mirrors Python's {@code SpawnPayloadBuilder} in
     * {@code openjiuwen/agent_teams/agent/payload.py}.</p>
     *
     * <p>Mirrors Python's leader config persistence serialization used by
     * {@code openjiuwen/agent_teams/agent/recovery_manager.py}.</p>
     */
    public static class SpawnPayloadBuilder {
        private final TeamAgentSpec spec;
        private final TeamRuntimeContext ctx;
        private final Map<String, Integer> memberPortMap = new LinkedHashMap<>();
        private int teammatePortCounter;

        public SpawnPayloadBuilder(TeamAgentSpec spec, TeamRuntimeContext ctx) {
            this.spec = spec;
            this.ctx = ctx;
        }

        public Map<String, Object> buildSpawnPayload(
                TeamRuntimeContext memberContext,
                String initialMessage
        ) {
            TeamSpec teamSpec = memberContext.getTeamSpec();
            MessagerTransportConfig memberTransport = buildMemberMessagerConfig(memberContext.getMemberName());
            Map<String, Object> coordination = new LinkedHashMap<>();
            coordination.put("team_name", teamSpec == null ? "" : teamSpec.getTeamName());
            coordination.put("display_name", teamSpec == null ? "" : teamSpec.getDisplayName());
            coordination.put("leader_member_name", teamSpec == null ? null : teamSpec.getLeaderMemberName());
            coordination.put("member_name", memberContext.getMemberName());
            coordination.put("role", memberContext.getRole().value());
            coordination.put("persona", memberContext.getPersona());
            coordination.put("transport", memberTransport == null ? null : dumpMessagerConfig(memberTransport));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("coordination", coordination);
            payload.put("query", initialMessage == null ? DEFAULT_JOIN_MESSAGE : initialMessage);
            return payload;
        }

        public TeamRuntimeContext buildMemberContext(TeamMemberSpec memberSpec) {
            TeamRuntimeContext memberContext = new TeamRuntimeContext();
            memberContext.setRole(memberSpec.getRoleType());
            memberContext.setMemberName(memberSpec.getMemberName());
            memberContext.setPersona(memberSpec.getPersona());
            memberContext.setTeamSpec(ctx.getTeamSpec());
            memberContext.setMessagerConfig(buildMemberMessagerConfig(memberSpec.getMemberName()));
            memberContext.setDbConfig(ctx.getDbConfig());
            return memberContext;
        }

        public MessagerTransportConfig buildMemberMessagerConfig(String memberName) {
            MessagerTransportConfig leaderConfig = ctx.getMessagerConfig();
            if (leaderConfig == null) {
                return null;
            }
            Map<String, Object> metadata = spec.getMetadata();
            int basePort = ((Number) metadata.getOrDefault("teammate_base_port", 16000)).intValue();
            int portOffset = ((Number) metadata.getOrDefault("teammate_port_offset", 10)).intValue();
            int portBase;
            if (memberPortMap.containsKey(memberName)) {
                portBase = memberPortMap.get(memberName);
            } else {
                portBase = basePort + teammatePortCounter * portOffset;
                teammatePortCounter++;
                memberPortMap.put(memberName, portBase);
            }
            MessagerTransportConfig config = copyMessagerConfig(leaderConfig);
            config.setNodeId(memberName);
            config.setDirectAddr("tcp://127.0.0.1:" + portBase);
            config.setPubsubPublishAddr(leaderConfig.getPubsubPublishAddr());
            config.setPubsubSubscribeAddr(leaderConfig.getPubsubSubscribeAddr());
            Map<String, Object> memberMetadata = new LinkedHashMap<>(leaderConfig.getMetadata());
            memberMetadata.remove("pubsub_bind");
            config.setMetadata(memberMetadata);
            return config;
        }

        public SpawnAgentConfig buildSpawnConfig(TeamRuntimeContext memberContext) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("spec", dumpTeamAgentSpec(spec));
            payload.put("context", dumpRuntimeContext(memberContext));
            return new SpawnAgentConfig(
                    SpawnAgentKind.TEAM_AGENT,
                    Map.of(),
                    buildMemberLoggingConfig(memberContext.getMemberName() == null ? "" : memberContext.getMemberName()),
                    null,
                    payload
            );
        }

        private static Map<String, Object> dumpMessagerConfig(MessagerTransportConfig config) {
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("backend", config.getBackend());
            dump.put("team_name", config.getTeamName());
            dump.put("node_id", config.getNodeId());
            dump.put("direct_addr", config.getDirectAddr());
            dump.put("pubsub_publish_addr", config.getPubsubPublishAddr());
            dump.put("pubsub_subscribe_addr", config.getPubsubSubscribeAddr());
            dump.put("listen_addrs", config.getListenAddrs());
            dump.put("bootstrap_peers", config.getBootstrapPeers());
            dump.put("known_peers", config.getKnownPeers());
            dump.put("request_timeout", config.getRequestTimeout());
            dump.put("metadata", config.getMetadata());
            return dump;
        }

        static Map<String, Object> buildMemberLoggingConfig(String memberTag) {
            return rewriteMemberLoggingConfig(LoggingDefaults.getLogConfigSnapshot(), memberTag);
        }

        static Map<String, Object> rewriteMemberLoggingConfig(Map<String, Object> config, String memberTag) {
            Map<String, Object> copiedConfig = deepCopyMap(config == null ? Map.of() : config);
            Object sinksObject = copiedConfig.get("sinks");
            if (!(sinksObject instanceof Map<?, ?> sinks)) {
                return copiedConfig;
            }

            for (Object sinkValue : sinks.values()) {
                if (!(sinkValue instanceof Map<?, ?> sinkMap)) {
                    continue;
                }
                Object targetObject = sinkMap.get("target");
                if (!(targetObject instanceof String target)
                        || "stdout".equals(target)
                        || "stderr".equals(target)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> writableSink = (Map<String, Object>) sinkMap;
                int split = target.lastIndexOf('/');
                if (split >= 0) {
                    writableSink.put(
                            "target",
                            target.substring(0, split) + "/teammates/" + memberTag + "/" + target.substring(split + 1)
                    );
                } else {
                    writableSink.put("target", "teammates/" + memberTag + "/" + target);
                }
            }
            return copiedConfig;
        }

        static Map<String, Object> dumpTeamAgentSpec(TeamAgentSpec spec) {
            Map<String, Object> dump = new LinkedHashMap<>();
            Map<String, Object> agents = new LinkedHashMap<>();
            for (Map.Entry<String, DeepAgentSpec> entry : spec.getAgents().entrySet()) {
                agents.put(entry.getKey(), dumpDeepAgentSpec(entry.getValue()));
            }
            dump.put("agents", agents);
            dump.put("team_name", spec.getTeamName());
            dump.put("lifecycle", spec.getLifecycle());
            dump.put("teammate_mode", spec.getTeammateMode());
            dump.put("spawn_mode", spec.getSpawnMode());
            dump.put("team_mode", spec.getTeamMode());
            dump.put("predefined_members", dumpTeamMemberSpecs(spec.getPredefinedMembers()));
            dump.put("external_cli_agents", deepCopyList(spec.getExternalCliAgents()));
            dump.put("workspace", dumpTeamWorkspaceConfig(spec.getWorkspace()));
            dump.put("worktree", spec.getWorktree());
            dump.put("memory", spec.getMemory());
            dump.put("metadata", deepCopyMap(spec.getMetadata()));
            dump.put("enable_hitt", spec.isEnableHitt());
            dump.put("enable_bridge", spec.isEnableBridge());
            dump.put("expose_human_agents_to_teammates", spec.isExposeHumanAgentsToTeammates());
            return dump;
        }

        static Map<String, Object> dumpRuntimeContext(TeamRuntimeContext context) {
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("role", context.getRole() == null ? null : context.getRole().value());
            dump.put("member_name", context.getMemberName());
            dump.put("persona", context.getPersona());
            dump.put("team_spec", dumpTeamSpec(context.getTeamSpec()));
            dump.put("messager_config", context.getMessagerConfig() == null
                    ? null
                    : dumpMessagerConfig(context.getMessagerConfig()));
            dump.put("db_config", deepCopyMap(context.getDbConfig()));
            dump.put("member_model", deepCopyValue(context.getMemberModel()));
            dump.put("cli_agent", context.getCliAgent());
            return dump;
        }

        private static Map<String, Object> dumpDeepAgentSpec(DeepAgentSpec agentSpec) {
            if (agentSpec == null) {
                return null;
            }
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("language", agentSpec.getLanguage());
            dump.put("workspace", dumpWorkspaceSpec(agentSpec.getWorkspace()));
            dump.put("model", deepCopyValue(agentSpec.getModel()));
            dump.put("sys_operation", dumpSysOperationSpec(agentSpec.getSysOperation()));
            dump.put("tools", deepCopyList(agentSpec.getTools()));
            dump.put("approval_required_tools", new ArrayList<>(agentSpec.getApprovalRequiredTools()));
            dump.put("system_prompt", agentSpec.getSystemPrompt());
            return dump;
        }

        private static List<Object> dumpTeamMemberSpecs(List<TeamMemberSpec> memberSpecs) {
            List<Object> dump = new ArrayList<>();
            for (TeamMemberSpec memberSpec : memberSpecs) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("member_name", memberSpec.getMemberName());
                item.put("display_name", memberSpec.getDisplayName());
                item.put("role_type", memberSpec.getRoleType() == null ? null : memberSpec.getRoleType().value());
                item.put("persona", memberSpec.getPersona());
                item.put("prompt_hint", memberSpec.getPromptHint());
                item.put("model_name", memberSpec.getModelName());
                dump.add(item);
            }
            return dump;
        }

        private static Map<String, Object> dumpTeamSpec(TeamSpec teamSpec) {
            if (teamSpec == null) {
                return null;
            }
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("team_name", teamSpec.getTeamName());
            dump.put("display_name", teamSpec.getDisplayName());
            dump.put("leader_member_name", teamSpec.getLeaderMemberName());
            dump.put("language", teamSpec.getLanguage());
            dump.put("metadata", deepCopyMap(teamSpec.getMetadata()));
            dump.put("model_pool", deepCopyList(teamSpec.getModelPool()));
            dump.put("model_pool_strategy", teamSpec.getModelPoolStrategy());
            return dump;
        }

        private static Map<String, Object> dumpWorkspaceSpec(WorkspaceSpec workspaceSpec) {
            if (workspaceSpec == null) {
                return null;
            }
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("root_path", workspaceSpec.getRootPath());
            dump.put("language", workspaceSpec.getLanguage());
            dump.put("stable_base", workspaceSpec.isStableBase());
            return dump;
        }

        private static Map<String, Object> dumpTeamWorkspaceConfig(TeamWorkspaceConfig workspaceConfig) {
            if (workspaceConfig == null) {
                return null;
            }
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("enabled", workspaceConfig.isEnabled());
            dump.put("root_path", workspaceConfig.getRootPath());
            dump.put("artifact_dirs", new ArrayList<>(workspaceConfig.getArtifactDirs()));
            dump.put("version_control", workspaceConfig.isVersionControl());
            dump.put("conflict_strategy", workspaceConfig.getConflictStrategy());
            dump.put("remote_url", workspaceConfig.getRemoteUrl());
            return dump;
        }

        private static Map<String, Object> dumpSysOperationSpec(SysOperationSpec sysOperationSpec) {
            if (sysOperationSpec == null) {
                return null;
            }
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("id", sysOperationSpec.getId());
            dump.put("mode", sysOperationSpec.getMode());
            dump.put("work_config", deepCopyValue(sysOperationSpec.getWorkConfig()));
            return dump;
        }

        @SuppressWarnings("unchecked")
        private static Object deepCopyValue(Object value) {
            if (value instanceof Map<?, ?> mapValue) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                    copy.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
                }
                return copy;
            }
            if (value instanceof List<?> listValue) {
                return deepCopyList(listValue);
            }
            return value;
        }

        private static Map<String, Object> deepCopyMap(Map<String, ?> source) {
            Map<String, Object> copy = new LinkedHashMap<>();
            if (source == null) {
                return copy;
            }
            for (Map.Entry<String, ?> entry : source.entrySet()) {
                copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
            }
            return copy;
        }

        private static List<Object> deepCopyList(List<?> source) {
            List<Object> copy = new ArrayList<>();
            if (source == null) {
                return copy;
            }
            for (Object value : source) {
                copy.add(deepCopyValue(value));
            }
            return copy;
        }
    }

    /**
     * Spawn-agent kind enum for child process bootstrap.
     *
     * <p>Mirrors Python's {@code SpawnAgentKind} use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public enum SpawnAgentKind {
        CLASS_AGENT("class_agent"),
        TEAM_AGENT("team_agent");

        private final String value;

        SpawnAgentKind(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * Spawn config wrapper produced for teammate processes.
     *
     * <p>Mirrors Python's {@code SpawnAgentConfig} use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class SpawnAgentConfig {
        private final SpawnAgentKind agentKind;
        private final Map<String, Object> runnerConfig;
        private final Map<String, Object> loggingConfig;
        private final String sessionId;
        private final Map<String, Object> payload;

        public SpawnAgentConfig(
                SpawnAgentKind agentKind,
                Map<String, Object> runnerConfig,
                Map<String, Object> loggingConfig,
                String sessionId,
                Map<String, Object> payload
        ) {
            this.agentKind = agentKind;
            this.runnerConfig = runnerConfig == null ? Map.of() : new LinkedHashMap<>(runnerConfig);
            this.loggingConfig = loggingConfig == null ? Map.of() : new LinkedHashMap<>(loggingConfig);
            this.sessionId = sessionId;
            this.payload = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        }

        public SpawnAgentKind getAgentKind() {
            return agentKind;
        }

        public Map<String, Object> getRunnerConfig() {
            return runnerConfig;
        }

        public Map<String, Object> getLoggingConfig() {
            return loggingConfig;
        }

        public String getSessionId() {
            return sessionId;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }
    }

    /**
     * Team memory configuration consumed by configurator setup.
     *
     * <p>Mirrors Python's memory-config use in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class TeamMemoryConfig {
        private boolean enabled;
        private boolean sharedMemory;
        private boolean autoExtract;
        private String teamMemoryDir;
        private String parentWorkspacePath;
        private String scenario;
        private String memberMemoryPromptMode;
        private Double timezoneOffsetHours;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSharedMemory() {
            return sharedMemory;
        }

        public void setSharedMemory(boolean sharedMemory) {
            this.sharedMemory = sharedMemory;
        }

        public boolean isAutoExtract() {
            return autoExtract;
        }

        public void setAutoExtract(boolean autoExtract) {
            this.autoExtract = autoExtract;
        }

        public String getTeamMemoryDir() {
            return teamMemoryDir;
        }

        public void setTeamMemoryDir(String teamMemoryDir) {
            this.teamMemoryDir = teamMemoryDir;
        }

        public String getParentWorkspacePath() {
            return parentWorkspacePath;
        }

        public void setParentWorkspacePath(String parentWorkspacePath) {
            this.parentWorkspacePath = parentWorkspacePath;
        }

        public String getScenario() {
            return scenario;
        }

        public void setScenario(String scenario) {
            this.scenario = scenario;
        }

        public String getMemberMemoryPromptMode() {
            return memberMemoryPromptMode;
        }

        public void setMemberMemoryPromptMode(String memberMemoryPromptMode) {
            this.memberMemoryPromptMode = memberMemoryPromptMode;
        }

        public Double getTimezoneOffsetHours() {
            return timezoneOffsetHours;
        }

        public void setTimezoneOffsetHours(Double timezoneOffsetHours) {
            this.timezoneOffsetHours = timezoneOffsetHours;
        }
    }

    /**
     * Memory manager construction record.
     *
     * <p>Mirrors Python's {@code TeamMemoryManagerParams} assembly in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public record TeamMemoryManagerDescriptor(
            String memberName,
            String teamName,
            String role,
            String lifecycle,
            String scenario,
            String teamMemoryDir,
            String language,
            String promptMode,
            boolean enableAutoExtract,
            String readOnlySourceWorkspace,
            Object taskManager,
            Double timezoneOffsetHours
    ) {
    }

    /**
     * First-iteration gate handle for non-human agents.
     *
     * <p>Mirrors Python's {@code FirstIterationGate} attachment in
     * {@code openjiuwen/agent_teams/agent/agent_configurator.py}.</p>
     */
    public static class FirstIterationGateHandle {
    }
}
