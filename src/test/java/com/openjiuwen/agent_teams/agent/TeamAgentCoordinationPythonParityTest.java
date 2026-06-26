/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.coordination.EventBus;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.agent.coordination.handlers.AgentLifecycleHandler;
import com.openjiuwen.agent_teams.agent.coordination.handlers.MemberHandler;
import com.openjiuwen.agent_teams.agent.coordination.handlers.MessageHandler;
import com.openjiuwen.agent_teams.agent.coordination.handlers.StaleTaskHandler;
import com.openjiuwen.agent_teams.agent.coordination.handlers.TaskBoardHandler;
import com.openjiuwen.agent_teams.agent.coordination.handlers.TeamCompletionHandler;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.BroadcastEvent;
import com.openjiuwen.agent_teams.schema.MemberShutdownEvent;
import com.openjiuwen.agent_teams.schema.MemberStatusChangedEvent;
import com.openjiuwen.agent_teams.schema.MessageEvent;
import com.openjiuwen.agent_teams.schema.TaskClaimedEvent;
import com.openjiuwen.agent_teams.schema.TaskCompletedEvent;
import com.openjiuwen.agent_teams.schema.TaskCreatedEvent;
import com.openjiuwen.agent_teams.schema.TaskListDrainedEvent;
import com.openjiuwen.agent_teams.schema.TeamCleanedEvent;
import com.openjiuwen.agent_teams.schema.TeamCompletedEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.ToolApprovalResultEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamTask;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Supplemental parity tests for TeamAgent coordination lifecycle wiring.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_team_agent_coordination} in
 * {@code tests/unit_tests/agent_teams/test_team_agent_coordination.py}.</p>
 */
class TeamAgentCoordinationPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/agent_teams/test_team_agent_coordination.py";
    private static final String TEAM = "test-team";
    private static final String LEADER = "leader-1";
    private static final String DEV = "dev-1";
    private static final String HUMAN = "human_alice";
    private static final long STALE_UPDATED_AT = System.currentTimeMillis() - 20 * 60 * 1000L;

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonTestNodes")
    void mirrorsPythonCoordinationTests(String pythonNodeId, Scenario scenario) throws Exception {
        scenario.run();
    }

    private static Stream<Arguments> pythonTestNodes() {
        return Stream.of(
                arg("test_coordination_loop_created_on_configure", TeamAgentCoordinationPythonParityTest::coordinationLoopCreated),
                arg("test_start_stop_coordination", TeamAgentCoordinationPythonParityTest::startStopCoordination),
                arg("test_wake_feeds_messages_to_agent", TeamAgentCoordinationPythonParityTest::wakeFeedsMessagesToAgent),
                arg("test_human_agent_inbound_callback_fires_on_message_event",
                        TeamAgentCoordinationPythonParityTest::humanAgentInboundCallbackFiresOnMessageEvent),
                arg("test_tool_approval_event_resumes_interrupt",
                        TeamAgentCoordinationPythonParityTest::toolApprovalEventResumesInterrupt),
                arg("test_idle_human_agent_tears_down_on_self_shutdown",
                        TeamAgentCoordinationPythonParityTest::idleHumanAgentTearsDownOnSelfShutdown),
                arg("test_busy_human_agent_not_interrupted_on_self_shutdown",
                        TeamAgentCoordinationPythonParityTest::busyHumanAgentNotInterruptedOnSelfShutdown),
                arg("test_forced_shutdown_collapses_busy_human_agent",
                        TeamAgentCoordinationPythonParityTest::forcedShutdownCollapsesBusyHumanAgent),
                arg("test_human_agent_ignores_other_member_shutdown",
                        TeamAgentCoordinationPythonParityTest::humanAgentIgnoresOtherMemberShutdown),
                arg("test_human_agent_dispatch_delivers_message_broadcast_and_task_claimed",
                        TeamAgentCoordinationPythonParityTest::humanAgentDispatchDeliversWhitelistedEvents),
                arg("test_human_agent_dispatch_mutes_task_board_survey_events",
                        TeamAgentCoordinationPythonParityTest::humanAgentDispatchMutesTaskBoardSurveyEvents),
                arg("test_human_agent_ignores_other_member_task_claim",
                        TeamAgentCoordinationPythonParityTest::humanAgentIgnoresOtherMemberTaskClaim),
                arg("test_teammate_does_not_self_shutdown_on_member_shutdown",
                        TeamAgentCoordinationPythonParityTest::teammateDoesNotSelfShutdownOnMemberShutdown),
                arg("test_mailbox_messages_deferred_while_interrupt_pending",
                        TeamAgentCoordinationPythonParityTest::mailboxMessagesDeferredWhileInterruptPending),
                arg("test_resume_interrupt_queues_while_agent_running",
                        TeamAgentCoordinationPythonParityTest::resumeInterruptQueuesWhileAgentRunning),
                arg("test_member_ready_with_claimed_task_triggers_nudge",
                        TeamAgentCoordinationPythonParityTest::memberReadyWithClaimedTaskTriggersNudge),
                arg("test_member_error_with_claimed_task_triggers_nudge",
                        TeamAgentCoordinationPythonParityTest::memberErrorWithClaimedTaskTriggersNudge),
                arg("test_member_ready_without_claimed_task_skips_nudge",
                        TeamAgentCoordinationPythonParityTest::memberReadyWithoutClaimedTaskSkipsNudge),
                arg("test_member_status_unchanged_skips_nudge",
                        TeamAgentCoordinationPythonParityTest::memberStatusUnchangedSkipsNudge),
                arg("test_task_claimed_for_other_member_falls_through_to_board_nudge",
                        TeamAgentCoordinationPythonParityTest::taskClaimedForOtherMemberFallsThroughToBoardNudge),
                arg("test_task_claimed_for_other_member_nudges_leader_via_steer_when_busy",
                        TeamAgentCoordinationPythonParityTest::taskClaimedForOtherMemberNudgesLeaderWhenBusy),
                arg("test_task_claimed_for_self_uses_teammate_template",
                        TeamAgentCoordinationPythonParityTest::taskClaimedForSelfUsesTeammateTemplate),
                arg("test_task_claimed_for_self_uses_human_template_when_human_agent",
                        TeamAgentCoordinationPythonParityTest::taskClaimedForSelfUsesHumanTemplateWhenHumanAgent),
                arg("test_task_claimed_for_human_self_swallows_title_lookup_error",
                        TeamAgentCoordinationPythonParityTest::taskClaimedForHumanSelfSwallowsTitleLookupError),
                arg("test_format_message_uses_teammate_template_when_not_human",
                        TeamAgentCoordinationPythonParityTest::formatMessageUsesTeammateTemplateWhenNotHuman),
                arg("test_format_message_uses_human_template_when_human_agent",
                        TeamAgentCoordinationPythonParityTest::formatMessageUsesHumanTemplateWhenHumanAgent),
                arg("test_stale_claim_leader_messages_assignee",
                        TeamAgentCoordinationPythonParityTest::staleClaimLeaderMessagesAssignee),
                arg("test_stale_claim_fresh_task_does_not_nudge",
                        TeamAgentCoordinationPythonParityTest::staleClaimFreshTaskDoesNotNudge),
                arg("test_stale_claim_throttles_follow_up_polls",
                        TeamAgentCoordinationPythonParityTest::staleClaimThrottlesFollowUpPolls),
                arg("test_stale_claim_self_nudge_when_idle",
                        TeamAgentCoordinationPythonParityTest::staleClaimSelfNudgeWhenIdle),
                arg("test_stale_claim_self_nudge_steers_when_running",
                        TeamAgentCoordinationPythonParityTest::staleClaimSelfNudgeWhenRunning),
                arg("test_stale_claim_throttle_drops_unrelated_entries",
                        TeamAgentCoordinationPythonParityTest::staleClaimThrottleDropsUnrelatedEntries),
                arg("test_stale_pending_leader_self_nudges_with_hint",
                        TeamAgentCoordinationPythonParityTest::stalePendingLeaderSelfNudgesWithHint),
                arg("test_stale_pending_leader_steers_when_running",
                        TeamAgentCoordinationPythonParityTest::stalePendingLeaderSteersWhenRunning),
                arg("test_stale_pending_fresh_task_skipped",
                        TeamAgentCoordinationPythonParityTest::stalePendingFreshTaskSkipped),
                arg("test_stale_pending_throttled_after_first_nudge",
                        TeamAgentCoordinationPythonParityTest::stalePendingThrottledAfterFirstNudge),
                arg("test_stale_pending_teammate_skips_check",
                        TeamAgentCoordinationPythonParityTest::stalePendingTeammateSkipsCheck),
                arg("test_team_cleaned_event_shuts_down_teammate",
                        TeamAgentCoordinationPythonParityTest::teamCleanedEventShutsDownTeammate),
                arg("test_team_cleaned_event_ignored_by_leader",
                        TeamAgentCoordinationPythonParityTest::teamCleanedEventIgnoredByLeader),
                arg("test_shutdown_self_cancels_running_round_and_closes_stream",
                        TeamAgentCoordinationPythonParityTest::shutdownSelfCancelsRunningRoundAndClosesStream),
                arg("test_teammate_round_completion_wakes_mailbox_after_interrupt_clears",
                        TeamAgentCoordinationPythonParityTest::teammateRoundCompletionWakesMailboxAfterInterruptClears),
                arg("test_first_iter_gate_single_instance_registered_on_deep_agent",
                        TeamAgentCoordinationPythonParityTest::firstIterGateSingleInstanceRegisteredOnDeepAgent),
                arg("test_streaming_session_id_reads_from_contextvar",
                        TeamAgentCoordinationPythonParityTest::streamingSessionIdReadsFromContextvar),
                arg("test_dispatcher_registers_team_completion_handler",
                        TeamAgentCoordinationPythonParityTest::dispatcherRegistersTeamCompletionHandler),
                arg("test_team_completion_emits_on_idle_leader_when_complete",
                        TeamAgentCoordinationPythonParityTest::teamCompletionEmitsOnIdleLeaderWhenComplete),
                arg("test_team_completion_not_re_emitted_on_repeated_tick",
                        TeamAgentCoordinationPythonParityTest::teamCompletionNotReEmittedOnRepeatedTick),
                arg("test_team_completion_re_arms_after_falling_edge",
                        TeamAgentCoordinationPythonParityTest::teamCompletionReArmsAfterFallingEdge),
                arg("test_team_completion_never_emits_for_teammate",
                        TeamAgentCoordinationPythonParityTest::teamCompletionNeverEmitsForTeammate),
                arg("test_team_completion_skipped_when_round_in_flight",
                        TeamAgentCoordinationPythonParityTest::teamCompletionSkippedWhenRoundInFlight),
                arg("test_team_completion_consumers_accept_their_events",
                        TeamAgentCoordinationPythonParityTest::teamCompletionConsumersAcceptTheirEvents),
                arg("test_drained_fires_registered_completion_callbacks",
                        TeamAgentCoordinationPythonParityTest::drainedFiresRegisteredCompletionCallbacks),
                arg("test_drained_callback_failure_is_isolated",
                        TeamAgentCoordinationPythonParityTest::drainedCallbackFailureIsIsolated),
                arg("test_register_team_completion_callbacks_wires_skill_rail",
                        TeamAgentCoordinationPythonParityTest::registerTeamCompletionCallbacksWiresSkillRail),
                arg("test_task_completed_nudges_leader_with_all_done_when_idle",
                        TeamAgentCoordinationPythonParityTest::taskCompletedNudgesLeaderWithAllDoneWhenIdle),
                arg("test_task_completed_nudges_leader_via_steer_when_busy",
                        TeamAgentCoordinationPythonParityTest::taskCompletedNudgesLeaderWhenBusy),
                arg("test_task_completed_with_incomplete_tasks_nudges_leader_board",
                        TeamAgentCoordinationPythonParityTest::taskCompletedWithIncompleteTasksNudgesLeaderBoard),
                arg("test_persistent_completion_closes_leader_stream",
                        TeamAgentCoordinationPythonParityTest::persistentCompletionClosesLeaderStream),
                arg("test_temporary_completion_does_not_close_stream",
                        TeamAgentCoordinationPythonParityTest::temporaryCompletionDoesNotCloseStream),
                arg("test_persistent_completion_concludes_once_per_rising_edge",
                        TeamAgentCoordinationPythonParityTest::persistentCompletionConcludesOncePerRisingEdge),
                arg("test_rearm_allows_completion_to_conclude_again",
                        TeamAgentCoordinationPythonParityTest::rearmAllowsCompletionToConcludeAgain)
        );
    }

    private static Arguments arg(String name, Scenario scenario) {
        return Arguments.of(SOURCE + "::" + name, scenario);
    }

    private static void coordinationLoopCreated() {
        TeamAgent agent = configuredAgent(TeamRole.LEADER, LEADER, "temporary", new RuntimeStub());

        assertThat(agent.getCoordinationLoop()).isInstanceOf(EventBus.class);
        assertThat(((EventBus) agent.getCoordinationLoop()).getRole()).isEqualTo(TeamRole.LEADER);
    }

    private static void startStopCoordination() {
        try (EventBus bus = new EventBus(TeamRole.LEADER, 60.0d, 60.0d)) {
            bus.start(event -> CompletableFuture.completedFuture(null)).toCompletableFuture().join();
            assertThat(bus.isRunning()).isTrue();

            bus.stop().toCompletableFuture().join();
            assertThat(bus.isRunning()).isFalse();
        }
    }

    private static void wakeFeedsMessagesToAgent() {
        Fixture fixture = leaderFixture();
        fixture.messages.add(message("msg-1", DEV, LEADER, "task done", false, false, 1000L));

        join(fixture.messageHandler().processUnreadMessages(LEADER, true));

        assertThat(fixture.host.delivered).singleElement().asString().contains("msg-1", DEV, "task done");
        assertThat(fixture.messages.markedRead).containsExactly(new ReadMark("msg-1", LEADER));
    }

    private static void humanAgentInboundCallbackFiresOnMessageEvent() {
        Fixture fixture = leaderFixture();
        List<MessageHandler.HumanAgentInboundEvent> received = new ArrayList<>();
        fixture.backend.humanAgents.add(HUMAN);
        fixture.backend.inboundCallbacks.put(HUMAN, event -> {
            received.add(event);
            return CompletableFuture.completedFuture(null);
        });
        fixture.messages.add(message("msg-99", DEV, HUMAN, "leader pinging the user", false, false, 12345L));

        MessageEvent payload = new MessageEvent();
        payload.setTeamName(TEAM);
        payload.setMessageId("msg-99");
        payload.setFromMemberName(DEV);
        payload.setToMemberName(HUMAN);
        join(fixture.messageHandler().notifyHumanAgentInbound(transport(payload)));

        assertThat(received).singleElement()
                .satisfies(event -> {
                    assertThat(event.memberName()).isEqualTo(HUMAN);
                    assertThat(event.sender()).isEqualTo(DEV);
                    assertThat(event.body()).isEqualTo("leader pinging the user");
                    assertThat(event.broadcast()).isFalse();
                    assertThat(event.messageId()).isEqualTo("msg-99");
                });
    }

    private static void toolApprovalEventResumesInterrupt() {
        Fixture fixture = fixture(TeamRole.TEAMMATE, DEV, "temporary");
        ToolApprovalResultEvent payload = new ToolApprovalResultEvent();
        payload.setTeamName(TEAM);
        payload.setMemberName(DEV);
        payload.setToolCallId("call-1");
        payload.setApproved(true);
        payload.setFeedback("ok");
        payload.setAutoConfirm(true);

        join(fixture.lifecycleHandler().onToolApprovalResult(transport(payload)));

        assertThat(fixture.host.interruptResumes).singleElement().isInstanceOf(InteractiveInput.class);
        InteractiveInput input = (InteractiveInput) fixture.host.interruptResumes.get(0);
        assertThat(input.getUserInputs()).containsKey("call-1");
        Map<?, ?> approvalPayload = (Map<?, ?>) input.getUserInputs().get("call-1");
        assertThat(approvalPayload.get("approved")).isEqualTo(true);
        assertThat(approvalPayload.get("feedback")).isEqualTo("ok");
        assertThat(approvalPayload.get("auto_confirm")).isEqualTo(true);
    }

    private static void idleHumanAgentTearsDownOnSelfShutdown() {
        Fixture fixture = humanFixture();

        join(fixture.memberHandler().handleTeammateMemberEvent(memberShutdown(HUMAN, false)));

        assertThat(fixture.host.shutdownCalls).hasValue(1);
    }

    private static void busyHumanAgentNotInterruptedOnSelfShutdown() {
        Fixture fixture = humanFixture();
        fixture.host.inFlight = true;

        join(fixture.memberHandler().handleTeammateMemberEvent(memberShutdown(HUMAN, false)));

        assertThat(fixture.host.shutdownCalls).hasValue(0);
    }

    private static void forcedShutdownCollapsesBusyHumanAgent() {
        Fixture fixture = humanFixture();
        fixture.host.inFlight = true;

        join(fixture.memberHandler().handleTeammateMemberEvent(memberShutdown(HUMAN, true)));

        assertThat(fixture.host.shutdownCalls).hasValue(1);
    }

    private static void humanAgentIgnoresOtherMemberShutdown() {
        Fixture fixture = humanFixture();

        join(fixture.memberHandler().handleTeammateMemberEvent(memberShutdown(DEV, false)));

        assertThat(fixture.host.shutdownCalls).hasValue(0);
    }

    private static void humanAgentDispatchDeliversWhitelistedEvents() {
        Fixture fixture = humanFixture();
        EventDispatcher dispatcher = fixture.dispatcher();
        MessageEvent message = new MessageEvent();
        message.setTeamName(TEAM);
        message.setMessageId("m1");
        message.setFromMemberName(LEADER);
        message.setToMemberName(HUMAN);
        BroadcastEvent broadcast = new BroadcastEvent();
        broadcast.setTeamName(TEAM);
        broadcast.setMessageId("b1");
        broadcast.setFromMemberName(LEADER);
        TaskClaimedEvent claimed = new TaskClaimedEvent();
        claimed.setTeamName(TEAM);
        claimed.setMemberName(HUMAN);
        claimed.setTaskId("t1");

        join(dispatcher.dispatch(EventMessage.fromEvent(message)));
        join(dispatcher.dispatch(EventMessage.fromEvent(broadcast)));
        join(dispatcher.dispatch(EventMessage.fromEvent(claimed)));

        assertThat(dispatcher.getCallbackTrace())
                .contains("MessageHandler.on_message_or_broadcast", "TaskBoardHandler.on_task_claimed");
    }

    private static void humanAgentDispatchMutesTaskBoardSurveyEvents() {
        Fixture fixture = humanFixture();
        EventDispatcher dispatcher = fixture.dispatcher();
        TaskCreatedEvent event = new TaskCreatedEvent();
        event.setTeamName(TEAM);
        event.setTaskId("t1");
        event.setStatus(TaskStatus.PENDING.value());

        join(dispatcher.dispatch(EventMessage.fromEvent(event)));

        assertThat(dispatcher.getCallbackTrace()).isEmpty();
    }

    private static void humanAgentIgnoresOtherMemberTaskClaim() {
        Fixture fixture = humanFixture();
        fixture.tasks.add(task("task-7", TaskStatus.PENDING.value(), null, System.currentTimeMillis()));

        join(fixture.taskBoardHandler().onTaskClaimed(taskClaimed(DEV, "task-7")));

        assertThat(fixture.host.allDelivered()).isEmpty();
        assertThat(fixture.tasks.listCalls).isZero();
    }

    private static void teammateDoesNotSelfShutdownOnMemberShutdown() {
        Fixture fixture = fixture(TeamRole.TEAMMATE, DEV, "temporary");

        join(fixture.memberHandler().handleTeammateMemberEvent(memberShutdown(DEV, false)));

        assertThat(fixture.host.shutdownCalls).hasValue(0);
    }

    private static void mailboxMessagesDeferredWhileInterruptPending() {
        Fixture fixture = leaderFixture();
        fixture.host.pendingInterrupt = true;
        fixture.messages.add(message("msg-normal", DEV, LEADER, "normal mailbox message", false, false, 1000L));

        join(fixture.messageHandler().processUnreadMessages(LEADER, true));

        assertThat(fixture.host.allDelivered()).isEmpty();
        assertThat(fixture.messages.markedRead).isEmpty();
    }

    private static void resumeInterruptQueuesWhileAgentRunning() {
        RuntimeStub runtime = new RuntimeStub();
        TeamAgent agent = configuredAgent(TeamRole.LEADER, LEADER, "temporary", runtime);
        CompletableFuture<Void> runningTask = new CompletableFuture<>();
        setField(agent.getStreamController(), "agentTask", runningTask);
        InteractiveInput input = new InteractiveInput();
        input.update("call-1", Map.of("approved", true));

        join(agent.resumeInterrupt(input));

        assertThat(agent.getStreamController().getPendingInterruptResumes()).containsExactly(input);
    }

    private static void memberReadyWithClaimedTaskTriggersNudge() {
        Fixture fixture = leaderFixture();
        fixture.tasks.add(task("task-1", TaskStatus.CLAIMED.value(), DEV, STALE_UPDATED_AT));

        join(fixture.memberHandler().handleLeaderMemberEvent(memberStatusChanged(DEV, "busy", "ready")));

        assertThat(fixture.messages.sent).singleElement()
                .satisfies(sent -> assertThat(sent.target()).isEqualTo(DEV));
    }

    private static void memberErrorWithClaimedTaskTriggersNudge() {
        Fixture fixture = leaderFixture();
        fixture.tasks.add(task("task-1", TaskStatus.CLAIMED.value(), DEV, STALE_UPDATED_AT));

        join(fixture.memberHandler().handleLeaderMemberEvent(memberStatusChanged(DEV, "busy", "error")));

        assertThat(fixture.messages.sent).hasSize(1);
    }

    private static void memberReadyWithoutClaimedTaskSkipsNudge() {
        Fixture fixture = leaderFixture();

        join(fixture.memberHandler().handleLeaderMemberEvent(memberStatusChanged(DEV, "busy", "ready")));

        assertThat(fixture.messages.sent).isEmpty();
    }

    private static void memberStatusUnchangedSkipsNudge() {
        Fixture fixture = leaderFixture();
        fixture.tasks.add(task("task-1", TaskStatus.CLAIMED.value(), DEV, STALE_UPDATED_AT));

        join(fixture.memberHandler().handleLeaderMemberEvent(memberStatusChanged(DEV, "ready", "ready")));

        assertThat(fixture.messages.sent).isEmpty();
    }

    private static void taskClaimedForOtherMemberFallsThroughToBoardNudge() {
        Fixture fixture = leaderFixture();
        fixture.tasks.add(task("task-2", TaskStatus.PENDING.value(), null, System.currentTimeMillis()));

        join(fixture.taskBoardHandler().onTaskClaimed(taskClaimed(DEV, "task-2")));

        assertThat(fixture.host.delivered).singleElement().asString().contains("task-2");
    }

    private static void taskClaimedForOtherMemberNudgesLeaderWhenBusy() {
        Fixture fixture = leaderFixture();
        fixture.host.agentRunning = true;
        fixture.tasks.add(task("task-2", TaskStatus.PENDING.value(), null, System.currentTimeMillis()));

        join(fixture.taskBoardHandler().onTaskClaimed(taskClaimed(DEV, "task-2")));

        assertThat(fixture.host.followUps).singleElement().asString().contains("task-2");
    }

    private static void taskClaimedForSelfUsesTeammateTemplate() {
        Fixture fixture = fixture(TeamRole.TEAMMATE, DEV, "temporary");

        join(fixture.taskBoardHandler().onTaskClaimed(taskClaimed(DEV, "task-3")));

        assertThat(fixture.host.delivered).singleElement().asString().contains("task-3");
    }

    private static void taskClaimedForSelfUsesHumanTemplateWhenHumanAgent() {
        Fixture fixture = humanFixture();
        fixture.tasks.add(task("task-4", "Review report", "details", TaskStatus.CLAIMED.value(), HUMAN, STALE_UPDATED_AT));

        join(fixture.taskBoardHandler().onTaskClaimed(taskClaimed(HUMAN, "task-4")));

        assertThat(fixture.host.delivered).singleElement().asString().contains("task-4", "Review report");
    }

    private static void taskClaimedForHumanSelfSwallowsTitleLookupError() {
        Fixture fixture = humanFixture();
        fixture.tasks.throwOnGetTask = true;

        join(fixture.taskBoardHandler().onTaskClaimed(taskClaimed(HUMAN, "task-5")));

        assertThat(fixture.host.delivered).singleElement().asString().contains("task-5");
    }

    private static void formatMessageUsesTeammateTemplateWhenNotHuman() {
        Fixture fixture = leaderFixture();

        String body = fixture.messageHandler().formatMessage(
                message("msg-7", DEV, LEADER, "hello", false, false, 1000L), false, 2000L);

        assertThat(body).contains("msg-7", DEV, "hello");
    }

    private static void formatMessageUsesHumanTemplateWhenHumanAgent() {
        Fixture fixture = humanFixture();

        String body = fixture.messageHandler().formatMessage(
                message("msg-8", LEADER, HUMAN, "controller notice", false, false, 1000L), true, 2000L);

        assertThat(body).contains("msg-8", LEADER, "controller notice");
    }

    private static void staleClaimLeaderMessagesAssignee() {
        Fixture fixture = leaderFixture();
        fixture.tasks.add(task("task-1", TaskStatus.CLAIMED.value(), DEV, STALE_UPDATED_AT));

        join(fixture.staleTaskHandler().checkStaleClaimedTasks());

        assertThat(fixture.messages.sent).singleElement()
                .satisfies(sent -> assertThat(sent.target()).isEqualTo(DEV));
    }

    private static void staleClaimFreshTaskDoesNotNudge() {
        Fixture fixture = leaderFixture();
        fixture.tasks.add(task("task-1", TaskStatus.CLAIMED.value(), DEV, System.currentTimeMillis()));

        join(fixture.staleTaskHandler().checkStaleClaimedTasks());

        assertThat(fixture.messages.sent).isEmpty();
    }

    private static void staleClaimThrottlesFollowUpPolls() {
        Fixture fixture = leaderFixture();
        StaleTaskHandler handler = fixture.staleTaskHandler();
        handler.getLastStaleNudge().put("task-1", System.currentTimeMillis() / 1000.0d);
        fixture.tasks.add(task("task-1", TaskStatus.CLAIMED.value(), DEV, STALE_UPDATED_AT));

        join(handler.checkStaleClaimedTasks());

        assertThat(fixture.messages.sent).isEmpty();
    }

    private static void staleClaimSelfNudgeWhenIdle() {
        Fixture fixture = fixture(TeamRole.TEAMMATE, DEV, "temporary");
        fixture.tasks.add(task("task-1", TaskStatus.CLAIMED.value(), DEV, STALE_UPDATED_AT));

        join(fixture.staleTaskHandler().checkStaleClaimedTasks());

        assertThat(fixture.host.delivered).singleElement().asString().contains("task-1");
    }

    private static void staleClaimSelfNudgeWhenRunning() {
        Fixture fixture = fixture(TeamRole.TEAMMATE, DEV, "temporary");
        fixture.host.agentRunning = true;
        fixture.tasks.add(task("task-1", TaskStatus.CLAIMED.value(), DEV, STALE_UPDATED_AT));

        join(fixture.staleTaskHandler().checkStaleClaimedTasks());

        assertThat(fixture.host.followUps).singleElement().asString().contains("task-1");
    }

    private static void staleClaimThrottleDropsUnrelatedEntries() {
        Fixture fixture = leaderFixture();
        StaleTaskHandler handler = fixture.staleTaskHandler();
        handler.getLastStaleNudge().put("orphan", 1.0d);
        fixture.tasks.add(task("task-1", TaskStatus.CLAIMED.value(), DEV, System.currentTimeMillis()));

        join(handler.checkStaleClaimedTasks());

        assertThat(handler.getLastStaleNudge()).doesNotContainKey("orphan");
    }

    private static void stalePendingLeaderSelfNudgesWithHint() {
        Fixture fixture = leaderFixture();
        fixture.tasks.add(task("pending-1", TaskStatus.PENDING.value(), null, STALE_UPDATED_AT));

        join(fixture.staleTaskHandler().checkStalePendingTasks());

        assertThat(fixture.host.delivered).singleElement().asString().contains("pending-1");
    }

    private static void stalePendingLeaderSteersWhenRunning() {
        Fixture fixture = leaderFixture();
        fixture.host.agentRunning = true;
        fixture.tasks.add(task("pending-1", TaskStatus.PENDING.value(), null, STALE_UPDATED_AT));

        join(fixture.staleTaskHandler().checkStalePendingTasks());

        assertThat(fixture.host.followUps).singleElement().asString().contains("pending-1");
    }

    private static void stalePendingFreshTaskSkipped() {
        Fixture fixture = leaderFixture();
        fixture.tasks.add(task("pending-1", TaskStatus.PENDING.value(), null, System.currentTimeMillis()));

        join(fixture.staleTaskHandler().checkStalePendingTasks());

        assertThat(fixture.host.allDelivered()).isEmpty();
    }

    private static void stalePendingThrottledAfterFirstNudge() {
        Fixture fixture = leaderFixture();
        StaleTaskHandler handler = fixture.staleTaskHandler();
        fixture.tasks.add(task("pending-1", TaskStatus.PENDING.value(), null, STALE_UPDATED_AT));

        join(handler.checkStalePendingTasks());
        fixture.host.clearDelivered();
        join(handler.checkStalePendingTasks());

        assertThat(fixture.host.allDelivered()).isEmpty();
    }

    private static void stalePendingTeammateSkipsCheck() {
        Fixture fixture = fixture(TeamRole.TEAMMATE, DEV, "temporary");
        fixture.tasks.add(task("pending-1", TaskStatus.PENDING.value(), null, STALE_UPDATED_AT));

        join(fixture.staleTaskHandler().checkStalePendingTasks());

        assertThat(fixture.host.allDelivered()).isEmpty();
    }

    private static void teamCleanedEventShutsDownTeammate() {
        Fixture fixture = fixture(TeamRole.TEAMMATE, DEV, "temporary");

        join(fixture.lifecycleHandler().onCleaned(transport(new TeamCleanedEvent())));

        assertThat(fixture.host.shutdownCalls).hasValue(1);
    }

    private static void teamCleanedEventIgnoredByLeader() {
        Fixture fixture = leaderFixture();

        join(fixture.lifecycleHandler().onCleaned(transport(new TeamCleanedEvent())));

        assertThat(fixture.host.shutdownCalls).hasValue(0);
    }

    private static void shutdownSelfCancelsRunningRoundAndClosesStream() {
        RuntimeStub runtime = new RuntimeStub();
        TeamAgent agent = configuredAgent(TeamRole.TEAMMATE, DEV, "temporary", runtime);
        CompletableFuture<Void> runningTask = new CompletableFuture<>();
        setField(agent.getStreamController(), "agentTask", runningTask);

        join(agent.shutdownSelf());

        assertThat(runtime.abortCalls).hasValue(1);
        assertThat(runningTask).isCancelled();
        assertThat(agent.getStreamController().getRawStreamQueue()).hasSize(1);
    }

    private static void teammateRoundCompletionWakesMailboxAfterInterruptClears() {
        TeamAgent agent = configuredAgent(TeamRole.TEAMMATE, DEV, "temporary", new RuntimeStub());

        join(agent.getCoordination().wakeMailboxIfInterruptCleared());

        assertThat(((EventBus) agent.getCoordinationLoop()).getPendingEventCount()).isEqualTo(1);
    }

    private static void firstIterGateSingleInstanceRegisteredOnDeepAgent() {
        TeamAgent agent = configuredAgent(TeamRole.TEAMMATE, DEV, "temporary", null);
        Object gate = agent.getResources().getFirstIterGate();

        assertThat(gate).isNotNull();
        assertThat(agent.getHarness().findRails(Object.class)).contains(gate);
    }

    private static void streamingSessionIdReadsFromContextvar() {
        TeamAgent agent = configuredAgent(TeamRole.LEADER, LEADER, "temporary", new RuntimeStub());
        AgentTeamsContext.setSessionId("");
        assertThat(agent.getSessionId()).isEmpty();

        AgentTeamsContext.setSessionId("sess-xyz");

        assertThat(agent.getSessionId()).isEqualTo("sess-xyz");
    }

    private static void dispatcherRegistersTeamCompletionHandler() {
        EventDispatcher.TeamCompletionHandler handler = leaderFixture().dispatcher().getTeamCompletion();

        assertThat(handler.getCallbacks().keySet())
                .contains(InnerEventType.POLL_TASK.value(), TeamEvent.TASK_LIST_DRAINED, TeamEvent.TEAM_COMPLETED);
    }

    private static void teamCompletionEmitsOnIdleLeaderWhenComplete() {
        Fixture fixture = leaderFixture();
        fixture.backend.setCompletionSnapshots(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(2, 3)));

        join(fixture.teamCompletionHandler().onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));

        assertThat(fixture.messager.published).hasSize(1);
        assertThat(fixture.messager.published.get(0).getEventType()).isEqualTo(TeamEvent.TEAM_COMPLETED);
    }

    private static void teamCompletionNotReEmittedOnRepeatedTick() {
        Fixture fixture = leaderFixture();
        fixture.backend.setCompletionSnapshots(
                Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)),
                Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)));
        TeamCompletionHandler handler = fixture.teamCompletionHandler();

        join(handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));
        join(handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));

        assertThat(fixture.messager.published).hasSize(1);
    }

    private static void teamCompletionReArmsAfterFallingEdge() {
        Fixture fixture = leaderFixture();
        fixture.backend.setCompletionSnapshots(
                Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)),
                Optional.empty(),
                Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)));
        TeamCompletionHandler handler = fixture.teamCompletionHandler();

        join(handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));
        join(handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));
        join(handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));

        assertThat(fixture.messager.published).hasSize(2);
    }

    private static void teamCompletionNeverEmitsForTeammate() {
        Fixture fixture = fixture(TeamRole.TEAMMATE, DEV, "temporary");
        fixture.backend.setCompletionSnapshots(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)));

        join(fixture.teamCompletionHandler().onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));

        assertThat(fixture.messager.published).isEmpty();
    }

    private static void teamCompletionSkippedWhenRoundInFlight() {
        Fixture fixture = leaderFixture();
        fixture.host.inFlight = true;
        fixture.backend.setCompletionSnapshots(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)));

        join(fixture.teamCompletionHandler().onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));

        assertThat(fixture.backend.completionQueries).hasValue(0);
        assertThat(fixture.messager.published).isEmpty();
    }

    private static void teamCompletionConsumersAcceptTheirEvents() {
        Fixture fixture = leaderFixture();

        join(fixture.teamCompletionHandler().onTaskListDrained(transport(taskListDrained(4))));
        join(fixture.teamCompletionHandler().onTeamCompleted(transport(teamCompleted(2, 4))));
    }

    private static void drainedFiresRegisteredCompletionCallbacks() {
        Fixture fixture = leaderFixture();
        TeamCompletionHandler handler = fixture.teamCompletionHandler();
        AtomicInteger calls = new AtomicInteger();
        handler.registerCompletionCallback(() -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        join(handler.onTaskListDrained(transport(taskListDrained(2))));

        assertThat(calls).hasValue(1);
    }

    private static void drainedCallbackFailureIsIsolated() {
        Fixture fixture = leaderFixture();
        TeamCompletionHandler handler = fixture.teamCompletionHandler();
        AtomicInteger healthyCalls = new AtomicInteger();
        handler.registerCompletionCallback(
                () -> CompletableFuture.failedFuture(new IllegalStateException("boom")));
        handler.registerCompletionCallback(() -> {
            healthyCalls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        join(handler.onTaskListDrained(transport(taskListDrained(1))));

        assertThat(healthyCalls).hasValue(1);
    }

    private static void registerTeamCompletionCallbacksWiresSkillRail() {
        RuntimeStub runtime = new RuntimeStub();
        CompletionRail rail = new CompletionRail();
        runtime.rails.add(rail);

        TeamAgent agent = configuredAgent(TeamRole.LEADER, LEADER, "temporary", runtime);

        assertThat(agent.getCoordination().getDispatcher().getTeamCompletion().getCompletionCallbacks()).contains(rail);
    }

    private static void taskCompletedNudgesLeaderWithAllDoneWhenIdle() {
        Fixture fixture = leaderFixture();
        fixture.tasks.add(task("task-5", TaskStatus.COMPLETED.value(), "member-5", System.currentTimeMillis()));

        join(fixture.taskBoardHandler().onTaskBoardEvent(transport(taskCompleted("task-5"))));

        assertThat(fixture.host.delivered).hasSize(1);
    }

    private static void taskCompletedNudgesLeaderWhenBusy() {
        Fixture fixture = leaderFixture();
        fixture.host.agentRunning = true;
        fixture.tasks.add(task("task-5", TaskStatus.COMPLETED.value(), "member-5", System.currentTimeMillis()));

        join(fixture.taskBoardHandler().onTaskBoardEvent(transport(taskCompleted("task-5"))));

        assertThat(fixture.host.followUps).hasSize(1);
    }

    private static void taskCompletedWithIncompleteTasksNudgesLeaderBoard() {
        Fixture fixture = leaderFixture();
        fixture.tasks.add(task("task-1", TaskStatus.COMPLETED.value(), "member-1", System.currentTimeMillis()));
        fixture.tasks.add(task("task-2", TaskStatus.CLAIMED.value(), "member-2", System.currentTimeMillis()));

        join(fixture.taskBoardHandler().onTaskBoardEvent(transport(taskCompleted("task-1"))));

        assertThat(fixture.host.delivered).singleElement().asString().contains("task-2");
    }

    private static void persistentCompletionClosesLeaderStream() {
        Fixture fixture = fixture(TeamRole.LEADER, LEADER, "persistent");
        fixture.backend.setCompletionSnapshots(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(2, 3)));

        join(fixture.teamCompletionHandler().onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));

        assertThat(fixture.host.completedRounds).containsExactly(new CompletionRound(2, 3));
    }

    private static void temporaryCompletionDoesNotCloseStream() {
        Fixture fixture = leaderFixture();
        fixture.backend.setCompletionSnapshots(Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)));

        join(fixture.teamCompletionHandler().onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));

        assertThat(fixture.host.completedRounds).isEmpty();
        assertThat(fixture.messager.published).hasSize(1);
    }

    private static void persistentCompletionConcludesOncePerRisingEdge() {
        Fixture fixture = fixture(TeamRole.LEADER, LEADER, "persistent");
        fixture.backend.setCompletionSnapshots(
                Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)),
                Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)));
        TeamCompletionHandler handler = fixture.teamCompletionHandler();

        join(handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));
        join(handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));

        assertThat(fixture.host.completedRounds).hasSize(1);
    }

    private static void rearmAllowsCompletionToConcludeAgain() {
        Fixture fixture = fixture(TeamRole.LEADER, LEADER, "persistent");
        fixture.backend.setCompletionSnapshots(
                Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)),
                Optional.of(new TeamCompletionHandler.TeamCompletionSnapshot(1, 1)));
        TeamCompletionHandler handler = fixture.teamCompletionHandler();

        join(handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));
        handler.rearm();
        join(handler.onPollTask(new InnerEventMessage(InnerEventType.POLL_TASK)));

        assertThat(fixture.host.completedRounds).hasSize(2);
    }

    private static Fixture leaderFixture() {
        return fixture(TeamRole.LEADER, LEADER, "temporary");
    }

    private static Fixture humanFixture() {
        Fixture fixture = fixture(TeamRole.HUMAN_AGENT, HUMAN, "temporary");
        fixture.backend.humanAgents.add(HUMAN);
        return fixture;
    }

    private static Fixture fixture(TeamRole role, String memberName, String lifecycle) {
        return new Fixture(role, memberName, lifecycle);
    }

    private static TeamAgent configuredAgent(
            TeamRole role,
            String memberName,
            String lifecycle,
            MemberRuntime runtime) {
        TeamAgent agent = new TeamAgent(new AgentCard(memberName, memberName, "test"));
        agent.configure(spec(lifecycle), context(role, memberName), runtime);
        return agent;
    }

    private static TeamAgentSpec spec(String lifecycle) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(TEAM);
        spec.setLifecycle(lifecycle);
        spec.setAgents(Map.of("leader", new DeepAgentSpec(), "teammate", new DeepAgentSpec()));
        return spec;
    }

    private static TeamRuntimeContext context(TeamRole role, String memberName) {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(role);
        context.setMemberName(memberName);
        context.setPersona("persona");
        context.setTeamSpec(new TeamSpec(TEAM, "Test Team", LEADER));
        return context;
    }

    private static TeamAgentBlueprint blueprint(TeamRole role, String memberName, String lifecycle) {
        return new TeamAgentBlueprint(new AgentCard(memberName, memberName, "test"), spec(lifecycle),
                context(role, memberName), "", "en");
    }

    private static TeamTask task(String id, String status, String assignee, Long updatedAt) {
        return task(id, id, "content for " + id, status, assignee, updatedAt);
    }

    private static TeamTask task(
            String id,
            String title,
            String content,
            String status,
            String assignee,
            Long updatedAt) {
        return new TeamTask(id, TEAM, title, content, status, assignee, updatedAt);
    }

    private static TeamMessage message(
            String id,
            String from,
            String to,
            String content,
            boolean broadcast,
            boolean read,
            Long timestamp) {
        return new TeamMessage(id, TEAM, from, to, content, timestamp, broadcast, read);
    }

    private static TransportEvent transport(com.openjiuwen.agent_teams.schema.BaseEventMessage payload) {
        if (payload.getTeamName() == null) {
            payload.setTeamName(TEAM);
        }
        return new TransportEvent(EventMessage.fromEvent(payload));
    }

    private static TransportEvent memberShutdown(String memberName, boolean force) {
        MemberShutdownEvent payload = new MemberShutdownEvent();
        payload.setTeamName(TEAM);
        payload.setMemberName(memberName);
        payload.setForce(force);
        return transport(payload);
    }

    private static TransportEvent memberStatusChanged(String memberName, String oldStatus, String newStatus) {
        MemberStatusChangedEvent payload = new MemberStatusChangedEvent();
        payload.setTeamName(TEAM);
        payload.setMemberName(memberName);
        payload.setOldStatus(oldStatus);
        payload.setNewStatus(newStatus);
        return transport(payload);
    }

    private static TransportEvent taskClaimed(String memberName, String taskId) {
        TaskClaimedEvent payload = new TaskClaimedEvent();
        payload.setTeamName(TEAM);
        payload.setMemberName(memberName);
        payload.setTaskId(taskId);
        return transport(payload);
    }

    private static TaskCompletedEvent taskCompleted(String taskId) {
        TaskCompletedEvent payload = new TaskCompletedEvent();
        payload.setTeamName(TEAM);
        payload.setTaskId(taskId);
        return payload;
    }

    private static TaskListDrainedEvent taskListDrained(int taskCount) {
        TaskListDrainedEvent payload = new TaskListDrainedEvent();
        payload.setTeamName(TEAM);
        payload.setTaskCount(taskCount);
        return payload;
    }

    private static TeamCompletedEvent teamCompleted(int memberCount, int taskCount) {
        TeamCompletedEvent payload = new TeamCompletedEvent();
        payload.setTeamName(TEAM);
        payload.setMemberCount(memberCount);
        payload.setTaskCount(taskCount);
        return payload;
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @FunctionalInterface
    private interface Scenario {
        void run() throws Exception;
    }

    private static final class Fixture {
        private final HostSpy host = new HostSpy();
        private final PollSpy poll = new PollSpy();
        private final RecordingMessager messager = new RecordingMessager();
        private final BackendStub backend;
        private final TaskManagerStub tasks = new TaskManagerStub();
        private final MessageManagerStub messages = new MessageManagerStub();
        private final TeamAgentBlueprint blueprint;
        private final TeamInfra infra = new TeamInfra();

        private Fixture(TeamRole role, String memberName, String lifecycle) {
            this.blueprint = blueprint(role, memberName, lifecycle);
            this.backend = new BackendStub(role, messager);
            infra.setMessager(messager);
            infra.setTeamBackend(backend);
            infra.setTaskManager(tasks);
            infra.setMessageManager(messages);
        }

        private AgentLifecycleHandler lifecycleHandler() {
            return new AgentLifecycleHandler(host, blueprint, infra, poll);
        }

        private MemberHandler memberHandler() {
            return new MemberHandler(host, blueprint, infra, poll, new LinkedHashMap<>());
        }

        private MessageHandler messageHandler() {
            return new MessageHandler(host, blueprint, infra, poll);
        }

        private TaskBoardHandler taskBoardHandler() {
            return new TaskBoardHandler(host, blueprint, infra, poll);
        }

        private StaleTaskHandler staleTaskHandler() {
            return new StaleTaskHandler(host, blueprint, infra, poll, new LinkedHashMap<>());
        }

        private TeamCompletionHandler teamCompletionHandler() {
            return new TeamCompletionHandler(host, blueprint, infra, poll);
        }

        private EventDispatcher dispatcher() {
            return new EventDispatcher(host, blueprint, infra, poll);
        }
    }

    private static final class HostSpy implements DispatcherHost {
        private boolean agentReady = true;
        private boolean agentRunning;
        private boolean inFlight;
        private boolean pendingInterrupt;
        private final List<String> delivered = new ArrayList<>();
        private final List<String> steered = new ArrayList<>();
        private final List<String> followUps = new ArrayList<>();
        private final List<Object> interruptResumes = new ArrayList<>();
        private final List<CompletionRound> completedRounds = new ArrayList<>();
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private final AtomicInteger shutdownCalls = new AtomicInteger();

        @Override
        public boolean isAgentReady() {
            return agentReady;
        }

        @Override
        public boolean isAgentRunning() {
            return agentRunning;
        }

        @Override
        public boolean hasInFlightRound() {
            return inFlight;
        }

        @Override
        public boolean hasPendingInterrupt() {
            return pendingInterrupt;
        }

        @Override
        public CompletionStage<Void> cancelAgent() {
            cancelCalls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deliverInput(Object content, boolean useSteer) {
            String text = String.valueOf(content);
            if (agentRunning) {
                if (useSteer) {
                    steered.add(text);
                } else {
                    followUps.add(text);
                }
            } else {
                delivered.add(text);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumeInterrupt(Object userInput) {
            interruptResumes.add(userInput);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> shutdownSelf() {
            shutdownCalls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> concludeCompletedRound(int memberCount, int taskCount) {
            completedRounds.add(new CompletionRound(memberCount, taskCount));
            return CompletableFuture.completedFuture(null);
        }

        private List<String> allDelivered() {
            List<String> all = new ArrayList<>();
            all.addAll(delivered);
            all.addAll(steered);
            all.addAll(followUps);
            return all;
        }

        private void clearDelivered() {
            delivered.clear();
            steered.clear();
            followUps.clear();
        }
    }

    private static final class PollSpy implements PollController {
        private final AtomicInteger paused = new AtomicInteger();
        private final AtomicInteger resumed = new AtomicInteger();

        @Override
        public CompletionStage<Void> pausePolls() {
            paused.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumePolls() {
            resumed.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class BackendStub extends ConfiguredTeamBackend
            implements MessageHandler.TeamBackendView, TaskBoardHandler.TeamBackendView, TeamCompletionHandler.TeamBackendView {
        private final Set<String> humanAgents = new LinkedHashSet<>();
        private final Map<String, MessageHandler.HumanAgentInboundCallback> inboundCallbacks = new LinkedHashMap<>();
        private final Queue<Optional<TeamCompletionHandler.TeamCompletionSnapshot>> completionSnapshots = new ArrayDeque<>();
        private final AtomicInteger completionQueries = new AtomicInteger();

        private BackendStub(TeamRole role, Messager messager) {
            super(TEAM, role == TeamRole.LEADER ? LEADER : DEV, role == TeamRole.LEADER, Map.of(), messager,
                    "build", List.of(), null, null, false, false, List.of(), null, null, LEADER);
        }

        @Override
        public CompletionStage<Boolean> isHumanAgent(String memberName) {
            return CompletableFuture.completedFuture(humanAgents.contains(memberName));
        }

        @Override
        public CompletionStage<List<String>> humanAgentNames() {
            return CompletableFuture.completedFuture(new ArrayList<>(humanAgents));
        }

        @Override
        public MessageHandler.HumanAgentInboundCallback getHumanAgentInbound(String memberName) {
            return inboundCallbacks.get(memberName);
        }

        @Override
        public CompletionStage<Optional<TeamCompletionHandler.TeamCompletionSnapshot>> isTeamCompleted() {
            completionQueries.incrementAndGet();
            return CompletableFuture.completedFuture(
                    completionSnapshots.isEmpty() ? Optional.empty() : completionSnapshots.remove());
        }

        @Override
        public String teamName() {
            return TEAM;
        }

        @SafeVarargs
        private final void setCompletionSnapshots(Optional<TeamCompletionHandler.TeamCompletionSnapshot>... snapshots) {
            completionSnapshots.clear();
            completionSnapshots.addAll(List.of(snapshots));
        }
    }

    private static final class TaskManagerStub
            implements TaskBoardHandler.TaskManager, StaleTaskHandler.TaskManager, MemberHandler.TaskManager {
        private final List<TeamTask> tasks = new ArrayList<>();
        private int listCalls;
        private boolean throwOnGetTask;

        private void add(TeamTask task) {
            tasks.add(task);
        }

        @Override
        public CompletionStage<List<TeamTask>> listTasks() {
            listCalls++;
            return CompletableFuture.completedFuture(new ArrayList<>(tasks));
        }

        @Override
        public CompletionStage<Optional<TeamTask>> getTask(String taskId) {
            if (throwOnGetTask) {
                return CompletableFuture.failedFuture(new IllegalStateException("lookup failed"));
            }
            return CompletableFuture.completedFuture(tasks.stream()
                    .filter(task -> taskId.equals(task.getTaskId()))
                    .findFirst());
        }

        @Override
        public CompletionStage<List<TeamTask>> listTasks(String status) {
            listCalls++;
            return CompletableFuture.completedFuture(tasks.stream()
                    .filter(task -> status.equals(task.getStatus()))
                    .toList());
        }

        @Override
        public CompletionStage<List<TeamTask>> getTasksByAssignee(String targetId, String status) {
            return CompletableFuture.completedFuture(tasks.stream()
                    .filter(task -> targetId.equals(task.getAssignee()))
                    .filter(task -> status == null || status.equals(task.getStatus()))
                    .toList());
        }
    }

    private static final class MessageManagerStub
            implements MessageHandler.MessageManager, StaleTaskHandler.MessageManager, MemberHandler.MessageManager {
        private final List<TeamMessage> messages = new ArrayList<>();
        private final List<ReadMark> markedRead = new ArrayList<>();
        private final List<SentMessage> sent = new ArrayList<>();

        private void add(TeamMessage message) {
            messages.add(message);
        }

        @Override
        public CompletionStage<List<TeamMessage>> getMessages(String memberName, boolean unreadOnly) {
            return CompletableFuture.completedFuture(messages.stream()
                    .filter(message -> !Boolean.TRUE.equals(message.getBroadcast()))
                    .filter(message -> memberName.equals(message.getToMemberName()))
                    .filter(message -> !unreadOnly || !Boolean.TRUE.equals(message.getIsRead()))
                    .toList());
        }

        @Override
        public CompletionStage<List<TeamMessage>> getBroadcastMessages(String memberName, boolean unreadOnly) {
            return CompletableFuture.completedFuture(messages.stream()
                    .filter(message -> Boolean.TRUE.equals(message.getBroadcast()))
                    .filter(message -> !unreadOnly || !Boolean.TRUE.equals(message.getIsRead()))
                    .toList());
        }

        @Override
        public CompletionStage<Void> markMessageRead(String messageId, String memberName) {
            markedRead.add(new ReadMark(messageId, memberName));
            for (TeamMessage message : messages) {
                if (messageId.equals(message.getMessageId())) {
                    message.setIsRead(true);
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Optional<TeamMessage>> getMessage(String messageId) {
            return CompletableFuture.completedFuture(messages.stream()
                    .filter(message -> messageId.equals(message.getMessageId()))
                    .findFirst());
        }

        @Override
        public CompletionStage<Void> sendMessage(String content, String targetId) {
            sent.add(new SentMessage(content, targetId));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingMessager implements Messager {
        private final List<EventMessage> published = new ArrayList<>();

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
            published.add(message);
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

    private static final class RuntimeStub implements MemberRuntime {
        private final AtomicInteger abortCalls = new AtomicInteger();
        private final List<Object> rails = new ArrayList<>();

        @Override
        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
            return List.of().iterator();
        }

        @Override
        public CompletionStage<Void> steer(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> followUp(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> abort() {
            abortCalls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void initCwdForRound() {
        }

        @Override
        public boolean hasPendingInterrupt() {
            return false;
        }

        @Override
        public boolean isPendingInterruptResumeValid(Object userInput) {
            return true;
        }

        @Override
        public List<Object> findRails(Class<?> railType) {
            return rails;
        }

        @Override
        public CompletionStage<Void> registerRail(Object rail) {
            rails.add(rail);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterRail(Object rail) {
            rails.remove(rail);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void registerMemberTools(Object memoryManager) {
        }

        @Override
        public CompletionStage<Void> injectMemberMemory(Object memoryManager, String query) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void runAgentCustomizer(AgentCustomizer customizer) {
        }

        @Override
        public Object workspace() {
            return null;
        }

        @Override
        public Object sysOperation() {
            return null;
        }
    }

    private static final class CompletionRail implements TeamAgent.TeamCompletionCallback {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletionStage<Void> notifyTeamCompleted() {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
    }

    private record ReadMark(String messageId, String memberName) {
    }

    private record SentMessage(String content, String target) {
    }

    private record CompletionRound(int memberCount, int taskCount) {
    }
}
