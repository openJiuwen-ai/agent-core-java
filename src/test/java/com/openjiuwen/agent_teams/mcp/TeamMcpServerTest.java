/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamI18n;
import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.external.InboxView;
import com.openjiuwen.agent_teams.external.TeamJoinDescriptor;
import com.openjiuwen.agent_teams.schema.TaskDetail;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.tools.TeamMember;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the team-member MCP server surface.
 *
 * <p>Mirrors Python's MCP server tests in
 * {@code tests/unit_tests/agent_teams/external/test_mcp_server.py}.</p>
 */
class TeamMcpServerTest {

    private static final Set<String> EXPECTED_TOOLS = Set.of(
            "read_inbox",
            "send_message",
            "list_tasks",
            "claimable_tasks",
            "get_task",
            "claim_task",
            "complete_task",
            "update_task",
            "list_members"
    );

    @AfterEach
    void tearDown() {
        AgentTeamsContext.resetSessionId(new AgentTeamsContext.SessionIdToken(null, false));
        AgentTeamI18n.setLanguage(AgentTeamI18n.DEFAULT_LANGUAGE);
    }

    @Test
    void teammateRegistersExpectedTools() {
        TeamMcpServer server = TeamMcpServer.buildServer(factory(new FakeClient(false)), "teammate");

        assertThat(toolNames(server)).containsAll(EXPECTED_TOOLS);
    }

    @Test
    void leaderGetsFullToolset() {
        TeamMcpServer server = TeamMcpServer.buildServer(factory(new FakeClient(true)), "leader");

        assertThat(toolNames(server)).containsAll(EXPECTED_TOOLS);
    }

    @Test
    void unknownRoleLosesTaskOwnershipTools() {
        TeamMcpServer server = TeamMcpServer.buildServer(factory(new FakeClient(false)), "reviewer");
        Set<String> names = toolNames(server);

        assertThat(names).containsAll(TeamMcpServer.readAndMessageTools());
        assertThat(names).doesNotContainAnyElementsOf(TeamMcpServer.taskLifecycleTools());
    }

    @Test
    void roleResolvedFromEnvDescriptor() {
        TeamMcpServer server = TeamMcpServer.buildServerFromEnv(
                factory(new FakeClient(true)),
                descriptor("leader", "leader").toEnv()
        );

        assertThat(toolNames(server)).containsAll(EXPECTED_TOOLS);
    }

    @Test
    void sendMessageToolDelivers() {
        FakeClient client = new FakeClient(false);
        TeamMcpServer server = TeamMcpServer.buildServer(factory(client), "teammate");

        Map<String, Object> result = mapResult(server.callTool(
                TeamMcpServer.TOOL_SEND_MESSAGE,
                Map.of("to", "leader", "content", "hi via mcp")
        ));

        assertThat(result).containsEntry("ok", true);
        assertThat(result).containsEntry("message_id", "msg-1");
        assertThat(client.sentMessages).containsExactly(new SentMessage("leader", "hi via mcp"));
    }

    @Test
    void claimTaskToolEffect() {
        FakeClient client = new FakeClient(false);
        TeamTask task = new TeamTask("t1", "team-a", "Do X", "details", "pending", null, 10L);
        client.tasks.put("t1", task);
        TeamMcpServer server = TeamMcpServer.buildServer(factory(client), "teammate");

        Map<String, Object> result = mapResult(server.callTool(
                TeamMcpServer.TOOL_CLAIM_TASK,
                Map.of("task_id", "t1")
        ));

        assertThat(result).containsEntry("ok", true);
        assertThat(task.getAssignee()).isEqualTo("dev-1");
        assertThat(task.getStatus()).isEqualTo("claimed");
    }

    @Test
    void readInboxReturnsMessagesAndTaskBoard() {
        FakeClient client = new FakeClient(false);
        client.messages.add(new TeamMessage(
                "m1",
                "team-a",
                "leader",
                "dev-1",
                "hello",
                1_700_000_000_000L,
                false,
                false
        ));
        client.tasks.put("t1", new TeamTask("t1", "team-a", "Do X", "details", "pending", null, 10L));
        TeamMcpServer server = TeamMcpServer.buildServer(factory(client), "teammate");

        Map<String, Object> result = mapResult(server.callTool(TeamMcpServer.TOOL_READ_INBOX, Map.of()));

        assertThat(result).containsKey("messages");
        assertThat(result.get("task_board")).asString().contains("t1");
        assertThat(AgentTeamsContext.getSessionId()).isEqualTo("session-1");
    }

    @Test
    void clientFactoryConnectsLazilyOnce() {
        FakeClient client = new FakeClient(false);
        AtomicInteger connects = new AtomicInteger();
        TeamMcpServer server = TeamMcpServer.buildServer(() -> {
            connects.incrementAndGet();
            return CompletableFuture.completedFuture(client);
        }, "teammate");

        assertThat(connects).hasValue(0);

        server.callTool(TeamMcpServer.TOOL_LIST_MEMBERS, Map.of()).toCompletableFuture().join();
        server.callTool(TeamMcpServer.TOOL_LIST_MEMBERS, Map.of()).toCompletableFuture().join();

        assertThat(connects).hasValue(1);
    }

    private static Set<String> toolNames(TeamMcpServer server) {
        return server.listTools().stream().map(TeamMcpServer.TeamMcpTool::name).collect(Collectors.toSet());
    }

    private static TeamMcpServer.ClientFactory factory(FakeClient client) {
        return () -> CompletableFuture.completedFuture(client);
    }

    private static TeamJoinDescriptor descriptor(String memberName, String role) {
        return new TeamJoinDescriptor(
                "session-1",
                "team-a",
                memberName,
                role,
                "en",
                Map.of("db_type", "memory"),
                null
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapResult(CompletionStage<Object> stage) {
        return (Map<String, Object>) stage.toCompletableFuture().join();
    }

    /**
     * Mirrors Python's fake connected {@code ExternalTeamClient} behavior in
     * {@code openjiuwen/agent_teams/mcp/server.py} tests.
     */
    private static final class FakeClient implements TeamMcpServer.TeamClientFacade {
        private final boolean leader;
        private final List<TeamMessage> messages = new ArrayList<>();
        private final LinkedHashMap<String, TeamTask> tasks = new LinkedHashMap<>();
        private final List<SentMessage> sentMessages = new ArrayList<>();

        private FakeClient(boolean leader) {
            this.leader = leader;
        }

        @Override
        public String sessionId() {
            return "session-1";
        }

        @Override
        public String language() {
            return "en";
        }

        @Override
        public boolean isLeader() {
            return leader;
        }

        @Override
        public CompletionStage<InboxView> fetchInbox(boolean markRead) {
            return CompletableFuture.completedFuture(new InboxView(new ArrayList<>(messages), new ArrayList<>(tasks.values())));
        }

        @Override
        public CompletionStage<String> sendMessage(String to, String content) {
            sentMessages.add(new SentMessage(to, content));
            return CompletableFuture.completedFuture("msg-" + sentMessages.size());
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
            List<TeamTask> result = tasks.values().stream()
                    .filter(task -> "pending".equals(task.getStatus()))
                    .toList();
            return CompletableFuture.completedFuture(result);
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
            task.setAssignee("dev-1");
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
            return CompletableFuture.completedFuture(List.of(
                    new TeamMember("leader", "team-a", "Leader", null, null, "active",
                            null, null, "leader", null, null, 10L),
                    new TeamMember("dev-1", "team-a", "Dev", null, null, "active",
                            null, null, "teammate", null, null, 10L)
            ));
        }
    }

    /**
     * Captured fake send-message call.
     *
     * <p>Mirrors Python's {@code send_message} tool side effect in
     * {@code openjiuwen/agent_teams/mcp/server.py}.</p>
     */
    private record SentMessage(String to, String content) {
    }
}
