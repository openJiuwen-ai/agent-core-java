/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.BridgeMailboxInjectMode;
import com.openjiuwen.agent_teams.schema.ExternalCliAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamTools.MemberCompleteTaskTool;
import com.openjiuwen.agent_teams.tools.TeamTools.SendMessageTool;
import com.openjiuwen.agent_teams.tools.TeamTools.SpawnMemberTool;
import com.openjiuwen.agent_teams.tools.TeamTools.TeamTool;
import com.openjiuwen.agent_teams.tools.TeamTools.UpdateTaskTool;
import com.openjiuwen.agent_teams.tools.locales.TeamToolLocales;
import com.openjiuwen.harness.tools.ToolOutput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused parity tests for {@link TeamTools}.
 *
 * <p>Mirrors Python's {@code team_tools.py} in
 * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
 */
class TeamToolsTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void spawnMemberRejectsUnsafeMemberNamesBeforeBackendMutation() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(database, messager, MemberMode.BUILD_MODE, List.of(), true, true, List.of());
        SpawnMemberTool tool = new SpawnMemberTool(backend, TeamToolLocales.makeTranslator("en"), null);

        ToolOutput output = tool.invoke(Map.of(
                "member_name", "Bad_Name",
                "display_name", "Bad",
                "desc", "bad"
        )).toCompletableFuture().join();

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("Invalid member_name").contains("lowercase ASCII");
        assertThat(database.getMember("Bad_Name", "team-a").join()).isEmpty();
        assertThat(output.toString()).contains("Invalid member_name");
    }

    @Test
    void spawnMemberHandlesHumanBridgeAndExternalCliBranches() {
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
                "protocol", "plain",
                "adapter_config", Map.of("timeout", 3)
        )).toCompletableFuture().join();
        ToolOutput cli = tool.invoke(Map.of(
                "member_name", "cli-worker",
                "display_name", "CLI",
                "desc", "cli persona",
                "role_type", "external_cli",
                "cli_agent", "codex"
        )).toCompletableFuture().join();

        assertThat(human.isSuccess()).isTrue();
        assertThat(database.getMember("human", "team-a").join()).get()
                .extracting(TeamMember::getRole)
                .isEqualTo(TeamRole.HUMAN_AGENT.value());
        assertThat(bridge.isSuccess()).isTrue();
        assertThat(backend.isBridgeAgent("remote")).isTrue();
        assertThat(backend.getBridgeMemberSpec("remote").getMailboxInjectMode())
                .isEqualTo(BridgeMailboxInjectMode.REPHRASE);
        assertThat(cli.isSuccess()).isTrue();
        assertThat(backend.isExternalCliAgent("cli-worker")).isTrue();
        assertThat(backend.getExternalCliAgent("cli-worker")).isEqualTo("codex");
        assertThat(cli.toString()).contains("role=external_cli").contains("cli_agent=codex");
    }

    @Test
    void approvePlanToolUsesPlanIdFromSubmitPlan() throws Exception {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(
                database,
                messager,
                MemberMode.PLAN_MODE,
                List.of(teammate("planner")),
                true,
                true,
                List.of()
        );
        backend.buildTeam("Team", "desc", "Leader", "lead").toCompletableFuture().join();
        backend.getTaskManager().add("Plan task", "write plan", "task-plan", null).toCompletableFuture().join();
        TeamTaskManager planner = new TeamTaskManager("team-a", "planner", database, messager, tempDir, "team plan", "leader");
        Path sourcePlan = tempDir.resolve("source-plan.md");
        Files.writeString(sourcePlan, "# Plan\n\nDo it.");
        planner.submitPlan("task-plan", sourcePlan.toString(), "plan:one", "tool-1").toCompletableFuture().join();
        TeamTool approvePlan = TeamTools.createTeamTools(
                        "leader",
                        backend,
                        MemberMode.PLAN_MODE.value(),
                        "temporary",
                        null,
                        null,
                        null,
                        "en")
                .stream()
                .filter(tool -> "approve_plan".equals(tool.card().name()))
                .findFirst()
                .orElseThrow();

        ToolOutput approved = approvePlan.invoke(Map.of(
                "plan_id", "plan_one",
                "approved", true,
                "feedback", "ok"
        )).toCompletableFuture().join();

        assertThat(approved.isSuccess()).isTrue();
        assertThat(database.getTask("task-plan").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.PLAN_APPROVED.value());
        assertThat(approved.toString()).contains("Plan approved: plan_id=plan_one");
    }

    @Test
    void updateTaskRefusesLeaderCancelOrReassignForHumanClaimedTaskAndSelfCompleteAllowsHuman() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(database, messager, MemberMode.BUILD_MODE, List.of(), true, true, List.of());
        backend.buildTeam("Team", "desc", "Leader", "lead").toCompletableFuture().join();
        SpawnMemberTool spawn = new SpawnMemberTool(backend, TeamToolLocales.makeTranslator("en"), null);
        spawn.invoke(Map.of(
                "member_name", "human",
                "display_name", "Human",
                "desc", "human persona",
                "role_type", "human_agent"
        )).toCompletableFuture().join();
        spawn.invoke(Map.of(
                "member_name", "worker",
                "display_name", "Worker",
                "desc", "worker persona"
        )).toCompletableFuture().join();
        backend.getTaskManager().add("Human task", "do it", "task-human", null).toCompletableFuture().join();
        backend.getTaskManager().assign("task-human", "human").toCompletableFuture().join();
        UpdateTaskTool update = new UpdateTaskTool(backend, TeamToolLocales.makeTranslator("en"));

        ToolOutput cancel = update.invoke(Map.of(
                "task_id", "task-human",
                "status", "cancelled"
        )).toCompletableFuture().join();
        ToolOutput reassign = update.invoke(Map.of(
                "task_id", "task-human",
                "assignee", "worker"
        )).toCompletableFuture().join();
        MemberCompleteTaskTool complete = new MemberCompleteTaskTool(
                backend.getTaskManager(),
                "human",
                TeamToolLocales.makeTranslator("en")
        );
        ToolOutput completed = complete.invoke(Map.of(
                "task_id", "task-human",
                "note", "done"
        )).toCompletableFuture().join();

        assertThat(cancel.isSuccess()).isFalse();
        assertThat(cancel.getError()).contains("claimed by a human member");
        assertThat(reassign.isSuccess()).isFalse();
        assertThat(reassign.getError()).contains("cannot be reassigned");
        assertThat(completed.isSuccess()).isTrue();
        assertThat(completed.toString()).contains("Task #task-human completed").contains("note: done");
        assertThat(database.getTask("task-human").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.COMPLETED.value());
    }

    @Test
    void sendMessageMulticastReportsPartialFailuresAndRejectsBroadcastEquivalentTargets() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(
                database,
                messager,
                MemberMode.BUILD_MODE,
                List.of(teammate("worker"), teammate("carol")),
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

        ToolOutput partial = tool.invoke(Map.of(
                "to", List.of("worker", "missing", "worker", ""),
                "content", "hello",
                "summary", "greeting"
        )).toCompletableFuture().join();
        ToolOutput broadcastEquivalent = tool.invoke(Map.of(
                "to", List.of("worker", "carol"),
                "content", "hello all"
        )).toCompletableFuture().join();

        assertThat(partial.isSuccess()).isFalse();
        assertThat(partial.getError()).contains("Multicast partially failed");
        assertThat(partial.toString()).contains("delivered: worker").contains("missing - Member 'missing' not found");
        assertThat(database.getMessages("team-a", "worker", false, null).join())
                .extracting(TeamMessage::getContent)
                .containsExactly("hello");
        assertThat(broadcastEquivalent.isSuccess()).isFalse();
        assertThat(broadcastEquivalent.getError()).contains("use to='*' to broadcast");
    }

    @Test
    void createTeamToolsAppliesRoleModeLifecycleAndExcludeFilters() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(database, messager, MemberMode.PLAN_MODE, List.of(), true, true, List.of());

        List<String> leaderNames = TeamTools.createTeamTools(
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
        List<String> humanNames = TeamTools.createTeamTools(
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

        assertThat(leaderNames).contains("approve_plan", "approve_tool", "build_team", "update_task");
        assertThat(leaderNames).doesNotContain("clean_team", "send_message", "submit_plan");
        assertThat(humanNames).containsExactly("view_task", "member_complete_task", "send_message");
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

    /**
     * Recording messager collaborator for team-tools tests.
     *
     * <p>Mirrors Python's messager dependency used by
     * {@code openjiuwen/agent_teams/tools/team_tools.py}.</p>
     */
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
