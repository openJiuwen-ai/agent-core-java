package com.openjiuwen.agentteams.tools;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.status.ExecutionStatus;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.teamworkspace.TeamWorkspaceConfig;
import com.openjiuwen.agentteams.teamworkspace.TeamWorkspaceManager;
import com.openjiuwen.agentteams.worktree.WorktreeConfig;
import com.openjiuwen.agentteams.worktree.WorktreeManager;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.agentteams.tools.database.DatabaseConfig;
import com.openjiuwen.agentteams.tools.database.DatabaseType;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class TeamToolsCompatibilityTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        InProcessMessager.cleanupInprocessBus();
    }

    @Test
    void messageManagerShouldSendBroadcastAndMarkRead() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-a", "Team A", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName("leader").teamName("team-a").displayName("Leader")
                .agentCard("{}").status("ready").executionStatus("idle")
                .mode("build_mode").role("leader").build());
        db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName("member2").teamName("team-a").displayName("Member2")
                .agentCard("{}").status("ready").executionStatus("idle")
                .mode("build_mode").role("teammate").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamMessageManager manager = new TeamMessageManager("team-a", "leader", db, messager);
        TeamMessageManager member2Manager = new TeamMessageManager("team-a", "member2", db, messager);

        String directId = manager.sendMessage("hello", "member2").join();
        String broadcastId = manager.broadcastMessage("notice").join();

        assertThat(manager.getMessages("member2", false)).hasSize(1);
        assertThat(member2Manager.getBroadcastMessages(false)).hasSize(1);
        assertThat(manager.markMessageRead(directId)).isTrue();
        assertThat(manager.getMessages("member2", true)).isEmpty();
        assertThat(broadcastId).isNotBlank();
    }

    @Test
    void broadcastReadStatusShouldBeTrackedPerMember() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-a", "Team A", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName("leader").teamName("team-a").displayName("Leader")
                .agentCard("{}").status("ready").executionStatus("idle")
                .mode("build_mode").role("leader").build());
        db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName("worker-1").teamName("team-a").displayName("Worker1")
                .agentCard("{}").status("ready").executionStatus("idle")
                .mode("build_mode").role("teammate").build());
        db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName("worker-2").teamName("team-a").displayName("Worker2")
                .agentCard("{}").status("ready").executionStatus("idle")
                .mode("build_mode").role("teammate").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamMessageManager worker1Messages = new TeamMessageManager("team-a", "worker-1", db, messager);
        TeamMessageManager worker2Messages = new TeamMessageManager("team-a", "worker-2", db, messager);

        String broadcastId = worker1Messages.broadcastMessage("notice", "leader").join();

        assertThat(worker1Messages.getBroadcastMessages(true)).singleElement()
                .extracting(TeamMessage::getMessageId)
                .isEqualTo(broadcastId);
        assertThat(worker2Messages.getBroadcastMessages(true)).singleElement()
                .extracting(TeamMessage::getMessageId)
                .isEqualTo(broadcastId);

        assertThat(worker1Messages.markMessageRead(broadcastId)).isTrue();
        assertThat(worker1Messages.getBroadcastMessages(true)).isEmpty();
        assertThat(worker2Messages.getBroadcastMessages(true)).singleElement()
                .extracting(TeamMessage::getMessageId)
                .isEqualTo(broadcastId);
        assertThat(worker1Messages.markMessageRead(broadcastId, TeamConstants.USER_PSEUDO_MEMBER_NAME)).isFalse();
    }

    @Test
    void humanAgentMessagesShouldStartUnreadPendingHandlerDispatch() {
        // Mirrors Python 0.1.15: TeamMessageManager no longer pre-marks
        // human-agent messages as read. The handler layer
        // (MessageHandler.notifyHumanAgentInbound) is responsible for
        // surfacing + marking them. So at the manager level every message
        // starts unread, human agent or not.
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-hitt", "leader", true, messager);
        backend.spawnMember("human_designer", "Human Designer", AgentCard.builder().name("designer").description("desc").build(), com.openjiuwen.agentteams.schema.team.TeamRole.HUMAN_AGENT).join();
        backend.spawnMember("human_pm", "Human PM", AgentCard.builder().name("pm").description("desc").build(), com.openjiuwen.agentteams.schema.team.TeamRole.HUMAN_AGENT).join();
        TeamMessageManager manager = backend.getMessageManager();

        String directId = manager.sendMessage("please decide", "human_designer").join();
        String broadcastId = manager.broadcastMessage("notice for humans").join();

        // Direct messages to human agents start unread (handler marks them).
        assertThat(manager.getMessages("human_designer", false)).singleElement()
                .extracting(TeamMessage::getMessageId, TeamMessage::isRead)
                .containsExactly(directId, false);
        assertThat(manager.getMessages("human_designer", true)).singleElement()
                .extracting(TeamMessage::getMessageId)
                .isEqualTo(directId);
        // Broadcasts are unread for every member until they mark them read.
        assertThat(new TeamMessageManager("team-hitt", "human_designer", backend.getDb(), messager).getBroadcastMessages(true)).singleElement()
                .extracting(TeamMessage::getMessageId)
                .isEqualTo(broadcastId);
        assertThat(new TeamMessageManager("team-hitt", "human_pm", backend.getDb(), messager).getBroadcastMessages(true)).singleElement()
                .extracting(TeamMessage::getMessageId)
                .isEqualTo(broadcastId);
        assertThat(new TeamMessageManager("team-hitt", "worker", backend.getDb(), messager).getBroadcastMessages(true))
                .singleElement()
                .extracting(TeamMessage::getMessageId)
                .isEqualTo(broadcastId);
    }

    @Test
    void backendShouldValidateMemberStatusTransitionsLikePythonTeamMember() {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-member-status", "leader", true, messager);
        backend.spawnMember("worker-1", "Worker One", AgentCard.builder().name("worker").description("desc").build()).join();

        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.READY)).isTrue();
        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.BUSY)).isTrue();
        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.SHUTDOWN)).isFalse();
        assertThat(backend.getMember("worker-1").getStatus()).isEqualTo(MemberStatus.BUSY);
        assertThat(backend.getDb().member.getMember("worker-1", "team-member-status").getStatus()).isEqualTo("busy");

        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.READY)).isTrue();
        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.SHUTDOWN_REQUESTED)).isTrue();
        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.SHUTDOWN)).isTrue();
        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.READY)).isFalse();
        assertThat(backend.getMember("worker-1").getStatus()).isEqualTo(MemberStatus.SHUTDOWN);
    }

    @Test
    void backendShouldValidateExecutionStatusTransitionsLikePythonTeamMember() {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-execution-status", "leader", true, messager);
        backend.spawnMember("worker-1", "Worker One", AgentCard.builder().name("worker").description("desc").build()).join();

        assertThat(backend.updateMemberExecutionStatus("worker-1", ExecutionStatus.RUNNING.value())).isFalse();
        assertThat(backend.getDb().member.getMember("worker-1", "team-execution-status").getExecutionStatus())
                .isNullOrEmpty();

        assertThat(backend.updateMemberExecutionStatus("worker-1", ExecutionStatus.STARTING.value())).isTrue();
        assertThat(backend.updateMemberExecutionStatus("worker-1", ExecutionStatus.RUNNING.value())).isTrue();
        assertThat(backend.updateMemberExecutionStatus("worker-1", ExecutionStatus.COMPLETING.value())).isTrue();
        assertThat(backend.updateMemberExecutionStatus("worker-1", ExecutionStatus.COMPLETED.value())).isTrue();
        assertThat(backend.updateMemberExecutionStatus("worker-1", ExecutionStatus.IDLE.value())).isTrue();
        assertThat(backend.getDb().member.getMember("worker-1", "team-execution-status").getExecutionStatus())
                .isEqualTo("idle");
    }

    @Test
    void backendForceStatusUpdateShouldPreserveRecoveryStateMachineBypass() {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-recovery-force", "leader", true, messager);
        backend.spawnMember("worker-1", "Worker One", AgentCard.builder().name("worker").description("desc").build()).join();

        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.READY)).isTrue();
        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.RESTARTING)).isFalse();
        assertThat(backend.forceUpdateMemberStatus("worker-1", MemberStatus.RESTARTING)).isTrue();

        assertThat(backend.getMember("worker-1").getStatus()).isEqualTo(MemberStatus.RESTARTING);
        assertThat(backend.getDb().member.getMember("worker-1", "team-recovery-force").getStatus())
                .isEqualTo("restarting");
    }

    @Test
    void taskManagerShouldBlockAndUnblockDependentTasks() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-a", "Team A", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("leader").teamName("team-a").displayName("Leader").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("leader").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamTaskManager taskManager = new TeamTaskManager("team-a", "leader", db, messager);

        TeamTask task1 = taskManager.add("Task 1", "Content 1").join();
        TeamTask task2 = taskManager.add("Task 2", "Content 2", null, List.of(task1.getTaskId())).join();

        assertThat(task2.getStatus()).isEqualTo("blocked");
        assertThat(taskManager.claim(task1.getTaskId()).join()).isTrue();
        assertThat(taskManager.complete(task1.getTaskId()).join()).isTrue();
        assertThat(taskManager.get(task2.getTaskId()).orElseThrow().getStatus()).isEqualTo("pending");
    }

    @Test
    void taskManagerShouldCancelResetAndCancelAllActiveTasks() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-a", "Team A", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("leader").teamName("team-a").displayName("Leader").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("leader").build());
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("member1").teamName("team-a").displayName("Member1").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("teammate").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("member1").build());
        TeamTaskManager taskManager = new TeamTaskManager("team-a", "member1", db, messager);

        TeamTask claimed = taskManager.add("Claimed", "Content").join();
        assertThat(taskManager.claim(claimed.getTaskId()).join()).isTrue();
        assertThat(taskManager.reset(claimed.getTaskId()).join()).isTrue();
        assertThat(taskManager.get(claimed.getTaskId()).orElseThrow())
                .extracting(TeamTask::getStatus, TeamTask::getAssignee)
                .containsExactly("pending", null);

        TeamTask cancelled = taskManager.cancel(claimed.getTaskId()).join();
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.getStatus()).isEqualTo("cancelled");
        assertThat(taskManager.reset(claimed.getTaskId()).join()).isFalse();

        TeamTask active = taskManager.add("Active", "Cancel all").join();
        TeamTask done = taskManager.add("Done", "Skip completed").join();
        assertThat(taskManager.claim(done.getTaskId()).join()).isTrue();
        assertThat(taskManager.complete(done.getTaskId()).join()).isTrue();

        List<TeamTask> cancelledAll = taskManager.cancelAllTasks().join();
        assertThat(cancelledAll).extracting(TeamTask::getTaskId).containsExactly(active.getTaskId());
        assertThat(taskManager.get(active.getTaskId()).orElseThrow().getStatus()).isEqualTo("cancelled");
        assertThat(taskManager.get(done.getTaskId()).orElseThrow().getStatus()).isEqualTo("completed");
    }

    @Test
    void taskManagerAssignShouldValidateMemberAndClaimTask() {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-a", "leader", true, messager);
        backend.spawnMember("worker-1", "Worker One", AgentCard.builder().name("worker").description("desc").build()).join();
        TeamTaskManager taskManager = backend.getTaskManager();
        TeamTask task = taskManager.add("Assign", "Bind to worker", "assign-1", List.of()).join();

        assertThat(taskManager.assign(task.getTaskId(), "ghost").join()).isFalse();
        TaskOpResult ghostResult = taskManager.assignResult(task.getTaskId(), "ghost").join();
        assertThat(ghostResult.isOk()).isFalse();
        assertThat(ghostResult.getReason()).contains("ghost", "not found");
        assertThat(taskManager.get(task.getTaskId()).orElseThrow().getStatus()).isEqualTo("pending");

        assertThat(taskManager.assign(task.getTaskId(), "worker-1").join()).isTrue();
        assertThat(taskManager.get(task.getTaskId()).orElseThrow())
                .extracting(TeamTask::getStatus, TeamTask::getAssignee)
                .containsExactly("claimed", "worker-1");
        assertThat(taskManager.assign(task.getTaskId(), "leader").join()).isFalse();
        TaskOpResult reassigned = taskManager.assignResult(task.getTaskId(), "leader").join();
        assertThat(reassigned.isOk()).isFalse();
        assertThat(reassigned.getReason()).contains("already claimed by worker-1");
        assertThat(taskManager.assign(task.getTaskId(), "worker-1").join()).isTrue();
    }

    @Test
    void taskManagerShouldAddDependenciesRejectCyclesAndRefreshStatus() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-a", "Team A", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("leader").teamName("team-a").displayName("Leader").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("leader").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamTaskManager taskManager = new TeamTaskManager("team-a", "leader", db, messager);

        TeamTask upstream = taskManager.add("Up", "Dependency", "dep-up", List.of()).join();
        TeamTask downstream = taskManager.add("Down", "Dependent", "dep-down", List.of()).join();

        assertThat(taskManager.addDependencies(downstream.getTaskId(), List.of(upstream.getTaskId())).join()).isTrue();
        assertThat(taskManager.get(downstream.getTaskId()).orElseThrow().getStatus()).isEqualTo("blocked");
        assertThat(taskManager.getDependencies(downstream.getTaskId())).containsExactly(upstream.getTaskId());

        assertThat(taskManager.addDependencies(upstream.getTaskId(), List.of(downstream.getTaskId())).join()).isFalse();
        TaskOpResult cycle = taskManager.addDependenciesResult(upstream.getTaskId(), List.of(downstream.getTaskId())).join();
        assertThat(cycle.isOk()).isFalse();
        assertThat(cycle.getReason()).contains("Circular dependency");
        assertThat(taskManager.getDependencies(upstream.getTaskId())).isEmpty();
    }

    @Test
    void taskManagerShouldUpdateOnlyPendingOrBlockedTasks() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-a", "Team A", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("leader").teamName("team-a").displayName("Leader").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("leader").build());
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("member1").teamName("team-a").displayName("Member1").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("teammate").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("member1").build());
        TeamTaskManager taskManager = new TeamTaskManager("team-a", "member1", db, messager);

        TeamTask pending = taskManager.add("Original", "Content", "update-1", List.of()).join();
        assertThat(taskManager.updateTask(pending.getTaskId(), "Updated", null).join()).isTrue();
        assertThat(taskManager.get(pending.getTaskId()).orElseThrow())
                .extracting(TeamTask::getTitle, TeamTask::getContent)
                .containsExactly("Updated", "Content");

        TeamTask dependency = taskManager.add("Dependency", "Content", "update-dep", List.of()).join();
        TeamTask blocked = taskManager.add("Blocked", "Old", "update-blocked", List.of(dependency.getTaskId())).join();
        assertThat(blocked.getStatus()).isEqualTo("blocked");
        assertThat(taskManager.updateTask(blocked.getTaskId(), null, "New blocked content").join()).isTrue();
        assertThat(taskManager.get(blocked.getTaskId()).orElseThrow().getContent()).isEqualTo("New blocked content");

        assertThat(taskManager.claim(pending.getTaskId()).join()).isTrue();
        assertThat(taskManager.updateTask(pending.getTaskId(), "Rejected", "Rejected").join()).isFalse();
        TaskOpResult updateRejected = taskManager.updateTaskResult(pending.getTaskId(), "Rejected", "Rejected").join();
        assertThat(updateRejected.isOk()).isFalse();
        assertThat(updateRejected.getReason()).contains("claimed");
        assertThat(taskManager.get(pending.getTaskId()).orElseThrow())
                .extracting(TeamTask::getTitle, TeamTask::getContent)
                .containsExactly("Updated", "Content");
    }

    @Test
    void taskManagerShouldAddBatchAndSkipInvalidSpecs() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-a", "Team A", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("leader").teamName("team-a").displayName("Leader").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("leader").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamTaskManager taskManager = new TeamTaskManager("team-a", "leader", db, messager);
        TeamTask dep = taskManager.add("Dependency", "Dep content", "batch-dep", List.of()).join();

        List<TeamTask> created = taskManager.addBatch(List.of(
                Map.of("title", "Task 1", "content", "Content 1", "task_id", "batch-1"),
                Map.of("title", "Missing content"),
                Map.of("content", "Missing title"),
                Map.of("title", "Task 2", "content", "Content 2", "task_id", "batch-2", "dependencies", List.of(dep.getTaskId()))
        )).join();

        assertThat(created).extracting(TeamTask::getTaskId).containsExactly("batch-1", "batch-2");
        assertThat(created).extracting(TeamTask::getStatus).containsExactly("pending", "blocked");
        assertThat(taskManager.getDependencies("batch-2")).containsExactly("batch-dep");
    }

    @Test
    void taskManagerShouldReturnOnlyPendingClaimableTasks() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-a", "Team A", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("leader").teamName("team-a").displayName("Leader").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("leader").build());
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("member1").teamName("team-a").displayName("Member1").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("teammate").build());
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("member2").teamName("team-a").displayName("Member2").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("teammate").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("member1").build());
        TeamTaskManager taskManager = new TeamTaskManager("team-a", "member1", db, messager);
        // Distinct member so 0.1.15 task-hogging guard (one claimed task
        // per member) does not reject the second claim.
        TeamTaskManager member2Manager = new TeamTaskManager("team-a", "member2", db, messager);

        TeamTask pending = taskManager.add("Pending", "Claimable", "claimable-pending", List.of()).join();
        TeamTask claimed = taskManager.add("Claimed", "Not claimable", "claimable-claimed", List.of()).join();
        TeamTask completed = taskManager.add("Completed", "Not claimable", "claimable-completed", List.of()).join();
        TeamTask cancelled = taskManager.add("Cancelled", "Not claimable", "claimable-cancelled", List.of()).join();
        TeamTask dependency = taskManager.add("Dependency", "Blocks one", "claimable-dep", List.of()).join();
        taskManager.add("Blocked", "Not claimable", "claimable-blocked", List.of(dependency.getTaskId())).join();

        assertThat(taskManager.claim(claimed.getTaskId()).join()).isTrue();
        TaskOpResult secondClaim = taskManager.claimResult(claimed.getTaskId()).join();
        assertThat(secondClaim.isOk()).isTrue();
        assertThat(member2Manager.claim(completed.getTaskId()).join()).isTrue();
        assertThat(member2Manager.complete(completed.getTaskId()).join()).isTrue();
        assertThat(taskManager.cancel(cancelled.getTaskId()).join()).isNotNull();

        assertThat(taskManager.getClaimableTasks())
                .extracting(TeamTask::getTaskId)
                .containsExactlyInAnyOrder(pending.getTaskId(), dependency.getTaskId());
    }

    @Test
    void taskManagerShouldAddTopPriorityTaskAndBlockPendingTasks() {
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-a", "Team A", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("leader").teamName("team-a").displayName("Leader").agentCard("{}").status("ready").executionStatus("idle").mode("build_mode").role("leader").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamTaskManager taskManager = new TeamTaskManager("team-a", "leader", db, messager);
        TeamTask task1 = taskManager.add("Task 1", "Content 1", "priority-1", List.of()).join();
        TeamTask task2 = taskManager.add("Task 2", "Content 2", "priority-2", List.of()).join();
        TeamTask task3 = taskManager.add("Task 3", "Content 3", "priority-3", List.of()).join();

        TeamTask topTask = taskManager.addAsTopPriority("Top Priority", "Urgent", "priority-top").join();

        assertThat(topTask).isNotNull();
        assertThat(topTask.getStatus()).isEqualTo("pending");
        assertThat(taskManager.get(task1.getTaskId()).orElseThrow().getStatus()).isEqualTo("blocked");
        assertThat(taskManager.get(task2.getTaskId()).orElseThrow().getStatus()).isEqualTo("blocked");
        assertThat(taskManager.get(task3.getTaskId()).orElseThrow().getStatus()).isEqualTo("blocked");
        assertThat(taskManager.getDependencies(task1.getTaskId())).containsExactly(topTask.getTaskId());
        assertThat(taskManager.getDependencies(task2.getTaskId())).containsExactly(topTask.getTaskId());
        assertThat(taskManager.getDependencies(task3.getTaskId())).containsExactly(topTask.getTaskId());
    }

    @Test
    void taskManagerShouldApproveClaimedPlanBeforeCompletion() throws Exception {
        java.nio.file.Path plansDir = java.nio.file.Files.createTempDirectory("plans-approve");
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-a", "Team A", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("member1").teamName("team-a").displayName("Member One").agentCard("{}").status("ready").executionStatus("idle").mode("plan_mode").role("teammate").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("member1").build());
        TeamTaskManager taskManager = new TeamTaskManager("team-a", "member1", db, messager, plansDir, null, "leader");
        TeamTask task = taskManager.add("Plan", "Draft plan", "plan-1", List.of()).join();

        // approve_plan on a task with no submitted plan fails.
        TaskOpResult approveRejected = taskManager.approvePlanResult("unknown-plan-id", true, "go", "leader").join();
        assertThat(approveRejected.isOk()).isFalse();
        assertThat(approveRejected.getReason()).contains("not found");

        java.nio.file.Path planFile = plansDir.resolve("submitted.md");
        java.nio.file.Files.writeString(planFile, "# Plan\nStep one.");
        Map<String, Object> submitResult = taskManager.submitPlan(task.getTaskId(), planFile.toString(), null, "").join();
        assertThat(submitResult.get("success")).isEqualTo(true);
        String planId = (String) submitResult.get("plan_id");
        assertThat(planId).isNotBlank();

        assertThat(taskManager.approvePlan(planId, true, "Looks good", "leader").join()).isTrue();
        assertThat(taskManager.get(task.getTaskId()).orElseThrow().getStatus()).isEqualTo("plan_approved");
        assertThat(taskManager.complete(task.getTaskId()).join()).isTrue();
        assertThat(taskManager.get(task.getTaskId()).orElseThrow().getStatus()).isEqualTo("completed");
    }

    @Test
    void planModeMemberShouldOnlyCompleteApprovedPlanTask() throws Exception {
        java.nio.file.Path plansDir = java.nio.file.Files.createTempDirectory("plans-planmode");
        TeamDatabase db = new TeamDatabase(DatabaseConfig.builder().dbType(DatabaseType.SQLITE).connectionString(":memory:").build());
        db.initialize();
        db.team.createTeam("team-plan", "Team Plan", "leader");
        db.member.createMember(TeamDatabase.MemberCreateParams.builder().memberName("planner").teamName("team-plan").displayName("Planner").agentCard("{}").status("busy").executionStatus("idle").mode("plan_mode").role("teammate").build());
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("planner").build());
        TeamTaskManager taskManager = new TeamTaskManager("team-plan", "planner", db, messager, plansDir, null, "leader");
        TeamTask task = taskManager.add("Plan gated", "Needs approval", "plan-gated", List.of()).join();

        assertThat(taskManager.claim(task.getTaskId()).join()).isTrue();
        assertThat(taskManager.complete(task.getTaskId()).join()).isFalse();
        TaskOpResult completeRejected = taskManager.completeResult(task.getTaskId()).join();
        assertThat(completeRejected.isOk()).isFalse();
        assertThat(completeRejected.getReason()).contains("PLAN_MODE", "plan_approved");
        assertThat(taskManager.get(task.getTaskId()).orElseThrow().getStatus()).isEqualTo("claimed");

        java.nio.file.Path planFile = plansDir.resolve("planner-plan.md");
        java.nio.file.Files.writeString(planFile, "# Plan");
        Map<String, Object> submitResult = taskManager.submitPlan(task.getTaskId(), planFile.toString(), null, "").join();
        String planId = (String) submitResult.get("plan_id");
        assertThat(planId).isNotBlank();
        assertThat(taskManager.approvePlan(planId, true, "ok", "leader").join()).isTrue();
        assertThat(taskManager.complete(task.getTaskId()).join()).isTrue();
        assertThat(taskManager.get(task.getTaskId()).orElseThrow().getStatus()).isEqualTo("completed");
    }

    @Test
    void teamBackendShouldSpawnMembersAndExposeManagers() {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-a", "leader", true, messager);

        backend.spawnMember("member1", "Member One", AgentCard.builder().name("agent").description("desc").build()).join();

        assertThat(backend.isLeader()).isTrue();
        assertThat(backend.listMembers())
                .extracting(TeamMember::getMemberName)
                .containsExactly("member1");
        assertThat(backend.getMessageManager()).isNotNull();
        assertThat(backend.getTaskManager()).isNotNull();
    }

    @Test
    void listMembersToolShouldMirrorBackendRosterWithoutSelf() {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-list", "leader", true, messager);
        backend.spawnMember("member1", "Member One", AgentCard.builder().name("agent").description("desc").build()).join();
        backend.spawnMember("member2", "Member Two", AgentCard.builder().name("agent").description("desc").build()).join();

        TeamTools.ListMembersTool tool = new TeamTools.ListMembersTool(backend);
        var output = tool.invoke(Map.of(), Map.of());

        assertThat(output.isSuccess()).isTrue();
        assertThat(String.valueOf(output)).contains("member_name=member1", "member_name=member2");
        assertThat(String.valueOf(output)).doesNotContain("member_name=leader");
    }

    @Test
    void teamBackendShouldShutdownCancelAndCleanTeam() {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-clean", "leader", true, messager);
        backend.spawnMember("worker-1", "Worker One", AgentCard.builder().name("worker1").description("desc").build()).join();
        backend.spawnMember("worker-2", "Worker Two", AgentCard.builder().name("worker2").description("desc").build()).join();

        assertThat(backend.shutdownMember("worker-1").join().isOk()).isFalse();
        backend.updateMemberStatus("worker-1", MemberStatus.READY);
        assertThat(backend.shutdownMember("worker-1", true).join().isOk()).isTrue();
        assertThat(backend.getDb().member.getMember("worker-1", "team-clean").getStatus())
                .isEqualTo(MemberStatus.SHUTDOWN_REQUESTED.value());
        assertThat(backend.shutdownMember("missing").join().isOk()).isFalse();
        assertThat(backend.cleanTeam().join()).isFalse();

        backend.updateMemberStatus("worker-2", MemberStatus.READY);
        backend.updateMemberStatus("worker-2", MemberStatus.BUSY);
        TeamTask task = backend.getTaskManager().add("Work", "Content", "cancel-worker-task", List.of()).join();
        assertThat(backend.getTaskManager().assign(task.getTaskId(), "worker-2").join()).isTrue();
        assertThat(backend.cancelMember("worker-2").join()).isTrue();
        assertThat(backend.getTaskManager().get(task.getTaskId()).orElseThrow())
                .extracting(TeamTask::getStatus, TeamTask::getAssignee)
                .containsExactly("pending", null);

        backend.updateMemberStatus("worker-1", MemberStatus.SHUTDOWN);
        backend.updateMemberStatus("worker-2", MemberStatus.READY);
        backend.updateMemberStatus("worker-2", MemberStatus.SHUTDOWN_REQUESTED);
        backend.updateMemberStatus("worker-2", MemberStatus.SHUTDOWN);
        assertThat(backend.cleanTeam().join()).isTrue();
        assertThat(backend.getDb().team.getTeam("team-clean")).isNull();
        assertThat(backend.getDb().member.getTeamMembers("team-clean")).isEmpty();
        assertThat(backend.getDb().getTeamTasks("team-clean")).isEmpty();
    }

    @Test
    void teamToolsFactoryShouldFilterByRoleAndPlanMode() {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-tools", "leader", true, messager);

        assertThat(TeamTools.createTeamTools("leader", backend).stream().map(tool -> tool.getCard().getName()))
                .contains("build_team", "clean_team", "spawn_member", "shutdown_member", "create_task",
                        "update_task", "list_members", "view_task", "send_message")
                .doesNotContain("approve_plan", "approve_tool", "claim_task", "workspace_meta");
        TeamWorkspaceManager workspace = new TeamWorkspaceManager(
                TeamWorkspaceConfig.builder().versionControl(false).build(),
                tempDir.resolve("workspace").toString(),
                "team-tools"
        );
        assertThat(TeamTools.createTeamTools("leader", backend, "build_mode", Set.of(), workspace).stream()
                .map(tool -> tool.getCard().getName()))
                .contains("workspace_meta");
        assertThat(TeamTools.createTeamTools("leader", backend, "plan_mode", Set.of()).stream()
                .map(tool -> tool.getCard().getName()))
                .contains("approve_plan", "approve_tool");
        assertThat(TeamTools.createTeamTools("teammate", backend).stream().map(tool -> tool.getCard().getName()))
                .containsExactlyInAnyOrder("claim_task", "view_task", "send_message");
        WorktreeManager worktree = new WorktreeManager(WorktreeConfig.builder()
                .baseDir(tempDir.resolve("worktrees").toString())
                .build());
        assertThat(TeamTools.createTeamTools(TeamTools.TeamToolsConfig.builder()
                .role("teammate").backend(backend).teammateMode("build_mode")
                .excludeTools(Set.of()).workspaceManager(workspace).worktreeManager(worktree).build()).stream()
                .map(tool -> tool.getCard().getName()))
                .contains("enter_worktree", "exit_worktree", "workspace_meta");
        assertThat(TeamTools.createTeamTools("human_agent", backend).stream().map(tool -> tool.getCard().getName()))
                .containsExactly("send_message");
        assertThat(TeamTools.createTeamTools("leader", backend, "plan_mode", Set.of("clean_team")).stream()
                .map(tool -> tool.getCard().getName()))
                .doesNotContain("clean_team");
    }

    @Test
    void worktreeToolsShouldEnterAndExitSessionWhenManagerProvided() throws Exception {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("worker").build());
        TeamBackend backend = new TeamBackend("team-worktree-tool", "worker", false, messager);
        Path repoRoot = createGitRepo("repo-worktree-tools");
        WorktreeManager worktree = new WorktreeManager(WorktreeConfig.builder()
                .baseDir(tempDir.resolve("worktrees").toString())
                .build());
        List<Tool> tools = TeamTools.createTeamTools(TeamTools.TeamToolsConfig.builder()
                .role("teammate").backend(backend).teammateMode("build_mode")
                .excludeTools(Set.of()).worktreeManager(worktree).build());
        Tool enter = findTool(tools, "enter_worktree");
        Tool exit = findTool(tools, "exit_worktree");

        ToolOutput entered = (ToolOutput) enter.invoke(
                Map.of("name", "feature123"),
                Map.of("repo_root", repoRoot.toString(), "member_name", "worker", "team_name", "team-worktree-tool")
        );
        assertThat(entered.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) entered.getData()).get("worktree_branch")).isEqualTo("worktree-feature123");
        assertThat(worktree.getCurrentSession()).isNotNull();

        ToolOutput duplicateEnter = (ToolOutput) enter.invoke(Map.of("name", "feature456"),
                Map.of("repo_root", repoRoot.toString()));
        assertThat(duplicateEnter.isSuccess()).isFalse();
        assertThat(duplicateEnter.getError()).contains("Already in worktree");

        ToolOutput exited = (ToolOutput) exit.invoke(Map.of("action", "keep"));
        assertThat(exited.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) exited.getData()).get("action")).isEqualTo("keep");
        assertThat(worktree.getCurrentSession()).isNull();

        ToolOutput noSession = (ToolOutput) exit.invoke(Map.of("action", "keep"));
        assertThat(noSession.isSuccess()).isFalse();
        assertThat(noSession.getError()).contains("No active worktree session");
    }

    @Test
    void workspaceMetaToolShouldManageLocksAndHistory() throws Exception {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-workspace-tool", "leader", true, messager);
        TeamWorkspaceManager workspace = new TeamWorkspaceManager(
                TeamWorkspaceConfig.builder().versionControl(false).build(),
                tempDir.resolve("shared").toString(),
                "team-workspace-tool"
        );
        workspace.initialize();
        Tool workspaceMeta = findTool(TeamTools.createTeamTools("leader", backend, "build_mode", Set.of(), workspace),
                "workspace_meta");

        ToolOutput locked = (ToolOutput) workspaceMeta.invoke(
                Map.of("action", "lock", "path", "artifacts/code/a.java"),
                Map.of("member_name", "leader", "display_name", "Leader")
        );
        assertThat(locked.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) locked.getData()).get("locked")).isEqualTo("artifacts/code/a.java");

        ToolOutput blocked = (ToolOutput) workspaceMeta.invoke(
                Map.of("action", "lock", "path", "artifacts/code/a.java"),
                Map.of("member_name", "worker", "display_name", "Worker")
        );
        assertThat(blocked.isSuccess()).isFalse();
        assertThat(blocked.getError()).contains("Leader");

        ToolOutput locks = (ToolOutput) workspaceMeta.invoke(Map.of("action", "locks"));
        assertThat(locks.isSuccess()).isTrue();
        assertThat((List<?>) ((Map<?, ?>) locks.getData()).get("locks")).hasSize(1);

        ToolOutput history = (ToolOutput) workspaceMeta.invoke(Map.of("action", "history", "path", "artifacts/code/a.java"));
        assertThat(history.isSuccess()).isTrue();
        assertThat((List<?>) ((Map<?, ?>) history.getData()).get("history")).isEmpty();

        ToolOutput unlocked = (ToolOutput) workspaceMeta.invoke(
                Map.of("action", "unlock", "path", "artifacts/code/a.java"),
                Map.of("member_name", "leader")
        );
        assertThat(unlocked.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) unlocked.getData()).get("released")).isEqualTo(true);
    }

    @Test
    void teamToolsShouldInvokeBackedOperations() throws Exception {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-tool-invoke", "leader", true, messager);
        Tool spawn = findTool(TeamTools.createTeamTools("leader", backend), "spawn_member");
        Tool create = findTool(TeamTools.createTeamTools("leader", backend), "create_task");
        Tool update = findTool(TeamTools.createTeamTools("leader", backend), "update_task");
        Tool view = findTool(TeamTools.createTeamTools("leader", backend), "view_task");
        Tool send = findTool(TeamTools.createTeamTools("leader", backend), "send_message");

        ToolOutput spawned = (ToolOutput) spawn.invoke(Map.of(
                "member_name", "worker-1",
                "display_name", "Worker One",
                "desc", "worker desc"
        ));
        assertThat(spawned.isSuccess()).isTrue();
        assertThat(spawned).hasToString("Member spawned: member_name=worker-1 display_name=Worker One");

        ToolOutput created = (ToolOutput) create.invoke(Map.of("tasks", List.of(
                Map.of("task_id", "tool-task-1", "title", "Task 1", "content", "Content 1")
        )));
        assertThat(created.isSuccess()).isTrue();
        assertThat(backend.getTaskManager().get("tool-task-1")).isPresent();

        ToolOutput assigned = (ToolOutput) update.invoke(Map.of("task_id", "tool-task-1", "assignee", "worker-1"));
        assertThat(assigned.isSuccess()).isTrue();
        assertThat(backend.getTaskManager().get("tool-task-1").orElseThrow().getAssignee()).isEqualTo("worker-1");

        ToolOutput badAssign = (ToolOutput) update.invoke(Map.of("task_id", "tool-task-1", "assignee", "ghost"));
        assertThat(badAssign.isSuccess()).isFalse();
        assertThat(badAssign.getError()).contains("ghost", "not found");

        ToolOutput viewed = (ToolOutput) view.invoke(Map.of("action", "get", "task_id", "tool-task-1"));
        assertThat(viewed.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) viewed.getData()).get("task_id")).isEqualTo("tool-task-1");
        assertThat(viewed.toString()).contains("Task #tool-task-1: Task 1", "Assignee: worker-1");

        ToolOutput sent = (ToolOutput) send.invoke(Map.of("to", "worker-1", "content", "please work", "summary", "nudge"));
        assertThat(sent.isSuccess()).isTrue();
        assertThat(sent).hasToString("Message sent from leader to worker-1");
        assertThat(backend.getMessageManager().getMessages("worker-1", false)).singleElement()
                .extracting(TeamMessage::getContent)
                .isEqualTo("please work");
    }

    @Test
    void claimTaskToolShouldClaimAndCompleteTasks() throws Exception {
        InProcessMessager leaderMessager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend leader = new TeamBackend("team-claim-tool", "leader", true, leaderMessager);
        leader.spawnMember("worker-1", "Worker One", AgentCard.builder().name("worker").description("desc").build()).join();
        TeamTask task = leader.getTaskManager().add("Claimable", "Content", "claim-tool-1", List.of()).join();
        // ClaimTaskTool now requires TeamBackend (not TeamTaskManager); MEMORY db is process-shared.
        TeamBackend worker = new TeamBackend("team-claim-tool", "worker-1", false, leaderMessager,
                leader.getTeamSessionId());
        Tool claim = new TeamTools.ClaimTaskTool(worker);

        ToolOutput claimed = (ToolOutput) claim.invoke(Map.of("task_id", task.getTaskId(), "status", "claimed"));
        assertThat(claimed.isSuccess()).isTrue();
        assertThat(leader.getTaskManager().get(task.getTaskId()).orElseThrow().getAssignee()).isEqualTo("worker-1");
        ToolOutput completed = (ToolOutput) claim.invoke(Map.of("task_id", task.getTaskId(), "status", "completed"));
        assertThat(completed.isSuccess()).isTrue();
        assertThat(completed.toString()).contains("Task #claim-tool-1 claimed -> completed", "Call view_task now");
        assertThat(leader.getTaskManager().get(task.getTaskId()).orElseThrow().getStatus()).isEqualTo("completed");
    }

    private static Tool findTool(List<Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.getCard().getName()))
                .findFirst()
                .orElseThrow();
    }

    private Path createGitRepo(String name) throws Exception {
        Path repoRoot = tempDir.resolve(name);
        Files.createDirectories(repoRoot);
        // Avoid `git init -b` (requires Git >= 2.28); set branch name before first commit.
        runGitOrThrow(repoRoot, "init");
        runGitOrThrow(repoRoot, "symbolic-ref", "HEAD", "refs/heads/main");
        runGitOrThrow(repoRoot, "config", "user.email", "test@example.com");
        runGitOrThrow(repoRoot, "config", "user.name", "Test User");
        Files.writeString(repoRoot.resolve("README.md"), "hello\n");
        runGitOrThrow(repoRoot, "add", "README.md");
        runGitOrThrow(repoRoot, "commit", "-m", "initial");
        return repoRoot;
    }

    private static void runGitOrThrow(Path cwd, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException(new String(output, StandardCharsets.UTF_8));
        }
    }
}
