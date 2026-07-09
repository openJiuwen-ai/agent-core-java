
package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.team.TeamLifecycle;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamMessage;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptEntry;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

class EventDispatcherCompatibilityTest {
    @Test
    void dispatchShouldSkipWhenAgentIsNotReadyLikePythonDispatcher() {
        TeamAgent agent = new TeamAgent();
        EventDispatcher dispatcher = new EventDispatcher(agent);

        dispatcher.dispatch(InnerEventMessage.builder().eventType(InnerEventType.USER_INPUT)
                .payload(Map.of("content", "should not configure implicitly")).build());

        assertThat(agent.isAgentReady()).isFalse();
        assertThat(agent.getLeaderInbox()).isEmpty();
    }

    @Test
    void transportEventShouldSkipWhenMemberNameMissingLikePythonDispatcher() {
        TeamAgent agent = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .role(TeamRole.LEADER).build());

        agent.getMessageManager()
                .sendMessage("do not fallback to leader", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "worker-1").join();
        agent.getDispatcher().dispatch(EventMessage.builder().eventType(EventDispatcher.EVENT_MESSAGE).build());
        boolean handled = agent.getDispatcher()
                .handleTaskClaimed(EventMessage.builder().eventType(EventDispatcher.EVENT_TASK_CLAIMED)
                        .payload(Map.of("member_name", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "task_id", "task-1"))
                        .build());

        assertThat(agent.currentMemberName()).isNull();
        assertThat(handled).isFalse();
        assertThat(agent.getLeaderInbox()).isEmpty();
        assertThat(agent.getMessageManager().getMessages(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, true))
                .singleElement().extracting(TeamMessage::getContent).isEqualTo("do not fallback to leader");
    }

    @Test
    void innerUserInputShouldRouteMentionThroughCoordinationManager() {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));

        agent.getDispatcher().dispatch(InnerEventMessage.builder().eventType(InnerEventType.USER_INPUT)
                .payload(Map.of("content", "@worker-1 inspect mailbox")).build());

        assertThat(agent.getMessageManager().getMessages("worker-1", false)).singleElement()
                .extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly(TeamConstants.USER_PSEUDO_MEMBER_NAME, "inspect mailbox");
        assertThat(agent.getLeaderInbox()).isEmpty();
    }

    @Test
    void innerUserInputShouldFallbackToLeaderForPlainText() {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));

        agent.getDispatcher().dispatch(InnerEventMessage.builder().eventType(InnerEventType.USER_INPUT)
                .payload(Map.of("content", "plan the release")).build());

        assertThat(agent.getLeaderInbox()).containsExactly("plan the release");
        assertThat(agent.getContext().getMetadata()).containsEntry("last_route", "leader");
    }

    @Test
    void mailboxPollShouldReadUnreadMessagesInTimestampOrderAndMarkRead() {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        String directId = agent.getMessageManager()
                .sendMessage("direct second", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "worker-1").join();
        String broadcastId = agent.getMessageManager().broadcastMessage("broadcast first", "worker-2").join();
        agent.getTeamBackend().getDb().message.getMessage(directId).setTimestamp(1_000L);
        agent.getTeamBackend().getDb().message.getMessage(broadcastId).setTimestamp(2_000L);

        List<String> delivered = agent.getDispatcher().processUnreadMessages(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                .stream().map(message -> message.getContent()).toList();

        assertThat(delivered).containsExactly("broadcast first", "direct second");
        assertThat(agent.getLeaderInbox()).hasSize(2);
        assertThat(agent.getLeaderInbox().get(0)).contains("Received broadcast message", "worker-2", "broadcast first");
        assertThat(agent.getLeaderInbox().get(1)).contains("Received direct message", "worker-1", "direct second");
        assertThat(agent.getMessageManager().getMessages(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, true)).isEmpty();
        assertThat(agent.getMessageManager().getBroadcastMessages(true)).isEmpty();
    }

    @Test
    void mailboxPollShouldDeferMessagesWhilePendingInterruptExists() {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        agent.getAgentSession()
                .updateState(Map.of(ToolInterruptionState.INTERRUPTION_KEY,
                        ToolInterruptionState.builder()
                                .interruptedTools(List.of(ToolInterruptEntry.builder()
                                        .request(InterruptRequest.builder().interruptId("call-1").build()).build()))
                                .build()));
        agent.getMessageManager()
                .sendMessage("wait for approval first", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "worker-1").join();

        List<TeamMessage> delivered =
            agent.getDispatcher().processUnreadMessages(TeamConstants.DEFAULT_LEADER_MEMBER_NAME);

        assertThat(agent.hasPendingInterrupt()).isTrue();
        assertThat(delivered).isEmpty();
        assertThat(agent.getLeaderInbox()).isEmpty();
        assertThat(agent.getMessageManager().getMessages(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, true))
                .singleElement().extracting(TeamMessage::getContent).isEqualTo("wait for approval first");
    }

    @Test
    void teamCleanedShouldShutdownTeammateButNotLeader() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());

        leader.getDispatcher().dispatch(EventMessage.builder().eventType(EventDispatcher.EVENT_CLEANED).build());
        teammate.getDispatcher().dispatch(EventMessage.builder().eventType(EventDispatcher.EVENT_CLEANED).build());

        assertThat(leader.getContext().getLifecycle()).isNotEqualTo(TeamLifecycle.COMPLETED);
        assertThat(teammate.getContext().getLifecycle()).isEqualTo(TeamLifecycle.COMPLETED);
    }

    @Test
    void standbyShouldPausePollsAndMessageEventShouldResumeAndProcessMailbox() {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        agent.startCoordinationLoop();
        agent.getDispatcher().dispatch(EventMessage.builder().eventType(EventDispatcher.EVENT_STANDBY).build());
        assertThat(agent.getCoordinatorLoop().isPollsPaused()).isTrue();

        agent.getMessageManager().sendMessage("wake leader", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "worker-1")
                .join();
        agent.getDispatcher().dispatch(EventMessage.builder().eventType(EventDispatcher.EVENT_MESSAGE).build());

        assertThat(agent.getCoordinatorLoop().isPollsPaused()).isFalse();
        assertThat(agent.getLeaderInbox()).singleElement().satisfies(
                content -> assertThat(content).contains("Received direct message", "worker-1", "wake leader"));
        agent.stopCoordinationLoop();
    }

    @Test
    void taskClaimedForSelfShouldResumePollsAndDeliverAssignment() {
        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());
        teammate.startCoordinationLoop();
        teammate.pausePolls();

        boolean handled = teammate.getDispatcher()
                .handleTaskClaimed(EventMessage.builder().eventType(EventDispatcher.EVENT_TASK_CLAIMED)
                        .payload(Map.of("member_name", "worker-1", "task_id", "task-123")).build());

        assertThat(handled).isTrue();
        assertThat(teammate.getCoordinatorLoop().isPollsPaused()).isFalse();
        assertThat(teammate.getLeaderInbox()).containsExactly("Task assigned to you: task-123");
        teammate.stopCoordinationLoop();
    }

    @Test
    void taskClaimedForOtherMemberShouldBeIgnored() {
        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());

        boolean handled = teammate.getDispatcher()
                .handleTaskClaimed(EventMessage.builder().eventType(EventDispatcher.EVENT_TASK_CLAIMED)
                        .payload(Map.of("member_name", "worker-2", "task_id", "task-123")).build());

        assertThat(handled).isFalse();
        assertThat(teammate.getLeaderInbox()).isEmpty();
    }

    @Test
    void memberReadyWithStaleClaimedTaskShouldNudgeMember() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        leader.getTaskManager().add("Fix bug", "Investigate and fix the critical bug", "task-1", List.of()).join();
        leader.getTeamBackend().getDb().task.claimTask("task-1", "worker-1");
        leader.getTeamBackend().getDb().task.getTask("task-1").setUpdatedAt(0L);

        var stale = leader.getDispatcher()
                .handleMemberStatusChanged(EventMessage.builder().eventType(EventDispatcher.EVENT_MEMBER_STATUS_CHANGED)
                        .payload(Map.of("member_name", "worker-1", "old_status", "busy", "new_status", "ready"))
                        .build());

        assertThat(stale).singleElement().extracting(task -> task.getTaskId(), task -> task.getTitle())
                .containsExactly("task-1", "Fix bug");
        assertThat(leader.getMessageManager().getMessages("worker-1", false)).singleElement().satisfies(message -> {
            assertThat(message.getFromMemberName()).isEqualTo(TeamConstants.DEFAULT_LEADER_MEMBER_NAME);
            assertThat(message.getContent()).contains("task-1", "Fix bug");
        });
    }

    @Test
    void memberStatusUnchangedOrWithoutStaleClaimShouldSkipNudge() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        leader.getTaskManager().add("Fresh task", "Still fresh", "task-2", List.of()).join();
        leader.getTeamBackend().getDb().task.claimTask("task-2", "worker-1");

        assertThat(leader.getDispatcher()
                .handleMemberStatusChanged(EventMessage.builder().eventType(EventDispatcher.EVENT_MEMBER_STATUS_CHANGED)
                        .payload(Map.of("member_name", "worker-1", "old_status", "ready", "new_status", "ready"))
                        .build()))
                .isEmpty();
        assertThat(leader.getDispatcher()
                .handleMemberStatusChanged(EventMessage.builder().eventType(EventDispatcher.EVENT_MEMBER_STATUS_CHANGED)
                        .payload(Map.of("member_name", "worker-1", "old_status", "busy", "new_status", "ready"))
                        .build()))
                .isEmpty();
        assertThat(leader.getMessageManager().getMessages("worker-1", false)).isEmpty();
    }

    @Test
    void leaderShouldObserveMemberLifecycleEventsLikePythonDispatcher() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));

        assertThat(leader.getDispatcher().handleLeaderMemberLifecycleEvent(EventMessage.builder()
                .eventType(EventDispatcher.EVENT_MEMBER_SPAWNED).payload(Map.of("member_name", "worker-1")).build()))
                .isEqualTo("Member online: worker-1");
        assertThat(leader.getDispatcher().handleLeaderMemberLifecycleEvent(
                EventMessage.builder().eventType(EventDispatcher.EVENT_MEMBER_RESTARTED)
                        .payload(Map.of("member_name", "worker-1", "restart_count", 2)).build()))
                .isEqualTo("Member restarted: worker-1 restart_count=2");
        assertThat(leader.getDispatcher().handleLeaderMemberLifecycleEvent(
                EventMessage.builder().eventType(EventDispatcher.EVENT_MEMBER_EXECUTION_CHANGED)
                        .payload(Map.of("member_name", "worker-1", "old_status", "running", "new_status", "completed"))
                        .build()))
                .isEqualTo("Member execution changed: worker-1 running -> completed");
        assertThat(leader.getDispatcher().handleLeaderMemberLifecycleEvent(EventMessage.builder()
                .eventType(EventDispatcher.EVENT_MEMBER_SHUTDOWN).payload(Map.of("member_name", "worker-1")).build()))
                .isEqualTo("Member shutdown: worker-1");
        assertThat(leader.getDispatcher().handleLeaderMemberLifecycleEvent(EventMessage.builder()
                .eventType(EventDispatcher.EVENT_MEMBER_CANCELED).payload(Map.of("member_name", "worker-1")).build()))
                .isEqualTo("Member canceled: worker-1");
    }

    @Test
    void stalePendingTasksShouldSelfPromptLeaderWithSendMessageAndClaimHint() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        leader.getTaskManager().add("Assign review", "Find someone to review the PR", "p-1", List.of()).join();
        leader.getTeamBackend().getDb().task.getTask("p-1").setUpdatedAt(0L);

        var stale = leader.getDispatcher().checkStalePendingTasks();

        assertThat(stale).singleElement().extracting(task -> task.getTaskId(), task -> task.getTitle())
                .containsExactly("p-1", "Assign review");
        assertThat(leader.getLeaderInbox()).singleElement()
                .satisfies(content -> assertThat(content).contains("p-1", "send_message", "claim_task"));
        assertThat(leader.getDispatcher().getLastPendingNudgeMillis()).containsKey("p-1");
    }

    @Test
    void stalePendingTaskShouldSteerRunningLeaderInsteadOfQueueingNewRound() throws Exception {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        leader.getTaskManager().add("Assign review", "Find someone to review the PR", "p-running", List.of()).join();
        leader.getTeamBackend().getDb().task.getTask("p-running").setUpdatedAt(0L);
        setStreamingActive(leader, true);

        var stale = leader.getDispatcher().checkStalePendingTasks();

        assertThat(stale).singleElement().extracting(task -> task.getTaskId()).isEqualTo("p-running");
        assertThat(leader.getLeaderInbox()).isEmpty();
        assertThat(leader.getStreamController().getPendingInputs()).isEmpty();
        assertThat(leader.getDeepAgent().getLoopController().drainSteering(leader.getAgentSession().getSessionId()))
                .singleElement()
                .satisfies(content -> assertThat(content).contains("p-running", "send_message", "claim_task"));
        assertThat(leader.getContext().getMetadata()).containsEntry("last_running_input_route", "steer");
    }

    @Test
    void mailboxPollCanUseFollowUpForRunningAgentWhenSteerDisabled() throws Exception {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        leader.getMessageManager()
                .sendMessage("finish the prior notice", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "worker-1").join();
        setStreamingActive(leader, true);

        List<TeamMessage> delivered =
            leader.getDispatcher().processUnreadMessages(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, false);

        assertThat(delivered).singleElement().extracting(TeamMessage::getContent).isEqualTo("finish the prior notice");
        assertThat(leader.getLeaderInbox()).isEmpty();
        assertThat(leader.getStreamController().getPendingInputs()).isEmpty();
        assertThat(leader.getDeepAgent().getLoopController().drainFollowUp(leader.getAgentSession().getSessionId()))
                .singleElement().satisfies(content -> assertThat(content).contains("Received direct message",
                        "worker-1", "finish the prior notice"));
        assertThat(leader.getContext().getMetadata()).containsEntry("last_running_input_route", "follow_up");
    }

    @Test
    void stalePendingTasksShouldSkipFreshThrottleAndTeammatePaths() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        leader.getTaskManager().add("Fresh pending", "No nudge yet", "p-2", List.of()).join();

        assertThat(leader.getDispatcher().checkStalePendingTasks()).isEmpty();
        assertThat(leader.getLeaderInbox()).isEmpty();

        leader.getTeamBackend().getDb().task.getTask("p-2").setUpdatedAt(0L);
        assertThat(leader.getDispatcher().checkStalePendingTasks()).hasSize(1);
        assertThat(leader.getDispatcher().checkStalePendingTasks()).isEmpty();
        assertThat(leader.getLeaderInbox()).hasSize(1);

        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());
        teammate.getTaskManager().add("Teammate pending", "Leader only", "p-3", List.of()).join();
        teammate.getTeamBackend().getDb().task.getTask("p-3").setUpdatedAt(0L);
        assertThat(teammate.getDispatcher().checkStalePendingTasks()).isEmpty();
        assertThat(teammate.getLeaderInbox()).isEmpty();
    }

    @Test
    void taskBoardEventShouldNudgeIdleLeaderWithIncompleteTaskBoard() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        leader.getTaskManager().add("Plan", "Break down implementation", "task-board-1", List.of()).join();

        var incomplete = leader.getDispatcher().handleTaskBoardEvent();

        assertThat(incomplete).singleElement()
                .extracting(task -> task.getTaskId(), task -> task.getStatus(), task -> task.getAssignee())
                .containsExactly("task-board-1", "pending", null);
        assertThat(leader.getLeaderInbox()).singleElement()
                .satisfies(content -> assertThat(content).contains("Current task board", "task-board-1", "pending"));
    }

    @Test
    void taskEventShouldSkipTaskBoardNudgeWhileRoundIsInFlight() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        leader.getTaskManager().add("Plan", "Break down implementation", "task-board-1", List.of()).join();
        leader.setInFlightRound(true);

        leader.getDispatcher().dispatch(EventMessage.builder().eventType(EventDispatcher.EVENT_TASK_UPDATED)
                .payload(Map.of("task_id", "task-board-1")).build());

        assertThat(leader.getLeaderInbox()).isEmpty();
        assertThat(leader.getContext().getMetadata()).containsEntry("in_flight_round", true);

        leader.setInFlightRound(false);
        leader.getDispatcher().dispatch(EventMessage.builder().eventType(EventDispatcher.EVENT_TASK_UPDATED)
                .payload(Map.of("task_id", "task-board-1")).build());

        assertThat(leader.getLeaderInbox()).singleElement()
                .satisfies(content -> assertThat(content).contains("Current task board", "task-board-1"));
    }

    @Test
    void taskBoardEventShouldNudgeIdleTeammateWithTaskList() {
        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());
        teammate.getTaskManager().add("Implement", "Take a pending task", "task-board-2", List.of()).join();

        var incomplete = teammate.getDispatcher().handleTaskBoardEvent();

        assertThat(incomplete).singleElement()
                .extracting(task -> task.getTaskId(), task -> task.getStatus(), task -> task.getAssignee())
                .containsExactly("task-board-2", "pending", null);
        assertThat(teammate.getLeaderInbox()).singleElement().satisfies(
                content -> assertThat(content).contains("Current teammate task list", "task-board-2", "pending"));
    }

    @Test
    void leaderTaskBoardShouldPromptAllDoneOnlyOutsidePollPath() {
        TeamAgent temporaryLeader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));

        assertThat(temporaryLeader.getDispatcher().nudgeIdleAgent(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, true))
                .isEmpty();
        assertThat(temporaryLeader.getLeaderInbox()).isEmpty();

        assertThat(temporaryLeader.getDispatcher().nudgeIdleAgent(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, false))
                .isEmpty();
        assertThat(temporaryLeader.getLeaderInbox()).singleElement()
                .satisfies(content -> assertThat(content).contains("All tasks are complete"));

        TeamAgentSpec persistentSpec = teamSpec(TeamRole.LEADER);
        persistentSpec.setLifecycle("persistent");
        TeamAgent persistentLeader = TeamFactory.createAgentTeam(persistentSpec);
        assertThat(persistentLeader.getDispatcher().nudgeIdleAgent(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, false))
                .isEmpty();
        assertThat(persistentLeader.getLeaderInbox()).singleElement()
                .satisfies(content -> assertThat(content).contains("Stay available"));
    }

    @Test
    void pollTaskShouldNudgeStaleClaimedSelfAndThrottle() {
        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());
        teammate.getTaskManager().add("Fix", "Handle stale own task", "stale-self", List.of()).join();
        teammate.getTeamBackend().getDb().task.claimTask("stale-self", "worker-1");
        teammate.getTeamBackend().getDb().task.getTask("stale-self").setUpdatedAt(0L);

        var stale = teammate.getDispatcher().checkStaleClaimedTasks();
        var throttled = teammate.getDispatcher().checkStaleClaimedTasks();

        assertThat(stale).singleElement().extracting(task -> task.getTaskId(), task -> task.getAssignee())
                .containsExactly("stale-self", "worker-1");
        assertThat(throttled).isEmpty();
        assertThat(teammate.getLeaderInbox()).singleElement()
                .satisfies(content -> assertThat(content).contains("stale-self", "Fix"));
        assertThat(teammate.getDispatcher().getLastStaleNudgeMillis()).containsKey("stale-self");
    }

    @Test
    void pollTaskLeaderShouldMessageOtherAssigneeForStaleClaim() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        leader.getTaskManager().add("Review", "Check a stale claim", "stale-other", List.of()).join();
        leader.getTeamBackend().getDb().task.claimTask("stale-other", "worker-1");
        leader.getTeamBackend().getDb().task.getTask("stale-other").setUpdatedAt(0L);

        var stale = leader.getDispatcher().checkStaleClaimedTasks();

        assertThat(stale).singleElement().extracting(task -> task.getTaskId(), task -> task.getAssignee())
                .containsExactly("stale-other", "worker-1");
        assertThat(leader.getMessageManager().getMessages("worker-1", false)).singleElement()
                .satisfies(message -> assertThat(message.getContent()).contains("stale-other", "Review"));
        assertThat(leader.getLeaderInbox()).isEmpty();
    }

    @Test
    void leaderShouldAutoAckUserBoundDirectMessageEvent() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        String messageId = leader.getMessageManager()
                .sendMessage("done for user", TeamConstants.USER_PSEUDO_MEMBER_NAME, "worker-1").join();

        boolean acked = leader.getDispatcher().ackUserBoundMessage(EventMessage
                .builder().eventType(EventDispatcher.EVENT_MESSAGE).payload(Map.of("message_id", messageId,
                        "from_member_name", "worker-1", "to_member_name", TeamConstants.USER_PSEUDO_MEMBER_NAME))
                .build());

        assertThat(acked).isTrue();
        assertThat(leader.getMessageManager().getMessages(TeamConstants.USER_PSEUDO_MEMBER_NAME, true)).isEmpty();
        assertThat(leader.getMessageManager().getMessages(TeamConstants.USER_PSEUDO_MEMBER_NAME, false)).singleElement()
                .extracting(message -> message.isRead()).isEqualTo(true);
    }

    @Test
    void autoAckShouldSkipNonUserBoundMessage() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        String messageId = leader.getMessageManager()
                .sendMessage("for leader", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "worker-1").join();

        boolean acked = leader.getDispatcher().ackUserBoundMessage(EventMessage
                .builder().eventType(EventDispatcher.EVENT_MESSAGE).payload(Map.of("message_id", messageId,
                        "from_member_name", "worker-1", "to_member_name", TeamConstants.DEFAULT_LEADER_MEMBER_NAME))
                .build());

        assertThat(acked).isFalse();
        assertThat(leader.getMessageManager().getMessages(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, true))
                .singleElement().extracting(message -> message.isRead()).isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolApprovalResultShouldResumeInterruptForTargetMember() {
        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());
        teammate.getAgentSession()
                .updateState(Map.of(ToolInterruptionState.INTERRUPTION_KEY,
                        ToolInterruptionState.builder()
                                .interruptedTools(List.of(ToolInterruptEntry.builder()
                                        .request(InterruptRequest.builder().interruptId("call-1").build()).build()))
                                .build()));
        teammate.setInFlightRound(true);

        var input = teammate.getDispatcher().handleToolApprovalResult(EventMessage
                .builder().eventType(EventDispatcher.EVENT_TOOL_APPROVAL_RESULT).payload(Map.of("member_name",
                        "worker-1", "tool_call_id", "call-1", "approved", true, "feedback", "ok", "auto_confirm", true))
                .build());

        assertThat(input).isNotNull();
        assertThat(teammate.getLastResumedInterruptInput()).isNull();
        assertThat(teammate.getStreamController().getPendingInterruptResumes()).containsExactly(input);
        Map<String, Object> decision = (Map<String, Object>) input.getUserInputs().get("call-1");
        assertThat(decision).containsEntry("approved", true);
        assertThat(decision).containsEntry("feedback", "ok");
        assertThat(decision).containsEntry("auto_confirm", true);
        assertThat(teammate.getContext().getMetadata()).containsEntry("pending_interrupt_resume_count", 1);
    }

    @Test
    void toolApprovalResultShouldIgnoreOtherMembers() {
        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());

        var input = teammate.getDispatcher()
                .handleToolApprovalResult(EventMessage.builder().eventType(EventDispatcher.EVENT_TOOL_APPROVAL_RESULT)
                        .payload(Map.of("member_name", "worker-2", "tool_call_id", "call-1", "approved", true))
                        .build());

        assertThat(input).isNull();
        assertThat(teammate.getLastResumedInterruptInput()).isNull();
    }

    @Test
    void memberCanceledForSelfShouldCancelTeammateAgent() {
        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());

        boolean handled = teammate.getDispatcher().handleTeammateMemberLifecycleEvent(EventMessage.builder()
                .eventType(EventDispatcher.EVENT_MEMBER_CANCELED).payload(Map.of("member_name", "worker-1")).build());

        assertThat(handled).isTrue();
        assertThat(teammate.isCancelRequested()).isTrue();
        assertThat(teammate.getContext().getMetadata()).containsEntry("cancel_requested", true);
    }

    @Test
    void memberShutdownForSelfShouldProcessUnreadMailbox() {
        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());
        teammate.getMessageManager().sendMessage("shutdown notice", "worker-1", "team_leader").join();

        boolean handled = teammate.getDispatcher().handleTeammateMemberLifecycleEvent(EventMessage.builder()
                .eventType(EventDispatcher.EVENT_MEMBER_SHUTDOWN).payload(Map.of("member_name", "worker-1")).build());

        assertThat(handled).isTrue();
        assertThat(teammate.getLeaderInbox()).singleElement().satisfies(
                content -> assertThat(content).contains("Received direct message", "team_leader", "shutdown notice"));
        assertThat(teammate.getMessageManager().getMessages("worker-1", true)).isEmpty();
    }

    @Test
    void teammateMemberLifecycleShouldIgnoreLeaderAndOtherMembers() {
        TeamAgent leader = TeamFactory.createAgentTeam(teamSpec(TeamRole.LEADER));
        assertThat(leader.getDispatcher().handleTeammateMemberLifecycleEvent(
                EventMessage.builder().eventType(EventDispatcher.EVENT_MEMBER_CANCELED)
                        .payload(Map.of("member_name", TeamConstants.DEFAULT_LEADER_MEMBER_NAME)).build()))
                .isFalse();

        TeamAgent teammate = new TeamAgent().configure(teamSpec(TeamRole.LEADER),
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("dispatcher-team")
                        .memberName("worker-1").role(TeamRole.MEMBER).build());
        assertThat(teammate.getDispatcher().handleTeammateMemberLifecycleEvent(EventMessage.builder()
                .eventType(EventDispatcher.EVENT_MEMBER_CANCELED).payload(Map.of("member_name", "worker-2")).build()))
                .isFalse();
        assertThat(teammate.isCancelRequested()).isFalse();
    }

    private static TeamAgentSpec teamSpec(TeamRole leaderRole) {
        return TeamAgentSpec.builder().name("dispatcher-team").members(List.of(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(leaderRole).build(),
                TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).build())).build();
    }

    private static void setStreamingActive(TeamAgent agent, boolean active) throws Exception {
        Field field = StreamController.class.getDeclaredField("streamingActive");
        field.setAccessible(true);
        field.set(agent.getStreamController(), active);
    }
}
