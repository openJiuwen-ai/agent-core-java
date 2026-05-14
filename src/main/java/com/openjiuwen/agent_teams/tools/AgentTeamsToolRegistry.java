/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.core.foundation.tool.Tool;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal Java team-tool registry.
 *
 * <p>Mirrors Python's team-tool factory flow in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public final class AgentTeamsToolRegistry {

    private static final Set<String> LEADER_ONLY_TOOLS = Set.of(
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

    private static final Set<String> MEMBER_ONLY_TOOLS = Set.of(
            "claim_task"
    );

    private static final Set<String> SHARED_TOOLS = Set.of(
            "view_task",
            "send_message"
    );

    private static final Set<String> HUMAN_AGENT_TOOLS = Set.of(
            "send_message"
    );

    private AgentTeamsToolRegistry() {
    }

    public static List<Tool> createTeamTools(TeamBackend team, TeamRole role, String teammateMode) {
        Set<String> allowed = allowedTools(role, teammateMode);
        List<Tool> tools = new ArrayList<>();
        maybeAdd(tools, allowed, new BuildTeamTool(team));
        maybeAdd(tools, allowed, new CleanTeamTool(team));
        maybeAdd(tools, allowed, new ListMembersTool(team));
        maybeAdd(tools, allowed, new SpawnMemberTool(team));
        maybeAdd(tools, allowed, new ShutdownMemberTool(team));
        maybeAdd(tools, allowed, new ApprovePlanTool(team));
        maybeAdd(tools, allowed, new ApproveToolCallTool(team));
        maybeAdd(tools, allowed, new CreateTaskTool(team));
        maybeAdd(tools, allowed, new UpdateTaskTool(team));
        maybeAdd(tools, allowed, new ViewTaskTool(team));
        maybeAdd(tools, allowed, new ClaimTaskTool(team));
        maybeAdd(tools, allowed, new SendMessageTool(team));
        return tools;
    }

    private static Set<String> allowedTools(TeamRole role, String teammateMode) {
        if (role == TeamRole.HUMAN_AGENT) {
            return HUMAN_AGENT_TOOLS;
        }
        Set<String> allowed = new LinkedHashSet<>(SHARED_TOOLS);
        if (role == TeamRole.LEADER) {
            allowed.addAll(LEADER_ONLY_TOOLS);
            if (!"plan_mode".equalsIgnoreCase(teammateMode)) {
                allowed.remove("approve_plan");
                allowed.remove("approve_tool");
            }
            return allowed;
        }
        allowed.addAll(MEMBER_ONLY_TOOLS);
        return allowed;
    }

    private static void maybeAdd(List<Tool> tools, Set<String> allowed, Tool tool) {
        String toolName = readStringField(tool.getCard(), "name");
        if (allowed.contains(toolName)) {
            tools.add(tool);
        }
    }

    private static String readStringField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                return value != null ? String.valueOf(value) : null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }
}
