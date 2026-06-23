/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.PrivateAgentResources;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.StreamController.TeamOutputChunk;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link StreamController}.
 *
 * <p>Mirrors Python's {@code StreamController} in
 * {@code openjiuwen/agent_teams/agent/stream_controller.py}.</p>
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_stream_controller} in
 * {@code tests/unit_tests/agent_teams/test_stream_controller.py}.</p>
 */
class StreamControllerTest {

    @Test
    void tagChunkUpgradesPlainOutputWithMemberAndRole() {
        StreamController controller = controller(new RuntimeStub(List.of()), new TeamAgentState());
        OutputSchema chunk = new OutputSchema("message", 0, Map.of("text", "hello"));

        Object tagged = controller.tagChunk(chunk);

        assertThat(tagged).isInstanceOf(TeamOutputChunk.class);
        TeamOutputChunk teamChunk = (TeamOutputChunk) tagged;
        assertThat(teamChunk.getSourceMember()).isEqualTo("leader");
        assertThat(teamChunk.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(teamChunk.getPayload()).isEqualTo(Map.of("text", "hello"));
    }

    @Test
    void streamOneRoundQueuesTaggedChunksAndDetachesFailingObservers() {
        OutputSchema chunk = new OutputSchema("message", 0, Map.of("text", "hello"));
        StreamController controller = controller(new RuntimeStub(List.of(chunk), List.of(chunk)), new TeamAgentState());
        List<Object> observed = new ArrayList<>();
        controller.addChunkObserver(item -> {
            observed.add(item);
            return CompletableFuture.completedFuture(null);
        });
        SpawnManager.ChunkObserver failing = item -> {
            throw new IllegalStateException("observer failed");
        };
        controller.addChunkObserver(failing);

        controller.streamOneRound("query").toCompletableFuture().join();

        assertThat(controller.getRawStreamQueue()).hasSize(1);
        assertThat(controller.getRawStreamQueue().peek()).isInstanceOf(TeamOutputChunk.class);
        assertThat(observed).hasSize(1);
        controller.streamOneRound("query-2").toCompletableFuture().join();
        assertThat(observed).hasSize(2);
    }

    @Test
    void retryingStreamRetriesDetectedTransientTaskFailure() {
        OutputSchema failed = new OutputSchema(
                "message",
                0,
                Map.of("type", "task_failed", "data", List.of(Map.of("text", "[181001] transient")))
        );
        OutputSchema success = new OutputSchema("message", 1, Map.of("text", "ok"));
        RuntimeStub runtime = new RuntimeStub(List.of(failed), List.of(success));
        StreamController controller = controller(runtime, new TeamAgentState());

        controller.runRetryingStream("initial").toCompletableFuture().join();

        assertThat(runtime.queries).containsExactly("initial", "刚才有异常状况，继续执行");
        assertThat(controller.getRawStreamQueue()).hasSize(1);
    }

    @Test
    void runOneRoundUpdatesStatusExecutionAndRequestsCompletionPoll() {
        OutputSchema success = new OutputSchema("message", 0, Map.of("text", "ok"));
        TeamAgentState state = new TeamAgentState();
        List<MemberStatus> statuses = new ArrayList<>();
        List<ExecutionStatus> executions = new ArrayList<>();
        int[] completionPolls = {0};
        StreamController controller = controller(
                new RuntimeStub(List.of(success)),
                state,
                statuses,
                executions,
                () -> CompletableFuture.completedFuture(null),
                () -> {
                    completionPolls[0]++;
                    return CompletableFuture.completedFuture(null);
                }
        );

        controller.runOneRound("query").toCompletableFuture().join();

        assertThat(statuses).containsExactly(MemberStatus.READY, MemberStatus.BUSY, MemberStatus.READY);
        assertThat(executions).contains(
                ExecutionStatus.STARTING,
                ExecutionStatus.RUNNING,
                ExecutionStatus.COMPLETING,
                ExecutionStatus.COMPLETED,
                ExecutionStatus.IDLE
        );
        assertThat(completionPolls[0]).isEqualTo(1);
    }

    @Test
    void teamCleanedClosesStreamAfterRound() {
        TeamAgentState state = new TeamAgentState();
        state.setTeamCleaned(true);
        StreamController controller = controller(new RuntimeStub(List.of()), state);

        controller.runOneRound("query").toCompletableFuture().join();

        assertThat(controller.getRawStreamQueue()).containsExactly((Object) null);
    }

    @Test
    void drainClearsPendingInputsAndCancelsRunningTask() {
        StreamController controller = controller(new RuntimeStub(List.of()), new TeamAgentState());
        CompletableFuture<Void> task = new CompletableFuture<>();
        controller.getPendingInputs().add("input");
        controller.getPendingInterruptResumes().add("resume");

        setAgentTask(controller, task);
        controller.drainAgentTask().toCompletableFuture().join();

        assertThat(controller.getPendingInputs()).isEmpty();
        assertThat(controller.getPendingInterruptResumes()).isEmpty();
        assertThat(task.isCancelled()).isTrue();
    }

    @Test
    void cooperativeCancelNoOpWhenNoTask() {
        RuntimeStub runtime = new RuntimeStub(List.of());
        StreamController controller = controller(runtime, new TeamAgentState());

        controller.cooperativeCancel().toCompletableFuture().join();

        assertThat(runtime.aborted).isFalse();
    }

    @Test
    void cooperativeCancelFinishesWhenTaskRespondsToAbort() {
        RuntimeStub runtime = new RuntimeStub(List.of());
        CompletableFuture<Void> abortGate = new CompletableFuture<>();
        runtime.abortStage = abortGate;
        StreamController controller = controller(runtime, new TeamAgentState());
        CompletableFuture<Void> task = new CompletableFuture<>();
        setAgentTask(controller, task);

        CompletableFuture<Void> cancel = controller.cooperativeCancel().toCompletableFuture();
        assertThat(runtime.aborted).isTrue();
        assertThat(cancel).isNotDone();

        task.complete(null);
        abortGate.complete(null);
        cancel.join();

        assertThat(task.isDone()).isTrue();
        assertThat(task.isCancelled()).isFalse();
    }

    @Test
    void cooperativeCancelFallsBackToHardCancelWhenTaskStillRuns() {
        RuntimeStub runtime = new RuntimeStub(List.of());
        StreamController controller = controller(runtime, new TeamAgentState());
        CompletableFuture<Void> task = new CompletableFuture<>();
        setAgentTask(controller, task);

        controller.cooperativeCancel().toCompletableFuture().join();

        assertThat(runtime.aborted).isTrue();
        assertThat(task.isCancelled()).isTrue();
    }

    @Test
    void cancelAgentRecordsExecutionTransitions() {
        RuntimeStub runtime = new RuntimeStub(List.of());
        List<ExecutionStatus> executions = new ArrayList<>();
        StreamController controller = controller(
                runtime,
                new TeamAgentState(),
                new ArrayList<>(),
                executions,
                () -> CompletableFuture.completedFuture(null),
                null
        );
        setAgentTask(controller, new CompletableFuture<>());

        controller.cancelAgent().toCompletableFuture().join();

        assertThat(executions).containsExactly(ExecutionStatus.CANCEL_REQUESTED, ExecutionStatus.CANCELLING);
        assertThat(runtime.aborted).isTrue();
    }

    @Test
    void cancelAgentNoInFlightRoundIsSilent() {
        RuntimeStub runtime = new RuntimeStub(List.of());
        List<ExecutionStatus> executions = new ArrayList<>();
        StreamController controller = controller(
                runtime,
                new TeamAgentState(),
                new ArrayList<>(),
                executions,
                () -> CompletableFuture.completedFuture(null),
                null
        );

        controller.cancelAgent().toCompletableFuture().join();

        assertThat(executions).isEmpty();
        assertThat(runtime.aborted).isFalse();
    }

    @Test
    void drainAgentTaskNoInFlightRoundIsSilent() {
        RuntimeStub runtime = new RuntimeStub(List.of());
        List<ExecutionStatus> executions = new ArrayList<>();
        StreamController controller = controller(
                runtime,
                new TeamAgentState(),
                new ArrayList<>(),
                executions,
                () -> CompletableFuture.completedFuture(null),
                null
        );
        controller.getPendingInputs().add("queued");
        controller.getPendingInterruptResumes().add("resume");

        controller.drainAgentTask().toCompletableFuture().join();

        assertThat(controller.getPendingInputs()).isEmpty();
        assertThat(controller.getPendingInterruptResumes()).isEmpty();
        assertThat(executions).isEmpty();
        assertThat(runtime.aborted).isFalse();
    }

    @Test
    void executeRoundEmitsCancelledOnCooperativeAbortSuccess() {
        RuntimeStub runtime = new RuntimeStub(List.of());
        List<ExecutionStatus> executions = new ArrayList<>();
        StreamController controller = controller(
                runtime,
                new TeamAgentState(),
                new ArrayList<>(),
                executions,
                () -> CompletableFuture.completedFuture(null),
                null
        );
        setCancelRequested(controller, true);

        controller.executeRound("query").toCompletableFuture().join();

        assertThat(executions).contains(ExecutionStatus.CANCELLED);
        assertThat(executions).doesNotContain(ExecutionStatus.COMPLETING, ExecutionStatus.COMPLETED);
    }

    @Test
    void executeRoundEmitsCompletedOnNormalFinish() {
        RuntimeStub runtime = new RuntimeStub(List.of());
        List<ExecutionStatus> executions = new ArrayList<>();
        StreamController controller = controller(
                runtime,
                new TeamAgentState(),
                new ArrayList<>(),
                executions,
                () -> CompletableFuture.completedFuture(null),
                null
        );

        controller.executeRound("query").toCompletableFuture().join();

        assertThat(executions).contains(ExecutionStatus.COMPLETING, ExecutionStatus.COMPLETED);
        assertThat(executions).doesNotContain(ExecutionStatus.CANCELLED);
    }

    @Test
    void runOneRoundUsesControllerMemberIdentityForTaggedChunks() {
        OutputSchema raw = new OutputSchema("message", 0, Map.of("step", 1));
        StreamController controller = controllerWith("human-member-beta", TeamRole.LEADER, new RuntimeStub(List.of(raw)));

        controller.runOneRound("hi").toCompletableFuture().join();

        Object item = controller.getRawStreamQueue().poll();
        assertThat(item).isInstanceOf(TeamOutputChunk.class);
        TeamOutputChunk chunk = (TeamOutputChunk) item;
        assertThat(chunk.getSourceMember()).isEqualTo("human-member-beta");
        assertThat(chunk.getRole()).isEqualTo(TeamRole.LEADER);
    }

    @Test
    void tagChunkPassesThroughTeamOutputWithMatchingIdentity() {
        StreamController controller = controller(new RuntimeStub(List.of()), new TeamAgentState());
        TeamOutputChunk preTagged = new TeamOutputChunk("message", 0, Map.of(), "leader", TeamRole.LEADER);

        Object tagged = controller.tagChunk(preTagged);

        assertThat(tagged).isSameAs(preTagged);
    }

    @Test
    void tagChunkRewritesTeamOutputWithMismatchedMember() {
        StreamController controller = controller(new RuntimeStub(List.of()), new TeamAgentState());
        TeamOutputChunk preTagged = new TeamOutputChunk("message", 0, Map.of(), "other", TeamRole.TEAMMATE);

        Object tagged = controller.tagChunk(preTagged);

        assertThat(tagged).isInstanceOf(TeamOutputChunk.class);
        TeamOutputChunk rewritten = (TeamOutputChunk) tagged;
        assertThat(rewritten).isNotSameAs(preTagged);
        assertThat(rewritten.getSourceMember()).isEqualTo("leader");
        assertThat(rewritten.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(preTagged.getSourceMember()).isEqualTo("other");
        assertThat(preTagged.getRole()).isEqualTo(TeamRole.TEAMMATE);
    }

    @Test
    void tagChunkPassesThroughNonOutputSchema() {
        StreamController controller = controller(new RuntimeStub(List.of()), new TeamAgentState());
        Object custom = Map.of("type", "custom", "payload", Map.of("x", 1));

        assertThat(controller.tagChunk(custom)).isSameAs(custom);
    }

    @Test
    void streamOneRoundTagsAndFansOutToObservers() {
        OutputSchema first = new OutputSchema("message", 0, Map.of("step", 1));
        OutputSchema second = new OutputSchema("message", 1, Map.of("step", 2));
        StreamController controller = controller(new RuntimeStub(List.of(first, second)), new TeamAgentState());
        List<Object> observed = new ArrayList<>();
        controller.addChunkObserver(item -> {
            observed.add(item);
            return CompletableFuture.completedFuture(null);
        });

        controller.streamOneRound("hi").toCompletableFuture().join();

        assertThat(controller.getRawStreamQueue()).hasSize(2);
        assertThat(controller.getRawStreamQueue()).allMatch(TeamOutputChunk.class::isInstance);
        assertThat(observed).containsExactlyElementsOf(controller.getRawStreamQueue());
    }

    @Test
    void observerExceptionAutoDetachesAndDoesNotBlockStream() {
        OutputSchema first = new OutputSchema("message", 0, Map.of("step", 1));
        OutputSchema second = new OutputSchema("message", 1, Map.of("step", 2));
        StreamController controller = controller(new RuntimeStub(List.of(first, second)), new TeamAgentState());
        List<Integer> badCalls = new ArrayList<>();
        List<Object> goodCalls = new ArrayList<>();
        controller.addChunkObserver(item -> {
            badCalls.add(((OutputSchema) item).getIndex());
            throw new IllegalStateException("boom");
        });
        controller.addChunkObserver(item -> {
            goodCalls.add(item);
            return CompletableFuture.completedFuture(null);
        });

        controller.streamOneRound("hi").toCompletableFuture().join();

        assertThat(badCalls).containsExactly(0);
        assertThat(goodCalls).hasSize(2);
        assertThat(controller.getRawStreamQueue()).hasSize(2);
    }

    @Test
    void removeChunkObserverIsIdempotent() {
        OutputSchema chunk = new OutputSchema("message", 0, Map.of("step", 1));
        StreamController controller = controller(new RuntimeStub(List.of(chunk)), new TeamAgentState());
        List<Object> calls = new ArrayList<>();
        SpawnManager.ChunkObserver observer = item -> {
            calls.add(item);
            return CompletableFuture.completedFuture(null);
        };

        controller.removeChunkObserver(observer);
        controller.addChunkObserver(observer);
        controller.removeChunkObserver(observer);
        controller.removeChunkObserver(observer);
        controller.streamOneRound("hi").toCompletableFuture().join();

        assertThat(calls).isEmpty();
    }

    @Test
    void teammateChunksReachLeaderQueueViaForwardObserver() {
        OutputSchema first = new OutputSchema("message", 0, Map.of("step", 1));
        OutputSchema second = new OutputSchema("message", 1, Map.of("step", 2));
        StreamController leader = controllerWith("leader_m", TeamRole.LEADER, new RuntimeStub(List.of()));
        StreamController teammate = controllerWith("teammate_m", TeamRole.TEAMMATE, new RuntimeStub(List.of(first, second)));
        teammate.addChunkObserver(item -> {
            leader.getRawStreamQueue().offer(item);
            return CompletableFuture.completedFuture(null);
        });

        teammate.streamOneRound("any-query").toCompletableFuture().join();

        assertThat(leader.getRawStreamQueue()).hasSize(2);
        assertThat(leader.getRawStreamQueue()).allSatisfy(item -> {
            assertThat(item).isInstanceOf(TeamOutputChunk.class);
            TeamOutputChunk chunk = (TeamOutputChunk) item;
            assertThat(chunk.getSourceMember()).isEqualTo("teammate_m");
            assertThat(chunk.getRole()).isEqualTo(TeamRole.TEAMMATE);
        });
    }

    @Test
    void forwardObserverDropsWhenLeaderQueueUnset() {
        OutputSchema chunk = new OutputSchema("message", 0, Map.of("step", 1));
        StreamController leader = controllerWith("leader_m", TeamRole.LEADER, new RuntimeStub(List.of()));
        StreamController teammate = controllerWith("teammate_m", TeamRole.TEAMMATE, new RuntimeStub(List.of(chunk)));
        setStreamQueue(leader, null);
        List<Object> forwarded = new ArrayList<>();
        teammate.addChunkObserver(item -> {
            Queue<Object> leaderQueue = leader.getRawStreamQueue();
            if (leaderQueue == null) {
                return CompletableFuture.completedFuture(null);
            }
            forwarded.add(item);
            leaderQueue.offer(item);
            return CompletableFuture.completedFuture(null);
        });

        teammate.streamOneRound("any-query").toCompletableFuture().join();

        assertThat(forwarded).isEmpty();
        assertThat(teammate.getRawStreamQueue()).hasSize(1);
    }

    @Test
    void roundEndClosesStreamWhenTeamCleaned() {
        TeamAgentState state = new TeamAgentState();
        state.setTeamCleaned(true);
        StreamController controller = controller(new RuntimeStub(List.of()), state);

        controller.runOneRound("hi").toCompletableFuture().join();

        assertThat(controller.getRawStreamQueue()).containsExactly((Object) null);
    }

    @Test
    void roundEndNoCloseWhenNotCleaned() {
        StreamController controller = controller(new RuntimeStub(List.of()), new TeamAgentState());

        controller.runOneRound("hi").toCompletableFuture().join();

        assertThat(controller.getRawStreamQueue()).isEmpty();
    }

    @Test
    void teamCleanedTakesPriorityOverPendingInputs() {
        TeamAgentState state = new TeamAgentState();
        state.setTeamCleaned(true);
        StreamController controller = controller(new RuntimeStub(List.of()), state);
        controller.getPendingInputs().add("queued-after-clean");

        controller.runOneRound("hi").toCompletableFuture().join();

        assertThat(controller.getRawStreamQueue()).containsExactly((Object) null);
        assertThat(controller.getPendingInputs()).containsExactly("queued-after-clean");
    }

    @Test
    void teamCleanedClosesEvenWhenCancelRequested() {
        TeamAgentState state = new TeamAgentState();
        state.setTeamCleaned(true);
        RuntimeStub runtime = new RuntimeStub(List.of());
        StreamController controller = controller(runtime, state);
        runtime.onRunStreaming = () -> setCancelRequested(controller, true);

        controller.runOneRound("hi").toCompletableFuture().join();

        assertThat(controller.getRawStreamQueue()).containsExactly((Object) null);
    }

    @Test
    void emitCompletionAndCloseMarkerPrecedesSentinel() {
        StreamController controller = controller(new RuntimeStub(List.of()), new TeamAgentState());

        controller.emitCompletionAndClose(2, 3);

        Object marker = controller.getRawStreamQueue().poll();
        assertThat(marker).isInstanceOf(TeamOutputChunk.class);
        TeamOutputChunk chunk = (TeamOutputChunk) marker;
        assertThat(chunk.getPayload()).isEqualTo(Map.of("event_type", "team.completed", "member_count", 2, "task_count", 3));
        assertThat(chunk.getSourceMember()).isEqualTo("leader");
        assertThat(chunk.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(controller.getRawStreamQueue().poll()).isNull();
    }

    @Test
    void emitCompletionAndCloseNoopWithoutQueue() {
        StreamController controller = controller(new RuntimeStub(List.of()), new TeamAgentState());
        setStreamQueue(controller, null);

        controller.emitCompletionAndClose(1, 1);

        assertThat(controller.getRawStreamQueue()).isNull();
    }

    private static StreamController controller(MemberRuntime runtime, TeamAgentState state) {
        return controller(
                runtime,
                state,
                new ArrayList<>(),
                new ArrayList<>(),
                () -> CompletableFuture.completedFuture(null),
                null
        );
    }

    private static StreamController controller(
            MemberRuntime runtime,
            TeamAgentState state,
            List<MemberStatus> statuses,
            List<ExecutionStatus> executions,
            java.util.function.Supplier<CompletionStage<Void>> wakeMailbox,
            java.util.function.Supplier<CompletionStage<Void>> completionPoll
    ) {
        PrivateAgentResources resources = new PrivateAgentResources();
        resources.setHarness(runtime);
        return new StreamController(
                StreamControllerTest::blueprint,
                state,
                resources,
                status -> {
                    statuses.add(status);
                    return CompletableFuture.completedFuture(null);
                },
                status -> {
                    executions.add(status);
                    return CompletableFuture.completedFuture(null);
                },
                wakeMailbox,
                completionPoll
        );
    }

    private static StreamController controllerWith(String memberName, TeamRole role, MemberRuntime runtime) {
        PrivateAgentResources resources = new PrivateAgentResources();
        resources.setHarness(runtime);
        return new StreamController(
                () -> blueprint(memberName, role),
                new TeamAgentState(),
                resources,
                status -> CompletableFuture.completedFuture(null),
                status -> CompletableFuture.completedFuture(null)
        );
    }

    private static TeamAgentBlueprint blueprint() {
        return blueprint("leader", TeamRole.LEADER);
    }

    private static TeamAgentBlueprint blueprint(String memberName, TeamRole role) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setAgents(Map.of(memberName, new DeepAgentSpec()));
        spec.setTeamName("team");
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(role);
        ctx.setMemberName(memberName);
        ctx.setTeamSpec(new TeamSpec("team", "Team", "leader"));
        return new TeamAgentBlueprint(new AgentCard("card", "card", "desc"), spec, ctx, "", "en");
    }

    private static void setAgentTask(StreamController controller, CompletableFuture<Void> task) {
        try {
            java.lang.reflect.Field field = StreamController.class.getDeclaredField("agentTask");
            field.setAccessible(true);
            field.set(controller, task);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setCancelRequested(StreamController controller, boolean cancelRequested) {
        try {
            java.lang.reflect.Field field = StreamController.class.getDeclaredField("cancelRequested");
            field.setAccessible(true);
            field.set(controller, cancelRequested);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setStreamQueue(StreamController controller, Queue<Object> queue) {
        try {
            java.lang.reflect.Field field = StreamController.class.getDeclaredField("streamQueue");
            field.setAccessible(true);
            field.set(controller, queue);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class RuntimeStub implements MemberRuntime {
        private final List<List<Object>> streams = new ArrayList<>();
        private final List<Object> queries = new ArrayList<>();
        private CompletionStage<Void> abortStage = CompletableFuture.completedFuture(null);
        private Runnable onRunStreaming = () -> {
        };
        private int index;
        private boolean aborted;

        @SafeVarargs
        private RuntimeStub(List<Object>... streams) {
            this.streams.addAll(List.of(streams));
        }

        @Override
        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
            onRunStreaming.run();
            queries.add(inputs.get("query"));
            List<Object> stream = index < streams.size() ? streams.get(index) : List.of();
            index++;
            return stream.iterator();
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
            aborted = true;
            return abortStage;
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
            return "resume".equals(userInput);
        }

        @Override
        public List<Object> findRails(Class<?> railType) {
            return List.of();
        }

        @Override
        public CompletionStage<Void> registerRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterRail(Object rail) {
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
}
