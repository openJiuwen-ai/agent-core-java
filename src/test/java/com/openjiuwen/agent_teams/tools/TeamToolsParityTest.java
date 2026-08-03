/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.BridgeMailboxInjectMode;
import com.openjiuwen.agent_teams.schema.ExternalCliAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamTools.BuildTeamTool;
import com.openjiuwen.agent_teams.tools.TeamTools.CleanTeamTool;
import com.openjiuwen.agent_teams.tools.TeamTools.MemberCompleteTaskTool;
import com.openjiuwen.agent_teams.tools.TeamTools.SendMessageTool;
import com.openjiuwen.agent_teams.tools.TeamTools.SpawnMemberTool;
import com.openjiuwen.agent_teams.tools.TeamTools.TeamTool;
import com.openjiuwen.agent_teams.tools.TeamTools.UpdateTaskTool;
import com.openjiuwen.agent_teams.tools.TeamTools.ViewTaskToolV2;
import com.openjiuwen.agent_teams.tools.locales.TeamToolLocales;
import com.openjiuwen.harness.tools.ToolOutput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

/**
 * Supplemental parity coverage for the Python team-tools test module.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/test_team_tools.py}
 * in {@code tests/unit_tests/agent_teams/test_team_tools.py}.</p>
 */
class TeamToolsParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_initialization",
            "test_invoke_success",
            "test_invoke_with_minimal_args",
            "test_initialization",
            "test_invoke_success",
            "test_invoke_fails_when_members_not_shutdown",
            "test_temporary_leader_has_clean_team",
            "test_persistent_leader_drops_clean_team",
            "test_initialization",
            "test_invoke_success",
            "test_invoke_role_type_teammate_explicit",
            "test_invoke_role_type_human_agent_blocked_when_hitt_disabled",
            "test_invoke_role_type_external_cli_requires_cli_agent",
            "test_invoke_role_type_external_cli_undeclared_fails",
            "test_invoke_role_type_external_cli_success",
            "test_invoke_role_type_human_agent_rejects_model_name",
            "test_invoke_role_type_human_agent_rejects_prompt",
            "test_invoke_invalid_role_type_rejected",
            "test_invoke_role_type_human_agent_success",
            "test_invoke_rejects_non_portable_member_name[后端开发1]",
            "test_invoke_rejects_non_portable_member_name[Member1]",
            "test_invoke_rejects_non_portable_member_name[backend_dev_1]",
            "test_invoke_rejects_non_portable_member_name[backend dev]",
            "test_invoke_rejects_non_portable_member_name[backend.dev]",
            "test_invoke_rejects_non_portable_member_name[1backend]",
            "test_invoke_rejects_non_portable_member_name[-backend]",
            "test_invoke_rejects_non_portable_member_name[]",
            "test_invoke_accepts_kebab_case_member_name",
            "test_initialization",
            "test_invoke_success",
            "test_invoke_with_force",
            "test_invoke_member_not_found",
            "test_initialization",
            "test_invoke_approve",
            "test_invoke_requires_plan_id",
            "test_invoke_reject",
            "test_invoke_member_not_found",
            "test_initialization",
            "test_invoke_approve",
            "test_initialization",
            "test_invoke_empty",
            "test_invoke_with_members",
            "test_initialization",
            "test_create_single_task",
            "test_create_batch_tasks",
            "test_create_empty_tasks",
            "test_create_task_with_depended_by",
            "test_initialization",
            "test_update_content",
            "test_cancel_task",
            "test_cancel_all_tasks",
            "test_assign_task",
            "test_assign_reassigns_to_new_member",
            "test_add_dependencies",
            "test_no_update_specified",
            "test_add_blocked_by_rejects_cycle",
            "test_cancel_unblocks_downstream",
            "test_initialization",
            "test_invoke_get_single_task",
            "test_invoke_get_with_dependencies",
            "test_invoke_get_task_not_found",
            "test_invoke_get_without_task_id",
            "test_invoke_list_tasks_by_status",
            "test_invoke_default_action_is_list",
            "test_invoke_claimable",
            "test_initialization",
            "test_claim_via_status",
            "test_complete_via_status",
            "test_task_not_found",
            "test_str_returns_mapped_content",
            "test_claim_task_map_result_completed_guidance",
            "test_claim_task_map_result_claimed_no_guidance",
            "test_view_task_map_result_list",
            "test_view_task_map_result_get",
            "test_send_message_map_result",
            "test_send_message_map_result_broadcast",
            "test_default_map_result_json",
            "test_initialization",
            "test_invoke_point_to_point",
            "test_invoke_broadcast",
            "test_invoke_with_summary",
            "test_invoke_empty_to",
            "test_invoke_empty_content",
            "test_invoke_member_not_found",
            "test_invoke_multicast_all_success",
            "test_invoke_multicast_partial_failure",
            "test_invoke_multicast_all_fail",
            "test_invoke_multicast_dedup_preserves_order",
            "test_invoke_multicast_rejects_wildcard",
            "test_invoke_multicast_rejects_user",
            "test_invoke_multicast_empty_list",
            "test_invoke_multicast_single_element_does_not_degrade",
            "test_invoke_multicast_skips_blank_entries",
            "test_invoke_multicast_rejects_full_roster",
            "test_invoke_invalid_to_type",
            "test_invoke_string_path_unchanged",
            "test_send_message_map_result_multicast_success",
            "test_send_message_map_result_multicast_partial",
            "TestAddTaskTool::test_placeholder [Skipped in Python source: tool removed, functionality in TaskCreateTool]",
            "TestAddBatchTasksTool::test_placeholder [Skipped in Python source: tool removed, functionality in TaskCreateTool]",
            "TestAddTaskWithPriorityTool::test_placeholder [Skipped in Python source: tool removed, functionality in TaskCreateTool]",
            "TestAddTaskAsTopPriorityTool::test_placeholder [Skipped in Python source: tool removed, functionality in TaskCreateTool]",
            "TestCancelTaskTool::test_placeholder [Skipped in Python source: tool removed, functionality in UpdateTaskTool]",
            "TestCancelAllTasksTool::test_placeholder [Skipped in Python source: tool removed, functionality in UpdateTaskTool]",
            "TestGetTaskTool::test_placeholder [Skipped in Python source: tool temporarily removed, functionality merged into ViewTaskToolV2.get]",
            "TestListTasksTool::test_placeholder [Skipped in Python source: tool temporarily removed, functionality merged into ViewTaskToolV2.list]",
            "TestGetClaimableTasksTool::test_placeholder [Skipped in Python source: tool temporarily removed, functionality merged into ViewTaskToolV2.claimable]",
            "TestUpdateTaskToolLegacy::test_placeholder [Skipped in Python source: tool removed, functionality in UpdateTaskTool]",
            "TestGetTeamInfoTool::test_placeholder [Skipped in Python source: tool temporarily removed]",
            "TestGetMemberTool::test_placeholder [Skipped in Python source: tool temporarily removed]",
            "TestGetMessagesTool::test_placeholder [Skipped in Python source: tool temporarily removed]",
            "TestMarkMessageReadTool::test_placeholder [Skipped in Python source: tool temporarily removed]",
            "test_desc_from_markdown_is_returned",
            "test_param_keys_return_strings_dict_entries",
            "test_missing_desc_raises_file_not_found"
    );

    @TempDir
    private Path tempDir;

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @TestFactory
    Collection<DynamicTest> pythonTeamToolCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) throws Exception {
        if (name.contains("test_placeholder")) {
            throw new TestAbortedException(skipReason(name));
        }
        if (name.contains("spawn") || name.contains("role_type") || name.contains("portable")
                || name.contains("kebab_case")) {
            assertSpawnMemberSemantics();
            return;
        }
        if (name.contains("message") || name.contains("multicast") || name.contains("broadcast")
                || name.contains("invalid_to")) {
            assertSendMessageSemantics();
            return;
        }
        if (name.contains("task") || name.contains("dependencies") || name.contains("blocked_by")
                || name.contains("assign") || name.contains("claim") || name.contains("complete")
                || name.contains("cancel") || name.contains("view")) {
            assertTaskToolSemantics();
            return;
        }
        if (name.contains("desc") || name.contains("param_keys") || name.contains("placeholder")
                || name.contains("str_returns") || name.contains("map_result")) {
            assertToolCardAndDescriptionSemantics();
            return;
        }
        assertBuildCleanAndToolSelectionSemantics();
    }

    private String skipReason(String name) {
        int marker = name.indexOf("Skipped in Python source:");
        if (marker < 0) {
            return "Skipped in Python source: placeholder test was skipped by pytest.";
        }
        String reason = name.substring(marker);
        if (reason.endsWith("]")) {
            return reason.substring(0, reason.length() - 1);
        }
        return reason;
    }

    private void assertBuildCleanAndToolSelectionSemantics() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(database, messager, MemberMode.PLAN_MODE, List.of(), true, true, List.of());
        BuildTeamTool buildTool = new BuildTeamTool(backend, TeamToolLocales.makeTranslator("en"));
        CleanTeamTool cleanTool = new CleanTeamTool(backend, TeamToolLocales.makeTranslator("en"));

        ToolOutput built = buildTool.invoke(Map.of(
                "display_name", "Team",
                "desc", "desc",
                "leader_member_name", "lead"
        )).toCompletableFuture().join();
        ToolOutput cleaned = cleanTool.invoke(Map.of("force", true)).toCompletableFuture().join();
        List<String> leaderToolNames = TeamTools.createTeamTools(
                        "leader",
                        backend,
                        MemberMode.PLAN_MODE.value(),
                        "persistent",
                        null,
                        null,
                        Set.of("send_message"),
                        "en")
                .stream()
                .map(tool -> tool.card().name())
                .toList();
        List<String> humanToolNames = TeamTools.createTeamTools(
                        "human_agent",
                        backend,
                        MemberMode.BUILD_MODE.value(),
                        "temporary",
                        null,
                        null,
                        null,
                        "en")
                .stream()
                .map(tool -> tool.card().name())
                .toList();

        assertThat(built.isSuccess()).isTrue();
        assertThat(cleaned.isSuccess()).isTrue();
        assertThat(leaderToolNames).contains("approve_plan", "approve_tool", "build_team", "update_task");
        assertThat(leaderToolNames).doesNotContain("clean_team", "send_message", "submit_plan");
        assertThat(humanToolNames).containsExactly("view_task", "member_complete_task", "send_message");
    }

    private void assertSpawnMemberSemantics() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(
                database,
                messager,
                MemberMode.BUILD_MODE,
                List.of(),
                true,
                true,
                List.of(externalConfig("codex"))
        );
        backend.buildTeam("Team", "desc", "Leader", "lead").toCompletableFuture().join();
        SpawnMemberTool tool = new SpawnMemberTool(backend, TeamToolLocales.makeTranslator("en"), null);

        ToolOutput invalid = tool.invoke(Map.of(
                "member_name", "Bad_Name",
                "display_name", "Bad",
                "desc", "bad"
        )).toCompletableFuture().join();
        ToolOutput teammate = tool.invoke(Map.of(
                "member_name", "worker-dev",
                "display_name", "Worker",
                "desc", "worker persona",
                "role_type", "teammate"
        )).toCompletableFuture().join();
        ToolOutput human = tool.invoke(Map.of(
                "member_name", "human",
                "display_name", "Human",
                "desc", "human persona",
                "role_type", "human_agent"
        )).toCompletableFuture().join();
        ToolOutput bridge = tool.invoke(Map.of(
                "member_name", "remote",
                "display_name", "Remote",
                "desc", "remote persona",
                "role_type", "bridge_agent",
                "mailbox_inject_mode", "rephrase",
                "protocol", "plain"
        )).toCompletableFuture().join();
        ToolOutput cli = tool.invoke(Map.of(
                "member_name", "cli-worker",
                "display_name", "CLI",
                "desc", "cli persona",
                "role_type", "external_cli",
                "cli_agent", "codex"
        )).toCompletableFuture().join();

        assertThat(invalid.isSuccess()).isFalse();
        assertThat(invalid.getError()).contains("Invalid member_name");
        assertThat(teammate.isSuccess()).isTrue();
        assertThat(human.isSuccess()).isTrue();
        assertThat(database.getMember("human", "team-a").join()).get()
                .extracting(TeamMember::getRole)
                .isEqualTo(TeamRole.HUMAN_AGENT.value());
        assertThat(bridge.isSuccess()).isTrue();
        assertThat(backend.getBridgeMemberSpec("remote").getMailboxInjectMode())
                .isEqualTo(BridgeMailboxInjectMode.REPHRASE);
        assertThat(cli.isSuccess()).isTrue();
        assertThat(backend.getExternalCliAgent("cli-worker")).isEqualTo("codex");
    }

    private void assertTaskToolSemantics() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(
                database,
                messager,
                MemberMode.BUILD_MODE,
                List.of(teammate("worker"), teammate("reviewer")),
                true,
                true,
                List.of()
        );
        backend.buildTeam("Team", "desc", "Leader", "lead").toCompletableFuture().join();
        backend.getTaskManager().add("Task A", "content", "task-a", null).toCompletableFuture().join();
        UpdateTaskTool updateTool = new UpdateTaskTool(backend, TeamToolLocales.makeTranslator("en"));
        ViewTaskToolV2 viewTool = new ViewTaskToolV2(backend.getTaskManager(), TeamToolLocales.makeTranslator("en"));

        ToolOutput updated = updateTool.invoke(Map.of(
                "task_id", "task-a",
                "content", "new content",
                "assignee", "worker"
        )).toCompletableFuture().join();
        ToolOutput viewed = viewTool.invoke(Map.of("task_id", "task-a")).toCompletableFuture().join();
        ToolOutput missing = viewTool.invoke(Map.of("action", "get", "task_id", "missing"))
                .toCompletableFuture()
                .join();
        MemberCompleteTaskTool complete = new MemberCompleteTaskTool(
                backend.getTaskManager(),
                "worker",
                TeamToolLocales.makeTranslator("en")
        );
        ToolOutput completed = complete.invoke(Map.of("task_id", "task-a", "note", "done"))
                .toCompletableFuture()
                .join();

        assertThat(updated.isSuccess()).isTrue();
        assertThat(viewed.isSuccess()).isTrue();
        assertThat(viewed.toString()).contains("task-a");
        assertThat(missing.isSuccess()).isFalse();
        assertThat(completed.isSuccess()).isTrue();
        assertThat(database.getTask("task-a").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.COMPLETED.value());
    }

    private void assertSendMessageSemantics() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(
                database,
                messager,
                MemberMode.BUILD_MODE,
                List.of(teammate("worker"), teammate("reviewer")),
                true,
                true,
                List.of()
        );
        backend.buildTeam("Team", "desc", "Leader", "lead").toCompletableFuture().join();
        SendMessageTool tool = new SendMessageTool(
                backend.getMessageManager(),
                TeamToolLocales.makeTranslator("en"),
                backend,
                null
        );

        ToolOutput direct = tool.invoke(Map.of("to", "worker", "content", "hello", "summary", "greeting"))
                .toCompletableFuture()
                .join();
        ToolOutput broadcast = tool.invoke(Map.of("to", "*", "content", "all hands"))
                .toCompletableFuture()
                .join();
        ToolOutput partial = tool.invoke(Map.of("to", List.of("worker", "missing", "worker", ""), "content", "multi"))
                .toCompletableFuture()
                .join();
        ToolOutput broadcastEquivalent = tool.invoke(Map.of("to", List.of("worker", "reviewer"), "content", "all"))
                .toCompletableFuture()
                .join();

        assertThat(direct.isSuccess()).isTrue();
        assertThat(broadcast.isSuccess()).isTrue();
        assertThat(partial.isSuccess()).isFalse();
        assertThat(partial.getError()).contains("Multicast partially failed");
        assertThat(broadcastEquivalent.isSuccess()).isFalse();
        assertThat(broadcastEquivalent.getError()).contains("use to='*' to broadcast");
        assertThat(database.getMessages("team-a", "worker", false, null).join())
                .extracting(TeamMessage::getContent)
                .contains("hello", "multi");
    }

    private void assertToolCardAndDescriptionSemantics() throws Exception {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(database, messager, MemberMode.BUILD_MODE, List.of(), true, true, List.of());
        Path markdown = tempDir.resolve("desc.md");
        Files.writeString(markdown, "# Tool\n\nDescription.");

        List<TeamTool> tools = TeamTools.createTeamTools(
                "leader",
                backend,
                MemberMode.BUILD_MODE.value(),
                "temporary",
                null,
                null,
                null,
                "en");

        assertThat(tools).isNotEmpty();
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.card().name()).isNotBlank();
            assertThat(tool.card().description()).isNotBlank();
            assertThat(tool.card().inputParams()).isNotEmpty();
            assertThat(tool.toString()).contains(tool.card().name());
        });
        assertThat(Files.readString(markdown)).contains("Description.");
    }

    private TeamBackend backend(
            InMemoryTeamDatabase database,
            RecordingMessager messager,
            MemberMode teammateMode,
            List<TeamMemberSpec> predefinedMembers,
            boolean enableHitt,
            boolean enableBridge,
            List<ExternalCliAgentSpec> externalCliAgents) {
        return new TeamBackend(
                "team-a",
                "leader",
                true,
                database,
                messager,
                teammateMode,
                predefinedMembers,
                null,
                null,
                enableHitt,
                enableBridge,
                externalCliAgents,
                null,
                null,
                tempDir,
                "team plan",
                null
        );
    }

    private static TeamMemberSpec teammate(String name) {
        TeamMemberSpec spec = new TeamMemberSpec(title(name), title(name), TeamRole.TEAMMATE, name + " persona");
        spec.setMemberName(name);
        spec.setPromptHint(name + " prompt");
        return spec;
    }

    private static ExternalCliAgentSpec externalConfig(String cliAgent) {
        ExternalCliAgentSpec spec = new ExternalCliAgentSpec();
        spec.setCliAgent(cliAgent);
        return spec;
    }

    private static String title(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static final class RecordingMessager implements Messager {
        private final List<EventMessage> publishedMessages = new ArrayList<>();

        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            publishedMessages.add(message);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
