/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.core.foundation.tool.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java team-tool registry.
 *
 * <p>Mirrors Python's team-tool factory flow in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public final class AgentTeamsToolRegistry {

    private static final Set<String> LEADER_ONLY_TOOLS = orderedSet(
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

    private static final Set<String> MEMBER_ONLY_TOOLS = orderedSet(
            "claim_task"
    );

    private static final Set<String> SHARED_TOOLS = orderedSet(
            "view_task",
            "send_message",
            "workspace_meta"
    );

    public static final Set<String> LEADER_TOOLS = union(LEADER_ONLY_TOOLS, SHARED_TOOLS);

    public static final Set<String> MEMBER_TOOLS = union(MEMBER_ONLY_TOOLS, SHARED_TOOLS);

    private static final Set<String> HUMAN_AGENT_TOOLS = orderedSet(
            "send_message"
    );

    private AgentTeamsToolRegistry() {
    }

    public static List<Tool> createTeamTools(TeamBackend team, TeamRole role, String teammateMode) {
        return createTeamTools(team, role, teammateMode, null);
    }

    public static List<Tool> createTeamTools(
            TeamBackend team,
            TeamRole role,
            String teammateMode,
            Set<String> excludeTools
    ) {
        Set<String> allowed = allowedTools(role, teammateMode);
        if (excludeTools != null && !excludeTools.isEmpty()) {
            allowed.removeAll(excludeTools);
        }
        List<Tool> tools = new ArrayList<>();
        for (Map.Entry<String, Tool> entry : allTools(team).entrySet()) {
            if (allowed.contains(entry.getKey())) {
                tools.add(entry.getValue());
            }
        }
        return tools;
    }

    private static Set<String> allowedTools(TeamRole role, String teammateMode) {
        if (role == TeamRole.HUMAN_AGENT) {
            return new LinkedHashSet<>(HUMAN_AGENT_TOOLS);
        }
        if (role == TeamRole.LEADER) {
            Set<String> allowed = new LinkedHashSet<>(LEADER_TOOLS);
            if (!"plan_mode".equalsIgnoreCase(teammateMode)) {
                allowed.remove("approve_plan");
                allowed.remove("approve_tool");
            }
            return allowed;
        }
        return new LinkedHashSet<>(MEMBER_TOOLS);
    }

    private static Map<String, Tool> allTools(TeamBackend team) {
        Map<String, Tool> allTools = new LinkedHashMap<>();
        allTools.put("build_team", new BuildTeamTool(team));
        allTools.put("clean_team", new CleanTeamTool(team));
        allTools.put("spawn_member", new SpawnMemberTool(team));
        allTools.put("shutdown_member", new ShutdownMemberTool(team));
        allTools.put("approve_plan", new ApprovePlanTool(team));
        allTools.put("approve_tool", new ApproveToolCallTool(team));
        allTools.put("list_members", new ListMembersTool(team));
        allTools.put("create_task", new CreateTaskTool(team));
        allTools.put("update_task", new UpdateTaskTool(team));
        allTools.put("view_task", new ViewTaskTool(team));
        allTools.put("claim_task", new ClaimTaskTool(team));
        allTools.put("send_message", new SendMessageTool(team, true));
        return allTools;
    }

    private static Set<String> orderedSet(String... names) {
        Set<String> result = new LinkedHashSet<>();
        Collections.addAll(result, names);
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Collections.unmodifiableSet(result);
    }
}
