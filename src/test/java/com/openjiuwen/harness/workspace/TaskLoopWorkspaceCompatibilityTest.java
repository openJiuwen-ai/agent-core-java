
package com.openjiuwen.harness.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.TaskIterationRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.task_loop.CompletionPromiseEvaluator;
import com.openjiuwen.harness.task_loop.CoreTaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.CustomPredicateEvaluator;
import com.openjiuwen.harness.task_loop.DeepLoopEvent;
import com.openjiuwen.harness.task_loop.DeepLoopEventType;
import com.openjiuwen.harness.task_loop.LoopCoordinator;
import com.openjiuwen.harness.task_loop.LoopQueues;
import com.openjiuwen.harness.task_loop.MaxRoundsEvaluator;
import com.openjiuwen.harness.task_loop.SessionSpawnExecutor;
import com.openjiuwen.harness.task_loop.TaskIterationContext;
import com.openjiuwen.harness.task_loop.TaskLoopController;
import com.openjiuwen.harness.task_loop.TaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;
import com.openjiuwen.harness.task_loop.TaskPlan;
import com.openjiuwen.harness.task_loop.TimeoutEvaluator;
import com.openjiuwen.harness.task_loop.TokenBudgetEvaluator;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.tools.TodoStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

class TaskLoopWorkspaceCompatibilityTest {
    @TempDir
    Path tempDir;

    static class RecordingTaskIterationRail extends DeepAgentRail implements TaskIterationRail {
        final List<TaskIterationContext> contexts = new ArrayList<>();

        @Override
        public void afterTaskIteration(TaskIterationContext ctx) {
            contexts.add(ctx);
        }
    }

    @Test
    void workspaceShouldResolveNodesAndManageTeamLinks() throws Exception {
        Workspace workspace = Workspace.builder().rootPath(tempDir.toString()).build();
        Path teamTarget = tempDir.resolve("teamA");
        Files.createDirectories(teamTarget);

        assertThat(workspace.getNodePath("memory").toString()).contains("memory");
        Path link = workspace.linkTeam("teamA", teamTarget.toString());
        assertThat(Files.exists(link)).isTrue();
        assertThat(workspace.unlinkTeam("teamA")).isTrue();
    }

    @Test
    void loopCoordinatorAndQueuesShouldTrackStopAndFollowUps() {
        CompletionPromiseEvaluator completion = new CompletionPromiseEvaluator("DONE", 2);
        LoopCoordinator coordinator = new LoopCoordinator(java.util.List.of(completion));
        coordinator.reset();
        coordinator.incrementIteration();
        coordinator.addTokenUsage(10);
        coordinator.setLastResult(Map.of("ok", true));
        assertThat(coordinator.shouldContinue()).isTrue();
        completion.notifyFulfilled("DONE");
        assertThat(coordinator.shouldContinue()).isTrue();
        completion.notifyFulfilled("DONE");
        assertThat(coordinator.shouldContinue()).isFalse();
        assertThat(completion.getConfirmationCount()).isEqualTo(2);
        assertThat(coordinator.getState()).containsEntry("iteration", 1);
        coordinator.loadState(Map.of("iteration", 3, "token_usage", 99, "stop_reason", "CompletionPromise",
                "evaluator_states", Map.of("CompletionPromise", Map.of("completed", true, "confirmation_count", 2,
                        "required_confirmations", 2, "matched_text", "DONE"))));
        assertThat(coordinator.getCurrentIteration()).isEqualTo(3);
        assertThat(coordinator.getStopReason()).isEqualTo("CompletionPromise");
        assertThat(coordinator.getCompletionPromiseEvaluator().shouldStop(null)).isTrue();

        LoopQueues queues = new LoopQueues();
        queues.pushEvent(DeepLoopEventType.FOLLOWUP, "later");
        queues.pushEvent(DeepLoopEventType.STEER, "inspect");
        queues.pushEvent(DeepLoopEventType.ABORT, "stop");
        queues.pushSteer("inspect");
        queues.pushFollowUp("next");
        assertThat(queues.drainEvents()).extracting(DeepLoopEvent::getEventType)
                .containsExactly(DeepLoopEventType.ABORT, DeepLoopEventType.STEER, DeepLoopEventType.FOLLOWUP);
        assertThat(queues.drainSteering()).containsExactly("inspect", "inspect");
        assertThat(queues.hasFollowUp()).isTrue();
        assertThat(queues.drainFollowUp()).containsExactly("later", "next");
    }

    @Test
    void taskPlanShouldTrackTodoProgressAndDependencies() {
        TodoItem first = TodoItem.builder().id("t1").content("first").status(TodoStatus.PENDING).build();
        TodoItem second =
            TodoItem.builder().id("t2").content("second").status(TodoStatus.PENDING).dependsOn(List.of("t1")).build();
        TodoItem third = TodoItem.builder().id("t3").content("third").status(TodoStatus.PENDING).build();
        TaskPlan plan = TaskPlan.builder().goal("ship").tasks(new ArrayList<>(List.of(first, second, third))).build();

        assertThat(plan.getTask("t1")).isSameAs(first);
        assertThat(plan.getNextTask()).isSameAs(first);

        plan.markCompleted("t1", "done first");
        assertThat(plan.getNextTask()).isSameAs(second);
        assertThat(plan.getProgressSummary()).isEqualTo("1/3 completed");
        assertThat(plan.toMarkdown()).contains("## Goal: ship").contains("first - done first");

        TaskPlan restored = TaskPlan.fromMap(plan.toMap());
        assertThat(restored.getTask("t1").getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(restored.getTask("t2").getDependsOn()).containsExactly("t1");
    }

    @Test
    void taskPlanShouldPersistAndLoadStructuredState() throws Exception {
        Path planPath = tempDir.resolve(".task_plan").resolve("structured.json");
        TaskPlan plan = TaskPlan.builder().goal("ship")
                .tasks(new ArrayList<>(List.of(
                        TodoItem.builder().id("t1").content("first").status(TodoStatus.COMPLETED).resultSummary("done")
                                .build(),
                        TodoItem.builder().id("t2").content("second").status(TodoStatus.IN_PROGRESS)
                                .dependsOn(List.of("t1")).selectedModelId("fast").build())))
                .currentTaskId("t2").build();

        plan.save(planPath);
        TaskPlan loaded = TaskPlan.load(planPath);

        assertThat(Files.exists(planPath)).isTrue();
        assertThat(loaded.getGoal()).isEqualTo("ship");
        assertThat(loaded.getCurrentTaskId()).isEqualTo("t2");
        assertThat(loaded.getTask("t1").getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(loaded.getTask("t1").getResultSummary()).isEqualTo("done");
        assertThat(loaded.getTask("t2").getDependsOn()).containsExactly("t1");
        assertThat(loaded.getTask("t2").getSelectedModelId()).isEqualTo("fast");
    }

    @Test
    void taskLoopControllerShouldSubmitAndDrainFollowUps() {
        TaskLoopController controller = new TaskLoopController();
        int round = controller.submitRound("hello");
        int followUpRound = controller.submitRound("continue", true);
        controller.enqueueSteering("inspect files");
        controller.enqueueFollowUp("continue");
        controller.resolveCompletion(followUpRound,
                Map.of("status", "completed", "round", followUpRound, "is_follow_up", true));

        assertThat(round).isEqualTo(1);
        assertThat(followUpRound).isEqualTo(2);
        assertThat(controller.waitRoundCompletion()).containsEntry("status", "completed").containsEntry("round", 2)
                .containsEntry("is_follow_up", true);
        assertThat(controller.drainSteering()).containsExactly("inspect files");
        assertThat(controller.hasFollowUp()).isTrue();
        assertThat(controller.drainFollowUp()).containsExactly("continue");
    }

    @Test
    void stopConditionFamiliesShouldMatchPythonSemantics() {
        assertThat(new MaxRoundsEvaluator(3)
                .shouldStop(com.openjiuwen.harness.task_loop.StopEvaluationContext.builder().iteration(3).build()))
                .isTrue();
        assertThat(new TokenBudgetEvaluator(100)
                .shouldStop(com.openjiuwen.harness.task_loop.StopEvaluationContext.builder().tokenUsage(100).build()))
                .isTrue();
        assertThat(new TimeoutEvaluator(1.5).shouldStop(
                com.openjiuwen.harness.task_loop.StopEvaluationContext.builder().elapsedSeconds(2.0).build())).isTrue();
        assertThat(new CustomPredicateEvaluator("LastOk", ctx -> Boolean.TRUE.equals(ctx.getLastResult().get("ok")))
                .shouldStop(com.openjiuwen.harness.task_loop.StopEvaluationContext.builder()
                        .lastResult(Map.of("ok", true)).build()))
                .isTrue();
    }

    @Test
    void deepLoopEventsShouldOrderByPriorityThenSequence() {
        PriorityQueue<DeepLoopEvent> queue = new PriorityQueue<>();
        queue.add(DeepLoopEvent.builder(3, DeepLoopEventType.FOLLOWUP, "follow").build());
        queue.add(DeepLoopEvent.builder(2, DeepLoopEventType.STEER, "steer").build());
        queue.add(DeepLoopEvent.builder(1, DeepLoopEventType.ABORT, "abort").build());

        assertThat(queue.poll().getEventType()).isEqualTo(DeepLoopEventType.ABORT);
        assertThat(queue.poll().getEventType()).isEqualTo(DeepLoopEventType.STEER);
        assertThat(queue.poll().getEventType()).isEqualTo(DeepLoopEventType.FOLLOWUP);
        assertThat(DeepLoopEvent.defaultPriority(DeepLoopEventType.FOLLOWUP)).isEqualTo(10);
    }

    @Test
    void eventHandlerShouldCorrelateRoundsAndRejectStaleCompletion() {
        TaskLoopEventHandler handler = new TaskLoopEventHandler();

        int roundOne = handler.prepareRound();
        Map<String, Object> ack = handler.handleInput("first", Map.of("_handler_round_id", roundOne, "task_id", "t1"));
        assertThat(ack).containsEntry("status", "submitted").containsEntry("task_id", "t1").containsEntry("round", 1);

        int roundTwo = handler.prepareRound();
        assertThat(handler.resolveCompletion(roundOne, Map.of("status", "completed"))).containsEntry("status", "stale")
                .containsEntry("current_round", roundTwo);
        assertThat(handler.resolveCompletion(roundTwo, Map.of("status", "completed", "round", roundTwo)))
                .containsEntry("status", "completed").containsEntry("round", roundTwo);
        handler.abort("user requested");
        assertThat(handler.getLastResult()).containsEntry("status", "aborted");
    }

    @Test
    void taskLoopControllerShouldIsolateSessionState() {
        TaskLoopController controller = new TaskLoopController();

        int roundOne = controller.submitRound("session-one", "hello", false);
        int roundTwo = controller.submitRound("session-two", "world", true);
        controller.enqueueFollowUp("session-one", "follow-one");
        controller.enqueueFollowUp("session-two", "follow-two");
        controller.resolveCompletion("session-one", roundOne, Map.of("status", "completed", "round", roundOne));

        assertThat(controller.waitRoundCompletion("session-one")).containsEntry("round", roundOne);
        assertThat(controller.waitRoundCompletion("session-two")).containsEntry("error", "completion_timeout");
        assertThat(controller.drainFollowUp("session-one")).containsExactly("follow-one");
        assertThat(controller.drainFollowUp("session-two")).containsExactly("follow-two");
        controller.abort("session-two", "stop");
        assertThat(controller.getLastResult("session-two")).containsEntry("status", "aborted");
    }

    @Test
    void eventHandlerShouldCreateCoreTaskAndResolveCompletionEvents() {
        ControllerConfig config = new ControllerConfig();
        TaskManager taskManager = new TaskManager(config);
        TaskLoopEventHandler handler = new TaskLoopEventHandler();
        handler.setTaskManager(taskManager);

        int round = handler.prepareRound();
        InputEvent inputEvent = InputEvent.fromUserInput("inspect workspace");
        inputEvent.setMetadata(Map.of("_handler_round_id", round, "task_id", "task-loop-1", "run_kind", "outer_loop",
                "is_follow_up", false));

        Map<String, Object> ack =
            handler.handleInput(new EventHandlerInput(inputEvent, new AgentSessionApi("session-1")));

        assertThat(ack).containsEntry("status", "submitted").containsEntry("task_id", "task-loop-1")
                .containsEntry("round", round);
        Task stored =
            taskManager.getTask(com.openjiuwen.core.controller.modules.TaskFilter.byTaskId("task-loop-1")).get(0);
        assertThat(stored.getTaskType()).isEqualTo(TaskLoopEventExecutor.DEEP_TASK_TYPE);
        assertThat(stored.getDescription()).isEqualTo("inspect workspace");
        assertThat(stored.getMetadata()).containsEntry("_handler_round_id", round)
                .containsEntry("run_kind", "outer_loop").containsEntry("is_follow_up", false);

        TaskCompletionEvent completion =
            new TaskCompletionEvent(java.util.List.of(new DataFrame.JsonDataFrame(Map.of("output", "done"))), stored);
        Map<String, Object> resolved =
            handler.handleTaskCompletion(new EventHandlerInput(completion, new AgentSessionApi("session-1")));
        assertThat(resolved).containsEntry("output", "done");
        assertThat(handler.waitCompletion()).containsEntry("output", "done");
    }

    @Test
    void eventExecutorsShouldReturnCompletionAndFailurePayloads() {
        List<TaskIterationContext> contexts = new ArrayList<>();
        TaskLoopEventExecutor executor =
            new TaskLoopEventExecutor(
                    inputs -> Map.of("output", inputs.get("query"), "follow_up", inputs.get("is_follow_up"),
                            "usage_metadata",
                            UsageMetadata.builder().inputTokens(4).outputTokens(6).totalTokens(10).build()),
                    contexts::add);
        Map<String, Object> result = executor.execute("task-1", "do work", Map.of("is_follow_up", true));
        assertThat(result).containsEntry("type", "TASK_COMPLETION").containsEntry("task_id", "task-1")
                .containsEntry("task_type", TaskLoopEventExecutor.DEEP_TASK_TYPE);
        assertThat(((Map<?, ?>) result.get("data")).get("output")).isEqualTo("do work");
        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).getResult()).containsEntry("output", "do work");
        assertThat(contexts.get(0).tokenUsage()).isEqualTo(10);

        TaskLoopEventExecutor failing = new TaskLoopEventExecutor(inputs -> {
            throw new IllegalStateException("boom");
        });
        assertThat(failing.execute("task-2", "fail", Map.of())).containsEntry("type", "TASK_FAILED")
                .containsEntry("error", "boom");

        SessionSpawnExecutor spawnExecutor =
            new SessionSpawnExecutor(inputs -> inputs.get("subagent_type") + ":" + inputs.get("task_description"));
        Map<String, Object> spawnResult = spawnExecutor.execute("spawn-1",
                Map.of("subagent_type", "verification_agent", "task_description", "verify"));
        assertThat(spawnResult).containsEntry("type", "TASK_COMPLETION").containsEntry("task_type",
                SessionSpawnExecutor.SESSION_SPAWN_TASK_TYPE);
        assertThat(((Map<?, ?>) spawnResult.get("data")).get("output")).isEqualTo("verification_agent:verify");
        assertThat(spawnExecutor.canPause()).isFalse();
        assertThat(spawnExecutor.canCancel()).isTrue();
    }

    @Test
    void coreTaskLoopExecutorShouldUseTaskManagerAndEmitControllerChunks() {
        ControllerConfig config = new ControllerConfig();
        TaskManager taskManager = new TaskManager(config);
        Task task = new Task("session-1", "task-1", TaskLoopEventExecutor.DEEP_TASK_TYPE);
        task.setStatus(TaskStatus.SUBMITTED);
        task.setDescription("inspect repo");
        task.setMetadata(Map.of("run_kind", "outer_loop", "run_context", Map.of("round", 1), "is_follow_up", true));
        taskManager.addTask(task);

        TaskExecutorDependencies dependencies = new TaskExecutorDependencies(config, new Object(), new ContextEngine(),
                taskManager, new EventQueue(config));
        CoreTaskLoopEventExecutor executor = new CoreTaskLoopEventExecutor(dependencies, inputs -> Map.of("output",
                inputs.get("query"), "run_kind", inputs.get("run_kind"), "is_follow_up", inputs.get("is_follow_up")));

        ControllerOutputChunk chunk = executor.executeAbility("task-1", new AgentSessionApi("session-1")).next();

        assertThat(chunk.isLastChunk()).isTrue();
        assertThat(chunk.getControllerPayload().getType()).isEqualTo(EventType.TASK_COMPLETION.getValue());
        assertThat(chunk.getControllerPayload().getMetadata()).containsEntry("task_id", "task-1");
        assertThat(chunk.getControllerPayload().getData()).hasSize(1);
        DataFrame.JsonDataFrame data = (DataFrame.JsonDataFrame) chunk.getControllerPayload().getData().get(0);
        assertThat(data.data()).containsEntry("output", "inspect repo").containsEntry("run_kind", "outer_loop")
                .containsEntry("is_follow_up", true);
    }

    @Test
    void coreTaskLoopExecutorShouldEmitInnerStreamChunksBeforeCompletion() {
        ControllerConfig config = new ControllerConfig();
        TaskManager taskManager = new TaskManager(config);
        Task task = new Task("session-1", "task-stream", TaskLoopEventExecutor.DEEP_TASK_TYPE);
        task.setStatus(TaskStatus.SUBMITTED);
        task.setDescription("stream repo");
        taskManager.addTask(task);

        CoreTaskLoopEventExecutor executor = new CoreTaskLoopEventExecutor(
                new TaskExecutorDependencies(config, new Object(), new ContextEngine(), taskManager,
                        new EventQueue(config)),
                inputs -> Map.of("output", "done", "stream_chunks", List.of("token-1", Map.of("delta", "token-2"))));

        List<ControllerOutputChunk> chunks = new ArrayList<>();
        executor.executeAbility("task-stream", new AgentSessionApi("session-1")).forEachRemaining(chunks::add);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).isLastChunk()).isFalse();
        assertThat(chunks.get(0).getControllerPayload().getType()).isEqualTo("processing");
        assertThat(chunks.get(0).getControllerPayload().getMetadata()).containsEntry("task_id", "task-stream")
                .containsEntry("stream_kind", "inner_agent");
        assertThat(((DataFrame.TextDataFrame) chunks.get(0).getControllerPayload().getData().get(0)).text())
                .isEqualTo("token-1");
        assertThat(chunks.get(1).getControllerPayload().getType()).isEqualTo("processing");
        assertThat(((DataFrame.JsonDataFrame) chunks.get(1).getControllerPayload().getData().get(0)).data())
                .containsEntry("delta", "token-2");
        assertThat(chunks.get(2).isLastChunk()).isTrue();
        assertThat(chunks.get(2).getControllerPayload().getType()).isEqualTo(EventType.TASK_COMPLETION.getValue());
        assertThat(((DataFrame.JsonDataFrame) chunks.get(2).getControllerPayload().getData().get(0)).data())
                .containsEntry("output", "done");
    }

    @Test
    void coreTaskLoopExecutorShouldFireAfterTaskIterationRails() {
        ControllerConfig config = new ControllerConfig();
        TaskManager taskManager = new TaskManager(config);
        Task task = new Task("session-1", "task-after-iteration", TaskLoopEventExecutor.DEEP_TASK_TYPE);
        task.setStatus(TaskStatus.SUBMITTED);
        task.setDescription("inspect repo");
        task.setMetadata(Map.of("_handler_round_id", 7, "run_kind", "outer_loop", "is_follow_up", true));
        taskManager.addTask(task);
        RecordingTaskIterationRail rail = new RecordingTaskIterationRail();
        DeepAgent agent = HarnessFactory.createDeepAgent(
                AgentCard.builder().name("task-iteration-agent").description("Task iteration agent").build(),
                DeepAgentConfig.builder().rails(List.of(rail)).build(),
                Workspace.builder().rootPath(tempDir.toString()).build());
        agent.ensureInitialized();

        CoreTaskLoopEventExecutor executor = new CoreTaskLoopEventExecutor(
                new TaskExecutorDependencies(config, new Object(), new ContextEngine(), taskManager,
                        new EventQueue(config)),
                agent, inputs -> Map.of("output", inputs.get("query"), "usage",
                        Map.of("prompt_tokens", 3, "completion_tokens", 8, "total_tokens", 11)));

        ControllerOutputChunk chunk =
            executor.executeAbility("task-after-iteration", new AgentSessionApi("session-1")).next();

        assertThat(chunk.getControllerPayload().getType()).isEqualTo(EventType.TASK_COMPLETION.getValue());
        assertThat(rail.contexts).hasSize(1);
        TaskIterationContext ctx = rail.contexts.get(0);
        assertThat(ctx.taskId()).isEqualTo("task-after-iteration");
        assertThat(ctx.sessionId()).isEqualTo("session-1");
        assertThat(ctx.getRound()).isEqualTo(7);
        assertThat(ctx.isFollowUp()).isTrue();
        assertThat(ctx.getResult()).containsEntry("output", "inspect repo");
        assertThat(ctx.tokenUsage()).isEqualTo(11);
        assertThat(ctx.resolvedUsageMetadata().getInputTokens()).isEqualTo(3);
        assertThat(ctx.resolvedUsageMetadata().getOutputTokens()).isEqualTo(8);
    }
}
