
package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agentteams.schema.status.ExecutionStatus;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptEntry;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Tag("agent-teams-stream-slice")
class StreamControllerCompatibilityTest {
    @Test
    void detectTaskFailedShouldParseCodeAndText() {
        StreamController.TaskFailure failure =
            StreamController.detectTaskFailed(failedChunk(181001, "model call failed, reason: timeout"));

        assertThat(failure).isNotNull();
        assertThat(failure.code()).isEqualTo(181001);
        assertThat(failure.text()).isEqualTo("[181001] model call failed, reason: timeout");
        assertThat(StreamController.detectTaskFailed(answerChunk("ok"))).isNull();
    }

    @Test
    void executeRoundShouldRetryOn181001AndOnlyQueueFinalChunk() {
        List<ExecutionStatus> executionLog = new ArrayList<>();
        List<Map<String, Object>> callInputs = new ArrayList<>();
        StreamController controller = controller(ignored -> {
        }, executionLog::add, null,
                scriptedExecutor(callInputs,
                        List.of(List.of(failedChunk(181001, "timeout #1")), List.of(failedChunk(181001, "timeout #2")),
                                List.of(failedChunk(181001, "timeout #3")), List.of(answerChunk("final answer")))));

        controller.executeRound("initial query");

        assertThat(callInputs).hasSize(4);
        assertThat(callInputs.get(0)).containsEntry("query", "initial query");
        assertThat(callInputs.subList(1, callInputs.size()))
                .allSatisfy(inputs -> assertThat(inputs).containsEntry("query", StreamController.RETRY_QUERY));
        assertThat(drainQueue(controller.getStreamQueue())).singleElement().isInstanceOf(OutputSchema.class)
                .extracting(chunk -> ((Map<?, ?>) ((OutputSchema) chunk).getPayload()).get("output"))
                .isEqualTo("final answer");
        assertThat(executionLog).containsExactly(ExecutionStatus.STARTING, ExecutionStatus.RUNNING,
                ExecutionStatus.COMPLETING, ExecutionStatus.COMPLETED, ExecutionStatus.IDLE);
    }

    @Test
    void executeRoundShouldRaiseWhenRetriesExhausted() {
        List<MemberStatus> statusLog = new ArrayList<>();
        List<ExecutionStatus> executionLog = new ArrayList<>();
        List<Map<String, Object>> callInputs = new ArrayList<>();
        List<List<Object>> failingRounds = new ArrayList<>();
        for (int index = 0; index <= StreamController.MAX_RETRY_ATTEMPTS; index++) {
            failingRounds.add(List.of(failedChunk(181001, "timeout #" + index)));
        }
        StreamController controller =
            controller(statusLog::add, executionLog::add, null, scriptedExecutor(callInputs, failingRounds));

        assertThatThrownBy(() -> controller.runOneRound("initial query")).isInstanceOf(BaseError.class)
                .satisfies(error -> {
                    BaseError baseError = (BaseError) error;
                    assertThat(baseError.getStatus()).isEqualTo(StatusCode.AGENT_CONTROLLER_EXECUTION_CALL_FAILED);
                    assertThat(baseError.getMessage()).contains("181001");
                });

        assertThat(callInputs).hasSize(StreamController.MAX_RETRY_ATTEMPTS + 1);
        assertThat(statusLog).containsExactly(MemberStatus.READY, MemberStatus.BUSY, MemberStatus.ERROR);
        assertThat(executionLog).containsExactly(ExecutionStatus.STARTING, ExecutionStatus.RUNNING,
                ExecutionStatus.FAILED, ExecutionStatus.IDLE);
        assertThat(controller.getStreamQueue()).isEmpty();
    }

    @Test
    void interruptResumeValidationShouldRequireSubsetOfPendingInterruptIds() {
        AgentSessionApi session = AgentSessionApi.create("sess-1", null, null);
        session.updateState(Map.of(ToolInterruptionState.INTERRUPTION_KEY, interruptionState("call-1", "call-2")));
        StreamController controller = controller(ignored -> {
        }, execution -> {
        }, null, (agent, inputs, sessionId) -> List.<Object>of().iterator());
        controller = new StreamController(Object::new, () -> "leader", ignored -> {
        }, ignored -> {
        }, () -> session, null, (agent, inputs, sessionId) -> List.<Object>of().iterator(),
                () -> MemberStatus.READY);

        InteractiveInput valid = new InteractiveInput();
        valid.update("call-1", Map.of("approved", true));
        InteractiveInput stale = new InteractiveInput();
        stale.update("call-3", Map.of("approved", true));

        controller.getPendingInterruptResumes().offer(stale);
        controller.getPendingInterruptResumes().offer(valid);

        assertThat(controller.hasPendingInterrupt()).isTrue();
        assertThat(controller.isValidInterruptResume(valid)).isTrue();
        assertThat(controller.isValidInterruptResume(stale)).isFalse();
        assertThat(controller.dequeueValidInterruptResume()).isSameAs(valid);
    }

    @Test
    void runOneRoundShouldDrainPendingInputsIntoCombinedFollowUp() {
        List<Map<String, Object>> callInputs = new ArrayList<>();
        AtomicInteger wakeCount = new AtomicInteger();
        StreamController controller = controller(ignored -> {
        }, execution -> {
        }, wakeCount::incrementAndGet, scriptedExecutor(callInputs,
                List.of(List.of(answerChunk("round-1")), List.of(answerChunk("round-2")))));
        controller.getPendingInputs().add("follow-up one");
        controller.getPendingInputs().add("follow-up two");

        controller.runOneRound("initial query");

        assertThat(callInputs).hasSize(2);
        assertThat(callInputs.get(0)).containsEntry("query", "initial query");
        assertThat(callInputs.get(1)).containsEntry("query", "follow-up one\n\n---\n\nfollow-up two");
        assertThat(controller.getPendingInputs()).isEmpty();
        assertThat(wakeCount.get()).isEqualTo(1);
    }

    @Test
    void runOneRoundShouldWakeMailboxWhenNoPendingFollowUpExists() {
        AtomicInteger wakeCount = new AtomicInteger();
        StreamController controller = controller(ignored -> {
        }, execution -> {
        }, wakeCount::incrementAndGet, (agent, inputs, sessionId) -> List.<Object>of(answerChunk("done")).iterator());

        controller.runOneRound("initial query");

        assertThat(wakeCount.get()).isEqualTo(1);
    }

    private static StreamController controller(Consumer<MemberStatus> statusUpdater,
            Consumer<ExecutionStatus> executionUpdater, Runnable wakeMailboxCallback,
            StreamController.StreamRoundExecutor executor) {
        return new StreamController(Object::new, () -> "leader", statusUpdater, executionUpdater,
                () -> AgentSessionApi.create("sess-1", null, null), wakeMailboxCallback, executor,
                () -> MemberStatus.READY);
    }

    private static StreamController.StreamRoundExecutor scriptedExecutor(List<Map<String, Object>> callInputs,
            List<List<Object>> rounds) {
        List<List<Object>> mutableRounds = new ArrayList<>(rounds);
        return (agent, inputs, sessionId) -> {
            callInputs.add(new LinkedHashMap<>(inputs));
            List<Object> nextRound = mutableRounds.isEmpty() ? List.of() : mutableRounds.remove(0);
            return nextRound.iterator();
        };
    }

    private static List<Object> drainQueue(java.util.concurrent.LinkedBlockingQueue<Object> queue) {
        List<Object> drained = new ArrayList<>();
        queue.drainTo(drained);
        return drained;
    }

    private static ControllerOutputChunk failedChunk(int code, String message) {
        ControllerOutputPayload payload = new ControllerOutputPayload(StreamController.TASK_FAILED_PAYLOAD_TYPE,
                List.of(new DataFrame.TextDataFrame("[" + code + "] " + message)), Map.of("task_id", "t1"));
        ControllerOutputChunk chunk = new ControllerOutputChunk();
        chunk.setControllerPayload(payload);
        return chunk;
    }

    private static OutputSchema answerChunk(String output) {
        return new OutputSchema("answer", 0, Map.of("output", output, "result_type", "answer"));
    }

    private static ToolInterruptionState interruptionState(String... interruptIds) {
        List<ToolInterruptEntry> entries = new ArrayList<>();
        for (String interruptId : interruptIds) {
            entries.add(ToolInterruptEntry.builder()
                    .request(InterruptRequest.builder().interruptId(interruptId).build()).build());
        }
        return ToolInterruptionState.builder().interruptedTools(entries).build();
    }
}
