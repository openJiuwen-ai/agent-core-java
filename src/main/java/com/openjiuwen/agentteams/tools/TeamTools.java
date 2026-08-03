/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import com.openjiuwen.agentteams.agent.Allocation;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.teamworkspace.TeamWorkspaceManager;
import com.openjiuwen.agentteams.teamworkspace.WorkspaceFileLock;
import com.openjiuwen.agentteams.worktree.WorktreeChangeSummary;
import com.openjiuwen.agentteams.worktree.WorktreeManager;
import com.openjiuwen.agentteams.worktree.WorktreeSession;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.tools.ToolOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.function.Function;

/**
 * Role-filtered team tool wrappers aligned with Python agent_teams/tools/team_tools.py.
 *
 * @since 2026/7/9
 */
public final class TeamTools {
    /**
     * Tool names restricted to the leader role.
     */
    public static final Set<String> LEADER_ONLY_TOOLS = Set.of(
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

    /**
     * Tool names restricted to the member role.
     */
    public static final Set<String> MEMBER_ONLY_TOOLS = Set.of("claim_task", "enter_worktree", "exit_worktree");

    /**
     * Tool names shared between leader and member roles.
     */
    public static final Set<String> SHARED_TOOLS = Set.of("view_task", "send_message", "workspace_meta");

    /**
     * Tool names available to the human_agent role.
     */
    public static final Set<String> HUMAN_AGENT_TOOLS = Set.of("send_message");

    /**
     * Combined tool set for the leader role (leader-only plus shared).
     */
    public static final Set<String> LEADER_TOOLS = union(LEADER_ONLY_TOOLS, SHARED_TOOLS);

    /**
     * Combined tool set for the member role (member-only plus shared).
     */
    public static final Set<String> MEMBER_TOOLS = union(MEMBER_ONLY_TOOLS, SHARED_TOOLS);

    private TeamTools() {
    }

    /**
     * Creates team tools filtered by role with default teammate mode and no exclusions.
     *
     * @param role agent role (leader, member, or human_agent)
     * @param backend team backend providing data and message access
     * @return list of tools allowed for the given role
     */
    public static List<Tool> createTeamTools(String role, TeamBackend backend) {
        return createTeamTools(role, backend, "build_mode", Set.of());
    }

    /**
     * Creates team tools filtered by role with specified teammate mode and exclusions.
     *
     * @param role agent role (leader, member, or human_agent)
     * @param backend team backend providing data and message access
     * @param teammateMode teammate mode (build_mode or plan_mode)
     * @param excludeTools set of tool names to exclude from the result
     * @return list of tools allowed for the given role and mode
     */
    public static List<Tool> createTeamTools(
            String role,
            TeamBackend backend,
            String teammateMode,
            Set<String> excludeTools
    ) {
        return createTeamTools(role, backend, teammateMode, excludeTools, null);
    }

    /**
     * Creates team tools filtered by role with workspace manager support.
     *
     * @param role agent role (leader, member, or human_agent)
     * @param backend team backend providing data and message access
     * @param teammateMode teammate mode (build_mode or plan_mode)
     * @param excludeTools set of tool names to exclude from the result
     * @param workspaceManager workspace manager for lock and history operations, may be null
     * @return list of tools allowed for the given role and mode
     */
    public static List<Tool> createTeamTools(
            String role,
            TeamBackend backend,
            String teammateMode,
            Set<String> excludeTools,
            TeamWorkspaceManager workspaceManager
    ) {
        return createTeamTools(TeamToolsConfig.builder()
                .role(role).backend(backend)
                .teammateMode(teammateMode).excludeTools(excludeTools)
                .workspaceManager(workspaceManager)
                .modelConfigAllocator(ignored -> null)
                .build());
    }

    /**
     * Creates team tools filtered by role using a configuration object.
     *
     * @param config team tools configuration
     * @return list of tools allowed for the given role and mode
     */
    public static List<Tool> createTeamTools(TeamToolsConfig config) {
        TeamBackend backend = config.backend;
        Function<String, Allocation> modelConfigAllocator = config.modelConfigAllocator;
        Map<String, Tool> allTools = new LinkedHashMap<>();
        allTools.put("build_team", new BuildTeamTool(backend));
        allTools.put("clean_team", new CleanTeamTool(backend));
        allTools.put("spawn_member", new SpawnMemberTool(backend, modelConfigAllocator));
        allTools.put("shutdown_member", new ShutdownMemberTool(backend));
        allTools.put("approve_plan", new ApprovePlanTool(backend));
        allTools.put("approve_tool", new ApproveToolCallTool(backend));
        allTools.put("list_members", new ListMembersTool(backend));
        allTools.put("create_task", new TaskCreateTool(backend.getTaskManager()));
        allTools.put("update_task", new UpdateTaskTool(backend));
        allTools.put("view_task", new ViewTaskTool(backend.getTaskManager()));
        allTools.put("claim_task", new ClaimTaskTool(backend));
        allTools.put("send_message", new SendMessageTool(backend));
        if (config.workspaceManager != null) {
            allTools.put("workspace_meta", new WorkspaceMetaTool(config.workspaceManager, backend));
        }
        if (config.worktreeManager != null) {
            allTools.put("enter_worktree", new EnterWorktreeTool(config.worktreeManager, backend));
            allTools.put("exit_worktree", new ExitWorktreeTool(config.worktreeManager));
        }

        Set<String> isAllowed;
        if ("human_agent".equals(config.role)) {
            isAllowed = HUMAN_AGENT_TOOLS;
        } else if ("leader".equals(config.role)) {
            isAllowed = LEADER_TOOLS;
        } else {
            isAllowed = MEMBER_TOOLS;
        }
        isAllowed = new LinkedHashSet<>(isAllowed);
        if ("leader".equals(config.role) && !"plan_mode".equals(config.teammateMode)) {
            isAllowed.remove("approve_plan");
            isAllowed.remove("approve_tool");
        }
        if (config.excludeTools != null) {
            isAllowed.removeAll(config.excludeTools);
        }
        List<Tool> tools = new ArrayList<>();
        for (Map.Entry<String, Tool> entry : allTools.entrySet()) {
            if (isAllowed.contains(entry.getKey())) {
                tools.add(entry.getValue());
            }
        }
        return tools;
    }

    /**
     * Configuration for creating team tools.
     *
     * @since 0.1.15
     */
    public static final class TeamToolsConfig {
        final String role;
        final TeamBackend backend;
        final String teammateMode;
        final Set<String> excludeTools;
        final TeamWorkspaceManager workspaceManager;
        final WorktreeManager worktreeManager;
        final Function<String, Allocation> modelConfigAllocator;

        private TeamToolsConfig(Builder builder) {
            this.role = builder.role;
            this.backend = builder.backend;
            this.teammateMode = builder.teammateMode;
            this.excludeTools = builder.excludeTools;
            this.workspaceManager = builder.workspaceManager;
            this.worktreeManager = builder.worktreeManager;
            this.modelConfigAllocator = builder.modelConfigAllocator;
        }

        /**
         * Create a new builder.
         *
         * @return a new Builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for TeamToolsConfig.
         */
        public static final class Builder {
            private String role;
            private TeamBackend backend;
            private String teammateMode;
            private Set<String> excludeTools;
            private TeamWorkspaceManager workspaceManager;
            private WorktreeManager worktreeManager;
            private Function<String, Allocation> modelConfigAllocator;

            /**
             * Set agent role.
             *
             * @param val the agent role
             * @return this builder
             */
            public Builder role(String val) {
                this.role = val;
                return this;
            }

            /**
             * Set team backend.
             *
             * @param val the team backend
             * @return this builder
             */
            public Builder backend(TeamBackend val) {
                this.backend = val;
                return this;
            }

            /**
             * Set teammate mode.
             *
             * @param val the teammate mode
             * @return this builder
             */
            public Builder teammateMode(String val) {
                this.teammateMode = val;
                return this;
            }

            /**
             * Set excluded tools.
             *
             * @param val the set of excluded tool names
             * @return this builder
             */
            public Builder excludeTools(Set<String> val) {
                this.excludeTools = val;
                return this;
            }

            /**
             * Set workspace manager.
             *
             * @param val the workspace manager
             * @return this builder
             */
            public Builder workspaceManager(TeamWorkspaceManager val) {
                this.workspaceManager = val;
                return this;
            }

            /**
             * Set worktree manager.
             *
             * @param val the worktree manager
             * @return this builder
             */
            public Builder worktreeManager(WorktreeManager val) {
                this.worktreeManager = val;
                return this;
            }

            /**
             * Set model config allocator.
             *
             * @param val the model config allocator function
             * @return this builder
             */
            public Builder modelConfigAllocator(Function<String, Allocation> val) {
                this.modelConfigAllocator = val;
                return this;
            }

            /**
             * Build the config.
             *
             * @return the constructed TeamToolsConfig
             */
            public TeamToolsConfig build() {
                return new TeamToolsConfig(this);
            }
        }
    }

    /**
     * Computes the union of two string sets.
     *
     * @param left first set
     * @param right second set
     * @return immutable union of both sets
     */
    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> values = new LinkedHashSet<>(left);
        values.addAll(right);
        return Set.copyOf(values);
    }

    /**
     * Base class for team tools providing common output mapping.
     */
    static class TeamTool extends Tool {
        /**
         * Constructs a TeamTool with the given name, description, and input parameters.
         *
         * @param name tool name
         * @param description tool description
         * @param inputParams tool input parameter schema
         */
        protected TeamTool(String name, String description, Map<String, Object> inputParams) {
            super(ToolCard.builder()
                    .id("team." + name)
                    .name(name)
                    .description(description)
                    .inputParams(inputParams)
                    .build());
        }

        /**
         * Invokes the tool with the given inputs. Base implementation returns not-implemented error.
         *
         * @param inputs positional input map
         * @param kwargs keyword input map
         * @return tool execution result
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return ToolOutput.builder().success(false).error("Not implemented").build();
        }

        /**
         * Streams the tool result by delegating to invoke.
         *
         * @param inputs positional input map
         * @param kwargs keyword input map
         * @return iterator over the invocation result
         */
        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.<Object>of(invoke(inputs, kwargs)).iterator();
        }

        /**
         * Wraps data in a successful tool output with mapped content.
         *
         * @param data result data to wrap
         * @return successful tool output with mapped content
         */
        protected ToolOutput isOk(Object data) {
            ToolOutput output = ToolOutput.builder().success(true).data(data).build();
            return MappedToolOutput.from(output, mapResult(output));
        }

        /**
         * Wraps an error message in a failed tool output with mapped content.
         *
         * @param error error message
         * @return failed tool output with mapped content
         */
        protected ToolOutput error(String error) {
            ToolOutput output = ToolOutput.builder().success(false).error(error).build();
            return MappedToolOutput.from(output, mapResult(output));
        }

        /**
         * Maps a tool output to its string representation.
         *
         * @param output tool output to map
         * @return string representation of the output
         */
        protected String mapResult(ToolOutput output) {
            return mappedContent(output);
        }
    }

    /**
     * Tool output with a pre-computed mapped string content.
     */
    public static final class MappedToolOutput extends ToolOutput {
        private final String mappedContent;

        private MappedToolOutput(boolean success, Object data, String error, String mappedContent) {
            super(success, data, error);
            this.mappedContent = mappedContent;
        }

        /**
         * Creates a MappedToolOutput from an existing ToolOutput and mapped content string.
         *
         * @param output source tool output
         * @param mappedContent pre-computed string representation
         * @return new MappedToolOutput instance
         */
        public static MappedToolOutput from(ToolOutput output, String mappedContent) {
            return new MappedToolOutput(output.isSuccess(), output.getData(), output.getError(), mappedContent);
        }

        /**
         * Returns the mapped content string representation.
         *
         * @return mapped content string
         */
        @Override
        public String toString() {
            return mappedContent;
        }
    }

    /**
     * Tool for creating a new team.
     */
    static final class BuildTeamTool extends TeamTool {
        private final TeamBackend backend;

        BuildTeamTool(TeamBackend backend) {
            super("build_team", "Create a new team.", objectSchema(Map.of(
                    "display_name", stringSchema("Team display name"),
                    "team_desc", stringSchema("Team description"),
                    "leader_display_name", stringSchema("Leader display name"),
                    "leader_desc", stringSchema("Leader description"),
                    "enable_hitt", Map.of("type", "boolean", "default", false)
            ), List.of("display_name", "team_desc", "leader_display_name", "leader_desc")));
            this.backend = backend;
        }

        /**
         * Invokes the build_team tool to create a new team.
         *
         * @param inputs positional input map containing display_name, team_desc,
         *               leader_display_name, leader_desc, enable_hitt
         * @param kwargs keyword input map
         * @return tool execution result with team creation data
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("team_name", backend.getTeamName());
            data.put("display_name", stringValue(safeInputs.get("display_name"), backend.getDisplayName()));
            data.put("leader_member_name", backend.getMemberName());
            data.put(
                    "leader_display_name",
                    stringValue(safeInputs.get("leader_display_name"), backend.getMemberName()));
            data.put("enable_hitt", booleanValue(safeInputs.get("enable_hitt"), false));
            return isOk(data);
        }

        /**
         * Maps the build_team output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted team creation summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to build team";
            }
            Map<?, ?> data = (Map<?, ?>) output.getData();
            String hittNote = Boolean.TRUE.equals(data.get("enable_hitt")) ? " [human_agent registered]" : "";
            return "Team created: team_name=" + data.get("team_name")
                    + " display_name=" + data.get("display_name")
                    + " leader_member_name=" + data.get("leader_member_name")
                    + " leader_display_name=" + data.get("leader_display_name")
                    + hittNote;
        }
    }

    /**
     * Tool for cleaning up a team when all non-leader members are shutdown.
     */
    static final class CleanTeamTool extends TeamTool {
        private final TeamBackend backend;

        CleanTeamTool(TeamBackend backend) {
            super("clean_team", "Clean up a team when all non-leader members are shutdown.",
                    objectSchema(Map.of(), List.of()));
            this.backend = backend;
        }

        /**
         * Invokes the clean_team tool to clean up the team.
         *
         * @param inputs positional input map
         * @param kwargs keyword input map
         * @return tool execution result with cleanup status
         * @throws java.util.concurrent.CompletionException if backend operation fails
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            // Guard: don't allow cleanup while there are incomplete tasks.
            // Also reject if any leader-assigned task was cancelled (should be completed).
            var tasks = backend.getTaskManager().list();
            var incomplete = tasks.stream()
                    .filter(t -> !"completed".equals(t.getStatus()) && !"cancelled".equals(t.getStatus()))
                    .toList();
            if (!incomplete.isEmpty()) {
                List<String> titles = incomplete.stream()
                        .map(t -> "[" + t.getTaskId() + "] " + t.getTitle())
                        .toList();
                return error("Cannot clean team while " + incomplete.size()
                        + " task(s) remain incomplete. Complete them first: " + titles);
            }
            var cancelled = tasks.stream()
                    .filter(t -> "cancelled".equals(t.getStatus()))
                    .toList();
            if (!cancelled.isEmpty()) {
                List<String> titles = cancelled.stream()
                        .map(t -> "[" + t.getTaskId() + "] " + t.getTitle())
                        .toList();
                return error("Cannot clean team while " + cancelled.size()
                        + " task(s) were cancelled. The leader must complete them: " + titles);
            }
            boolean isSuccess = backend.cleanTeam().join();
            if (!isSuccess) {
                // Distinguish: cleanTeam returns false either because non-leader
                // members are not SHUTDOWN, or because deleteTeam returned false
                // (team already deleted or DB error). The backend log now carries
                // the exact reason; surface a more helpful message to the LLM.
                var remaining = backend.getDb().member.getTeamMembers(backend.getTeamName()).stream()
                        .filter(r -> !backend.getMemberName().equals(r.getMemberName()))
                        .filter(r -> !MemberStatus.SHUTDOWN.value().equals(r.getStatus()))
                        .toList();
                if (remaining.isEmpty()) {
                    return error("Team cleanup failed: team may already be cleaned or deleted. "
                            + "No active members remain — the team is already in a terminal state.");
                }
                return error("Active members remain. Use shutdown_member to close all members first. "
                        + "Non-shutdown members: " + remaining.stream()
                                .map(r -> r.getMemberName() + "=" + r.getStatus())
                                .toList());
            }
            return isOk(Map.of("team_name", backend.getTeamName()));
        }

        /**
         * Maps the clean_team output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted team cleanup summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to clean team";
            }
            return "Team cleaned: team_name=" + ((Map<?, ?>) output.getData()).get("team_name");
        }
    }

    /**
     * Tool for spawning a new team member.
     */
    static final class SpawnMemberTool extends TeamTool {
        private final TeamBackend backend;
        private final Function<String, Allocation> modelConfigAllocator;

        SpawnMemberTool(TeamBackend backend, Function<String, Allocation> modelConfigAllocator) {
            super("spawn_member", "Create a new team member.", objectSchema(Map.of(
                    "member_name", stringSchema("Member name"),
                    "display_name", stringSchema("Display name"),
                    "desc", stringSchema("Member description"),
                    "prompt", stringSchema(
                            "First instruction the member receives at startup. "
                                    + "Use it to assign specific tasks, set priorities, "
                                    + "or define constraints. Give clear direction; "
                                    + "each member should receive a different prompt "
                                    + "matching their assigned task. Leave blank to let "
                                    + "the member choose tasks autonomously by domain."),
                    "model_name", stringSchema("Model name")
            ), List.of("member_name", "display_name")));
            this.backend = backend;
            this.modelConfigAllocator = modelConfigAllocator != null ? modelConfigAllocator : ignored -> null;
        }

        /**
         * Invokes the spawn_member tool to create a new team member.
         *
         * @param inputs positional input map containing member_name, display_name, desc, prompt, model_name
         * @param kwargs keyword input map
         * @return tool execution result with spawned member data
         * @throws java.util.concurrent.CompletionException if backend operation fails
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String memberName = stringValue(safeInputs.get("member_name"), "");
            String displayName = stringValue(safeInputs.get("display_name"), memberName);
            if (memberName.isBlank()) {
                return error("'member_name' is required");
            }
            Loggers.TOOL.info("SpawnMemberTool: enter member={} thread={}",
                    memberName, Thread.currentThread().getName());
            String modelName = stringValue(safeInputs.get("model_name"), null);
            Allocation allocation = modelConfigAllocator.apply(modelName);
            boolean isSuccess = backend.spawnMember(TeamBackend.SpawnMemberParams.builder()
                    .memberName(memberName)
                    .displayName(displayName)
                    .agentCard(AgentCard.builder()
                            .name(displayName)
                            .description(stringValue(safeInputs.get("desc"), ""))
                            .build())
                    .role(TeamRole.MEMBER)
                    .prompt(stringValue(safeInputs.get("prompt"), null))
                    .allocation(allocation)
                    .build()).join();
            Loggers.TOOL.info("SpawnMemberTool: exit member={} isSuccess={}",
                    memberName, isSuccess);
            if (!isSuccess) {
                return error("Failed to spawn member");
            }
            return isOk(Map.of("member_name", memberName, "display_name", displayName));
        }

        /**
         * Maps the spawn_member output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted member spawn summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to spawn member";
            }
            Map<?, ?> data = (Map<?, ?>) output.getData();
            return "Member spawned: member_name=" + data.get("member_name")
                    + " display_name=" + data.get("display_name");
        }
    }

    /**
     * Tool for shutting down a team member.
     */
    static final class ShutdownMemberTool extends TeamTool {
        private final TeamBackend backend;

        ShutdownMemberTool(TeamBackend backend) {
            super("shutdown_member", "Shutdown a member.", objectSchema(Map.of(
                    "member_name", stringSchema("Member name"),
                    "force", Map.of("type", "boolean", "default", false)
            ), List.of("member_name")));
            this.backend = backend;
        }

        /**
         * Invokes the shutdown_member tool to shut down a team member.
         *
         * <p>Guard: refuse to shut down a member that still has non-terminal tasks
         * assigned, unless the caller passes {@code force=true}. Shutting down a
         * member with in-flight tasks would orphan those tasks and stall the
         * team DAG without an explicit recovery path. {@code force=true} keeps
         * the existing escape hatch for legitimate administrative resets.</p>
         *
         * @param inputs positional input map containing member_name and optional force flag
         * @param kwargs keyword input map
         * @return tool execution result with shutdown status
         * @throws java.util.concurrent.CompletionException if backend operation fails
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String memberName = stringValue(safeInputs.get("member_name"), "");
            if (memberName.isBlank()) {
                return error("'member_name' is required");
            }
            boolean isForceEnabled = booleanValue(safeInputs.get("force"), false);
            // Guard: refuse to shutdown a member that still owns non-terminal
            // tasks. Doing so would orphan claimed/in-flight tasks and stall
            // the team DAG. force=true overrides for administrative resets.
            if (!isForceEnabled) {
                var ownedIncomplete = backend.getTaskManager().list().stream()
                        .filter(t -> memberName.equals(t.getAssignee()))
                        .filter(t -> {
                            String status = t.getStatus();
                            return status != null
                                    && !"completed".equals(status)
                                    && !"cancelled".equals(status);
                        })
                        .toList();
                if (!ownedIncomplete.isEmpty()) {
                    List<String> titles = ownedIncomplete.stream()
                            .map(t -> "[" + t.getTaskId() + "] " + t.getTitle()
                                    + " (" + t.getStatus() + ")")
                            .toList();
                    return error("Cannot shutdown member=" + memberName + " while "
                            + ownedIncomplete.size() + " task(s) it owns are not terminal. "
                            + "Complete, reassign, or cancel them first; or pass force=true "
                            + "for an administrative reset. Tasks: " + titles);
                }
            }
            MemberOpResult result =
                    backend.shutdownMember(memberName, isForceEnabled).join();
            if (!result.isOk()) {
                return error(result.getReason());
            }
            return isOk(Map.of("member_name", memberName, "status", "shutdown_requested"));
        }

        /**
         * Maps the shutdown_member output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted member shutdown summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to shutdown member";
            }
            return "Member shutdown: member_name=" + ((Map<?, ?>) output.getData()).get("member_name");
        }
    }

    /**
     * Tool for approving or rejecting a member plan.
     */
    static final class ApprovePlanTool extends TeamTool {
        private final TeamBackend backend;

        ApprovePlanTool(TeamBackend backend) {
            super("approve_plan", "Approve or reject a member plan.", objectSchema(Map.of(
                    "plan_id", stringSchema("Member plan submission identifier"),
                    "approved", Map.of("type", "boolean"),
                    "feedback", stringSchema("Feedback")
            ), List.of("plan_id", "approved")));
            this.backend = backend;
        }

        /**
         * Invokes the approve_plan tool to approve or reject a member plan.
         *
         * @param inputs positional input map containing plan_id, approved, and optional feedback
         * @param kwargs keyword input map
         * @return tool execution result with approval status
         * @throws java.util.concurrent.CompletionException if backend operation fails
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String planId = stringValue(safeInputs.get("plan_id"), "");
            if (planId.isBlank()) {
                return error("'plan_id' is required");
            }
            boolean approved = booleanValue(safeInputs.get("approved"), false);
            if (!backend.approvePlan(planId, approved, stringValue(safeInputs.get("feedback"), null)).join()) {
                return error("Failed to approve plan");
            }
            return isOk(Map.of("plan_id", planId, "approved", approved));
        }

        /**
         * Maps the approve_plan output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted plan approval summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to approve/reject plan";
            }
            Map<?, ?> data = (Map<?, ?>) output.getData();
            String decision = Boolean.TRUE.equals(data.get("approved")) ? "approved" : "rejected";
            return "Plan " + decision + ": plan_id=" + data.get("plan_id") + " decision=" + decision;
        }
    }

    /**
     * Tool for approving or rejecting a teammate tool call.
     */
    static final class ApproveToolCallTool extends TeamTool {
        private final TeamBackend backend;

        ApproveToolCallTool(TeamBackend backend) {
            super("approve_tool", "Approve or reject a teammate tool call.", objectSchema(Map.of(
                    "member_name", stringSchema("Member name"),
                    "tool_call_id", stringSchema("Tool call id"),
                    "approved", Map.of("type", "boolean"),
                    "feedback", stringSchema("Feedback"),
                    "auto_confirm", Map.of("type", "boolean", "default", false)
            ), List.of("member_name", "tool_call_id", "approved")));
            this.backend = backend;
        }

        /**
         * Invokes the approve_tool tool to approve or reject a teammate tool call.
         *
         * @param inputs positional input map containing member_name, tool_call_id,
         *               approved, and optional feedback/auto_confirm
         * @param kwargs keyword input map
         * @return tool execution result with tool call approval status
         * @throws java.util.concurrent.CompletionException if backend operation fails
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String memberName = stringValue(safeInputs.get("member_name"), "");
            String toolCallId = stringValue(safeInputs.get("tool_call_id"), "");
            if (memberName.isBlank() || toolCallId.isBlank()) {
                return error("'member_name' and 'tool_call_id' are required");
            }
            boolean approved = booleanValue(safeInputs.get("approved"), false);
            boolean autoConfirm = booleanValue(safeInputs.get("auto_confirm"), false);
            if (!backend.approveTool(memberName, toolCallId, approved,
                    stringValue(safeInputs.get("feedback"), null), autoConfirm).join()) {
                return error("Failed to approve tool call");
            }
            return isOk(Map.of("member_name", memberName, "tool_call_id", toolCallId, "approved", approved));
        }

        /**
         * Maps the approve_tool output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted tool call approval summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to approve/reject tool call";
            }
            Map<?, ?> data = (Map<?, ?>) output.getData();
            String decision = Boolean.TRUE.equals(data.get("approved")) ? "approved" : "rejected";
            return "Tool call " + decision + ": tool_call_id=" + data.get("tool_call_id")
                    + " member_name=" + data.get("member_name")
                    + " decision=" + decision;
        }
    }

    /**
     * Tool for listing all team members.
     */
    static final class ListMembersTool extends TeamTool {
        private final TeamBackend backend;

        ListMembersTool(TeamBackend backend) {
            super("list_members", "List all team members.", objectSchema(Map.of(), List.of()));
            this.backend = backend;
        }

        /**
         * Invokes the list_members tool to list all team members.
         *
         * @param inputs positional input map
         * @param kwargs keyword input map
         * @return tool execution result with member list and count
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            List<Map<String, Object>> members = backend.listMembers().stream()
                    .map(member -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("member_name", member.getMemberName());
                        item.put("display_name", member.getDisplayName());
                        item.put("status", member.getStatus() != null ? member.getStatus().value() : "");
                        item.put(
                                "role",
                                member.getRole() != null
                                        ? member.getRole().name().toLowerCase(Locale.ROOT)
                                        : "");
                        return item;
                    })
                    .toList();
            return isOk(Map.of("members", members, "count", members.size()));
        }

        /**
         * Maps the list_members output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted member list summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to list members";
            }
            Object membersValue = ((Map<?, ?>) output.getData()).get("members");
            if (!(membersValue instanceof List<?> members) || members.isEmpty()) {
                return "No members";
            }
            return members.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> {
                        Map<?, ?> member = (Map<?, ?>) item;
                        return "member_name=" + member.get("member_name")
                                + " display_name=" + member.get("display_name")
                                + " status=" + member.get("status");
                    })
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("No members");
        }
    }

    /**
     * Tool for creating team tasks.
     */
    static final class TaskCreateTool extends TeamTool {
        private final TeamTaskManager taskManager;

        TaskCreateTool(TeamTaskManager taskManager) {
            super("create_task", "Create team tasks. Each task MUST have a title and content.", objectSchema(Map.of(
                    "tasks", Map.of("type", "array",
                            "items", objectSchema(Map.of(
                                    "title", stringSchema("Task title"),
                                    "content", stringSchema("Task description or instructions"),
                                    "task_id", stringSchema("Optional custom task ID"),
                                    "assignee", stringSchema(
                                            "Optional assignee member name; if set, "
                                                    + "only this member can claim the task"),
                                    "dependencies", Map.of("type", "array", "items", Map.of("type", "string"),
                                            "description", "List of task IDs this task depends on")
                            ), List.of("title", "content")))
            ), List.of("tasks")));
            this.taskManager = taskManager;
        }

        /**
         * Invokes the create_task tool to create team tasks.
         *
         * @param inputs positional input map containing tasks array
         * @param kwargs keyword input map
         * @return tool execution result with created task list and count
         * @throws java.util.concurrent.CompletionException if task manager operation fails
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Object tasksValue = safeInputs(inputs).get("tasks");
            if (!(tasksValue instanceof List<?> rawTasks) || rawTasks.isEmpty()) {
                return error("'tasks' is required");
            }
            List<Map<String, Object>> taskSpecs = rawTasks.stream()
                    .filter(Map.class::isInstance)
                    .map(value -> (Map<String, Object>) value)
                    .toList();
            List<TeamTask> created;
            try {
                created = taskManager.addBatch(taskSpecs).join();
            } catch (java.util.concurrent.CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                return error("create_task failed: " + cause.getMessage()
                        + ". Hint: call spawn_member before create_task when using assignee.");
            }
            if (created.isEmpty()) {
                return error("No valid tasks created");
            }
            return isOk(Map.of(
                    "tasks", created.stream().map(TeamTools::taskBrief).toList(),
                    "count", created.size()
            ));
        }

        /**
         * Maps the create_task output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted task creation summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Operation failed";
            }
            Object tasksValue = ((Map<?, ?>) output.getData()).get("tasks");
            if (!(tasksValue instanceof List<?> tasks)) {
                return mappedContent(output);
            }
            List<String> lines = new ArrayList<>();
            for (Object item : tasks) {
                if (item instanceof Map<?, ?> task) {
                    lines.add("task_id=" + task.get("task_id") + " title=" + task.get("title"));
                }
            }
            lines.add("Created " + ((Map<?, ?>) output.getData()).get("count") + ", skipped 0");
            return String.join("\n", lines);
        }
    }

    /**
     * Tool for viewing tasks.
     */
    static final class ViewTaskTool extends TeamTool {
        private final TeamTaskManager taskManager;

        ViewTaskTool(TeamTaskManager taskManager) {
            super("view_task", "View tasks.", objectSchema(Map.of(
                    "action", Map.of("type", "string", "enum", List.of("get", "list", "claimable")),
                    "task_id", stringSchema("Task id"),
                    "status", stringSchema("Task status")
            ), List.of()));
            this.taskManager = taskManager;
        }

        /**
         * Invokes the view_task tool to view one or more tasks.
         *
         * @param inputs positional input map containing action, optional task_id and status
         * @param kwargs keyword input map
         * @return tool execution result with task data or task list
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String action = stringValue(safeInputs.get("action"), "list");
            if ("get".equals(action)) {
                Optional<TeamTask> taskOpt = taskManager.get(stringValue(safeInputs.get("task_id"), ""));
                if (taskOpt.isEmpty()) {
                    return error("Task not found");
                }
                return isOk(taskBrief(taskOpt.get()));
            }
            List<TeamTask> tasks;
            if ("claimable".equals(action)) {
                tasks = taskManager.getClaimableTasks();
            } else {
                String statusFilter = safeInputs.get("status") != null
                        ? String.valueOf(safeInputs.get("status")) : null;
                tasks = taskManager.list().stream()
                        .filter(task -> statusFilter == null
                                || statusFilter.equals(task.getStatus())
                                || ("claimable".equals(statusFilter) && "pending".equals(task.getStatus())))
                        .toList();
            }
            Loggers.TOOL.info("view_task action={} found {} task(s)", action, tasks.size());
            return isOk(Map.of("tasks", tasks.stream().map(TeamTools::taskBrief).toList(), "count", tasks.size()));
        }

        /**
         * Maps the view_task output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted task view summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Task not found";
            }
            Object data = output.getData();
            if (data instanceof Map<?, ?> map && map.containsKey("content")) {
                List<String> lines = new ArrayList<>();
                lines.add("Task #" + map.get("task_id") + ": " + map.get("title"));
                lines.add("Status: " + map.get("status"));
                lines.add("Content: " + map.get("content"));
                if (map.get("assignee") != null) {
                    lines.add("Assignee: " + map.get("assignee"));
                }
                return String.join("\n", lines);
            }
            Object tasksValue = data instanceof Map<?, ?> map ? map.get("tasks") : null;
            if (!(tasksValue instanceof List<?> tasks) || tasks.isEmpty()) {
                return "No tasks isFound";
            }
            return tasks.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> {
                        Map<?, ?> task = (Map<?, ?>) item;
                        String line = "#" + task.get("task_id") + " [" + task.get("status") + "] " + task.get("title");
                        if (task.get("assignee") != null) {
                            line += " (" + task.get("assignee") + ")";
                        }
                        return line;
                    })
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("No tasks isFound");
        }
    }

    /**
     * Tool for updating task content or cancelling tasks.
     */
    static final class UpdateTaskTool extends TeamTool {
        private final TeamBackend backend;

        UpdateTaskTool(TeamBackend backend) {
            super("update_task", "Update task content or cancel tasks.", objectSchema(Map.of(
                    "task_id", stringSchema("Task id"),
                    "status", Map.of("type", "string", "enum", List.of("cancelled")),
                    "title", stringSchema("Title"),
                    "content", stringSchema("Content"),
                    "assignee", stringSchema("Assignee"),
                    "add_blocked_by", Map.of("type", "array", "items", Map.of("type", "string"))
            ), List.of("task_id")));
            this.backend = backend;
        }

        /**
         * Invokes the update_task tool to update or cancel a task.
         *
         * @param inputs positional input map containing task_id and optional status,
         *               title, content, assignee, add_blocked_by
         * @param kwargs keyword input map
         * @return tool execution result with update or cancellation status
         * @throws java.util.concurrent.CompletionException if task manager operation fails
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String taskId = stringValue(safeInputs.get("task_id"), "");
            if (taskId.isBlank()) {
                return error("'task_id' is required");
            }
            if ("*".equals(taskId) && "cancelled".equals(safeInputs.get("status"))) {
                int count = backend.getTaskManager().cancelAllTasks().join().size();
                return isOk(Map.of("cancelled_count", count));
            }
            Optional<TeamTask> taskOpt = backend.getTaskManager().get(taskId);
            if (taskOpt.isEmpty()) {
                return error("Task not found");
            }
            TeamTask task = taskOpt.get();
            if ("cancelled".equals(safeInputs.get("status"))) {
                // Check if task is owned by a human_agent (locked)
                if (isHumanAgentLocked(task)) {
                    return error("Cannot cancel or reassign human member's tasks");
                }
                TeamTask cancelled = backend.getTaskManager().cancel(taskId).join();
                if (cancelled == null) {
                    return error("Failed to cancel task");
                }

                // If task was claimed by a member, send cancel notification
                cancelMemberIfClaimed(task);
                return isOk(Map.of("task_id", taskId, "status", "cancelled"));
            }

            // Check human_agent lock before editing
            if ((safeInputs.containsKey("title") || safeInputs.containsKey("content")
                    || safeInputs.containsKey("assignee") || safeInputs.containsKey("add_blocked_by"))
                    && isHumanAgentLocked(task)) {
                return error("Cannot cancel or reassign human member's tasks");
            }
            List<String> updated = new ArrayList<>();
            if (safeInputs.containsKey("title") || safeInputs.containsKey("content")) {
                TaskOpResult result = backend.getTaskManager().updateTaskResult(taskId,
                        stringValue(safeInputs.get("title"), null),
                        stringValue(safeInputs.get("content"), null)).join();
                if (!result.isOk()) {
                    return error(result.getReason());
                }
                if (safeInputs.containsKey("title")) {
                    updated.add("title");
                }
                if (safeInputs.containsKey("content")) {
                    updated.add("content");
                }
            }
            if (safeInputs.containsKey("assignee")) {
                String assignee = stringValue(safeInputs.get("assignee"), "");
                TaskOpResult result = backend.getTaskManager().assignResult(taskId, assignee).join();
                if (!result.isOk()) {
                    return error(result.getReason());
                }
                updated.add("assignee");
            }
            List<String> deps = stringList(safeInputs.get("add_blocked_by"));
            if (!deps.isEmpty()) {
                TaskOpResult result = backend.getTaskManager().addDependenciesResult(taskId, deps).join();
                if (!result.isOk()) {
                    return error(result.getReason());
                }
                updated.add("blocked_by");
            }
            if (updated.isEmpty()) {
                return error("No update specified");
            }
            return isOk(Map.of("task_id", taskId, "status", "updated", "updated_fields", updated));
        }

        /**
         * Checks whether the task assignee is a human_agent role.
         *
         * @param task task to check
         * @return true if the task is assigned to a human_agent
         */
        private boolean isHumanAgentLocked(TeamTask task) {
            if (task == null || task.getAssignee() == null) {
                return false;
            }
            var member = backend.getMember(task.getAssignee());
            return member != null
                    && member.getRole() == com.openjiuwen.agentteams.schema.team.TeamRole.HUMAN_AGENT;
        }

        /**
         * Shuts down the member who claimed the task if the task is cancelled.
         *
         * @param task cancelled task whose assignee may need shutdown
         * @throws java.util.concurrent.CompletionException if shutdown operation fails
         */
        private void cancelMemberIfClaimed(TeamTask task) {
            if (task.getAssignee() != null) {
                MemberOpResult result = backend.shutdownMember(task.getAssignee(), false).join();
                if (!result.isOk()) {
                    com.openjiuwen.core.common.logging.Loggers.TOOL.warn(
                            "cancelMemberIfClaimed: shutdown failed for member={}: {}",
                            task.getAssignee(), result.getReason());
                }
            }
        }

        /**
         * Maps the update_task output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted task update summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Operation failed";
            }
            Map<?, ?> data = (Map<?, ?>) output.getData();
            if (data.containsKey("cancelled_count")) {
                return "Cancelled " + data.get("cancelled_count") + " tasks";
            }
            return "Task #" + data.get("task_id") + " " + data.get("status");
        }
    }

    /**
     * Tool for claiming or completing a task.
     */
    static final class ClaimTaskTool extends TeamTool {
        private final TeamBackend backend;
        private final TeamTaskManager taskManager;

        ClaimTaskTool(TeamBackend backend) {
            super("claim_task", "Claim or complete a task.", objectSchema(Map.of(
                    "task_id", stringSchema("Task id"),
                    "status", Map.of("type", "string", "enum", List.of("claimed", "completed"))
            ), List.of("task_id", "status")));
            this.backend = backend;
            this.taskManager = backend.getTaskManager();
        }

        /**
         * Invokes the claim_task tool to claim or complete a task.
         *
         * <p>Owner check: if the task already has an assignee and the caller is not that
         * assignee, the operation is rejected upfront. This prevents a member from
         * claiming or completing a task that the leader has assigned to someone else
         * (the underlying {@code TeamTaskManager.claimResult} / {@code completeResult}
         * would also reject it, but we fail fast here to give a clearer error and
         * to avoid the {@code assignee=null} race where a freshly created task has
         * not yet been assigned).</p>
         *
         * @param inputs positional input map containing task_id and status
         * @param kwargs keyword input map
         * @return tool execution result with status change details
         * @throws java.util.concurrent.CompletionException if task manager operation fails
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String taskId = stringValue(safeInputs.get("task_id"), "");
            String status = stringValue(safeInputs.get("status"), "");
            String callerMember = backend.getMemberName();
            com.openjiuwen.core.common.logging.Loggers.TOOL.info(
                    "ClaimTaskTool.invoke: taskId={} status={} caller={} cardId={}",
                    taskId, status, callerMember,
                    getCard() != null ? getCard().getId() : "null");
            Optional<TeamTask> taskOpt = taskManager.get(taskId);
            if (taskOpt.isEmpty()) {
                return error("Task not found");
            }
            TeamTask task = taskOpt.get();
            String assignee = task.getAssignee();
            if (assignee != null && !assignee.isBlank() && !assignee.equals(callerMember)) {
                return error("Task " + taskId + " is assigned to " + assignee
                        + "; " + callerMember + " is not the assignee and cannot "
                        + "claim or complete it. Use update_task to reassign first.");
            }
            TaskOpResult result;
            String nextStatus;
            if ("claimed".equals(status)) {
                result = taskManager.claimResult(taskId).join();
                nextStatus = "claimed";
            } else if ("completed".equals(status)) {
                result = taskManager.completeResult(taskId).join();
                nextStatus = "completed";
            } else {
                return error("Invalid status: " + status);
            }
            if (!result.isOk()) {
                return error(result.getReason());
            }
            return isOk(Map.of(
                    "task_id", taskId,
                    "updated_fields", List.of("status"),
                    "status_change", Map.of("from", task.getStatus(), "to", nextStatus)
            ));
        }

        /**
         * Maps the claim_task output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted task claim or completion summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Task not found";
            }
            Map<?, ?> data = (Map<?, ?>) output.getData();
            Map<?, ?> change = (Map<?, ?>) data.get("status_change");
            String text = "Task #" + data.get("task_id") + " " + change.get("from") + " -> " + change.get("to");
            if ("completed".equals(change.get("to"))) {
                text += "\n\nTask completed. Call view_task now to find your next available task.";
            }
            return text;
        }
    }

    /**
     * Tool for sending a message to a team member or broadcasting.
     */
    static final class SendMessageTool extends TeamTool {
        private final TeamBackend backend;

        SendMessageTool(TeamBackend backend) {
            super("send_message", "Send a message to a member or broadcast.", objectSchema(Map.of(
                    "to", stringSchema("Recipient member name or *"),
                    "content", stringSchema("Message content"),
                    "summary", stringSchema("Short summary")
            ), List.of("to", "content")));
            this.backend = backend;
        }

        /**
         * Invokes the send_message tool to send or broadcast a message.
         *
         * @param inputs positional input map containing to, content, and optional summary
         * @param kwargs keyword input map
         * @return tool execution result with message delivery status
         * @throws java.util.concurrent.CompletionException if message delivery fails
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String to = stringValue(safeInputs.get("to"), "").trim();
            String content = stringValue(safeInputs.get("content"), "").trim();
            String summary = stringValue(safeInputs.get("summary"), null);
            if (to.isBlank()) {
                return error("'to' is required");
            }
            if (content.isBlank()) {
                return error("'content' is required");
            }
            if (!"*".equals(to)
                    && !"user".equals(to)
                    && backend.getDb().member.getMember(to, backend.getTeamName()) == null) {
                // Try to resolve by display_name
                Optional<String> resolved = backend.resolveMemberName(to);
                if (resolved.isPresent()) {
                    to = resolved.get();
                } else {
                    return error("Member '" + to + "' not found");
                }
            }

            // Mirrors Python tool_message.py:SendMessageTool._auto_start_members:
            // every send_message path (broadcast / multicast / unicast)
            // lazily starts UNSTARTED members before delivery. Message
            // delivery itself stays on the mailbox — no content is folded
            // into the spawn prompt.
            Loggers.TOOL.info("send_message: before auto-start thread={} to={}",
                    Thread.currentThread().getName(), to);
            List<String> started = backend.startupAllUnstarted();
            if (!started.isEmpty()) {
                Loggers.TOOL.info("send_message auto-started {} unstarted member(s): {}",
                        started.size(), started);
            } else {
                Loggers.TOOL.info("send_message: no members auto-started (none UNSTARTED or CAS failed)",
                        new Object[0]);
            }
            String messageId = "*".equals(to)
                    ? backend.getMessageManager().broadcastMessage(content).join()
                    : backend.getMessageManager().sendMessage(content, to).join();
            if (messageId == null || messageId.isBlank()) {
                return error("Failed to send message");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", "*".equals(to) ? "broadcast" : "message");
            data.put("from", backend.getMemberName());
            if (!"*".equals(to)) {
                data.put("to", to);
            }
            data.put("summary", summary);
            return isOk(data);
        }

        /**
         * Maps the send_message output to a human-readable string.
         *
         * @param output tool output to map
         * @return formatted message delivery summary
         */
        @Override
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to send message";
            }
            Map<?, ?> data = (Map<?, ?>) output.getData();
            if ("broadcast".equals(data.get("type"))) {
                return "Broadcast sent from " + data.get("from");
            }
            return "Message sent from " + data.get("from") + " to " + data.get("to");
        }
    }

    /**
     * Tool for workspace lock management and version history.
     */
    static final class WorkspaceMetaTool extends TeamTool {
        private final TeamWorkspaceManager workspaceManager;
        private final TeamBackend backend;

        WorkspaceMetaTool(TeamWorkspaceManager workspaceManager, TeamBackend backend) {
            super("workspace_meta", "Workspace lock management and version history.", objectSchema(Map.of(
                    "action", Map.of("type", "string", "enum", List.of("lock", "unlock", "locks", "history")),
                    "path", stringSchema("Workspace-relative path")
            ), List.of("action")));
            this.workspaceManager = workspaceManager;
            this.backend = backend;
        }

        /**
         * Invokes the workspace_meta tool for lock, unlock, locks, or history actions.
         *
         * @param inputs positional input map containing action and optional path
         * @param kwargs keyword input map, may contain member_name and display_name
         * @return tool execution result with lock or history data
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String action = stringValue(safeInputs.get("action"), "");
            String path = stringValue(safeInputs.get("path"), "");
            String memberName = stringValue(kwargs != null ? kwargs.get("member_name") : null, backend.getMemberName());
            String displayName = stringValue(kwargs != null ? kwargs.get("display_name") : null, memberName);
            if ("lock".equals(action)) {
                if (path.isBlank()) {
                    return error("'path' is required for lock action");
                }
                boolean acquired = workspaceManager.acquireLock(path, memberName, displayName, 300);
                if (!acquired) {
                    Optional<WorkspaceFileLock> lockOpt = workspaceManager.getLock(path);
                    return error(lockOpt.map(WorkspaceFileLock::getHolderName)
                            .map(name -> "Locked by " + name)
                            .orElse("Lock failed"));
                }
                return isOk(Map.of("locked", path));
            }
            if ("unlock".equals(action)) {
                if (path.isBlank()) {
                    return error("'path' is required for unlock action");
                }
                return isOk(Map.of("released", workspaceManager.releaseLock(path, memberName)));
            }
            if ("locks".equals(action)) {
                return isOk(Map.of("locks", workspaceManager.listLocks().stream().map(TeamTools::lockData).toList()));
            }
            if ("history".equals(action)) {
                if (path.isBlank()) {
                    return error("'path' is required for history action");
                }
                return isOk(Map.of("history", workspaceManager.getHistory(path)));
            }
            return error("Unknown action '" + action + "'");
        }
    }

    /**
     * Tool for creating or entering an isolated git worktree.
     */
    static final class EnterWorktreeTool extends TeamTool {
        private final WorktreeManager worktreeManager;
        private final TeamBackend backend;

        EnterWorktreeTool(WorktreeManager worktreeManager, TeamBackend backend) {
            super("enter_worktree", "Create or enter an isolated git worktree.", objectSchema(Map.of(
                    "name", stringSchema("Worktree slug")
            ), List.of()));
            this.worktreeManager = worktreeManager;
            this.backend = backend;
        }

        /**
         * Invokes the enter_worktree tool to create or enter a git worktree.
         *
         * @param inputs positional input map containing optional name
         * @param kwargs keyword input map, may contain repo_root, member_name, team_name
         * @return tool execution result with worktree path and branch info
         * @throws IOException if worktree creation fails due to IO error
         * @throws IllegalArgumentException if worktree parameters are invalid
         * @throws IllegalStateException if worktree is already in an invalid state
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            if (worktreeManager.getCurrentSession() != null) {
                return error("Already in worktree '" + worktreeManager.getCurrentSession().getWorktreeName()
                        + "'. Exit first with exit_worktree.");
            }
            Map<String, Object> safeInputs = safeInputs(inputs);
            String slug = stringValue(safeInputs.get("name"), "");
            if (slug.isBlank()) {
                slug = "worktree-" + UUID.randomUUID().toString().substring(0, 8);
            }
            String repoRoot = stringValue(
                    kwargs != null ? kwargs.get("repo_root") : null,
                    System.getProperty("user.dir"));
            String memberName = stringValue(kwargs != null ? kwargs.get("member_name") : null, backend.getMemberName());
            String teamName = stringValue(kwargs != null ? kwargs.get("team_name") : null, backend.getTeamName());
            try {
                WorktreeSession session = worktreeManager.enter(slug, repoRoot, memberName, teamName);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("worktree_path", session.getWorktreePath());
                data.put("worktree_branch", session.getWorktreeBranch());
                data.put("message", "Created worktree at " + session.getWorktreePath()
                        + " on branch " + session.getWorktreeBranch() + ". CWD switched to worktree.");
                return isOk(data);
            } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                return error("Failed to create worktree: " + e.getMessage());
            }
        }
    }

    /**
     * Tool for exiting the current git worktree session.
     */
    static final class ExitWorktreeTool extends TeamTool {
        private final WorktreeManager worktreeManager;

        ExitWorktreeTool(WorktreeManager worktreeManager) {
            super("exit_worktree", "Exit the current worktree session.", objectSchema(Map.of(
                    "action", Map.of("type", "string", "enum", List.of("keep", "remove")),
                    "discard_changes", Map.of("type", "boolean", "default", false)
            ), List.of("action")));
            this.worktreeManager = worktreeManager;
        }

        /**
         * Invokes the exit_worktree tool to exit the current worktree session.
         *
         * @param inputs positional input map containing action and optional discard_changes
         * @param kwargs keyword input map
         * @return tool execution result with exit status and worktree details
         * @throws IOException if worktree exit fails due to IO error
         * @throws IllegalArgumentException if action parameter is invalid
         * @throws IllegalStateException if worktree session is in an invalid state
         */
        @Override
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            WorktreeSession session = worktreeManager.getCurrentSession();
            if (session == null) {
                return error("No active worktree session to exit.");
            }
            Map<String, Object> safeInputs = safeInputs(inputs);
            String action = stringValue(safeInputs.get("action"), "");
            if (!"keep".equals(action) && !"remove".equals(action)) {
                return error("'action' must be 'keep' or 'remove'.");
            }
            boolean discard = booleanValue(safeInputs.get("discard_changes"), false);
            WorktreeChangeSummary summary = null;
            try {
                if ("remove".equals(action) && discard) {
                    summary = worktreeManager.countChanges();
                }
                worktreeManager.exit(action, discard);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("action", action);
                data.put("original_cwd", session.getOriginalCwd());
                data.put("worktree_path", session.getWorktreePath());
                data.put("worktree_branch", session.getWorktreeBranch());
                data.put("message", ("keep".equals(action) ? "Kept" : "Removed")
                        + " worktree (branch " + session.getWorktreeBranch() + "). Returned to "
                        + session.getOriginalCwd());
                if (summary != null) {
                    data.put("discarded_files", summary.getChangedFiles());
                    data.put("discarded_commits", summary.getCommits());
                }
                return isOk(data);
            } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                return error("Failed to exit worktree: " + e.getMessage());
            }
        }
    }

    /**
     * Builds an object schema map for tool input parameters.
     *
     * @param properties map of property name to property schema
     * @param required list of required property names
     * @return object schema map
     */
    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    /**
     * Builds a string schema map with a description.
     *
     * @param description property description
     * @return string schema map
     */
    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    /**
     * Returns the input map or an empty map if null.
     *
     * @param inputs input map, may be null
     * @return non-null input map
     */
    private static Map<String, Object> safeInputs(Map<String, Object> inputs) {
        return inputs != null ? inputs : Map.of();
    }

    /**
     * Converts a value to string, returning a fallback if null.
     *
     * @param value value to convert
     * @param fallback fallback string if value is null
     * @return string representation of value or fallback
     */
    private static String stringValue(Object value, String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }

    /**
     * Converts a value to boolean, returning a fallback if null.
     *
     * @param value value to convert
     * @param fallback fallback boolean if value is null
     * @return boolean representation of value or fallback
     */
    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null ? Boolean.parseBoolean(String.valueOf(value)) : fallback;
    }

    /**
     * Converts a value to a list of strings.
     *
     * @param value value to convert, may be a List
     * @return list of string values, or empty list if not a List
     */
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Creates a brief summary map of a task.
     *
     * @param task task to summarize
     * @return map containing task_id, title, content, status, assignee, and dependencies
     */
    private static Map<String, Object> taskBrief(TeamTask task) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("task_id", task.getTaskId());
        item.put("title", task.getTitle());
        item.put("content", task.getContent());
        item.put("status", task.getStatus());
        item.put("assignee", task.getAssignee());
        item.put("dependencies", task.getDependencies());
        return item;
    }

    /**
     * Creates a data map from a workspace file lock.
     *
     * @param lock file lock to convert
     * @return map containing file_path, holder_id, holder_name, acquired_at, and timeout_seconds
     */
    private static Map<String, Object> lockData(WorkspaceFileLock lock) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("file_path", lock.getFilePath());
        item.put("holder_id", lock.getHolderId());
        item.put("holder_name", lock.getHolderName());
        item.put("acquired_at", lock.getAcquiredAt());
        item.put("timeout_seconds", lock.getTimeoutSeconds());
        return item;
    }

    /**
     * Converts a ToolOutput to its mapped string content representation.
     *
     * @param output tool output to convert
     * @return string content: error message if failed, "OK" if no data, or JSON representation of data
     */
    static String mappedContent(ToolOutput output) {
        if (!output.isSuccess()) {
            return output.getError() != null ? output.getError() : "Operation failed";
        }
        if (output.getData() == null) {
            return "OK";
        }
        return JsonUtils.safeJsonDumps(output.getData(), String.valueOf(output.getData()));
    }
}
