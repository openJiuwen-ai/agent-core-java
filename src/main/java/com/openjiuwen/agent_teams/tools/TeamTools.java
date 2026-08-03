/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.AgentTeamTimefmt;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.schema.BridgeMailboxInjectMode;
import com.openjiuwen.agent_teams.schema.MemberOpResult;
import com.openjiuwen.agent_teams.schema.TaskCreateResult;
import com.openjiuwen.agent_teams.schema.TaskDetail;
import com.openjiuwen.agent_teams.schema.TaskListResult;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.schema.TaskSummary;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.locales.TeamToolLocales;
import com.openjiuwen.agent_teams.tools.locales.TeamToolLocales.Translator;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tool wrappers that expose team management, task management, and messaging actions.
 *
 * <p>Mirrors Python's {@code team_tools.py} module in
 * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
 */
public final class TeamTools {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final Pattern MEMBER_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");

    public static final Set<String> LEADER_ONLY_TOOLS = orderedSet(
            "build_team",
            "clean_team",
            "spawn_member",
            "shutdown_member",
            "approve_plan",
            "approve_tool",
            "create_task",
            "update_task",
            "list_members"
    );
    public static final Set<String> MEMBER_ONLY_TOOLS = orderedSet("claim_task", "submit_plan");
    public static final Set<String> SHARED_TOOLS = orderedSet("view_task", "send_message", "workspace_meta");
    public static final Set<String> LEADER_TOOLS = union(LEADER_ONLY_TOOLS, SHARED_TOOLS);
    public static final Set<String> MEMBER_TOOLS = union(MEMBER_ONLY_TOOLS, SHARED_TOOLS);
    public static final Set<String> HUMAN_AGENT_TOOLS = orderedSet(
            "view_task",
            "member_complete_task",
            "send_message"
    );

    private TeamTools() {
    }

    public static List<TeamTool> createTeamTools(
            String role,
            TeamBackend agentTeam,
            String teammateMode,
            String lifecycle,
            TeamBackend.MemberStartupCallback onTeammateCreated,
            AgentConfigurator.ModelAllocator modelConfigAllocator,
            Set<String> excludeTools,
            String lang) {
        Objects.requireNonNull(agentTeam, "agentTeam");
        Translator translator = TeamToolLocales.makeTranslator(lang == null ? "cn" : lang);
        TeamTaskManager taskManager = agentTeam.getTaskManager();
        TeamMessageManager messageManager = agentTeam.getMessageManager();

        Map<String, TeamTool> allTools = new LinkedHashMap<>();
        allTools.put("build_team", new BuildTeamTool(agentTeam, translator));
        allTools.put("clean_team", new CleanTeamTool(agentTeam, translator));
        allTools.put("spawn_member", new SpawnMemberTool(agentTeam, translator, modelConfigAllocator));
        allTools.put("shutdown_member", new ShutdownMemberTool(agentTeam, translator));
        allTools.put("approve_plan", new ApprovePlanTool(agentTeam, translator));
        allTools.put("approve_tool", new ApproveToolCallTool(agentTeam, translator));
        allTools.put("list_members", new ListMembersTool(agentTeam, translator));
        allTools.put("create_task", new TaskCreateTool(agentTeam, translator));
        allTools.put("update_task", new UpdateTaskTool(agentTeam, translator));
        allTools.put("view_task", new ViewTaskToolV2(taskManager, translator));
        allTools.put("claim_task", new ClaimTaskTool(taskManager, translator));
        allTools.put("submit_plan", new SubmitPlanTool(taskManager, translator));
        allTools.put("member_complete_task", new MemberCompleteTaskTool(
                taskManager,
                agentTeam.getMemberName(),
                translator
        ));
        allTools.put("send_message", new SendMessageTool(
                messageManager,
                translator,
                agentTeam,
                onTeammateCreated
        ));

        Set<String> allowed;
        if ("human_agent".equals(role)) {
            allowed = new LinkedHashSet<>(HUMAN_AGENT_TOOLS);
        } else if ("leader".equals(role)) {
            allowed = new LinkedHashSet<>(LEADER_TOOLS);
        } else {
            allowed = new LinkedHashSet<>(MEMBER_TOOLS);
        }
        if (!MemberMode.PLAN_MODE.value().equals(teammateMode)) {
            allowed.removeAll(Set.of("approve_plan", "approve_tool", "submit_plan"));
        }
        if ("persistent".equals(lifecycle)) {
            allowed.remove("clean_team");
        }
        if (excludeTools != null) {
            allowed.removeAll(excludeTools);
        }

        List<TeamTool> result = new ArrayList<>();
        for (Map.Entry<String, TeamTool> entry : allTools.entrySet()) {
            if (allowed.contains(entry.getKey())) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    /**
     * Minimal Java tool card preserved for team-tool metadata.
     *
     * <p>Mirrors Python's {@code ToolCard} usage in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public record ToolCard(String id, String name, String description, Map<String, Object> inputParams) {
        public ToolCard {
            inputParams = inputParams == null ? Map.of() : Map.copyOf(inputParams);
        }
    }

    /**
     * Team tool contract with mapped model-facing output.
     *
     * <p>Mirrors Python's {@code TeamTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public interface TeamTool {
        ToolCard card();

        CompletionStage<ToolOutput> invoke(Map<String, ?> inputs);

        String mapResult(ToolOutput output);
    }

    /**
     * ToolOutput variant whose {@code toString()} returns the mapped LLM text.
     *
     * <p>Mirrors Python's {@code MappedToolOutput} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class MappedToolOutput extends ToolOutput {
        private final String mappedContent;

        private MappedToolOutput(ToolOutput output, String mappedContent) {
            super(output.isSuccess(), output.getData(), output.getError());
            this.mappedContent = mappedContent == null ? "" : mappedContent;
        }

        public static MappedToolOutput fromOutput(ToolOutput output, String mappedContent) {
            return new MappedToolOutput(output, mappedContent);
        }

        @Override
        public String toString() {
            return mappedContent;
        }
    }

    /**
     * Shared base for mapped team tools.
     *
     * <p>Mirrors Python's {@code TeamTool} base behavior in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public abstract static class AbstractTeamTool implements TeamTool {
        private final ToolCard card;

        protected AbstractTeamTool(ToolCard card) {
            this.card = Objects.requireNonNull(card, "card");
        }

        @Override
        public ToolCard card() {
            return card;
        }

        @Override
        public String toString() {
            return card.name();
        }

        @Override
        public final CompletionStage<ToolOutput> invoke(Map<String, ?> inputs) {
            Map<String, ?> safeInputs = inputs == null ? Map.of() : inputs;
            TEAM_LOGGER.debug("[%s] invoke start, inputs=%s", card.name(), safeInputs);
            try {
                return invokeRaw(safeInputs).handle((output, exception) -> {
                    if (exception != null) {
                        Throwable cause = unwrap(exception);
                        ToolOutput failed = ToolOutput.failure("Internal error: " + cause.getMessage());
                        TEAM_LOGGER.debug("[%s] invoke end, output=%s", card.name(), failed);
                        return MappedToolOutput.fromOutput(failed, mapResult(failed));
                    }
                    TEAM_LOGGER.debug("[%s] invoke end, output=%s", card.name(), output);
                    return MappedToolOutput.fromOutput(output, mapResult(output));
                });
            } catch (RuntimeException exception) {
                ToolOutput failed = ToolOutput.failure("Internal error: " + exception.getMessage());
                return CompletableFuture.completedFuture(MappedToolOutput.fromOutput(failed, mapResult(failed)));
            }
        }

        protected abstract CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs);

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Operation failed");
            }
            Object data = output.getData();
            return data == null ? "OK" : String.valueOf(data);
        }
    }

    /**
     * Tool that creates a team.
     *
     * <p>Mirrors Python's {@code BuildTeamTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class BuildTeamTool extends AbstractTeamTool {
        private final TeamBackend team;

        public BuildTeamTool(TeamBackend team, Translator translator) {
            super(TeamTools.card("team.build_team", "build_team", translator));
            this.team = Objects.requireNonNull(team, "team");
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            String displayName = stringValue(inputs.get("display_name"));
            String leaderDisplayName = stringValue(inputs.get("leader_display_name"));
            Boolean enableHitt = optionalBoolean(inputs.get("enable_hitt"));
            TeamBackend.CapabilityOverrides overrides = new TeamBackend.CapabilityOverrides(enableHitt, null);
            return team.buildTeam(
                    displayName,
                    stringValue(inputs.get("team_desc")),
                    leaderDisplayName,
                    stringValue(inputs.get("leader_desc")),
                    overrides
            ).thenApply(ignored -> ToolOutput.success(linkedMap(
                    "team_name", team.getTeamName(),
                    "display_name", displayName,
                    "leader_member_name", team.getMemberName(),
                    "leader_display_name", leaderDisplayName,
                    "enable_hitt", team.hittEnabled()
            )));
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Failed to build team");
            }
            Map<String, Object> data = dataMap(output);
            return "Team created: team_name=" + data.get("team_name")
                    + " display_name=" + data.get("display_name")
                    + " leader_member_name=" + data.get("leader_member_name")
                    + " leader_display_name=" + data.get("leader_display_name")
                    + " hitt_enabled=" + data.get("enable_hitt");
        }
    }

    /**
     * Tool that cleans an inactive team.
     *
     * <p>Mirrors Python's {@code CleanTeamTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class CleanTeamTool extends AbstractTeamTool {
        private final TeamBackend team;

        public CleanTeamTool(TeamBackend team, Translator translator) {
            super(TeamTools.card("team.clean_team", "clean_team", translator));
            this.team = Objects.requireNonNull(team, "team");
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            String teamName = team.getTeamName();
            return team.cleanTeam().thenApply(success -> success
                    ? ToolOutput.success(linkedMap("team_name", teamName))
                    : ToolOutput.failure("Active members remain. Use shutdown_member to close all members first."));
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Failed to clean team");
            }
            return "Team cleaned: team_name=" + dataMap(output).get("team_name");
        }
    }

    /**
     * Tool that creates teammate, human-agent, bridge-agent, or external CLI members.
     *
     * <p>Mirrors Python's {@code SpawnMemberTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class SpawnMemberTool extends AbstractTeamTool {
        private final TeamBackend team;
        private final AgentConfigurator.ModelAllocator modelAllocator;

        public SpawnMemberTool(
                TeamBackend team,
                Translator translator,
                AgentConfigurator.ModelAllocator modelAllocator) {
            super(TeamTools.card("team.spawn_member", "spawn_member", translator));
            this.team = Objects.requireNonNull(team, "team");
            this.modelAllocator = modelAllocator;
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            String memberName = stringValue(inputs.get("member_name"));
            String displayName = stringValue(inputs.get("display_name"));
            String desc = stringValue(inputs.get("desc"));
            String roleType = firstNonBlank(stringValue(inputs.get("role_type")), "teammate").toLowerCase();

            if (memberName.isEmpty() || !MEMBER_NAME_PATTERN.matcher(memberName).matches()) {
                return completedFailure("Invalid member_name '" + memberName
                        + "': must start with a lowercase ASCII letter (a-z), followed by lowercase letters, "
                        + "digits (0-9) or hyphen (-); no uppercase, underscore, whitespace, or non-ASCII characters");
            }
            if (!Set.of("teammate", "human_agent", "bridge_agent", "external_cli").contains(roleType)) {
                return completedFailure("Invalid role_type '" + roleType
                        + "'; expected 'teammate', 'human_agent', 'bridge_agent', or 'external_cli'");
            }
            return switch (roleType) {
                case "human_agent" -> spawnHuman(inputs, memberName, displayName, desc);
                case "bridge_agent" -> spawnBridge(inputs, memberName, displayName);
                case "external_cli" -> spawnExternalCli(inputs, memberName, displayName);
                default -> spawnTeammate(inputs, memberName, displayName, desc);
            };
        }

        private CompletionStage<ToolOutput> spawnHuman(
                Map<String, ?> inputs,
                String memberName,
                String displayName,
                String desc) {
            if (!team.hittEnabled()) {
                return completedFailure(
                        "Cannot spawn human agent: HITT capability is disabled "
                                + "(enable_hitt=False on TeamAgentSpec or build_team). "
                                + "Either enable HITT in the team spec or use role_type='teammate'."
                );
            }
            if (isPresent(inputs.get("model_name")) || isPresent(inputs.get("prompt"))) {
                return completedFailure(
                        "role_type='human_agent' does not accept 'model_name' or 'prompt'; "
                                + "human members use the framework template - remove these fields"
                );
            }
            return team.spawnHumanAgent(memberName, displayName, desc, null)
                    .thenApply(result -> memberOutput(result, memberName, displayName, "human_agent", null));
        }

        private CompletionStage<ToolOutput> spawnBridge(
                Map<String, ?> inputs,
                String memberName,
                String displayName) {
            if (!team.bridgeEnabled()) {
                return completedFailure(
                        "Cannot spawn bridge agent: Bridge capability is disabled "
                                + "(enable_bridge=False on TeamAgentSpec or build_team). "
                                + "Either enable Bridge in the team spec or use role_type='teammate'."
                );
            }
            String persona = firstNonBlank(stringValue(inputs.get("desc")), stringValue(inputs.get("prompt")));
            if (persona.isEmpty()) {
                return completedFailure(
                        "role_type='bridge_agent' requires a non-empty 'desc' (or 'prompt') - "
                                + "it is the persona/briefing the remote agent adopts via adapter.connect"
                );
            }
            String modeRaw = firstNonBlank(stringValue(inputs.get("mailbox_inject_mode")), "passthrough").toLowerCase();
            BridgeMailboxInjectMode injectMode;
            try {
                injectMode = BridgeMailboxInjectMode.fromValue(modeRaw);
            } catch (IllegalArgumentException exception) {
                return completedFailure("Invalid mailbox_inject_mode '" + modeRaw
                        + "'; expected 'passthrough' or 'rephrase'");
            }
            Map<String, Object> adapterConfig;
            try {
                adapterConfig = objectMap(inputs.get("adapter_config"));
            } catch (IllegalArgumentException exception) {
                return completedFailure("adapter_config must be an object/dict");
            }
            return team.spawnBridgeAgent(
                    memberName,
                    displayName,
                    persona,
                    stringValue(inputs.get("desc")),
                    emptyToNull(stringValue(inputs.get("model_name"))),
                    injectMode,
                    stringValue(inputs.get("protocol")),
                    adapterConfig
            ).thenApply(result -> {
                ToolOutput output = memberOutput(result, memberName, displayName, "bridge_agent", null);
                if (!output.isSuccess()) {
                    return output;
                }
                Map<String, Object> data = dataMap(output);
                data.put("mailbox_inject_mode", injectMode.value());
                data.put("protocol", stringValue(inputs.get("protocol")));
                return ToolOutput.success(data);
            });
        }

        private CompletionStage<ToolOutput> spawnExternalCli(
                Map<String, ?> inputs,
                String memberName,
                String displayName) {
            String cliAgent = stringValue(inputs.get("cli_agent")).trim();
            String persona = firstNonBlank(stringValue(inputs.get("desc")), stringValue(inputs.get("prompt")));
            if (cliAgent.isEmpty()) {
                return completedFailure(
                        "role_type='external_cli' requires 'cli_agent' naming a CLI kind declared in "
                                + "TeamAgentSpec.external_cli_agents (e.g. 'claude' or 'codex')"
                );
            }
            if (persona.isEmpty()) {
                return completedFailure("role_type='external_cli' requires a non-empty 'desc' (the member persona)");
            }
            return team.spawnExternalCliAgent(
                    memberName,
                    displayName,
                    cliAgent,
                    persona,
                    stringValue(inputs.get("desc")),
                    null
            ).thenApply(result -> memberOutput(result, memberName, displayName, "external_cli", cliAgent));
        }

        private CompletionStage<ToolOutput> spawnTeammate(
                Map<String, ?> inputs,
                String memberName,
                String displayName,
                String desc) {
            String modelName = emptyToNull(stringValue(inputs.get("model_name")));
            AgentConfigurator.Allocation allocation = modelAllocator == null ? null : modelAllocator.allocate(modelName);
            AgentConfigurator.AgentCard card = new AgentConfigurator.AgentCard(
                    team.getTeamName() + "_" + memberName,
                    displayName,
                    desc
            );
            return team.spawnMember(
                    memberName,
                    displayName,
                    card,
                    desc,
                    emptyToNull(stringValue(inputs.get("prompt"))),
                    MemberStatus.UNSTARTED,
                    ExecutionStatus.IDLE,
                    MemberMode.BUILD_MODE,
                    allocation,
                    TeamRole.TEAMMATE
            ).thenApply(result -> memberOutput(result, memberName, displayName, "teammate", null));
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Failed to spawn member");
            }
            Map<String, Object> data = dataMap(output);
            String cliAgent = stringValue(data.get("cli_agent"));
            String suffix = cliAgent.isEmpty() ? "" : " cli_agent=" + cliAgent;
            return "Member spawned: member_name=" + data.get("member_name")
                    + " display_name=" + data.get("display_name")
                    + " role=" + data.getOrDefault("role_type", "teammate")
                    + suffix;
        }
    }

    /**
     * Tool that asks a member to shut down.
     *
     * <p>Mirrors Python's {@code ShutdownMemberTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class ShutdownMemberTool extends AbstractTeamTool {
        private final TeamBackend team;

        public ShutdownMemberTool(TeamBackend team, Translator translator) {
            super(TeamTools.card("team.shutdown_member", "shutdown_member", translator));
            this.team = Objects.requireNonNull(team, "team");
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            String memberName = stringValue(inputs.get("member_name"));
            boolean force = booleanValue(inputs.get("force"), false);
            return team.shutdownMember(memberName, force)
                    .thenApply(result -> ToolOutput.of(
                            result.isOk(),
                            linkedMap("member_name", memberName),
                            result.isOk() ? null : result.getReason()
                    ));
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Failed to shutdown member");
            }
            return "Member shutdown: member_name=" + dataMap(output).get("member_name");
        }
    }

    /**
     * Tool that approves or rejects a submitted member plan.
     *
     * <p>Mirrors Python's {@code ApprovePlanTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class ApprovePlanTool extends AbstractTeamTool {
        private final TeamBackend team;

        public ApprovePlanTool(TeamBackend team, Translator translator) {
            super(TeamTools.card("team.approve_plan", "approve_plan", translator));
            this.team = Objects.requireNonNull(team, "team");
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            String planId = stringValue(inputs.get("plan_id"));
            boolean approved = booleanValue(inputs.get("approved"), false);
            return team.approvePlan(planId, approved, emptyToNull(stringValue(inputs.get("feedback"))))
                    .thenApply(success -> ToolOutput.of(
                            success,
                            linkedMap("plan_id", planId, "approved", approved),
                            success ? null : "Failed to approve/reject plan"
                    ));
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Failed to approve/reject plan");
            }
            Map<String, Object> data = dataMap(output);
            boolean approved = Boolean.TRUE.equals(data.get("approved"));
            String decision = approved ? "approved" : "rejected";
            return "Plan " + decision + ": plan_id=" + data.get("plan_id") + " decision=" + decision;
        }
    }

    /**
     * Tool that approves or rejects a pending teammate tool call.
     *
     * <p>Mirrors Python's {@code ApproveToolCallTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class ApproveToolCallTool extends AbstractTeamTool {
        private final TeamBackend team;

        public ApproveToolCallTool(TeamBackend team, Translator translator) {
            super(TeamTools.card("team.approve_tool", "approve_tool", translator));
            this.team = Objects.requireNonNull(team, "team");
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            String memberName = stringValue(inputs.get("member_name"));
            String toolCallId = stringValue(inputs.get("tool_call_id"));
            boolean approved = booleanValue(inputs.get("approved"), false);
            return team.approveTool(
                    memberName,
                    toolCallId,
                    approved,
                    emptyToNull(stringValue(inputs.get("feedback"))),
                    booleanValue(inputs.get("auto_confirm"), false)
            ).thenApply(success -> ToolOutput.of(
                    success,
                    linkedMap("member_name", memberName, "tool_call_id", toolCallId, "approved", approved),
                    success ? null : "Failed to approve/reject tool call"
            ));
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Failed to approve/reject tool call");
            }
            Map<String, Object> data = dataMap(output);
            boolean approved = Boolean.TRUE.equals(data.get("approved"));
            String decision = approved ? "approved" : "rejected";
            return "Tool call " + decision + ": tool_call_id=" + data.get("tool_call_id")
                    + " member_name=" + data.get("member_name") + " decision=" + decision;
        }
    }

    /**
     * Tool that lists team members.
     *
     * <p>Mirrors Python's {@code ListMembersTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class ListMembersTool extends AbstractTeamTool {
        private final TeamBackend team;

        public ListMembersTool(TeamBackend team, Translator translator) {
            super(TeamTools.card("team.list_members", "list_members", translator));
            this.team = Objects.requireNonNull(team, "team");
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            return team.listMembers().thenApply(members -> ToolOutput.success(linkedMap(
                    "members", members.stream().map(TeamTools::memberMap).toList(),
                    "count", members.size()
            )));
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Failed to list members");
            }
            List<Map<String, Object>> members = mapList(dataMap(output).get("members"));
            if (members.isEmpty()) {
                return "No members";
            }
            return members.stream()
                    .map(member -> "member_name=" + member.get("member_name")
                            + " display_name=" + member.get("display_name")
                            + " status=" + member.get("status"))
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * Tool that creates one or more tasks.
     *
     * <p>Mirrors Python's {@code TaskCreateTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class TaskCreateTool extends AbstractTeamTool {
        private final TeamTaskManager taskManager;

        public TaskCreateTool(TeamBackend agentTeam, Translator translator) {
            super(TeamTools.card("team.create_task", "create_task", translator));
            this.taskManager = Objects.requireNonNull(agentTeam, "agentTeam").getTaskManager();
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            List<Map<String, Object>> tasks = mapList(inputs.get("tasks"));
            if (tasks.isEmpty()) {
                return completedFailure("'tasks' is required");
            }
            return completed(() -> {
                if (tasks.size() == 1) {
                    Map<String, Object> spec = tasks.get(0);
                    if (stringValue(spec.get("title")).isEmpty() || stringValue(spec.get("content")).isEmpty()) {
                        return ToolOutput.failure("Task '" + specLabel(spec) + "' missing required title/content");
                    }
                    TaskCreateResult result = createOne(spec);
                    return result.ok()
                            ? ToolOutput.success(taskBrief(result.task()))
                            : ToolOutput.failure(result.reason());
                }

                List<Map<String, Object>> created = new ArrayList<>();
                List<Map<String, Object>> failures = new ArrayList<>();
                for (Map<String, Object> spec : tasks) {
                    if (stringValue(spec.get("title")).isEmpty() || stringValue(spec.get("content")).isEmpty()) {
                        failures.add(linkedMap("spec", specLabel(spec), "reason", "missing required title/content"));
                        continue;
                    }
                    TaskCreateResult result = createOne(spec);
                    if (result.ok()) {
                        created.add(taskBrief(result.task()));
                    } else {
                        failures.add(linkedMap("spec", specLabel(spec), "reason", result.reason()));
                    }
                }
                if (created.isEmpty() && !failures.isEmpty()) {
                    String joined = failures.stream()
                            .map(failure -> failure.get("spec") + ": " + failure.get("reason"))
                            .collect(Collectors.joining("; "));
                    return ToolOutput.failure("All " + failures.size() + " task creations failed: " + joined);
                }
                return ToolOutput.success(linkedMap(
                        "tasks", created,
                        "count", created.size(),
                        "skipped", failures.size(),
                        "failures", failures
                ));
            });
        }

        private TaskCreateResult createOne(Map<String, Object> spec) {
            if (isPresent(spec.get("depended_by"))) {
                return join(taskManager.addWithPriority(
                        stringValue(spec.get("title")),
                        stringValue(spec.get("content")),
                        emptyToNull(stringValue(spec.get("task_id"))),
                        stringList(spec.get("depends_on")),
                        stringList(spec.get("depended_by"))
                ));
            }
            return join(taskManager.add(
                    stringValue(spec.get("title")),
                    stringValue(spec.get("content")),
                    emptyToNull(stringValue(spec.get("task_id"))),
                    stringList(spec.get("depends_on"))
            ));
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Operation failed");
            }
            Map<String, Object> data = dataMap(output);
            if (data.containsKey("task_id") && data.containsKey("title")) {
                return "Task created: task_id=" + data.get("task_id") + " title=" + data.get("title");
            }
            List<Map<String, Object>> tasks = mapList(data.get("tasks"));
            List<String> lines = tasks.stream()
                    .map(task -> "task_id=" + task.get("task_id") + " title=" + task.get("title"))
                    .collect(Collectors.toCollection(ArrayList::new));
            lines.add("Created " + data.get("count") + ", skipped " + data.getOrDefault("skipped", 0));
            for (Map<String, Object> failure : mapList(data.get("failures"))) {
                lines.add("  - skipped " + failure.get("spec") + ": " + failure.get("reason"));
            }
            return String.join("\n", lines);
        }
    }

    /**
     * Unified task read tool.
     *
     * <p>Mirrors Python's {@code ViewTaskToolV2} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class ViewTaskToolV2 extends AbstractTeamTool {
        private final TeamTaskManager taskManager;

        public ViewTaskToolV2(TeamTaskManager taskManager, Translator translator) {
            super(TeamTools.card("team.view_task", "view_task", translator));
            this.taskManager = Objects.requireNonNull(taskManager, "taskManager");
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            String action = firstNonBlank(stringValue(inputs.get("action")), "list");
            if ("get".equals(action)) {
                String taskId = stringValue(inputs.get("task_id"));
                if (taskId.isEmpty()) {
                    return completedFailure("task_id required for get action");
                }
                return taskManager.getTaskDetail(taskId).thenApply(detail -> detail == null
                        ? ToolOutput.failure("Task not found")
                        : ToolOutput.success(detailMap(detail)));
            }
            String status = "claimable".equals(action) ? TaskStatus.PENDING.value() : emptyToNull(stringValue(inputs.get("status")));
            return taskManager.listTasksWithDeps(status).thenApply(result -> ToolOutput.success(taskListMap(result)));
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Task not found");
            }
            Map<String, Object> data = dataMap(output);
            long nowMs = InMemoryTeamDatabase.getCurrentTime();
            if (data.containsKey("content")) {
                List<String> lines = new ArrayList<>();
                lines.add("Task #" + data.get("task_id") + ": " + data.get("title"));
                lines.add("Status: " + data.get("status"));
                lines.add("Content: " + data.get("content"));
                if (isPresent(data.get("assignee"))) {
                    lines.add("Assignee: " + data.get("assignee"));
                }
                if (data.get("updated_at") instanceof Number number) {
                    lines.add("Updated: " + AgentTeamTimefmt.formatTimeContext(number.longValue(), nowMs));
                }
                if (!stringList(data.get("blocked_by")).isEmpty()) {
                    lines.add("Blocked by: " + prefixedIds(stringList(data.get("blocked_by"))));
                }
                if (!stringList(data.get("blocks")).isEmpty()) {
                    lines.add("Blocks: " + prefixedIds(stringList(data.get("blocks"))));
                }
                return String.join("\n", lines);
            }
            List<Map<String, Object>> tasks = mapList(data.get("tasks"));
            if (tasks.isEmpty()) {
                return "No tasks found";
            }
            List<String> lines = new ArrayList<>();
            for (Map<String, Object> task : tasks) {
                List<String> parts = new ArrayList<>();
                parts.add("#" + task.get("task_id") + " [" + task.get("status") + "] " + task.get("title"));
                if (isPresent(task.get("assignee"))) {
                    parts.add("(" + task.get("assignee") + ")");
                }
                if (task.get("updated_at") instanceof Number number) {
                    parts.add("(" + AgentTeamTimefmt.formatTimeContext(number.longValue(), nowMs) + ")");
                }
                List<String> blockedBy = stringList(task.get("blocked_by"));
                if (!blockedBy.isEmpty()) {
                    parts.add("[blocked by " + prefixedIds(blockedBy) + "]");
                }
                lines.add(String.join(" ", parts));
            }
            return String.join("\n", lines);
        }
    }

    /**
     * Tool that edits, assigns, or cancels tasks.
     *
     * <p>Mirrors Python's {@code UpdateTaskTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class UpdateTaskTool extends AbstractTeamTool {
        private final TeamBackend agentTeam;
        private final TeamTaskManager taskManager;
        private final Translator translator;

        public UpdateTaskTool(TeamBackend agentTeam, Translator translator) {
            super(TeamTools.card("team.update_task", "update_task", translator));
            this.agentTeam = Objects.requireNonNull(agentTeam, "agentTeam");
            this.taskManager = agentTeam.getTaskManager();
            this.translator = translator;
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            return completed(() -> invokeSync(inputs));
        }

        private ToolOutput invokeSync(Map<String, ?> inputs) {
            String taskId = stringValue(inputs.get("task_id"));
            if (taskId.isEmpty()) {
                return ToolOutput.failure("'task_id' is required");
            }
            String status = stringValue(inputs.get("status"));
            String title = emptyToNull(stringValue(inputs.get("title")));
            String content = emptyToNull(stringValue(inputs.get("content")));
            String assignee = emptyToNull(stringValue(inputs.get("assignee")));
            List<String> addBlockedBy = stringList(inputs.get("add_blocked_by"));

            if ("*".equals(taskId) && TaskStatus.CANCELLED.value().equals(status)) {
                cancelClaimedMembers();
                Set<String> skip = join(agentTeam.humanAgentNames());
                int count = join(agentTeam.cancelAllTasks(skip.isEmpty() ? null : skip));
                return ToolOutput.success(linkedMap("cancelled_count", count));
            }

            TeamTask task = join(taskManager.get(taskId)).orElse(null);
            if (task == null) {
                return ToolOutput.failure("Task not found");
            }
            if (TaskStatus.CANCELLED.value().equals(status)) {
                if (isHumanAgentLocked(task)) {
                    return ToolOutput.failure(translator.translate(
                            "update_task",
                            "error_human_agent_locked_cancel",
                            Map.of("task_id", taskId)
                    ));
                }
                cancelMemberIfClaimed(taskId);
                boolean success = join(agentTeam.cancelTask(taskId));
                return success
                        ? ToolOutput.success(linkedMap("task_id", taskId, "status", "cancelled"))
                        : ToolOutput.failure("Failed to cancel task");
            }

            List<String> updated = new ArrayList<>();
            if (title != null || content != null) {
                cancelMemberIfClaimed(taskId);
                TaskOpResult result = join(taskManager.updateTask(taskId, title, content));
                if (!result.ok()) {
                    return ToolOutput.failure(result.reason());
                }
                if (title != null) {
                    updated.add("title");
                }
                if (content != null) {
                    updated.add("content");
                }
            }
            if (assignee != null) {
                if (task.getAssignee() != null && !Objects.equals(task.getAssignee(), assignee)) {
                    if (isHumanAgentLocked(task)) {
                        return ToolOutput.failure(translator.translate(
                                "update_task",
                                "error_human_agent_locked_reassign",
                                Map.of("task_id", taskId, "new_assignee", assignee)
                        ));
                    }
                    join(agentTeam.cancelMember(task.getAssignee()));
                    TaskOpResult reset = join(taskManager.reset(taskId));
                    if (!reset.ok()) {
                        return ToolOutput.failure("Failed to reset task before reassigning from "
                                + task.getAssignee() + " to " + assignee + ": " + reset.reason());
                    }
                }
                TaskOpResult assign = join(taskManager.assign(taskId, assignee));
                if (!assign.ok()) {
                    return ToolOutput.failure(assign.reason());
                }
                updated.add("assignee");
            }
            if (!addBlockedBy.isEmpty()) {
                TaskOpResult deps = join(taskManager.addDependencies(taskId, addBlockedBy));
                if (!deps.ok()) {
                    return ToolOutput.failure(deps.reason());
                }
                updated.add("blocked_by");
            }
            if (updated.isEmpty()) {
                return ToolOutput.failure("No update specified - provide status, title, content, assignee, or add_blocked_by");
            }
            return ToolOutput.success(linkedMap(
                    "task_id", taskId,
                    "status", "updated",
                    "updated_fields", updated
            ));
        }

        private boolean cancellableAssignee(String assignee) {
            return assignee != null && !assignee.isEmpty() && !join(agentTeam.isHumanAgent(assignee));
        }

        private void cancelMemberIfClaimed(String taskId) {
            TeamTask task = join(taskManager.get(taskId)).orElse(null);
            if (task == null || !TaskStatus.CLAIMED.value().equals(task.getStatus())) {
                return;
            }
            if (cancellableAssignee(task.getAssignee())) {
                join(agentTeam.cancelMember(task.getAssignee()));
            }
        }

        private void cancelClaimedMembers() {
            List<TeamTask> claimedTasks = join(taskManager.listTasks(TaskStatus.CLAIMED.value()));
            Set<String> cancelled = new LinkedHashSet<>();
            for (TeamTask task : claimedTasks) {
                String assignee = task.getAssignee();
                if (cancelled.contains(assignee) || !cancellableAssignee(assignee)) {
                    continue;
                }
                join(agentTeam.cancelMember(assignee));
                cancelled.add(assignee);
            }
        }

        private boolean isHumanAgentLocked(TeamTask task) {
            return join(agentTeam.isHumanAgent(task.getAssignee()))
                    && TaskStatus.CLAIMED.value().equals(task.getStatus());
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Operation failed");
            }
            Map<String, Object> data = dataMap(output);
            if (data.containsKey("cancelled_count")) {
                return "Cancelled " + data.get("cancelled_count") + " tasks";
            }
            return "Task #" + data.get("task_id") + " " + data.get("status");
        }
    }

    /**
     * Tool that submits a plan-mode member execution plan.
     *
     * <p>Mirrors Python's {@code SubmitPlanTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class SubmitPlanTool extends AbstractTeamTool {
        private final TeamTaskManager taskManager;

        public SubmitPlanTool(TeamTaskManager taskManager, Translator translator) {
            super(TeamTools.card("team.submit_plan", "submit_plan", translator));
            this.taskManager = Objects.requireNonNull(taskManager, "taskManager");
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            return taskManager.submitPlan(
                    stringValue(inputs.get("task_id")),
                    stringValue(inputs.get("plan_path")),
                    emptyToNull(stringValue(inputs.get("plan_id"))),
                    null
            ).thenApply(result -> ToolOutput.of(
                    Boolean.TRUE.equals(result.get("success")),
                    result,
                    Boolean.TRUE.equals(result.get("success"))
                            ? null
                            : firstNonBlank(stringValue(result.get("message")), "Failed to submit member plan")
            ));
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Failed to submit member plan");
            }
            Map<String, Object> data = dataMap(output);
            return "Member plan submitted: task_id=" + data.get("task_id")
                    + " plan_id=" + data.get("plan_id")
                    + " status=" + data.get("status")
                    + " member_plan_md=" + data.get("member_plan_md");
        }
    }

    /**
     * Tool that claims or completes tasks for autonomous teammates.
     *
     * <p>Mirrors Python's {@code ClaimTaskTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class ClaimTaskTool extends AbstractTeamTool {
        private final TeamTaskManager taskManager;

        public ClaimTaskTool(TeamTaskManager taskManager, Translator translator) {
            super(TeamTools.card("team.claim_task", "claim_task", translator));
            this.taskManager = Objects.requireNonNull(taskManager, "taskManager");
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            return completed(() -> {
                String taskId = stringValue(inputs.get("task_id"));
                String status = stringValue(inputs.get("status"));
                TeamTask task = join(taskManager.get(taskId)).orElse(null);
                if (task == null) {
                    return ToolOutput.failure("Task not found");
                }
                TaskOpResult result;
                String targetStatus;
                if (TaskStatus.CLAIMED.value().equals(status)) {
                    result = join(taskManager.claim(taskId));
                    targetStatus = TaskStatus.CLAIMED.value();
                } else if (TaskStatus.COMPLETED.value().equals(status)) {
                    result = join(taskManager.complete(taskId));
                    targetStatus = TaskStatus.COMPLETED.value();
                } else {
                    return ToolOutput.failure("Invalid status: " + status);
                }
                if (!result.ok()) {
                    return ToolOutput.failure(result.reason());
                }
                return ToolOutput.success(linkedMap(
                        "task_id", taskId,
                        "updated_fields", List.of("status"),
                        "status_change", linkedMap("from", task.getStatus(), "to", targetStatus)
                ));
            });
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Task not found");
            }
            Map<String, Object> data = dataMap(output);
            Map<String, Object> statusChange = objectMap(data.get("status_change"));
            String result = "Task #" + data.get("task_id") + " "
                    + statusChange.get("from") + " -> " + statusChange.get("to");
            if (TaskStatus.COMPLETED.value().equals(statusChange.get("to"))) {
                result += "\n\nTask completed. Call view_task now to find your next available task.";
            }
            return result;
        }
    }

    /**
     * Human-agent self-only task completion tool.
     *
     * <p>Mirrors Python's {@code MemberCompleteTaskTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class MemberCompleteTaskTool extends AbstractTeamTool {
        private final TeamTaskManager taskManager;
        private final String memberName;

        public MemberCompleteTaskTool(TeamTaskManager taskManager, String memberName, Translator translator) {
            super(TeamTools.card("team.member_complete_task", "member_complete_task", translator));
            this.taskManager = Objects.requireNonNull(taskManager, "taskManager");
            this.memberName = stringValue(memberName);
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            return completed(() -> {
                String taskId = stringValue(inputs.get("task_id")).trim();
                if (taskId.isEmpty()) {
                    return ToolOutput.failure("'task_id' is required");
                }
                TeamTask task = join(taskManager.get(taskId)).orElse(null);
                if (task == null) {
                    return ToolOutput.failure("Task '" + taskId + "' not found");
                }
                if (!Objects.equals(task.getAssignee(), memberName)) {
                    return ToolOutput.failure("Task '" + taskId + "' is assigned to '"
                            + firstNonBlank(task.getAssignee(), "<unassigned>") + "', not '" + memberName
                            + "'; you can only complete tasks assigned to yourself");
                }
                TaskOpResult result = join(taskManager.complete(taskId));
                if (!result.ok()) {
                    return ToolOutput.failure(result.reason());
                }
                String note = emptyToNull(stringValue(inputs.get("note")).trim());
                return ToolOutput.success(linkedMap("task_id", taskId, "status", "completed", "note", note));
            });
        }

        @Override
        public String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return firstNonBlank(output.getError(), "Failed to complete task");
            }
            Map<String, Object> data = dataMap(output);
            String result = "Task #" + data.get("task_id") + " completed";
            if (isPresent(data.get("note"))) {
                result += " (note: " + data.get("note") + ")";
            }
            return result;
        }
    }

    /**
     * Tool that sends direct, multicast, and broadcast team messages.
     *
     * <p>Mirrors Python's {@code SendMessageTool} in
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    public static final class SendMessageTool extends AbstractTeamTool {
        private final TeamMessageManager messageManager;
        private final TeamBackend team;
        private final TeamBackend.MemberStartupCallback onTeammateCreated;

        public SendMessageTool(
                TeamMessageManager messageManager,
                Translator translator,
                TeamBackend team,
                TeamBackend.MemberStartupCallback onTeammateCreated) {
            super(TeamTools.card("team.send_message", "send_message", translator));
            this.messageManager = Objects.requireNonNull(messageManager, "messageManager");
            this.team = team;
            this.onTeammateCreated = onTeammateCreated;
        }

        @Override
        protected CompletionStage<ToolOutput> invokeRaw(Map<String, ?> inputs) {
            return completed(() -> {
                Object toRaw = inputs.get("to");
                String content = stringValue(inputs.get("content")).trim();
                String summary = stringValue(inputs.get("summary")).trim();
                if (content.isEmpty()) {
                    return ToolOutput.failure("'content' is required");
                }
                if (toRaw instanceof Collection<?> collection) {
                    return multicast(collection, content, summary);
                }
                if (toRaw instanceof String text) {
                    String to = text.trim();
                    if (to.isEmpty()) {
                        return ToolOutput.failure("'to' is required");
                    }
                    if ("*".equals(to)) {
                        return broadcast(content, summary);
                    }
                    return send(to, content, summary);
                }
                return ToolOutput.failure("'to' must be a string or an array of strings");
            });
        }

        private ToolOutput broadcast(String content, String summary) {
            autoStartMembers();
            String messageId = join(messageManager.broadcastMessage(content));
            if (messageId == null || messageId.isEmpty()) {
                return ToolOutput.failure("Failed to broadcast message");
            }
            return ToolOutput.success(linkedMap(
                    "type", "broadcast",
                    "from", fromMemberName(),
                    "summary", emptyToNull(summary)
            ));
        }

        private ToolOutput send(String to, String content, String summary) {
            if (team != null && !"user".equals(to) && join(team.getMember(to)).isEmpty()) {
                return ToolOutput.failure("Member '" + to + "' not found");
            }
            autoStartMembers();
            String messageId = join(messageManager.sendMessage(content, to));
            if (messageId == null || messageId.isEmpty()) {
                return ToolOutput.failure("Failed to send message to '" + to + "'");
            }
            return ToolOutput.success(linkedMap(
                    "type", "message",
                    "from", fromMemberName(),
                    "to", to,
                    "summary", emptyToNull(summary)
            ));
        }

        private ToolOutput multicast(Collection<?> rawTargets, String content, String summary) {
            List<String> targets = rawTargets.stream()
                    .map(item -> item instanceof String text ? text.trim() : "")
                    .filter(item -> !item.isEmpty())
                    .distinct()
                    .toList();
            if (targets.isEmpty()) {
                return ToolOutput.failure("'to' list must contain at least one member name");
            }
            if (targets.contains("*")) {
                return ToolOutput.failure("Cannot mix broadcast '*' with member names; use to='*' for broadcast");
            }
            if (targets.contains("user")) {
                return ToolOutput.failure("'user' cannot be combined in multicast; send to user separately");
            }
            if (team != null) {
                Set<String> roster = join(team.listMembers()).stream()
                        .map(TeamMember::getMemberName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (!roster.isEmpty() && roster.equals(new LinkedHashSet<>(targets))) {
                    return ToolOutput.failure(
                            "Multicast targets cover every other team member; use to='*' to broadcast instead - "
                                    + "same delivery, lower cost."
                    );
                }
            }
            autoStartMembers();
            List<String> delivered = new ArrayList<>();
            List<Map<String, Object>> failed = new ArrayList<>();
            for (String name : targets) {
                if (team != null && join(team.getMember(name)).isEmpty()) {
                    failed.add(linkedMap("to", name, "reason", "Member '" + name + "' not found"));
                    continue;
                }
                String messageId = join(messageManager.sendMessage(content, name));
                if (messageId == null || messageId.isEmpty()) {
                    failed.add(linkedMap("to", name, "reason", "Failed to send message to '" + name + "'"));
                    continue;
                }
                delivered.add(name);
            }
            boolean ok = failed.isEmpty();
            return ToolOutput.of(
                    ok,
                    linkedMap(
                            "type", "multicast",
                            "from", fromMemberName(),
                            "delivered", delivered,
                            "failed", failed,
                            "summary", emptyToNull(summary)
                    ),
                    ok ? null : "Multicast partially failed: " + failed.size() + "/" + targets.size()
                            + " target(s) failed"
            );
        }

        private void autoStartMembers() {
            if (team != null && onTeammateCreated != null && team.isLeader()) {
                List<String> started = join(team.startup(onTeammateCreated));
                if (!started.isEmpty()) {
                    TEAM_LOGGER.info("Auto-started members: %s", started);
                }
            }
        }

        private String fromMemberName() {
            return team == null ? "" : team.getMemberName();
        }

        @Override
        public String mapResult(ToolOutput output) {
            Map<String, Object> data = dataMap(output);
            if (!output.isSuccess()) {
                String base = firstNonBlank(output.getError(), "Failed to send message");
                if ("multicast".equals(data.get("type"))) {
                    return formatMulticastText(base, data);
                }
                return base;
            }
            if ("broadcast".equals(data.get("type"))) {
                return "Broadcast sent from " + data.get("from");
            }
            if ("multicast".equals(data.get("type"))) {
                return formatMulticastText(null, data);
            }
            return "Message sent from " + data.get("from") + " to " + data.get("to");
        }

        private static String formatMulticastText(String error, Map<String, Object> data) {
            List<String> delivered = stringList(data.get("delivered"));
            List<Map<String, Object>> failed = mapList(data.get("failed"));
            List<String> parts = new ArrayList<>();
            if (error != null && !error.isEmpty()) {
                parts.add(error);
            } else {
                String head = "Multicast sent from " + stringValue(data.get("from"));
                if (!delivered.isEmpty()) {
                    head += " to: " + String.join(", ", delivered);
                }
                head += " (" + delivered.size() + " delivered)";
                parts.add(head);
            }
            if (error != null && !error.isEmpty() && !delivered.isEmpty()) {
                parts.add("delivered: " + String.join(", ", delivered));
            }
            if (!failed.isEmpty()) {
                String failedText = failed.stream()
                        .map(item -> item.get("to") + " - " + item.get("reason"))
                        .collect(Collectors.joining("; "));
                parts.add("failed: " + failedText);
            }
            return String.join("; ", parts);
        }
    }

    private static ToolCard card(String id, String name, Translator translator) {
        return new ToolCard(id, name, safeTranslate(translator, name), inputParamsFor(name, translator));
    }

    private static Map<String, Object> inputParamsFor(String name, Translator translator) {
        return switch (name) {
            case "build_team" -> objectSchema(Map.of(
                    "display_name", stringParam(translator, name, "display_name"),
                    "team_desc", stringParam(translator, name, "team_desc"),
                    "leader_display_name", stringParam(translator, name, "leader_display_name"),
                    "leader_desc", stringParam(translator, name, "leader_desc"),
                    "enable_hitt", typedParam("boolean", translator, name, "enable_hitt")
            ), List.of("display_name", "team_desc", "leader_display_name", "leader_desc"));
            case "clean_team", "list_members" -> objectSchema(Map.of(), List.of());
            case "spawn_member" -> objectSchema(Map.of(
                    "member_name", stringParam(translator, name, "member_name"),
                    "display_name", stringParam(translator, name, "display_name"),
                    "desc", stringParam(translator, name, "desc"),
                    "role_type", enumParam(translator, name, "role_type",
                            List.of("teammate", "human_agent", "bridge_agent", "external_cli")),
                    "cli_agent", stringParam(translator, name, "cli_agent"),
                    "prompt", stringParam(translator, name, "prompt"),
                    "model_name", stringParam(translator, name, "model_name"),
                    "mailbox_inject_mode", enumParam(translator, name, "mailbox_inject_mode",
                            List.of("passthrough", "rephrase")),
                    "protocol", stringParam(translator, name, "protocol"),
                    "adapter_config", typedParam("object", translator, name, "adapter_config")
            ), List.of("member_name", "display_name", "desc"));
            case "shutdown_member" -> objectSchema(Map.of(
                    "member_name", stringParam(translator, name, "member_name"),
                    "force", typedParam("boolean", translator, name, "force")
            ), List.of("member_name"));
            case "approve_plan", "approve_tool" -> objectSchema(Map.of(
                    "plan_id", stringParam(translator, name, "plan_id"),
                    "approved", typedParam("boolean", translator, name, "approved"),
                    "reason", stringParam(translator, name, "reason")
            ), List.of("approved"));
            case "create_task" -> objectSchema(Map.of(
                    "tasks", typedParam("array", translator, name, "tasks")
            ), List.of("tasks"));
            case "view_task" -> objectSchema(Map.of(
                    "action", enumParam(translator, name, "action", List.of("list", "get", "claimable")),
                    "task_id", stringParam(translator, name, "task_id"),
                    "status", stringParam(translator, name, "status")
            ), List.of());
            case "update_task" -> objectSchema(Map.of(
                    "task_id", stringParam(translator, name, "task_id"),
                    "status", stringParam(translator, name, "status"),
                    "title", stringParam(translator, name, "title"),
                    "content", stringParam(translator, name, "content"),
                    "assignee", stringParam(translator, name, "assignee"),
                    "add_blocked_by", typedParam("array", translator, name, "add_blocked_by")
            ), List.of("task_id"));
            case "submit_plan" -> objectSchema(Map.of(
                    "plan_id", stringParam(translator, name, "plan_id"),
                    "summary", stringParam(translator, name, "summary")
            ), List.of("plan_id"));
            case "claim_task", "member_complete_task" -> objectSchema(Map.of(
                    "task_id", stringParam(translator, name, "task_id"),
                    "status", enumParam(translator, name, "status", List.of("claimed", "completed")),
                    "note", stringParam(translator, name, "note")
            ), List.of("task_id", "status"));
            case "send_message" -> objectSchema(Map.of(
                    "to", typedParam("string", translator, name, "to"),
                    "content", stringParam(translator, name, "content"),
                    "summary", stringParam(translator, name, "summary")
            ), List.of("to", "content"));
            default -> objectSchema(Map.of(), List.of());
        };
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return linkedMap(
                "type", "object",
                "properties", properties == null ? Map.of() : properties,
                "required", required == null ? List.of() : required
        );
    }

    private static Map<String, Object> stringParam(Translator translator, String tool, String key) {
        return typedParam("string", translator, tool, key);
    }

    private static Map<String, Object> typedParam(String type, Translator translator, String tool, String key) {
        return linkedMap(
                "type", type,
                "description", safeTranslate(translator, tool, key)
        );
    }

    private static Map<String, Object> enumParam(
            Translator translator,
            String tool,
            String key,
            List<String> values) {
        Map<String, Object> param = new LinkedHashMap<>(stringParam(translator, tool, key));
        param.put("enum", values);
        return param;
    }

    private static String safeTranslate(Translator translator, String name) {
        try {
            return translator.translate(name);
        } catch (RuntimeException exception) {
            return name;
        }
    }

    private static String safeTranslate(Translator translator, String tool, String key) {
        try {
            return translator.translate(tool, key);
        } catch (RuntimeException exception) {
            return key;
        }
    }

    private static ToolOutput memberOutput(
            MemberOpResult result,
            String memberName,
            String displayName,
            String roleType,
            String cliAgent) {
        Map<String, Object> data = linkedMap(
                "member_name", memberName,
                "display_name", displayName,
                "role_type", roleType
        );
        if (cliAgent != null && !cliAgent.isEmpty()) {
            data.put("cli_agent", cliAgent);
        }
        return ToolOutput.of(result.isOk(), data, result.isOk() ? null : result.getReason());
    }

    private static Map<String, Object> memberMap(TeamMember member) {
        return linkedMap(
                "member_name", member.getMemberName(),
                "team_name", member.getTeamName(),
                "display_name", member.getDisplayName(),
                "desc", member.getDesc(),
                "agent_card", member.getAgentCard(),
                "status", member.getStatus(),
                "execution_status", member.getExecutionStatus(),
                "mode", member.getMode(),
                "role", member.getRole(),
                "prompt", member.getPrompt(),
                "model_ref_json", member.getModelRefJson(),
                "updated_at", member.getUpdatedAt()
        );
    }

    private static Map<String, Object> taskBrief(Object task) {
        if (task instanceof TeamTask teamTask) {
            return linkedMap(
                    "task_id", teamTask.getTaskId(),
                    "title", teamTask.getTitle(),
                    "status", teamTask.getStatus()
            );
        }
        return objectMap(task);
    }

    private static Map<String, Object> taskListMap(TaskListResult result) {
        return linkedMap(
                "tasks", result.getTasks().stream().map(TeamTools::taskSummaryMap).toList(),
                "count", result.getCount()
        );
    }

    private static Map<String, Object> taskSummaryMap(TaskSummary task) {
        return linkedMap(
                "task_id", task.getTaskId(),
                "title", task.getTitle(),
                "status", task.getStatus(),
                "assignee", task.getAssignee(),
                "blocked_by", task.getBlockedBy(),
                "updated_at", task.getUpdatedAt()
        );
    }

    private static Map<String, Object> detailMap(TaskDetail detail) {
        Map<String, Object> result = taskSummaryMap(detail);
        result.put("content", detail.getContent());
        result.put("blocks", detail.getBlocks());
        return result;
    }

    private static String specLabel(Map<String, Object> spec) {
        return firstNonBlank(stringValue(spec.get("task_id")), stringValue(spec.get("title")), "<unnamed>");
    }

    private static String prefixedIds(List<String> ids) {
        return ids.stream().map(id -> "#" + id).collect(Collectors.joining(", "));
    }

    private static CompletionStage<ToolOutput> completedFailure(String error) {
        return CompletableFuture.completedFuture(ToolOutput.failure(error));
    }

    private static CompletionStage<ToolOutput> completed(SupplierWithException<ToolOutput> supplier) {
        try {
            return CompletableFuture.completedFuture(supplier.get());
        } catch (RuntimeException exception) {
            CompletableFuture<ToolOutput> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    private static <T> T join(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException exception) {
            throw exception;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    @SafeVarargs
    private static <T> Set<T> orderedSet(T... values) {
        return Arrays.stream(values).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return result;
    }

    private static Map<String, Object> linkedMap(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataMap(ToolOutput output) {
        Object data = output.getData();
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> objectMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("value must be a map");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                result.add(objectMap(map));
            }
        }
        return result;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .map(TeamTools::stringValue)
                .filter(text -> !text.isEmpty())
                .toList();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        Boolean result = optionalBoolean(value);
        return result == null ? defaultValue : result;
    }

    private static Boolean optionalBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private static boolean isPresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }

    /**
     * Checked supplier used to normalize synchronous tool bodies.
     *
     * <p>Mirrors Python's synchronous validation blocks inside
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
