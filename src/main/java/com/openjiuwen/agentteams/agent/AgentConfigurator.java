/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.openjiuwen.core.common.exception.BaseError;
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
import java.util.Map;
import java.util.Set;

/**
 * Handles agent configuration, setup, and initialization for TeamAgent.
 * <p>
 * Mirrors Python AgentConfigurator: single entry point for configuring
 * and initializing a TeamAgent's infrastructure and DeepAgent instance.
 * Two-phase setup: setupInfra (infrastructure) and setupAgent (agent).
 * </p>
 * 
 * @since 0.1.7
 */
public class AgentConfigurator {
    private final AgentCard card;

    private TeamAgentSpec spec;
    private TeamRuntimeContext ctx;
    private String rolePolicy;
    private ModelAllocator modelAllocator;
    private Allocation leaderAllocation;
    private List<ToolCard> toolCards;
    private DeepAgent deepAgent;
    private TeamBackend teamBackend;
    private Messager messager;
    private TeamMemoryManager memoryManager;

    /**
     * Create an AgentConfigurator bound to the given agent card.
     *
     * @param card the agent card defining the agent identity and capabilities
     * @since 0.1.7
     */
    public AgentConfigurator(AgentCard card) {
        this.card = card;
        this.toolCards = new ArrayList<>();
    }

    // ---- Properties ----

    /**
     * Return the team agent specification.
     *
     * @return the team agent spec, or null if not yet configured
     * @since 0.1.7
     */
    public TeamAgentSpec getSpec() {
        return spec;
    }

    /**
     * Return the runtime context for the current team session.
     *
     * @return the runtime context, or null if not yet configured
     * @since 0.1.7
     */
    public TeamRuntimeContext getCtx() {
        return ctx;
    }

    /**
     * Return the team role derived from the runtime context.
     *
     * @return the team role, defaults to LEADER if context is absent
     * @since 0.1.7
     */
    public TeamRole getRole() {
        return ctx != null ? ctx.getRole() : TeamRole.LEADER;
    }

    /**
     * Return the member name derived from the runtime context.
     *
     * @return the member name, or null if context is absent
     * @since 0.1.7
     */
    public String getMemberName() {
        return ctx != null ? ctx.getMemberName() : null;
    }

    /**
     * Return the model allocator used for LLM model selection.
     *
     * @return the model allocator, or null if not yet set
     * @since 0.1.7
     */
    public ModelAllocator getModelAllocator() {
        return modelAllocator;
    }

    /**
     * Replace the model allocator instance.
     *
     * @param modelAllocator the new model allocator
     * @since 0.1.7
     */
    public void setModelAllocator(ModelAllocator modelAllocator) {
        this.modelAllocator = modelAllocator;
    }

    /**
     * Return the team backend for inter-member communication.
     *
     * @return the team backend, or null if infrastructure not yet set up
     * @since 0.1.7
     */
    public TeamBackend getTeamBackend() {
        return teamBackend;
    }

    /**
     * Return the configured DeepAgent instance.
     *
     * @return the deep agent, or null if not yet configured
     * @since 0.1.7
     */
    public DeepAgent getDeepAgent() {
        return deepAgent;
    }

    /**
     * Return the team memory manager.
     *
     * @return the memory manager, or null if not yet set up
     * @since 0.1.7
     */
    public TeamMemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * Return the messager used for team event transport.
     *
     * @return the messager, or null if not yet created
     * @since 0.1.7
     */
    public Messager getMessager() {
        return messager;
    }

    // ---- Main entry point ----

    /**
     * Two-phase setup: infrastructure then agent, returning the configured DeepAgent.
     *
     * @param spec the team agent specification
     * @param ctx the runtime context for this team session
     * @return the fully configured DeepAgent instance
     * @since 0.1.7
     */
    public DeepAgent configure(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        setupInfra(spec, ctx);
        return setupAgent(spec, ctx);
    }

    // ---- Phase 1: Infrastructure ----

    /**
     * Phase 1: create messager, model allocator, and register team tools.
     *
     * @param spec the team agent specification
     * @param ctx the runtime context for this team session
     * @since 0.1.7
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

    /**
     * Create a Messager from the transport config in the spec.
     *
     * @param spec the team agent specification
     * @return the created messager, or null on failure
     * @since 0.1.7
     */
    private Messager buildMessagerFromSpec(TeamAgentSpec spec) {
        try {
            MessagerTransportConfig config =
                MessagerTransportConfig.builder().teamName(spec.getName()).nodeId(resolveLeaderMemberName())
                        .backend(spec.getTransport() != null ? spec.getTransport() : "inprocess").build();
            return MessagerFactory.createMessager(config);
        } catch (IllegalArgumentException | NullPointerException e) {
            Loggers.AGENT.debug("Failed to create messager: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create a TeamWorkspaceManager, ensuring the workspace directory exists.
     *
     * @param wsConfig workspace configuration including root path
     * @param teamName team name used for default path resolution
     * @return the created workspace manager
     * @since 0.1.7
     */
    public static TeamWorkspaceManager createWorkspaceManager(TeamWorkspaceConfig wsConfig, String teamName) {
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

    // ---- Phase 2: Agent setup (delegated to TeamAgent) ----

    /**
     * Phase 2: return the DeepAgent (actual wiring delegated to TeamAgent).
     *
     * @param spec the team agent specification
     * @param ctx the runtime context for this team session
     * @return the deep agent instance
     * @since 0.1.7
     */
    public DeepAgent setupAgent(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        return this.deepAgent;
    }

    // ---- Tool registration ----

    /**
     * Create TeamBackend and register team tools with the resource manager.
     *
     * @param spec the team agent specification
     * @param ctx the runtime context for this team session
     * @return the list of tool cards for the registered team tools
     * @since 0.1.7
     */
    private List<ToolCard> registerTeamTools(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        String teamName = ctx.getTeamId() != null ? ctx.getTeamId() : "default";
        String currentMemberName = ctx.getMemberName() != null ? ctx.getMemberName() : resolveLeaderMemberName();

        boolean isLeader = ctx.getRole() == TeamRole.LEADER;

        this.teamBackend = new TeamBackend(
                teamName, currentMemberName, isLeader, messager, ctx.getSessionId());
        this.teamBackend.syncMembers(spec.getMembers());

        String roleValue = ctx.getRole() != null ? payloadRole(ctx.getRole()) : "leader";
        String teammateMode = spec.getTeammateMode() != null ? spec.getTeammateMode() : "build_mode";
        String teamMode = spec.getTeamMode() != null && !spec.getTeamMode().isBlank() ? spec.getTeamMode() : "default";
        Set<String> excludeTools = "predefined".equals(teamMode) ? Set.of("spawn_member") : Set.of();

        List<Tool> tools = TeamTools.createTeamTools(TeamTools.TeamToolsConfig.builder()
                .role(roleValue).backend(teamBackend)
                .teammateMode(teammateMode).excludeTools(excludeTools)
                .workspaceManager(null).worktreeManager(null)
                .modelConfigAllocator(modelName ->
                        modelAllocator != null ? modelAllocator.allocate(modelName) : null)
                .build());
        qualifyTeamToolIds(tools, teamName, currentMemberName);

        try {
            for (Tool tool : tools) {
                Runner.resourceMgr().addTool(tool, teamName + "." + currentMemberName);
            }
        } catch (BaseError | NullPointerException e) {
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
     * Merge new model pool entries into the spec and rebuild the allocator.
     *
     * @param newPool the new model pool entries to merge
     * @since 0.1.7
     */
    public void updateModelPool(List<ModelPoolEntry> newPool) {
        if (ctx == null) {
            return;
        }
        List<ModelPoolEntry> merged = ModelPoolEntries.inheritPoolIds(spec.getModelPool(), new ArrayList<>(newPool));
        spec.setModelPool(merged);
        this.modelAllocator = ModelAllocators.buildModelAllocator(spec);
    }

    /**
     * Attach an externally created model allocator and leader allocation.
     *
     * @param allocator the model allocator to use
     * @param leaderAllocation the model allocation reserved for the leader
     * @since 0.1.7
     */
    public void attachModelAllocator(ModelAllocator allocator, Allocation leaderAllocation) {
        this.modelAllocator = allocator;
        this.leaderAllocation = leaderAllocation;
    }

    /**
     * Restore allocator internal state from a previously saved state dict.
     *
     * @param state the state dictionary to load
     * @since 0.1.7
     */
    public void restoreAllocatorState(Map<String, Object> state) {
        if (modelAllocator != null && state != null) {
            modelAllocator.loadStateDict(state);
        }
    }

    // ---- Spawn helpers ----

    /**
     * Build the spawn payload map for a new teammate process.
     *
     * @param ctx the runtime context of the member to spawn
     * @param initialMessage the first message sent to the new teammate, or null for default
     * @return the spawn payload as a plain map
     * @since 0.1.7
     */
    public Map<String, Object> buildSpawnPayload(TeamRuntimeContext ctx, String initialMessage) {
        Map<String, Object> coordination = new LinkedHashMap<>();
        coordination.put("team_name", spec.getName());
        coordination.put("display_name", spec.getName());
        coordination.put("leader_member_name", resolveLeaderMemberName());
        coordination.put("member_name", ctx.getMemberName());
        coordination.put("role", payloadRole(ctx.getRole()));
        coordination.put("persona", ctx.getMetadata() != null ? ctx.getMetadata().get("persona") : null);
        coordination.put("transport", null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("coordination", coordination);
        payload.put("query",
                initialMessage != null && !initialMessage.isBlank()
                        ? initialMessage
                        : "Join the team and wait for your first assignment.");
        return payload;
    }

    /**
     * Build a TeamRuntimeContext for a member from its spec.
     *
     * @param memberSpec the member specification
     * @return the constructed runtime context
     * @since 0.1.7
     */
    public TeamRuntimeContext buildMemberContext(TeamMemberSpec memberSpec) {
        return TeamRuntimeContext.builder().teamId(ctx.getTeamId()).sessionId(ctx.getSessionId())
                .memberName(memberSpec.getName())
                .role(memberSpec.getRole() == TeamRole.LEADER ? TeamRole.LEADER : TeamRole.MEMBER)
                .metadata(new LinkedHashMap<>()).build();
    }

    /**
     * Build a SpawnAgentConfig for spawning a teammate as a sub-process.
     *
     * @param ctx the runtime context of the member to spawn
     * @return the spawn agent configuration
     * @since 0.1.7
     */
    public SpawnAgentConfig buildSpawnConfig(TeamRuntimeContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("spec", serializeSpec(spec));
        payload.put("context", serializeContext(ctx));
        return SpawnAgentConfig.builder().agentKind(SpawnAgentKind.TEAM_AGENT)
                .runnerConfig(com.openjiuwen.core.runner.RunnerConfig.getRunnerConfig())
                .loggingConfig(Map.of("member_name", ctx.getMemberName())).sessionId(null).payload(payload).build();
    }

    // ---- Private helpers ----

    /**
     * Prefix tool IDs with team and member keys for namespace isolation.
     *
     * @param tools the tools to qualify
     * @param teamName the team name
     * @param memberName the member name
     * @since 0.1.7
     */
    private void qualifyTeamToolIds(List<Tool> tools, String teamName, String memberName) {
        String teamKey = teamName != null ? teamName : "default";
        String memberKey = memberName != null && !memberName.isBlank() ? memberName : "unknown";
        for (Tool tool : tools) {
            ToolCard toolCard = tool.getCard();
            if (toolCard == null || toolCard.getId() == null || toolCard.getId().isBlank()) {
                continue;
            }
            String qualifiedId = toolCard.getId() + "." + teamKey + "." + memberKey;
            if (!qualifiedId.equals(toolCard.getId())) {
                toolCard.setId(qualifiedId);
            }
        }
    }

    /**
     * Find the leader member name from the spec, with fallbacks.
     *
     * @return the leader member name
     * @since 0.1.7
     */
    private String resolveLeaderMemberName() {
        return spec.getMembers().stream().filter(m -> m.getRole() == TeamRole.LEADER).map(TeamMemberSpec::getName)
                .findFirst()
                .orElseGet(() -> spec.getMembers().isEmpty()
                        ? (ctx != null ? ctx.getTeamId() : "leader")
                        : spec.getMembers().get(0).getName());
    }

    /**
     * Convert a TeamRole to the snake_case string used in spawn payloads.
     *
     * @param role the team role to convert
     * @return the snake_case role string
     * @since 0.1.7
     */
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
    /**
     * Serialize the spec to a plain map via Jackson round-trip.
     *
     * @param spec the team agent specification
     * @return the serialized map, or an empty map on failure
     * @since 0.1.7
     */
    private static Map<String, Object> serializeSpec(TeamAgentSpec spec) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(spec);
            return mapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * Serialize the context to a plain map via Jackson round-trip.
     *
     * @param ctx the runtime context
     * @return the serialized map, or an empty map on failure
     * @since 0.1.7
     */
    private static Map<String, Object> serializeContext(TeamRuntimeContext ctx) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(ctx);
            return mapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * Return null, used as a type-safe null placeholder.
     *
     * @return null
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }
}
