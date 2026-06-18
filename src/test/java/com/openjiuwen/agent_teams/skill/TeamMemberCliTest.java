/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.external.InboxObserver;
import com.openjiuwen.agent_teams.external.InboxView;
import com.openjiuwen.agent_teams.external.TeamJoinDescriptor;
import com.openjiuwen.agent_teams.schema.TaskDetail;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.tools.TeamMember;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamTask;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;

/**
 * Tests for the non-interactive external-member CLI.
 *
 * <p>Mirrors Python's {@code test_cli.py} for
 * {@code openjiuwen/agent_teams/skill/cli.py}.</p>
 */
class TeamMemberCliTest {

    @Test
    void cliMembers() {
        FakeClient client = new FakeClient();

        CliResult result = run(client, descriptor("dev-1", "teammate"), "members");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("leader").contains("dev-1");
    }

    @Test
    void cliSendThenInbox() {
        FakeClient client = new FakeClient();

        CliResult send = run(client, descriptor("dev-1", "teammate"), "send", "leader", "hi from cli");
        CliResult inbox = run(client, descriptor("leader", "leader"), "inbox");

        assertThat(send.exitCode()).isZero();
        assertThat(inbox.exitCode()).isZero();
        assertThat(inbox.stdout()).contains("hi from cli");
    }

    @Test
    void cliTaskList() {
        FakeClient client = new FakeClient();
        client.tasks.put("t1", new TeamTask("t1", "ext_team", "Build it", "details", "pending", null, 10L));

        CliResult result = run(client, descriptor("dev-1", "teammate"), "task", "list");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("t1").contains("Build it");
    }

    @Test
    void cliClaim() {
        FakeClient client = new FakeClient();
        client.tasks.put("t1", new TeamTask("t1", "ext_team", "Build it", "details", "pending", null, 10L));

        CliResult result = run(client, descriptor("dev-1", "teammate"), "claim", "t1");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("claimed t1");
        assertThat(client.tasks.get("t1").getStatus()).isEqualTo("claimed");
        assertThat(client.tasks.get("t1").getAssignee()).isEqualTo("dev-1");
    }

    @Test
    void cliInboxEmpty() {
        FakeClient client = new FakeClient();

        CliResult result = run(client, descriptor("dev-1", "teammate"), "inbox");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("(inbox empty)");
    }

    private static CliResult run(FakeClient client, TeamJoinDescriptor descriptor, String... command) {
        List<String> argv = new ArrayList<>();
        argv.add("--descriptor-json");
        argv.add(descriptor.toJson());
        argv.addAll(List.of(command));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);
        int exitCode = TeamMemberCli.execute(
                argv.toArray(String[]::new),
                parsed -> {
                    client.descriptor = parsed;
                    return CompletableFuture.completedFuture(client);
                },
                out,
                err
        );
        return new CliResult(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private static TeamJoinDescriptor descriptor(String member, String role) {
        return new TeamJoinDescriptor(
                "session-1",
                "ext_team",
                member,
                role,
                "en",
                Map.of("db_type", "memory"),
                null
        );
    }

    /**
     * Captured CLI process result.
     *
     * <p>Mirrors Python's {@code capsys} assertions for
     * {@code openjiuwen/agent_teams/skill/cli.py}.</p>
     */
    private record CliResult(int exitCode, String stdout, String stderr) {
    }

    /**
     * In-memory external client used by CLI tests.
     *
     * <p>Mirrors Python's fixture-backed {@code ExternalTeamClient} behavior in
     * {@code openjiuwen/agent_teams/skill/cli.py} tests.</p>
     */
    private static final class FakeClient implements TeamMemberCli.TeamMemberClient {
        private final List<TeamMessage> messages = new ArrayList<>();
        private final LinkedHashMap<String, TeamTask> tasks = new LinkedHashMap<>();
        private final List<TeamMember> members = List.of(
                new TeamMember("leader", "ext_team", "Leader", null, null, "active",
                        null, null, "leader", null, null, 10L),
                new TeamMember("dev-1", "ext_team", "Developer", null, null, "active",
                        null, null, "teammate", null, null, 10L)
        );
        private TeamJoinDescriptor descriptor;

        @Override
        public CompletionStage<Void> connect() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> close() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isLeader() {
            return "leader".equals(descriptor.getRole());
        }

        @Override
        public CompletionStage<InboxView> fetchInbox() {
            List<TeamMessage> memberMessages = messages.stream()
                    .filter(message -> Boolean.TRUE.equals(message.getBroadcast())
                            || descriptor.getMemberName().equals(message.getToMemberName()))
                    .toList();
            return CompletableFuture.completedFuture(new InboxView(memberMessages, new ArrayList<>(tasks.values())));
        }

        @Override
        public CompletionStage<Void> watch(InboxObserver observer) {
            CompletableFuture<Void> never = new CompletableFuture<>();
            return never;
        }

        @Override
        public CompletionStage<String> sendMessage(String to, String content) {
            String messageId = "msg-" + (messages.size() + 1);
            boolean broadcast = TeamMemberCli.BROADCAST_TARGET.equals(to);
            messages.add(new TeamMessage(
                    messageId,
                    descriptor.getTeamName(),
                    descriptor.getMemberName(),
                    broadcast ? "" : to,
                    content,
                    10L,
                    broadcast,
                    false
            ));
            return CompletableFuture.completedFuture(messageId);
        }

        @Override
        public CompletionStage<List<TeamTask>> listTasks(String status) {
            List<TeamTask> result = tasks.values().stream()
                    .filter(task -> status == null || status.equals(task.getStatus()))
                    .toList();
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletionStage<List<TeamTask>> claimableTasks() {
            return CompletableFuture.completedFuture(tasks.values().stream()
                    .filter(task -> "pending".equals(task.getStatus()))
                    .toList());
        }

        @Override
        public CompletionStage<TaskDetail> getTask(String taskId) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                return CompletableFuture.completedFuture(null);
            }
            TaskDetail detail = new TaskDetail();
            detail.setTaskId(task.getTaskId());
            detail.setTitle(task.getTitle());
            detail.setStatus(task.getStatus());
            detail.setAssignee(task.getAssignee());
            detail.setUpdatedAt(task.getUpdatedAt());
            detail.setContent(task.getContent());
            return CompletableFuture.completedFuture(detail);
        }

        @Override
        public CompletionStage<TaskOpResult> claimTask(String taskId) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                return CompletableFuture.completedFuture(TaskOpResult.fail("missing task"));
            }
            task.setAssignee(descriptor.getMemberName());
            task.setStatus("claimed");
            return CompletableFuture.completedFuture(TaskOpResult.success());
        }

        @Override
        public CompletionStage<TaskOpResult> completeTask(String taskId) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                return CompletableFuture.completedFuture(TaskOpResult.fail("missing task"));
            }
            task.setStatus("completed");
            return CompletableFuture.completedFuture(TaskOpResult.success());
        }

        @Override
        public CompletionStage<TaskOpResult> updateTask(String taskId, String title, String content) {
            TeamTask task = tasks.get(taskId);
            if (task == null) {
                return CompletableFuture.completedFuture(TaskOpResult.fail("missing task"));
            }
            task.setTitle(title);
            task.setContent(content);
            return CompletableFuture.completedFuture(TaskOpResult.success());
        }

        @Override
        public CompletionStage<List<TeamMember>> listMembers() {
            return CompletableFuture.completedFuture(members);
        }
    }
}
