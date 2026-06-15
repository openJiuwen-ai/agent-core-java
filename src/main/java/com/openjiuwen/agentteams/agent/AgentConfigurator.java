/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.TeamPaths;
import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.messager.MessagerFactory;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntries;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.teamworkspace.TeamWorkspaceConfig;
import com.openjiuwen.agentteams.teamworkspace.TeamWorkspaceManager;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamTools;
import com.openjiuwen.agentteams.worktree.WorktreeManager;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.memory.team.TeamMemoryManager;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfig;
import com.openjiuwen.core.runner.spawn.SpawnAgentKind;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Handles agent configuration, setup, and initialization for TeamAgent.
 *
 * <p>Mirrors Python AgentConfigurator: single entry point for configuring
 * and initializing a TeamAgent's infrastructure and DeepAgent instance.
 * Two-phase setup: setupInfra (infrastructure) and setupAgent (agent).</p>
 */
public class AgentConfigurator {

    private final AgentCard card;

    private TeamAgentSpec spec;
    private TeamRuntimeContext ctx;
    private String rolePolicy;
    private TeamWorkspaceManager workspaceManager;
    private WorktreeManager worktreeManager;
    private ModelAllocator modelAllocator;
    private Allocation leaderAllocation;
    private List<ToolCard> toolCards;
    private DeepAgent deepAgent;
    private TeamBackend teamBackend;
    private Messager messager;
    private TeamMemoryManager memoryManager;
    private FirstIterationGate firstIterGate;

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentConfigurator(AgentCard card) {
        this.card = card;
        this.toolCards = new ArrayList<>();
    }

    // ---- Properties ----

    public TeamAgentSpec getSpec() {
        return spec;
    }

    public TeamRuntimeContext getCtx() {
        return ctx;
    }

    public TeamRole getRole() {
        return ctx != null ? ctx.getRole() : TeamRole.LEADER;
    }

    public String getMemberName() {
        return ctx != null ? ctx.getMemberName() : null;
    }

    public ModelAllocator getModelAllocator() {
        return modelAllocator;
    }

    public void setModelAllocator(ModelAllocator modelAllocator) {
        this.modelAllocator = modelAllocator;
    }

    public Allocation getLeaderAllocation() {
        return leaderAllocation;
    }

    public TeamBackend getTeamBackend() {
        return teamBackend;
    }

    public DeepAgent getDeepAgent() {
        return deepAgent;
    }

    public TeamMemoryManager getMemoryManager() {
        return memoryManager;
    }

    public FirstIterationGate getFirstIterGate() {
        return firstIterGate;
    }

    public Messager getMessager() {
        return messager;
    }

    public TeamWorkspaceManager getWorkspaceManager() {
        return workspaceManager;
    }

    public WorktreeManager getWorktreeManager() {
        return worktreeManager;
    }

    // ---- Main entry point ----

    /**
     * Auto-generated for codecheck compliance.
     */
    public DeepAgent configure(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        setupInfra(spec, ctx);
        return setupAgent(spec, ctx);
    }

    // ---- Phase 1: Infrastructure ----

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setupInfra(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        this.spec = spec;
        this.ctx = ctx;

        // Create messager from transport config in metadata
        this.messager = buildMessagerFromSpec(spec);

        if (ctx.getRole() == TeamRole.LEADER && this.modelAllocator == null) {
            this.modelAllocator = ModelAllocators.buildModelAllocator(spec);
        }

        this.toolCards = registerTeamTools(spec, ctx);
    }

    private Messager buildMessagerFromSpec(TeamAgentSpec spec) {
        try {
            MessagerTransportConfig config = MessagerTransportConfig.builder()
                    .teamName(spec.getName())
                    .nodeId(resolveLeaderMemberName())
                    .backend(spec.getTransport() != null ? spec.getTransport() : "inprocess")
                    .build();
            return MessagerFactory.createMessager(config);
        } catch (Exception e) {
            Loggers.AGENT.debug("Failed to create messager: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TeamWorkspaceManager createWorkspaceManager(
            TeamWorkspaceConfig wsConfig, String teamName) {
        String wsPath = wsConfig.getRootPath() != null && !wsConfig.getRootPath().isBlank()
                ? wsConfig.getRootPath()
                : TeamPaths.teamHome(teamName).resolve("team-workspace").toString();
        try {
            Files.createDirectories(Path.of(wsPath));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create workspace directory: " + wsPath, e);
        }
        Loggers.AGENT.info("Team workspace directory ensured at {}", wsPath);
        return new TeamWorkspaceManager(wsConfig, wsPath, teamName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorktreeManager createWorktreeManager() {
        return new WorktreeManager(null);
    }

    // ---- Phase 2: Agent setup (delegated to TeamAgent) ----

    /**
     * Auto-generated for codecheck compliance.
     */
    public DeepAgent setupAgent(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        return this.deepAgent;
    }

    // ---- Tool registration ----

    private List<ToolCard> registerTeamTools(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        String teamName = ctx.getTeamId() != null ? ctx.getTeamId() : "default";
        String currentMemberName = ctx.getMemberName() != null
                ? ctx.getMemberName() : resolveLeaderMemberName();

        boolean isLeader = ctx.getRole() == TeamRole.LEADER;

        this.teamBackend = new TeamBackend(teamName, currentMemberName, isLeader, messager);
        this.teamBackend.syncMembers(spec.getMembers());

        String roleValue = ctx.getRole() != null
                ? ctx.getRole().name().toLowerCase(Locale.ROOT) : "leader";
        String teammateMode = spec.getTeammateMode() != null
                ? spec.getTeammateMode() : "build_mode";
        String teamMode = spec.getTeamMode() != null && !spec.getTeamMode().isBlank()
                ? spec.getTeamMode() : "default";
        Set<String> excludeTools = "predefined".equals(teamMode)
                ? Set.of("spawn_member") : Set.of();

        List<Tool> tools = TeamTools.createTeamTools(
                roleValue, teamBackend, teammateMode, excludeTools,
                null, null,
                modelName -> modelAllocator != null ? modelAllocator.allocate(modelName) : null
        );
        qualifyTeamToolIds(tools, teamName, currentMemberName);

        try {
            for (Tool tool : tools) {
                Runner.resourceMgr().addTool(tool, teamName + "." + currentMemberName);
            }
        } catch (Exception e) {
            Loggers.AGENT.debug("Runner.resource_mgr not available, skipping tool registration");
        }

        List<ToolCard> cards = new ArrayList<>();
        for (Tool tool : tools) {
            if (tool.getCard() != null) {
                cards.add(tool.getCard());
            }
        }
        return cards;
    }

    // ---- Model pool ----

    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateModelPool(List<ModelPoolEntry> newPool) {
        if (ctx == null) {
            return;
        }
        List<ModelPoolEntry> merged = ModelPoolEntries.inheritPoolIds(
                spec.getModelPool(), new ArrayList<>(newPool));
        spec.setModelPool(merged);
        this.modelAllocator = ModelAllocators.buildModelAllocator(spec);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void attachModelAllocator(ModelAllocator allocator, Allocation leaderAllocation) {
        this.modelAllocator = allocator;
        this.leaderAllocation = leaderAllocation;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void restoreAllocatorState(Map<String, Object> state) {
        if (modelAllocator != null && state != null) {
            modelAllocator.loadStateDict(state);
        }
    }

    // ---- Spawn helpers ----

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> buildSpawnPayload(TeamRuntimeContext ctx, String initialMessage) {
        Map<String, Object> coordination = new LinkedHashMap<>();
        coordination.put("team_name", spec.getName());
        coordination.put("display_name", spec.getName());
        coordination.put("leader_member_name", resolveLeaderMemberName());
        coordination.put("member_name", ctx.getMemberName());
        coordination.put("role", payloadRole(ctx.getRole()));
        coordination.put("persona", ctx.getMetadata() != null
                ? ctx.getMetadata().get("persona") : null);
        coordination.put("transport", null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("coordination", coordination);
        payload.put("query", initialMessage != null && !initialMessage.isBlank()
                ? initialMessage
                : "Join the team and wait for your first assignment.");
        return payload;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamRuntimeContext buildMemberContext(TeamMemberSpec memberSpec) {
        return TeamRuntimeContext.builder()
                .teamId(ctx.getTeamId())
                .sessionId(ctx.getSessionId())
                .memberName(memberSpec.getName())
                .role(memberSpec.getRole() == TeamRole.LEADER ? TeamRole.LEADER : TeamRole.MEMBER)
                .metadata(new LinkedHashMap<>())
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SpawnAgentConfig buildSpawnConfig(TeamRuntimeContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("spec", serializeSpec(spec));
        payload.put("context", serializeContext(ctx));
        return SpawnAgentConfig.builder()
                .agentKind(SpawnAgentKind.TEAM_AGENT)
                .runnerConfig(com.openjiuwen.core.runner.RunnerConfig.getRunnerConfig())
                .loggingConfig(Map.of("member_name", ctx.getMemberName()))
                .sessionId(null)
                .payload(payload)
                .build();
    }

    // ---- Private helpers ----

    private void qualifyTeamToolIds(List<Tool> tools, String teamName, String memberName) {
        String teamKey = teamName != null ? teamName : "default";
        String memberKey = memberName != null && !memberName.isBlank() ? memberName : "unknown";
        for (Tool tool : tools) {
            ToolCard card = tool.getCard();
            if (card == null || card.getId() == null || card.getId().isBlank()) {
                continue;
            }
            String qualifiedId = card.getId() + "." + teamKey + "." + memberKey;
            if (!qualifiedId.equals(card.getId())) {
                card.setId(qualifiedId);
            }
        }
    }

    private String resolveLeaderMemberName() {
        return spec.getMembers().stream()
                .filter(m -> m.getRole() == TeamRole.LEADER)
                .map(TeamMemberSpec::getName)
                .findFirst()
                .orElseGet(() -> spec.getMembers().isEmpty()
                        ? (ctx != null ? ctx.getTeamId() : "leader")
                        : spec.getMembers().get(0).getName());
    }

    private static String payloadRole(TeamRole role) {
        if (role == TeamRole.LEADER) {
            return "leader";
        }
        if (role == TeamRole.HUMAN_AGENT) {
            return "human_agent";
        }
        if (role == TeamRole.USER) {
            return "user";
        }
        return "teammate";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serializeSpec(TeamAgentSpec spec) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(spec);
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serializeContext(TeamRuntimeContext ctx) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(ctx);
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static <T> T nullValue() {
        return null;
    }
}
