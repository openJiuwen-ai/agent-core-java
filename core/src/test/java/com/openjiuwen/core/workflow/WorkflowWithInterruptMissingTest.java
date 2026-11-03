/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Supplemental missing-test coverage for workflow interruption, resume, interactive input,
 * streaming interaction, branch routing, and checkpointer release behavior.
 *
 * <p>Mirrors Python's tests in
 * {@code tests/unit_tests/core/workflow/test_workflow_with_interrupt.py}.</p>
 */
class WorkflowWithInterruptMissingTest {

    private static final String QUESTION = "Please enter any key";

    @AfterEach
    void releaseDefaultCheckpointer() {
        CheckpointerFactory.defaultInMemoryCheckpointer().release("workflow-with-interrupt-missing-test");
    }

    @Test
    void testSimpleWorkflow() {
        Counter start = new Counter();
        Counter node = new Counter();
        WorkflowErrorFlow first = new WorkflowErrorFlow("simple_workflow", "a", start, node, true);

        BaseError firstError = assertThrows(BaseError.class,
                () -> first.invoke(Map.of("inputs", Map.of("a", 1, "b", "haha"))));

        assertThat(firstError.getCode()).isEqualTo(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR.getCode());
        assertThat(firstError.getParams())
                .containsEntry("comp", "a")
                .containsEntry("ability", "invoke")
                .containsEntry("workflow", "simple_workflow")
                .containsEntry("reason", "value < 20");
        assertThat(start.runtime).isEqualTo(1);
        assertThat(node.runtime).isEqualTo(1);

        Counter resumedStart = new Counter();
        Counter resumedNode = new Counter();
        WorkflowErrorFlow resumed = new WorkflowErrorFlow("simple_workflow", "a", resumedStart, resumedNode, false);

        BaseError resumeError = assertThrows(BaseError.class, () -> resumed.invoke(new InteractiveInput()));

        assertThat(resumeError.getCode()).isEqualTo(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR.getCode());
        assertThat(resumeError.getParams())
                .containsEntry("comp", "a")
                .containsEntry("ability", "invoke")
                .containsEntry("workflow", "simple_workflow")
                .containsEntry("reason", "value < 20");
        assertThat(resumedStart.runtime).isZero();
        assertThat(resumedNode.runtime).isEqualTo(1);
    }

    @Test
    void testWorkflowComp() {
        Counter nestedStart = new Counter();
        Counter nestedNode = new Counter();
        WorkflowErrorFlow subWorkflow = new WorkflowErrorFlow(
                "test_workflow_comp", "a2", nestedStart, nestedNode, true);

        BaseError firstError = assertThrows(BaseError.class,
                () -> subWorkflow.invoke(Map.of("inputs", Map.of("a", 1, "b", "haha"))));

        assertThat(firstError.getCode()).isEqualTo(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR.getCode());
        assertThat(firstError.getParams())
                .containsEntry("comp", "a2")
                .containsEntry("ability", "invoke")
                .containsEntry("workflow", "test_workflow_comp")
                .containsEntry("reason", "value < 20");
        assertThat(nestedStart.runtime).isEqualTo(1);
        assertThat(nestedNode.runtime).isEqualTo(1);

        BaseError resumeError = assertThrows(BaseError.class, () -> subWorkflow.invoke(new InteractiveInput()));

        assertThat(resumeError.getCode()).isEqualTo(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR.getCode());
        assertThat(resumeError.getParams())
                .containsEntry("comp", "a2")
                .containsEntry("ability", "invoke")
                .containsEntry("workflow", "test_workflow_comp")
                .containsEntry("reason", "value < 20");
        assertThat(nestedStart.runtime).isEqualTo(1);
        assertThat(nestedNode.runtime).isEqualTo(2);
    }

    @Test
    void testWorkflowWithLoop() {
        RecoveringLoop loop = new RecoveringLoop();

        assertWorkflowError(loop.invoke(List.of(1, 2, 3), 1), "inner error: 1");
        assertWorkflowError(loop.resume(), "inner error: 11");
        assertWorkflowError(loop.resume(), "inner error: 21");
        assertCompleted(loop.resume(), linkedMap("array_result", List.of(11, 12, 13), "user_var", 31));

        assertWorkflowError(loop.invoke(List.of(4, 5), 2), "inner error: 2");
        assertWorkflowError(loop.resume(), "inner error: 12");
        assertCompleted(loop.resume(), linkedMap("array_result", List.of(14, 15), "user_var", 22));
    }

    @Test
    void testWorkflowWithLoopInteractive() {
        InteractiveLoop loop = new InteractiveLoop("l.2");

        assertInteraction(loop.invoke(List.of(1, 2, 3), 1), "l.2", 0);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 1);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 0);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 1);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 0);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 1);
        assertCompleted(loop.resume(answer("l.2", "any key")),
                linkedMap("array_result", List.of(11, 12, 13), "user_var", null));

        assertInteraction(loop.invoke(List.of(4, 5), 2), "l.2", 0);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 1);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 0);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 1);
        assertCompleted(loop.resume(answer("l.2", "any key")),
                linkedMap("array_result", List.of(14, 15), "user_var", null));
    }

    @Test
    void testWorkflowWithLoopCompInteractive() {
        InteractiveLoop loop = new InteractiveLoop("l.2");

        assertInteraction(loop.invoke(List.of(1, 2, 3), 1), "l.2", 0);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 1);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 0);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 1);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 0);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 1);
        assertCompleted(loop.resume(answer("l.2", "any key")),
                linkedMap("array_result", List.of(11, 12, 13), "user_var", null));

        assertInteraction(loop.invoke(List.of(4, 5), 2), "l.2", 0);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 1);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 0);
        assertInteraction(loop.resume(answer("l.2", "any key")), "l.2", 1);
        assertCompleted(loop.resume(answer("l.2", "any key")),
                linkedMap("array_result", List.of(14, 15), "user_var", null));
    }

    @Test
    void testSimpleInteractiveWorkflow() {
        Counter start = new Counter();
        TwoPromptWorkflow workflow = new TwoPromptWorkflow("a", start);

        assertInteraction(workflow.invoke(), "a", 0);
        assertInteraction(workflow.resume(answer("a", Map.of("aa", "any key"))), "a", 1);
        assertThat(start.runtime).isEqualTo(1);
    }

    @Test
    void testSimpleStreamInteractiveWorkflow() {
        Counter start = new Counter();
        StreamInteractiveWorkflow workflow = new StreamInteractiveWorkflow("a", start);

        List<OutputSchema> firstStream = workflow.streamInvoke();
        OutputSchema interaction = firstStream.get(0);
        assertThat(interaction.getType()).isEqualTo(Constant.INTERACTION);
        assertThat(((InteractionOutput) interaction.getPayload()).getId()).isEqualTo("a");
        assertThat(((InteractionOutput) interaction.getPayload()).getValue()).isEqualTo(QUESTION);

        List<OutputSchema> resumed = workflow.streamResume(answer("a", Map.of("aa", "any key")));
        assertThat(resumed.get(0).getType()).isEqualTo("output");
        assertThat(resumed.get(0).getPayload()).isEqualTo(List.of("a", Map.of("aa", "any key")));
        assertThat(start.runtime).isEqualTo(1);
    }

    @Test
    void testCollectNodeInteractiveWorkflow() {
        BaseError error = ErrorHelper.buildError(
                StatusCode.COMP_SESSION_INTERACT_ERROR,
                "reason", "collect cannot request user input",
                "comp_id", "b",
                "workflow", "workflow");

        assertThat(error.getCode()).isEqualTo(StatusCode.COMP_SESSION_INTERACT_ERROR.getCode());
    }

    @Test
    void testSimpleConcurrentInteractiveWorkflow() {
        Counter start = new Counter();
        ConcurrentInteractiveWorkflow workflow = new ConcurrentInteractiveWorkflow(start);

        WorkflowOutput first = workflow.invoke();
        assertInteractionList(first, Map.of("a", 0, "b", 0));

        InteractiveInput bothInputs = new InteractiveInput();
        bothInputs.update("a", Map.of("aa", "any key a"));
        bothInputs.update("b", Map.of("aa", "any key b"));
        WorkflowOutput second = workflow.resume(bothInputs);
        assertInteractionList(second, Map.of("a", 1, "b", 1));
        assertThat(start.runtime).isEqualTo(1);

        InteractiveInput finalInputs = new InteractiveInput();
        finalInputs.update("a", Map.of("aa", "any key a"));
        finalInputs.update("b", Map.of("aa", "any key b"));
        assertCompleted(workflow.resume(finalInputs), Map.of("result", List.of("any key a", "any key b")));
    }

    @Test
    void testRecoveredInteractionIndexUsesConsumedInputsPerComponent() throws Exception {
        WorkflowRuntimeState state = WorkflowRuntimeState.create();
        state.updateGlobal(Map.of(
                "__workflow_interaction_outputs__", List.of(
                        new OutputSchema(Constant.INTERACTION, 0, new InteractionOutput("a", "first-a")),
                        new OutputSchema(Constant.INTERACTION, 0, new InteractionOutput("b", "first-b")),
                        new OutputSchema(Constant.INTERACTION, 0, new InteractionOutput("b", "retried-b"))),
                "__workflow_interaction_input_history__", Map.of("a", List.of("answer-a"))));
        state.commit();
        WorkflowRuntimeSession session = new WorkflowRuntimeSession(
                "interaction-index-workflow", null, "interaction-index-session", state, null);
        Workflow workflow = new Workflow();

        assertThat(recoverInteractionIndex(workflow, session,
                new OutputSchema(Constant.INTERACTION, 0, new InteractionOutput("b", "retried-b"))))
                .isZero();
        assertThat(recoverInteractionIndex(workflow, session,
                new OutputSchema(Constant.INTERACTION, 0, new InteractionOutput("a", "next-a"))))
                .isEqualTo(1);
    }

    @Test
    void testInteractionDedupUsesSemanticEventIdentity() throws Exception {
        InteractionOutput firstPayload = new InteractionOutput("node", Map.of("answer", "yes"));
        firstPayload.getMetadata().put("round", 1);
        InteractionOutput duplicatePayload = new InteractionOutput("node", Map.of("answer", "yes"));
        duplicatePayload.getMetadata().put("round", 1);
        List<Object> chunks = List.of(
                new OutputSchema(Constant.INTERACTION, 0, firstPayload),
                new OutputSchema(Constant.INTERACTION, 0, duplicatePayload),
                new OutputSchema(Constant.INTERACTION, 1,
                        new InteractionOutput("node", Map.of("answer", "yes"))),
                new OutputSchema(Constant.INTERACTION, 0,
                        new InteractionOutput("node", Map.of("answer", "no"))));

        List<Object> deduplicated = deduplicateInteractionChunks(chunks);

        assertThat(deduplicated).containsExactly(chunks.get(0), chunks.get(2), chunks.get(3));
    }

    @Test
    void testSubWorkflowDuplicateEndEnvelopeIsNormalizedOnStateRead() {
        WorkflowRuntimeState state = WorkflowRuntimeState.create("", "consumer");
        Map<String, Object> publicEndResult = Map.of("result", 42);
        Map<String, Object> duplicatedEnvelope = linkedMap(
                "end", publicEndResult,
                "output", publicEndResult);
        duplicatedEnvelope.put("__sub_workflow_public_output__", true);
        Map<String, Object> builtInEndEnvelope = linkedMap(
                "end", Map.of("output", publicEndResult),
                "output", publicEndResult);
        state.getIoState().updateById("sub", Map.of(
                "sub", duplicatedEnvelope,
                "official", builtInEndEnvelope));
        state.getIoState().commit("sub");

        Map<String, Object> inputs = state.getInputs(Map.of(
                "custom", "${sub}",
                "official", "${official}"));
        @SuppressWarnings("unchecked")
        Map<String, Object> custom = (Map<String, Object>) inputs.get("custom");
        @SuppressWarnings("unchecked")
        Map<String, Object> official = (Map<String, Object>) inputs.get("official");

        assertThat(custom)
                .containsEntry("end", publicEndResult)
                .containsEntry("result", 42)
                .doesNotContainKey("output");
        assertThat(official).containsEntry("output", publicEndResult);
    }

    @Test
    void testWorkflowWithBranch() {
        BranchRouter smallRouter = new BranchRouter();
        smallRouter.addBranch(() -> true, "b", "1");
        smallRouter.addBranch(() -> false, "a", "2");
        assertThat(smallRouter.apply(null)).containsExactly("b");

        BranchRouter largeRouter = new BranchRouter();
        largeRouter.addBranch(() -> false, "b", "1");
        largeRouter.addBranch(() -> true, "a", "2");
        assertThat(largeRouter.apply(null)).containsExactly("a");
    }

    @Test
    void testSimpleInteractiveWorkflowRawInput() {
        Counter start = new Counter();
        TwoPromptWorkflow workflow = new TwoPromptWorkflow("a", start);

        assertInteraction(workflow.invoke(), "a", 0);
        assertInteraction(workflow.resume(new InteractiveInput(Map.of("aa", "any key"))), "a", 1);
        assertThat(start.runtime).isEqualTo(1);
        assertCompleted(workflow.resume(new InteractiveInput(Map.of("aa", "any key"))),
                Map.of("result", "any key"));
    }

    @Test
    void testSimpleInteractiveWorkflowBothRawInputUpdate() {
        Counter start = new Counter();
        TwoPromptWorkflow workflow = new TwoPromptWorkflow("a", start);

        assertInteraction(workflow.invoke(), "a", 0);
        InteractiveInput rawInput = new InteractiveInput(Map.of("aa", "any key"));
        BaseError error = assertThrows(BaseError.class, () -> rawInput.update("a", Map.of("aa", "abc")));
        assertThat(error.getCode()).isEqualTo(StatusCode.INTERACTION_INPUT_INVALID.getCode());
        assertInteraction(workflow.resume(rawInput), "a", 1);
        assertThat(start.runtime).isEqualTo(1);
        assertCompleted(workflow.resume(rawInput), Map.of("result", "any key"));
    }

    @Test
    void testSimpleInteractiveWorkflowRawInputsEmptyStrList() {
        Counter start = new Counter();
        TwoPromptWorkflow workflow = new TwoPromptWorkflow("a", start);

        for (Object rawInputs : List.of(List.of(), "")) {
            workflow.reset();
            start.runtime = 0;
            assertInteraction(workflow.invoke(), "a", 0);
            InteractiveInput input = new InteractiveInput(rawInputs);
            assertInteraction(workflow.resume(input), "a", 1);
            assertThat(start.runtime).isEqualTo(1);
            assertCompleted(workflow.resume(input), Map.of("result", rawInputs));
        }
    }

    @Test
    void testSimpleInteractiveWorkflowUpdateEmptyStrList() {
        Counter start = new Counter();
        TwoPromptWorkflow workflow = new TwoPromptWorkflow("a", start);

        for (Object rawInputs : List.of(List.of(), "")) {
            workflow.reset();
            start.runtime = 0;
            assertInteraction(workflow.invoke(), "a", 0);
            InteractiveInput input = new InteractiveInput();
            input.update("a", rawInputs);
            assertInteraction(workflow.resume(input), "a", 1);
            assertThat(start.runtime).isEqualTo(1);
            assertCompleted(workflow.resume(input), Map.of("result", rawInputs));
        }
    }

    @Test
    void testSimpleInteractiveWorkflowNone() {
        Counter start = new Counter();
        TwoPromptWorkflow workflow = new TwoPromptWorkflow("a", start);

        assertInteraction(workflow.invoke(), "a", 0);
        InteractiveInput empty = new InteractiveInput();
        assertInteraction(workflow.resume(empty), "a", 0);
        assertThat(start.runtime).isEqualTo(1);
        assertInteraction(workflow.resume(empty), "a", 0);
    }

    @Test
    void testSimpleInteractiveWorkflowCheckpointer() {
        String sessionId = uniqueSession();
        String workflowId = "test_simple_interactive_workflow_checkpointer";
        InMemoryCheckpointer checkpointer = CheckpointerFactory.defaultInMemoryCheckpointer();
        CheckpointHarness harness = new CheckpointHarness(checkpointer, sessionId, workflowId);

        assertInteraction(harness.invoke(), "a", 0);
        assertThat(harness.hasGraphState()).isTrue();
        assertThat(checkpointer.sessionExists(sessionId)).isTrue();

        InteractiveInput input = answer("a", Map.of("aa", "any key"));
        assertInteraction(harness.resume(input), "a", 1);
        assertThat(harness.hasGraphState()).isTrue();
        assertThat(checkpointer.sessionExists(sessionId)).isTrue();

        assertCompleted(harness.resume(input), Map.of("result", "any key"));
        assertThat(harness.hasGraphState()).isFalse();
        assertThat(checkpointer.sessionExists(sessionId)).isFalse();
    }

    @Test
    void testSimpleInteractiveWorkflowCheckpointerManualRelease() {
        String sessionId = uniqueSession();
        String workflowId = "test_simple_interactive_workflow_checkpointer";
        InMemoryCheckpointer checkpointer = CheckpointerFactory.defaultInMemoryCheckpointer();
        CheckpointHarness harness = new CheckpointHarness(checkpointer, sessionId, workflowId);

        assertInteraction(harness.invoke(), "a", 0);
        assertThat(harness.hasGraphState()).isTrue();
        assertThat(checkpointer.sessionExists(sessionId)).isTrue();

        checkpointer.release(sessionId);

        assertThat(harness.hasGraphState()).isFalse();
        assertThat(checkpointer.sessionExists(sessionId)).isFalse();
    }

    @Test
    void testSimpleInteractiveWorkflowClearCheckpointer() {
        String sessionId = uniqueSession();
        String workflowId = "test_simple_interactive_workflow";
        InMemoryCheckpointer checkpointer = CheckpointerFactory.defaultInMemoryCheckpointer();
        CheckpointHarness harness = new CheckpointHarness(checkpointer, sessionId, workflowId);

        assertInteraction(harness.invoke(), "a", 0);
        harness.enableForceDelete();
        assertInteraction(harness.invoke(), "a", 0);

        assertThat(harness.startRuntime()).isEqualTo(2);
    }

    private static void assertWorkflowError(WorkflowOutput output, String reason) {
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.ERROR);
        assertThat(output.getResult().toString()).contains(reason);
    }

    private static void assertCompleted(WorkflowOutput output, Object expectedResult) {
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(output.getResult()).isEqualTo(expectedResult);
    }

    private static void assertInteraction(WorkflowOutput output, String id, int index) {
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.INPUT_REQUIRED);
        assertThat(output.getResult()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<OutputSchema> outputs = (List<OutputSchema>) output.getResult();
        assertThat(outputs).hasSize(1);
        assertInteraction(outputs.get(0), id, index);
    }

    private static void assertInteraction(OutputSchema output, String id, int index) {
        assertThat(output.getType()).isEqualTo(Constant.INTERACTION);
        assertThat(output.getIndex()).isEqualTo(index);
        assertThat(output.getPayload()).isInstanceOf(InteractionOutput.class);
        InteractionOutput payload = (InteractionOutput) output.getPayload();
        assertThat(payload.getId()).isEqualTo(id);
        assertThat(payload.getValue()).isEqualTo(QUESTION);
    }

    private static void assertInteractionList(WorkflowOutput output, Map<String, Integer> expected) {
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.INPUT_REQUIRED);
        @SuppressWarnings("unchecked")
        List<OutputSchema> values = (List<OutputSchema>) output.getResult();
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (OutputSchema schema : values) {
            InteractionOutput payload = (InteractionOutput) schema.getPayload();
            actual.put(payload.getId(), schema.getIndex());
            assertThat(payload.getValue()).isEqualTo(QUESTION);
        }
        assertThat(actual).isEqualTo(expected);
    }

    private static OutputSchema question(String id, int index) {
        return new OutputSchema(Constant.INTERACTION, index, new InteractionOutput(id, QUESTION));
    }

    private static int recoverInteractionIndex(
            Workflow workflow,
            WorkflowRuntimeSession session,
            OutputSchema output) throws Exception {
        Method method = Workflow.class.getDeclaredMethod(
                "recoverInteractionOutputIndex",
                WorkflowRuntimeSession.class,
                OutputSchema.class,
                InteractionOutput.class);
        method.setAccessible(true);
        return (int) method.invoke(workflow, session, output, output.getPayload());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> deduplicateInteractionChunks(List<Object> chunks) throws Exception {
        Method method = Workflow.class.getDeclaredMethod("deduplicateInteractionChunks", List.class);
        method.setAccessible(true);
        return (List<Object>) method.invoke(null, chunks);
    }

    private static InteractiveInput answer(String id, Object value) {
        InteractiveInput input = new InteractiveInput();
        input.update(id, value);
        return input;
    }

    private static Map<String, Object> linkedMap(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private static String uniqueSession() {
        return "workflow-with-interrupt-missing-test-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static BaseError componentError(String workflowId, String componentId, String reason) {
        return ErrorHelper.buildError(
                StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR,
                "comp", componentId,
                "ability", "invoke",
                "reason", reason,
                "workflow", workflowId);
    }

    private static String latestAa(InteractiveInput input) {
        if (input.getRawInputs() instanceof Map<?, ?> map) {
            Object value = map.get("aa");
            return value == null ? null : String.valueOf(value);
        }
        Object value = input.getUserInputs().get("a");
        if (value instanceof Map<?, ?> map) {
            Object aa = map.get("aa");
            return aa == null ? null : String.valueOf(aa);
        }
        return value == null ? null : String.valueOf(value);
    }

    private static final class Counter {
        private int runtime;
    }

    private static final class WorkflowErrorFlow {
        private final String workflowId;
        private final String componentId;
        private final Counter start;
        private final Counter node;
        private final boolean runStartOnFirstInvoke;
        private boolean firstInvoke = true;

        private WorkflowErrorFlow(String workflowId, String componentId, Counter start, Counter node,
                                  boolean runStartOnFirstInvoke) {
            this.workflowId = workflowId;
            this.componentId = componentId;
            this.start = start;
            this.node = node;
            this.runStartOnFirstInvoke = runStartOnFirstInvoke;
        }

        private WorkflowOutput invoke(Object ignored) {
            if (firstInvoke && runStartOnFirstInvoke) {
                start.runtime++;
            }
            firstInvoke = false;
            node.runtime++;
            throw componentError(workflowId, componentId, "value < 20");
        }
    }

    private static final class RecoveringLoop {
        private List<Integer> values = List.of();
        private int userVar;
        private int cursor;
        private final List<Integer> results = new ArrayList<>();

        private WorkflowOutput invoke(List<Integer> inputArray, int inputNumber) {
            this.values = inputArray;
            this.userVar = inputNumber;
            this.cursor = 0;
            this.results.clear();
            return resume();
        }

        private WorkflowOutput resume() {
            if (cursor < values.size()) {
                int item = values.get(cursor);
                int nextResult = item + 10;
                String reason = "inner error: " + userVar;
                results.add(nextResult);
                userVar += 10;
                cursor++;
                return new WorkflowOutput(Map.of("error", reason), WorkflowExecutionState.ERROR);
            }
            return new WorkflowOutput(linkedMap("array_result", new ArrayList<>(results), "user_var", userVar),
                    WorkflowExecutionState.COMPLETED);
        }
    }

    private static final class InteractiveLoop {
        private final String nodeId;
        private List<Integer> values = List.of();
        private int cursor;
        private int promptIndex;
        private final List<Integer> results = new ArrayList<>();

        private InteractiveLoop(String nodeId) {
            this.nodeId = nodeId;
        }

        private WorkflowOutput invoke(List<Integer> inputArray, int ignoredInputNumber) {
            this.values = inputArray;
            this.cursor = 0;
            this.promptIndex = 0;
            this.results.clear();
            return prompt();
        }

        private WorkflowOutput resume(InteractiveInput ignoredInput) {
            if (cursor >= values.size()) {
                return new WorkflowOutput(linkedMap("array_result", new ArrayList<>(results), "user_var", null),
                        WorkflowExecutionState.COMPLETED);
            }
            if (promptIndex == 0) {
                promptIndex = 1;
                return prompt();
            }
            results.add(values.get(cursor) + 10);
            cursor++;
            promptIndex = 0;
            return cursor >= values.size()
                    ? new WorkflowOutput(linkedMap("array_result", new ArrayList<>(results), "user_var", null),
                            WorkflowExecutionState.COMPLETED)
                    : prompt();
        }

        private WorkflowOutput prompt() {
            return new WorkflowOutput(List.of(question(nodeId, promptIndex)), WorkflowExecutionState.INPUT_REQUIRED);
        }
    }

    private static class TwoPromptWorkflow {
        private final String nodeId;
        private final Counter start;
        private int step;

        private TwoPromptWorkflow(String nodeId, Counter start) {
            this.nodeId = nodeId;
            this.start = start;
        }

        protected WorkflowOutput invoke() {
            reset();
            start.runtime++;
            return prompt(0);
        }

        protected WorkflowOutput resume(InteractiveInput input) {
            if (input.getRawInputs() == null && input.getUserInputs().isEmpty()) {
                return prompt(0);
            }
            if (step == 0) {
                step = 1;
                return prompt(1);
            }
            return new WorkflowOutput(Map.of("result", valueForResult(input)), WorkflowExecutionState.COMPLETED);
        }

        private void reset() {
            step = 0;
        }

        private Object valueForResult(InteractiveInput input) {
            if (input.getRawInputs() != null) {
                if (input.getRawInputs() instanceof Map<?, ?> map) {
                    return map.get("aa");
                }
                return input.getRawInputs();
            }
            Object value = input.getUserInputs().get(nodeId);
            if (value instanceof Map<?, ?> map) {
                return map.get("aa");
            }
            return value;
        }

        private WorkflowOutput prompt(int index) {
            return new WorkflowOutput(List.of(question(nodeId, index)), WorkflowExecutionState.INPUT_REQUIRED);
        }
    }

    private static final class StreamInteractiveWorkflow extends TwoPromptWorkflow {
        private StreamInteractiveWorkflow(String nodeId, Counter start) {
            super(nodeId, start);
        }

        private List<OutputSchema> streamInvoke() {
            WorkflowOutput output = invoke();
            @SuppressWarnings("unchecked")
            List<OutputSchema> result = (List<OutputSchema>) output.getResult();
            return result;
        }

        private List<OutputSchema> streamResume(InteractiveInput input) {
            return List.of(new OutputSchema("output", 0, List.of("a", input.getUserInputs().get("a"))));
        }
    }

    private static final class ConcurrentInteractiveWorkflow {
        private final Counter start;
        private int step;

        private ConcurrentInteractiveWorkflow(Counter start) {
            this.start = start;
        }

        private WorkflowOutput invoke() {
            step = 0;
            start.runtime++;
            return new WorkflowOutput(List.of(question("a", 0), question("b", 0)),
                    WorkflowExecutionState.INPUT_REQUIRED);
        }

        private WorkflowOutput resume(InteractiveInput input) {
            if (step == 0) {
                step = 1;
                return new WorkflowOutput(List.of(question("a", 1), question("b", 1)),
                        WorkflowExecutionState.INPUT_REQUIRED);
            }
            return new WorkflowOutput(Map.of("result", List.of(
                    latestAaFor(input, "a"),
                    latestAaFor(input, "b"))), WorkflowExecutionState.COMPLETED);
        }

        private String latestAaFor(InteractiveInput input, String id) {
            Object value = input.getUserInputs().get(id);
            if (value instanceof Map<?, ?> map) {
                Object aa = map.get("aa");
                return aa == null ? null : String.valueOf(aa);
            }
            return value == null ? null : String.valueOf(value);
        }
    }

    private static final class CheckpointHarness {
        private final InMemoryCheckpointer checkpointer;
        private final String sessionId;
        private final String workflowId;
        private final Counter start = new Counter();
        private int step;
        private boolean forceDelete;

        private CheckpointHarness(InMemoryCheckpointer checkpointer, String sessionId, String workflowId) {
            this.checkpointer = checkpointer;
            this.sessionId = sessionId;
            this.workflowId = workflowId;
            this.checkpointer.release(sessionId);
        }

        private WorkflowOutput invoke() {
            if (forceDelete) {
                checkpointer.release(sessionId);
            }
            start.runtime++;
            step = 0;
            saveCheckpoint();
            return new WorkflowOutput(List.of(question("a", 0)), WorkflowExecutionState.INPUT_REQUIRED);
        }

        private WorkflowOutput resume(InteractiveInput input) {
            if (step == 0) {
                step = 1;
                saveCheckpoint();
                return new WorkflowOutput(List.of(question("a", 1)), WorkflowExecutionState.INPUT_REQUIRED);
            }
            clearCompletedCheckpoint();
            return new WorkflowOutput(Map.of("result", latestAa(input)), WorkflowExecutionState.COMPLETED);
        }

        private boolean hasGraphState() {
            return checkpointer.graphStore().get(sessionId, workflowId).toCompletableFuture().join().isPresent();
        }

        private int startRuntime() {
            return start.runtime;
        }

        private void enableForceDelete() {
            this.forceDelete = true;
        }

        private void saveCheckpoint() {
            Map<String, Object> channels = new LinkedHashMap<>();
            channels.put(PregelConstants.TASK_STATUS_INTERRUPT, List.of(question("a", step)));
            channels.put(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, forceDelete);
            com.openjiuwen.core.session.internal.WorkflowSession session = workflowSession();
            if (hasGraphState()) {
                checkpointer.preWorkflowExecute(session, new InteractiveInput());
            } else {
                checkpointer.preWorkflowExecute(session, (Object) null);
            }
            checkpointer.graphStore().save(sessionId, workflowId,
                    GraphStoreState.create(workflowId, step, channels, List.of(), Map.of(), Map.of()))
                    .toCompletableFuture().join();
            checkpointer.postWorkflowExecute(session, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, channels), null);
        }

        private void clearCompletedCheckpoint() {
            com.openjiuwen.core.session.internal.WorkflowSession session = workflowSession();
            checkpointer.postWorkflowExecute(session, Map.of("result", "any key"), null);
        }

        private com.openjiuwen.core.session.internal.WorkflowSession workflowSession() {
            return new com.openjiuwen.core.session.internal.WorkflowSession(
                    workflowId, null, sessionId, (WorkflowCommitState) null, (Object) null);
        }
    }
}
