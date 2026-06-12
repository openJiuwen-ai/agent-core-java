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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link StreamController}.
 *
 * <p>Mirrors Python's {@code StreamController} in
 * {@code openjiuwen/agent_teams/agent/stream_controller.py}.</p>
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

        assertThat(runtime.queries).containsExactly("initial", "鍒氭墠鏈夊紓甯哥姸鍐碉紝缁х画鎵ц");
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

    private static TeamAgentBlueprint blueprint() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setAgents(Map.of("leader", new DeepAgentSpec()));
        spec.setTeamName("team");
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.LEADER);
        ctx.setMemberName("leader");
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

    private static final class RuntimeStub implements MemberRuntime {
        private final List<List<Object>> streams = new ArrayList<>();
        private final List<Object> queries = new ArrayList<>();
        private int index;
        private boolean aborted;

        @SafeVarargs
        private RuntimeStub(List<Object>... streams) {
            this.streams.addAll(List.of(streams));
        }

        @Override
        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
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
