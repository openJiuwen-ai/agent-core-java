/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.TeamPaths;
import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility tests for the Python-style plan-mode submit and approval tool flow.
 *
 * @since 2026-08-07
 */
class SubmitPlanToolCompatibilityTest {
    private static final String TEAM_NAME = "submit-plan-team";
    private static final String TEAM_SESSION_ID = "submit-plan-session";

    @TempDir
    private Path tempDir;

    @BeforeEach
    void configureTeamHome() {
        TeamBackend.resetSharedDbCache();
        TeamPaths.configureOpenjiuwenHome(tempDir);
    }

    @AfterEach
    void cleanTeamState() {
        InProcessMessager.cleanupInprocessBus();
        TeamBackend.resetSharedDbCache();
        TeamPaths.resetOpenjiuwenHome();
    }

    @Test
    void factoryShouldExposeSubmitPlanOnlyToPlanModeMembersWithPythonSchema() {
        TeamBackend backend = createBackend("leader", true);

        List<Tool> planModeTools = TeamTools.createTeamTools(
                "teammate", backend, "plan_mode", Set.of());
        TeamTools.SubmitPlanTool submitPlan = findTool(
                planModeTools, "submit_plan", TeamTools.SubmitPlanTool.class);
        Map<String, Object> schema = submitPlan.getCard().getInputParams();
        Object propertiesValue = schema.get("properties");

        assertThat(submitPlan.getCard().getId()).isEqualTo("team.submit_plan");
        assertThat(schema).containsEntry("type", "object")
                .containsEntry("required", List.of("task_id", "plan_path"));
        assertThat(propertiesValue).isInstanceOf(Map.class);
        List<String> propertyNames = ((Map<?, ?>) propertiesValue).keySet().stream()
                .map(String::valueOf).toList();
        assertThat(propertyNames).containsExactlyInAnyOrder("task_id", "plan_id", "plan_path");
        assertThat(toolNames(TeamTools.createTeamTools("teammate", backend, "build_mode", Set.of())))
                .doesNotContain("submit_plan");
        assertThat(toolNames(TeamTools.createTeamTools("leader", backend, "plan_mode", Set.of())))
                .doesNotContain("submit_plan");
    }

    @Test
    void toolsShouldSubmitPersistClaimAndApprovePlan() throws IOException {
        TeamBackend leader = createBackend("leader", true);
        TeamBackend planner = createBackend("planner", false);
        MemberRecord plannerRecord = planner.getDb().member.getMember("planner", TEAM_NAME);
        plannerRecord.setMode("plan_mode");
        TeamTask task = leader.getTaskManager().add(
                "Prepare implementation", "Write a reviewed plan", "task-plan-1", List.of()).join();
        Path sourcePlan = tempDir.resolve("member-plan.md");
        Files.writeString(sourcePlan, "# Plan" + System.lineSeparator() + "1. Implement.",
                StandardCharsets.UTF_8);

        TeamTools.SubmitPlanTool submitPlan = findTool(TeamTools.createTeamTools(
                "teammate", planner, "plan_mode", Set.of()), "submit_plan", TeamTools.SubmitPlanTool.class);
        ToolOutput submitted = submitPlan.invoke(Map.of(
                "task_id", task.getTaskId(), "plan_id", "member-plan-1", "plan_path", sourcePlan.toString()),
                Map.of("tool_call_id", "submit-call-1"));
        Map<?, ?> submittedData = outputData(submitted);
        Map<String, Object> planRecord = planner.getTaskManager().getPlanRecord("member-plan-1");
        Path managedPlan = Path.of(String.valueOf(submittedData.get("member_plan_md")));

        assertThat(submitted.isSuccess()).isTrue();
        assertThat(submittedData.get("status")).isEqualTo("claimed");
        assertThat(Files.readString(managedPlan, StandardCharsets.UTF_8))
                .isEqualTo(Files.readString(sourcePlan, StandardCharsets.UTF_8));
        assertThat(planRecord).containsEntry("task_id", task.getTaskId())
                .containsEntry("member_name", "planner")
                .containsEntry("decision", "pending");
        assertThat(planner.getTaskManager().get(task.getTaskId()).orElseThrow())
                .extracting(TeamTask::getStatus, TeamTask::getAssignee)
                .containsExactly("claimed", "planner");

        TeamTools.ApprovePlanTool approvePlan = findTool(TeamTools.createTeamTools(
                "leader", leader, "plan_mode", Set.of()), "approve_plan", TeamTools.ApprovePlanTool.class);
        ToolOutput approved = approvePlan.invoke(Map.of(
                "plan_id", "member-plan-1", "approved", true, "feedback", "Proceed"), Map.of());

        assertThat(approved.isSuccess()).isTrue();
        assertThat(leader.getTaskManager().get(task.getTaskId()).orElseThrow().getStatus())
                .isEqualTo("plan_approved");
        assertThat(leader.getTaskManager().getPlanRecord("member-plan-1"))
                .containsEntry("decision", "approve")
                .containsEntry("status", "plan_approved");
    }

    private TeamBackend createBackend(String memberName, boolean isLeader) {
        InProcessMessager messager = new InProcessMessager(
                MessagerTransportConfig.builder().nodeId(memberName).build());
        return new TeamBackend(TEAM_NAME, memberName, isLeader, messager, TEAM_SESSION_ID);
    }

    private static <T extends Tool> T findTool(List<Tool> tools, String name, Class<T> toolType) {
        Tool tool = tools.stream()
                .filter(candidate -> name.equals(candidate.getCard().getName()))
                .findFirst()
                .orElseThrow();
        return toolType.cast(tool);
    }

    private static List<String> toolNames(List<Tool> tools) {
        return tools.stream().map(tool -> tool.getCard().getName()).toList();
    }

    private static Map<?, ?> outputData(ToolOutput output) {
        assertThat(output.getData()).isInstanceOf(Map.class);
        return (Map<?, ?>) output.getData();
    }
}
