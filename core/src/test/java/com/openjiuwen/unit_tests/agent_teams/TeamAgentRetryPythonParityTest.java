/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.PrivateAgentResources;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.AgentCustomizer;
import com.openjiuwen.agent_teams.agent.MemberRuntime;
import com.openjiuwen.agent_teams.agent.StreamController;
import com.openjiuwen.agent_teams.agent.TeamAgentState;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Missing-test parity coverage for StreamController task-failed retry handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_team_agent_retry} in
 * {@code tests/unit_tests/agent_teams/test_team_agent_retry.py}.</p>
 */
class TeamAgentRetryPythonParityTest {

    private static final int MAX_RETRY_ATTEMPTS = 10;
    private static final String RETRY_QUERY = "刚才有异常状况，继续执行";

    @Test
    void testRetryOn181001ThenSucceed() {
        RuntimeStub runtime = new RuntimeStub(
                List.of(failedChunk(181001, "model call failed, reason: timeout")),
                List.of(failedChunk(181001, "model call failed, reason: timeout")),
                List.of(failedChunk(181001, "model call failed, reason: timeout")),
                List.of(answerChunk("final answer"))
        );
        List<ExecutionStatus> executions = new ArrayList<>();
        StreamController controller = controller(runtime, executions);

        controller.executeRound("initial query").toCompletableFuture().join();

        assertThat(runtime.queries).containsExactly("initial query", RETRY_QUERY, RETRY_QUERY, RETRY_QUERY);
        assertThat(controller.getRawStreamQueue()).hasSize(1);
        Object chunk = controller.getRawStreamQueue().peek();
        assertThat(chunk).isInstanceOf(StreamController.TeamOutputChunk.class);
        StreamController.TeamOutputChunk output = (StreamController.TeamOutputChunk) chunk;
        assertThat(output.getType()).isEqualTo("answer");
        assertThat(output.getPayload()).isEqualTo(Map.of("output", "final answer", "result_type", "answer"));
        assertThat(executions).contains(ExecutionStatus.COMPLETED);
        assertThat(executions).doesNotContain(ExecutionStatus.FAILED);
    }

    @Test
    void testRetriesExhaustedRaises() {
        RuntimeStub runtime = new RuntimeStub(repeatedFailures(MAX_RETRY_ATTEMPTS + 1, 181001));
        List<ExecutionStatus> executions = new ArrayList<>();
        StreamController controller = controller(runtime, executions);

        assertThatThrownBy(() -> controller.executeRound("initial query").toCompletableFuture().join())
                .satisfies(error -> {
                    BaseError baseError = unwrapBaseError(error);
                    assertThat(baseError.getStatus()).isEqualTo(StatusCode.AGENT_TEAM_EXECUTION_ERROR);
                    assertThat(baseError.toString()).contains("181001");
                });

        assertThat(runtime.queries).hasSize(MAX_RETRY_ATTEMPTS + 1);
        assertThat(runtime.queries.get(0)).isEqualTo("initial query");
        assertThat(runtime.queries.subList(1, runtime.queries.size())).containsOnly(RETRY_QUERY);
        assertThat(executions).contains(ExecutionStatus.FAILED);
        assertThat(executions).doesNotContain(ExecutionStatus.COMPLETED);
    }

    @Test
    void testNonRetryableCodeRaisesImmediately() {
        RuntimeStub runtime = new RuntimeStub(List.of(failedChunk(182012, "tool execution error, card=X, reason=Y")));
        List<ExecutionStatus> executions = new ArrayList<>();
        StreamController controller = controller(runtime, executions);

        assertThatThrownBy(() -> controller.executeRound("initial query").toCompletableFuture().join())
                .satisfies(error -> assertThat(unwrapBaseError(error).toString()).contains("182012"));

        assertThat(runtime.queries).containsExactly("initial query");
        assertThat(executions).contains(ExecutionStatus.FAILED);
    }

    @Test
    void testMissingCodePrefixIsNonRetryable() {
        RuntimeStub runtime = new RuntimeStub(List.of(failedChunkRaw("unexpected error without code")));
        StreamController controller = controller(runtime, new ArrayList<>());

        assertThatThrownBy(() -> controller.executeRound("initial query").toCompletableFuture().join())
                .satisfies(error -> assertThat(unwrapBaseError(error).toString()).contains("last error code=null"));

        assertThat(runtime.queries).containsExactly("initial query");
    }

    @Test
    void testTrailingFramesAfterErrorAreSwallowed() {
        RuntimeStub runtime = new RuntimeStub(
                List.of(
                        failedChunk(181001, "model call failed, reason: boom"),
                        answerChunk("should NOT reach downstream"),
                        answerChunk("also should NOT reach downstream")
                ),
                List.of(answerChunk("final"))
        );
        StreamController controller = controller(runtime, new ArrayList<>());

        controller.executeRound("initial query").toCompletableFuture().join();

        assertThat(runtime.queries).containsExactly("initial query", RETRY_QUERY);
        assertThat(controller.getRawStreamQueue()).hasSize(1);
        StreamController.TeamOutputChunk output = (StreamController.TeamOutputChunk) controller.getRawStreamQueue().peek();
        assertThat(output.getPayload()).isEqualTo(Map.of("output", "final", "result_type", "answer"));
    }

    @Test
    void testDetectTaskFailedParsesCodeAndText() {
        StreamController.StreamFailure result = detectFailureFrom(
                failedChunk(181001, "model call failed, reason: timeout"));

        assertThat(result).isNotNull();
        assertThat(result.errorCode()).isEqualTo(181001);
        assertThat(result.errorText()).isEqualTo("[181001] model call failed, reason: timeout");
    }

    @Test
    void testDetectTaskFailedReturnsNoneForNormalChunk() {
        StreamController.StreamFailure result = detectFailureFrom(answerChunk("hello"));

        assertThat(result).isNull();
    }

    @Test
    void testDetectTaskFailedNoneCodeWhenNoPrefix() {
        StreamController.StreamFailure result = detectFailureFrom(failedChunkRaw("no prefix here"));

        assertThat(result).isNotNull();
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorText()).isEqualTo("no prefix here");
    }

    @Test
    void testDetectTaskFailedHandlesEmptyData() {
        StreamController.StreamFailure result = detectFailureFrom(emptyFailedChunk());

        assertThat(result).isEqualTo(new StreamController.StreamFailure(null, ""));
    }

    private static StreamController controller(RuntimeStub runtime, List<ExecutionStatus> executions) {
        PrivateAgentResources resources = new PrivateAgentResources();
        resources.setHarness(runtime);
        return new StreamController(
                TeamAgentRetryPythonParityTest::blueprint,
                new TeamAgentState(),
                resources,
                status -> CompletableFuture.completedFuture(null),
                status -> {
                    executions.add(status);
                    return CompletableFuture.completedFuture(null);
                }
        );
    }

    private static StreamController.StreamFailure detectFailureFrom(Object chunk) {
        StreamController controller = controller(new RuntimeStub(List.of(chunk)), new ArrayList<>());
        return controller.streamOneRound("query").toCompletableFuture().join();
    }

    private static TeamAgentBlueprint blueprint() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("team");
        spec.setAgents(Map.of("stub", new DeepAgentSpec()));
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.LEADER);
        ctx.setMemberName("stub");
        ctx.setTeamSpec(new TeamSpec("team", "Team", "stub"));
        return new TeamAgentBlueprint(new AgentCard("card", "card", "desc"), spec, ctx, "", "en");
    }

    private static OutputSchema failedChunk(int code, String message) {
        return new OutputSchema(
                "message",
                0,
                Map.of(
                        "type", "task_failed",
                        "data", List.of(Map.of("text", "[" + code + "] " + message)),
                        "metadata", Map.of("task_id", "t1")
                )
        );
    }

    private static OutputSchema failedChunkRaw(String text) {
        return new OutputSchema(
                "message",
                0,
                Map.of(
                        "type", "task_failed",
                        "data", List.of(Map.of("text", text)),
                        "metadata", Map.of("task_id", "t1")
                )
        );
    }

    private static OutputSchema emptyFailedChunk() {
        return new OutputSchema(
                "message",
                0,
                Map.of("type", "task_failed", "data", List.of(), "metadata", Map.of())
        );
    }

    private static OutputSchema answerChunk(String text) {
        return new OutputSchema("answer", 0, Map.of("output", text, "result_type", "answer"));
    }

    @SuppressWarnings("unchecked")
    private static List<Object>[] repeatedFailures(int count, int code) {
        List<Object>[] rounds = new List[count];
        for (int index = 0; index < count; index++) {
            rounds[index] = List.of(failedChunk(code, "timeout #" + index));
        }
        return rounds;
    }

    private static BaseError unwrapBaseError(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        assertThat(current).isInstanceOf(BaseError.class);
        return (BaseError) current;
    }

    private static final class RuntimeStub implements MemberRuntime {
        private final List<List<Object>> streams = new ArrayList<>();
        private final List<Object> queries = new ArrayList<>();
        private int index;

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
