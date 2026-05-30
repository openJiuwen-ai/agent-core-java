/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.Paths;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamModelConfig;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.TeamSpec;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.tools.AgentTeamsToolRegistry;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.workspace.TeamWorkspaceConfig;
import com.openjiuwen.agent_teams.workspace.TeamWorkspaceManager;
import com.openjiuwen.agent_teams.worktree.WorktreeConfig;
import com.openjiuwen.agent_teams.worktree.WorktreeManager;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.memory.team.TeamMemoryConfig;
import com.openjiuwen.core.memory.team.TeamMemoryManager;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent configuration, setup, and initialization for TeamAgent.
 * <p>
 * Responsibilities:
 * - Spec and context management
 * - Workspace and worktree setup
 * - Tool registration
 * - Model allocation
 * - DeepAgent construction
 * <p>
 * Mirrors Python's {@code AgentConfigurator} in
 * {@code openjiuwen.agent_teams.agent.agent_configurator}.
 */
public class AgentConfigurator {

    private final Object card;
    private Object spec;
    private Object ctx;
    private String rolePolicy;
    private Object workspaceManager;
    private boolean workspaceInitialized;
    private Object worktreeManager;
    private Object memoryManager;
    private ModelAllocator modelAllocator;
    private Object teamBackend;
    private Object deepAgent;
    private List<Object> registeredTools;

    /**
     * Create AgentConfigurator.
     *
     * @param card AgentCard for the agent
     */
    public AgentConfigurator(Object card) {
        this.card = card;
        this.registeredTools = new ArrayList<>();
    }

    /**
     * Initialize with spec and context.
     *
     * @param spec TeamAgentSpec
     * @param ctx  TeamRuntimeContext
     */
    public void initialize(Object spec, Object ctx) {
        this.spec = spec;
        this.ctx = ctx;

        this.rolePolicy = loadRolePolicy();
        setupWorkspace();
        setupWorktree();
        setupMemoryManager();
    }

    /**
     * Configure and build the DeepAgent.
     *
     * @return Configured DeepAgent instance
     */
    public Object buildAgent() {
        registerDefaultTools();
        allocateModels();
        return constructDeepAgent();
    }

    public String getRolePolicy() {
        return rolePolicy;
    }

    public List<Object> getRegisteredTools() {
        return new ArrayList<>(registeredTools);
    }

    public Object getWorkspaceManager() {
        return workspaceManager;
    }

    public boolean isWorkspaceInitialized() {
        return workspaceInitialized;
    }

    public Object getWorktreeManager() {
        return worktreeManager;
    }

    public Object getMemoryManager() {
        return memoryManager;
    }

    public ModelAllocator getModelAllocator() {
        return modelAllocator;
    }

    public Object getTeamBackend() {
        return teamBackend;
    }

    public Object getDeepAgent() {
        return deepAgent;
    }

    // -- Setup methods --

    private String loadRolePolicy() {
        AgentPolicy.TeamRole policyRole = resolveRole() == TeamRole.LEADER
                ? AgentPolicy.TeamRole.LEADER : AgentPolicy.TeamRole.MEMBER;
        return AgentPolicy.rolePolicy(policyRole, resolveLanguage());
    }

    private void setupWorkspace() {
        TeamAgentSpec teamSpec = asSpec();
        if (teamSpec == null) {
            return;
        }
        TeamWorkspaceConfig wsConfig = teamSpec.getWorkspace();
        if (wsConfig == null || !wsConfig.isEnabled()) {
            workspaceManager = null;
            workspaceInitialized = false;
            return;
        }

        String teamName = resolveTeamName();
        String wsPath = wsConfig.getRootPath();
        if (wsPath == null || wsPath.isBlank()) {
            wsPath = Paths.teamHome(teamName).resolve("team-workspace").toString();
        }
        try {
            Files.createDirectories(Path.of(wsPath));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize team workspace: " + wsPath, e);
        }
        workspaceManager = new TeamWorkspaceManager(wsConfig, wsPath, teamName);
        workspaceInitialized = true;
    }

    private void setupWorktree() {
        TeamAgentSpec teamSpec = asSpec();
        if (teamSpec == null) {
            return;
        }
        WorktreeConfig worktreeConfig = teamSpec.getWorktree();
        if (worktreeConfig == null || !worktreeConfig.isEnabled()) {
            worktreeManager = null;
            return;
        }
        String workspaceRoot = workspaceManager instanceof TeamWorkspaceManager manager
                ? manager.getWorkspacePath() : null;
        worktreeManager = new WorktreeManager(worktreeConfig, null, workspaceRoot);
    }

    private void setupMemoryManager() {
        TeamAgentSpec teamSpec = asSpec();
        if (teamSpec == null) {
            return;
        }
        TeamMemoryConfig memoryConfig = teamSpec.getMemory();
        if (memoryConfig == null || !memoryConfig.isEnabled()) {
            memoryManager = null;
            return;
        }
        String teamName = resolveTeamName();
        String memberName = resolveMemberName();
        String lifecycle = teamSpec.getLifecycle() != null ? teamSpec.getLifecycle().name().toLowerCase() : "temporary";
        String teamMemoryDir = memoryConfig.getTeamMemoryDir();
        if ((teamMemoryDir == null || teamMemoryDir.isBlank()) && memoryConfig.isSharedMemory()
                && "persistent".equals(lifecycle)) {
            teamMemoryDir = Paths.teamMemoryDir(teamName).toString();
        }
        memoryManager = new TeamMemoryManager(
                memberName,
                teamName,
                resolveRole().name().toLowerCase(),
                lifecycle,
                memoryConfig.getScenario(),
                null,
                resolveLanguage(),
                memoryConfig.getMemberMemoryPromptMode(),
                memoryConfig.isAutoExtract(),
                null,
                null,
                teamMemoryDir
        );
    }

    private void registerDefaultTools() {
        TeamAgentSpec teamSpec = asSpec();
        if (teamSpec == null) {
            registeredTools = new ArrayList<>();
            return;
        }

        TeamRole role = resolveRole();
        TeamBackend backend = new TeamBackend(
                resolveTeamName(),
                resolveMemberName(),
                role == TeamRole.LEADER,
                "plan_mode".equalsIgnoreCase(teamSpec.getTeammateMode()) ? MemberMode.PLAN_MODE : MemberMode.BUILD_MODE,
                teamSpec.getPredefinedMembers()
        );
        if (workspaceManager instanceof TeamWorkspaceManager manager) {
            backend.registerCleanupPath(manager.getWorkspacePath());
        }
        backend.registerCleanupPath(Paths.teamHome(resolveTeamName()).toString());
        teamBackend = backend;

        List<Tool> tools = AgentTeamsToolRegistry.createTeamTools(backend, role, teamSpec.getTeammateMode());
        registeredTools = new ArrayList<>();
        for (Tool tool : tools) {
            if (tool != null && tool.getCard() != null) {
                registeredTools.add(tool.getCard());
            }
        }
        try {
            Runner.resourceMgr().addTools(tools, null);
        } catch (Exception ignored) {
            // Runner may not be configured in small unit tests; DeepAgent still receives the cards below.
        }
    }

    private void allocateModels() {
        TeamAgentSpec teamSpec = asSpec();
        TeamSpec allocatorTeamSpec = resolveAllocatorTeamSpec(teamSpec);
        if (teamSpec == null || resolveRole() != TeamRole.LEADER
                || allocatorTeamSpec == null || allocatorTeamSpec.getModelPool().isEmpty()) {
            modelAllocator = null;
            return;
        }
        modelAllocator = ModelAllocators.buildModelAllocator(teamSpec, allocatorTeamSpec);
    }

    private Object constructDeepAgent() {
        TeamAgentSpec teamSpec = asSpec();
        DeepAgentConfig config = new DeepAgentConfig();
        if (teamSpec != null) {
            DeepAgentSpec agentSpec = resolveAgentSpec(teamSpec, resolveRole(), resolveMemberName());
            if (agentSpec != null && agentSpec.getConfig() != null) {
                config = agentSpec.getConfig();
            }
            TeamModelConfig modelConfig = resolveModelConfig(agentSpec);
            if (modelConfig != null) {
                config.setModelClientConfig(modelConfig.getModelClientConfig());
                config.setModelRequestConfig(modelConfig.getModelRequestConfig());
            }
        }
        if (card instanceof AgentCard agentCard) {
            config.setCard(agentCard);
        }
        String basePrompt = config.getSystemPrompt() != null ? config.getSystemPrompt() : "";
        String policy = rolePolicy != null ? rolePolicy : loadRolePolicy();
        config.setSystemPrompt(basePrompt.isBlank() ? policy : basePrompt + "\n\n" + policy);
        config.setTools(registeredTools.stream()
                .filter(ToolCard.class::isInstance)
                .map(ToolCard.class::cast)
                .toList());

        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        deepAgent = agent;
        if (memoryManager instanceof TeamMemoryManager manager) {
            manager.initToolkit().join();
            manager.registerTools(agent);
        }
        return agent;
    }

    /**
     * Resolve team mode from spec.
     * <p>
     * Mirrors Python: _resolve_team_mode(spec)
     *
     * @param spec TeamAgentSpec
     * @return Team mode string ("predefined" or "default")
     */
    public static String resolveTeamMode(Object spec) {
        if (spec instanceof TeamAgentSpec teamSpec) {
            if (teamSpec.getTeamMode() != null && !teamSpec.getTeamMode().isBlank()) {
                return teamSpec.getTeamMode();
            }
            return teamSpec.getPredefinedMembers() != null && !teamSpec.getPredefinedMembers().isEmpty()
                    ? "predefined" : "default";
        }
        return "default";
    }

    private TeamAgentSpec asSpec() {
        return spec instanceof TeamAgentSpec teamSpec ? teamSpec : null;
    }

    private TeamRuntimeContext asContext() {
        return ctx instanceof TeamRuntimeContext context ? context : null;
    }

    private TeamRole resolveRole() {
        TeamRuntimeContext context = asContext();
        return context != null && context.getRole() != null ? context.getRole() : TeamRole.LEADER;
    }

    private String resolveLanguage() {
        TeamRuntimeContext context = asContext();
        if (context != null && context.getTeamSpec() != null
                && context.getTeamSpec().getLanguage() != null
                && !context.getTeamSpec().getLanguage().isBlank()) {
            return context.getTeamSpec().getLanguage();
        }
        TeamAgentSpec teamSpec = asSpec();
        if (teamSpec != null && teamSpec.getLanguage() != null && !teamSpec.getLanguage().isBlank()) {
            return teamSpec.getLanguage();
        }
        return "cn";
    }

    private String resolveTeamName() {
        TeamRuntimeContext context = asContext();
        TeamSpec contextTeamSpec = context != null ? context.getTeamSpec() : null;
        if (contextTeamSpec != null && contextTeamSpec.getTeamName() != null
                && !contextTeamSpec.getTeamName().isBlank()) {
            return contextTeamSpec.getTeamName();
        }
        TeamAgentSpec teamSpec = asSpec();
        return teamSpec != null && teamSpec.getTeamName() != null && !teamSpec.getTeamName().isBlank()
                ? teamSpec.getTeamName() : "agent_team";
    }

    private String resolveMemberName() {
        TeamRuntimeContext context = asContext();
        if (context != null && context.getMemberName() != null && !context.getMemberName().isBlank()) {
            return context.getMemberName();
        }
        TeamAgentSpec teamSpec = asSpec();
        if (teamSpec != null && teamSpec.getLeader() != null
                && teamSpec.getLeader().getMemberName() != null
                && !teamSpec.getLeader().getMemberName().isBlank()) {
            return teamSpec.getLeader().getMemberName();
        }
        return resolveRole() == TeamRole.LEADER ? "team_leader" : "teammate";
    }

    private static DeepAgentSpec resolveAgentSpec(TeamAgentSpec spec, TeamRole role, String memberName) {
        Map<String, DeepAgentSpec> agents = spec.getAgents();
        if (memberName != null && agents.containsKey(memberName)) {
            return agents.get(memberName);
        }
        String roleKey = role != null ? role.name().toLowerCase() : "leader";
        DeepAgentSpec byRole = agents.get(roleKey);
        if (byRole != null) {
            return byRole;
        }
        DeepAgentSpec teammate = agents.get("teammate");
        return teammate != null ? teammate : agents.get("leader");
    }

    private TeamSpec resolveAllocatorTeamSpec(TeamAgentSpec teamSpec) {
        TeamRuntimeContext context = asContext();
        TeamSpec team = context != null ? context.getTeamSpec() : null;
        if (team != null && !team.getModelPool().isEmpty()) {
            return team;
        }
        if (teamSpec == null || teamSpec.getModelPool().isEmpty()) {
            return team;
        }
        TeamSpec fallback = team != null ? team : new TeamSpec();
        fallback.setModelPool(teamSpec.getModelPool());
        fallback.setModelPoolStrategy(teamSpec.getModelPoolStrategy());
        if (team != null) {
            context.setTeamSpec(fallback);
        }
        return fallback;
    }

    private TeamModelConfig resolveModelConfig(DeepAgentSpec agentSpec) {
        TeamRuntimeContext context = asContext();
        if (context != null && context.getMemberModel() != null) {
            return context.getMemberModel();
        }
        return agentSpec != null ? agentSpec.getModel() : null;
    }
}
