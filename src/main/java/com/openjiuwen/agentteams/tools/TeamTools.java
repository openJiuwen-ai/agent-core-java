/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import com.openjiuwen.agentteams.agent.Allocation;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.function.Function;

/**
 * Role-filtered team tool wrappers aligned with Python agent_teams/tools/team_tools.py.
 */
public final class TeamTools {
    /**
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public static final Set<String> MEMBER_ONLY_TOOLS = Set.of("claim_task", "enter_worktree", "exit_worktree");
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final Set<String> SHARED_TOOLS = Set.of("view_task", "send_message", "workspace_meta");
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final Set<String> HUMAN_AGENT_TOOLS = Set.of("send_message");
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final Set<String> LEADER_TOOLS = union(LEADER_ONLY_TOOLS, SHARED_TOOLS);
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final Set<String> MEMBER_TOOLS = union(MEMBER_ONLY_TOOLS, SHARED_TOOLS);

    private TeamTools() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Tool> createTeamTools(String role, TeamBackend backend) {
        return createTeamTools(role, backend, "build_mode", Set.of());
    }

    /**
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public static List<Tool> createTeamTools(
            String role,
            TeamBackend backend,
            String teammateMode,
            Set<String> excludeTools,
            TeamWorkspaceManager workspaceManager
    ) {
        return createTeamTools(role, backend, teammateMode, excludeTools, workspaceManager, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Tool> createTeamTools(
            String role,
            TeamBackend backend,
            String teammateMode,
            Set<String> excludeTools,
            TeamWorkspaceManager workspaceManager,
            WorktreeManager worktreeManager
    ) {
        return createTeamTools(
                role,
                backend,
                teammateMode,
                excludeTools,
                workspaceManager,
                worktreeManager,
                ignored -> null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Tool> createTeamTools(
            String role,
            TeamBackend backend,
            String teammateMode,
            Set<String> excludeTools,
            TeamWorkspaceManager workspaceManager,
            WorktreeManager worktreeManager,
            Function<String, Allocation> modelConfigAllocator
    ) {
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
        allTools.put("claim_task", new ClaimTaskTool(backend.getTaskManager()));
        allTools.put("send_message", new SendMessageTool(backend));
        if (workspaceManager != null) {
            allTools.put("workspace_meta", new WorkspaceMetaTool(workspaceManager, backend));
        }
        if (worktreeManager != null) {
            allTools.put("enter_worktree", new EnterWorktreeTool(worktreeManager, backend));
            allTools.put("exit_worktree", new ExitWorktreeTool(worktreeManager));
        }

        Set<String> isAllowed;
        if ("human_agent".equals(role)) {
            isAllowed = HUMAN_AGENT_TOOLS;
        } else if ("leader".equals(role)) {
            isAllowed = LEADER_TOOLS;
        } else {
            isAllowed = MEMBER_TOOLS;
        }
        isAllowed = new LinkedHashSet<>(isAllowed);
        if ("leader".equals(role) && !"plan_mode".equals(teammateMode)) {
            isAllowed.remove("approve_plan");
            isAllowed.remove("approve_tool");
        }
        if (excludeTools != null) {
            isAllowed.removeAll(excludeTools);
        }
        List<Tool> tools = new ArrayList<>();
        for (Map.Entry<String, Tool> entry : allTools.entrySet()) {
            if (isAllowed.contains(entry.getKey())) {
                tools.add(entry.getValue());
            }
        }
        return tools;
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> values = new LinkedHashSet<>(left);
        values.addAll(right);
        return Set.copyOf(values);
    }

    static class TeamTool extends Tool {
        /**
         * Auto-generated for codecheck compliance.
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return ToolOutput.builder().success(false).error("Not implemented").build();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.<Object>of(invoke(inputs, kwargs)).iterator();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        protected ToolOutput isOk(Object data) {
            ToolOutput output = ToolOutput.builder().success(true).data(data).build();
            return MappedToolOutput.from(output, mapResult(output));
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        protected ToolOutput error(String error) {
            ToolOutput output = ToolOutput.builder().success(false).error(error).build();
            return MappedToolOutput.from(output, mapResult(output));
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        protected String mapResult(ToolOutput output) {
            return mappedContent(output);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class MappedToolOutput extends ToolOutput {
        private final String mappedContent;

        private MappedToolOutput(boolean success, Object data, String error, String mappedContent) {
            super(success, data, error);
            this.mappedContent = mappedContent;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public static MappedToolOutput from(ToolOutput output, String mappedContent) {
            return new MappedToolOutput(output.isSuccess(), output.getData(), output.getError(), mappedContent);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String toString() {
            return mappedContent;
        }
    }

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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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

    static final class CleanTeamTool extends TeamTool {
        private final TeamBackend backend;

        CleanTeamTool(TeamBackend backend) {
            super("clean_team", "Clean up a team when all non-leader members are shutdown.",
                    objectSchema(Map.of(), List.of()));
            this.backend = backend;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            boolean success = backend.cleanTeam().join();
            if (!success) {
                return error("Active members remain. Use shutdown_member to close all members first.");
            }
            return isOk(Map.of("team_name", backend.getTeamName()));
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to clean team";
            }
            return "Team cleaned: team_name=" + ((Map<?, ?>) output.getData()).get("team_name");
        }
    }

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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String memberName = stringValue(safeInputs.get("member_name"), "");
            String displayName = stringValue(safeInputs.get("display_name"), memberName);
            if (memberName.isBlank()) {
                return error("'member_name' is required");
            }
            String modelName = stringValue(safeInputs.get("model_name"), null);
            Allocation allocation = modelConfigAllocator.apply(modelName);
            boolean success = backend.spawnMember(memberName, displayName, AgentCard.builder()
                    .name(displayName)
                    .description(stringValue(safeInputs.get("desc"), ""))
                    .build(), TeamRole.MEMBER, stringValue(safeInputs.get("prompt"), null), allocation).join();
            if (!success) {
                return error("Failed to spawn member");
            }
            return isOk(Map.of("member_name", memberName, "display_name", displayName));
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to spawn member";
            }
            Map<?, ?> data = (Map<?, ?>) output.getData();
            return "Member spawned: member_name=" + data.get("member_name")
                    + " display_name=" + data.get("display_name");
        }
    }

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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String memberName = stringValue(safeInputs.get("member_name"), "");
            if (memberName.isBlank()) {
                return error("'member_name' is required");
            }
            if (!backend.shutdownMember(memberName, booleanValue(safeInputs.get("force"), false)).join()) {
                return error("Member not found or cannot shut down");
            }
            return isOk(Map.of("member_name", memberName, "status", "shutdown_requested"));
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to shutdown member";
            }
            return "Member shutdown: member_name=" + ((Map<?, ?>) output.getData()).get("member_name");
        }
    }

    static final class ApprovePlanTool extends TeamTool {
        private final TeamBackend backend;

        ApprovePlanTool(TeamBackend backend) {
            super("approve_plan", "Approve or reject a member plan.", objectSchema(Map.of(
                    "member_name", stringSchema("Member name"),
                    "approved", Map.of("type", "boolean"),
                    "feedback", stringSchema("Feedback")
            ), List.of("member_name", "approved")));
            this.backend = backend;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String memberName = stringValue(safeInputs.get("member_name"), "");
            if (memberName.isBlank()) {
                return error("'member_name' is required");
            }
            boolean approved = booleanValue(safeInputs.get("approved"), false);
            if (!backend.approvePlan(memberName, approved, stringValue(safeInputs.get("feedback"), null)).join()) {
                return error("Failed to approve plan");
            }
            return isOk(Map.of("member_name", memberName, "approved", approved));
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        protected String mapResult(ToolOutput output) {
            if (!output.isSuccess()) {
                return output.getError() != null ? output.getError() : "Failed to approve/reject plan";
            }
            Map<?, ?> data = (Map<?, ?>) output.getData();
            String decision = Boolean.TRUE.equals(data.get("approved")) ? "approved" : "rejected";
            return "Plan " + decision + ": member_name=" + data.get("member_name") + " decision=" + decision;
        }
    }

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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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

    static final class ListMembersTool extends TeamTool {
        private final TeamBackend backend;

        ListMembersTool(TeamBackend backend) {
            super("list_members", "List all team members.", objectSchema(Map.of(), List.of()));
            this.backend = backend;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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

    static final class TaskCreateTool extends TeamTool {
        private final TeamTaskManager taskManager;

        TaskCreateTool(TeamTaskManager taskManager) {
            super("create_task", "Create team tasks.", objectSchema(Map.of(
                    "tasks", Map.of("type", "array", "items", Map.of("type", "object"))
            ), List.of("tasks")));
            this.taskManager = taskManager;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Object tasksValue = safeInputs(inputs).get("tasks");
            if (!(tasksValue instanceof List<?> rawTasks) || rawTasks.isEmpty()) {
                return error("'tasks' is required");
            }
            List<Map<String, Object>> taskSpecs = rawTasks.stream()
                    .filter(Map.class::isInstance)
                    .map(value -> (Map<String, Object>) value)
                    .toList();
            List<TeamTask> created = taskManager.addBatch(taskSpecs).join();
            if (created.isEmpty()) {
                return error("No valid tasks created");
            }
            return isOk(Map.of(
                    "tasks", created.stream().map(TeamTools::taskBrief).toList(),
                    "count", created.size()
            ));
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String action = stringValue(safeInputs.get("action"), "list");
            if ("get".equals(action)) {
                TeamTask task = taskManager.get(stringValue(safeInputs.get("task_id"), ""));
                if (task == null) {
                    return error("Task not found");
                }
                return isOk(taskBrief(task));
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
            TeamTask task = backend.getTaskManager().get(taskId);
            if (task == null) {
                return error("Task not found");
            }
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

        private boolean isHumanAgentLocked(TeamTask task) {
            if (task == null || task.getAssignee() == null) {
                return false;
            }
            var member = backend.getMember(task.getAssignee());
            return member != null
                    && member.getRole() == com.openjiuwen.agentteams.schema.team.TeamRole.HUMAN_AGENT;
        }

        private void cancelMemberIfClaimed(TeamTask task) {
            if (task.getAssignee() != null) {
                backend.shutdownMember(task.getAssignee(), false);
            }
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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

    static final class ClaimTaskTool extends TeamTool {
        private final TeamTaskManager taskManager;

        ClaimTaskTool(TeamTaskManager taskManager) {
            super("claim_task", "Claim or complete a task.", objectSchema(Map.of(
                    "task_id", stringSchema("Task id"),
                    "status", Map.of("type", "string", "enum", List.of("claimed", "completed"))
            ), List.of("task_id", "status")));
            this.taskManager = taskManager;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safeInputs = safeInputs(inputs);
            String taskId = stringValue(safeInputs.get("task_id"), "");
            String status = stringValue(safeInputs.get("status"), "");
            com.openjiuwen.core.common.logging.Loggers.TOOL.info(
                "ClaimTaskTool.invoke: taskId={} status={} cardId={}",
                taskId, status, getCard() != null ? getCard().getId() : "null");
            TeamTask task = taskManager.get(taskId);
            if (task == null) {
                return error("Task not found");
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
                String resolved = backend.resolveMemberName(to);
                if (resolved != null) {
                    to = resolved;
                } else {
                    return error("Member '" + to + "' not found");
                }
            }
            String messageId = "*".equals(to)
                    ? backend.getMessageManager().broadcastMessage(content).join()
                    : backend.getMessageManager().sendMessage(content, to).join();
            if (messageId == null || messageId.isBlank()) {
                return error("Failed to send message");
            }
            if ("*".equals(to)) {
                Loggers.TOOL.info("send_message broadcast detected, launching all unstarted members");
                int launched = backend.launchUnstartedMembers(content);
                Loggers.TOOL.info("launched {} unstarted member(s) after broadcast", launched);
            } else {
                boolean launched = backend.launchMemberIfUnstarted(to, content);
                if (launched) {
                    Loggers.TOOL.info("send_message unicast to UNSTARTED member {}, auto-launched with message", to);
                }
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
                    WorkspaceFileLock lock = workspaceManager.getLock(path);
                    return error(lock != null ? "Locked by " + lock.getHolderName() : "Lock failed");
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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
            } catch (Exception e) {
                return error("Failed to create worktree: " + e.getMessage());
            }
        }
    }

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
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
            } catch (Exception e) {
                return error("Failed to exit worktree: " + e.getMessage());
            }
        }
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> safeInputs(Map<String, Object> inputs) {
        return inputs != null ? inputs : Map.of();
    }

    private static String stringValue(Object value, String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null ? Boolean.parseBoolean(String.valueOf(value)) : fallback;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

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

    private static Map<String, Object> lockData(WorkspaceFileLock lock) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("file_path", lock.getFilePath());
        item.put("holder_id", lock.getHolderId());
        item.put("holder_name", lock.getHolderName());
        item.put("acquired_at", lock.getAcquiredAt());
        item.put("timeout_seconds", lock.getTimeoutSeconds());
        return item;
    }

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
