// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller;

import com.openjiuwen.core.controller.modules.*;
import com.openjiuwen.core.controller.schema.*;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.internal.TaskSession;
import com.openjiuwen.core.singleagent.ControllerAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Controller integration test — end-to-end tool invocation workflow.
 *
 * <p>Converted from: test_controller_invoke_tool.py
 *
 * <p>Demonstrates a complete workflow with:
 * <ol>
 *   <li>User input triggers handle_input → task planning</li>
 *   <li>Task executor invokes a registered tool (add function)</li>
 *   <li>Task completion triggers handle_task_completion callback</li>
 *   <li>Output from both event handler and task executor is streamed</li>
 * </ol>
 */
@DisplayName("Controller Invoke Tool Test")
class ControllerInvokeToolTest {

    private static final Logger logger = LoggerFactory.getLogger(ControllerInvokeToolTest.class);

    // ==================== Task Executors ====================

    /**
     * Data collection task executor that uses a registered tool (add).
     */
    static class DataCollectTaskExecutor extends TaskExecutor {
        DataCollectTaskExecutor(TaskExecutorDependencies deps) {
            super(deps);
        }

        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            List<ControllerOutputChunk> chunks = new ArrayList<>();

            // Processing chunk — "collecting data"
            chunks.add(new ControllerOutputChunk(0, "controller_output",
                    new ControllerOutputPayload("processing",
                            List.of(new TextDataFrame("正在收集芯片相关的Arxiv论文数据...")), null),
                    false));

            // Look up and invoke the "add" tool
            Optional<Object> toolCardOpt = abilityManager.get("add");
            if (toolCardOpt.isPresent()) {
                ToolCard toolCard = (ToolCard) toolCardOpt.get();
                Object tool = Runner.getResourceMgr().getTool(toolCard.getId(), null, null);
                if (tool instanceof LocalFunction addTool) {
                    try {
                        Object result = addTool.invoke(Map.of("a", 1, "b", 2), null).join();
                        // Completion chunk
                        chunks.add(new ControllerOutputChunk(1, "controller_output",
                                new ControllerOutputPayload(EventType.TASK_COMPLETION.getValue(),
                                        List.of(new TextDataFrame("工具执行完成啦，返回结果是" + result)), null),
                                true));
                    } catch (Exception e) {
                        // Failure chunk
                        chunks.add(new ControllerOutputChunk(1, "controller_output",
                                new ControllerOutputPayload(EventType.TASK_FAILED.getValue(),
                                        List.of(new TextDataFrame("工具执行失败: " + e.getMessage())), null),
                                true));
                    }
                }
            }
            return chunks.iterator();
        }

        @Override
        public PauseResult canPause(String taskId, Session session) {
            return new PauseResult(false, "");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            return false;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            return new CancelResult(false, "");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            return false;
        }
    }

    // ==================== Event Handler ====================

    /**
     * DeepSearch event handler — creates data collection tasks and chains stages.
     */
    static class DeepSearchEventHandler extends EventHandler {

        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            logger.info("handle input called");

            String sid = inputs.getSession().getSessionId();
            // Simple planning: one data collection task
            Task task = Task.builder(sid, "task_DC_id0", "data_collect")
                    .priority(1)
                    .status(TaskStatus.SUBMITTED)
                    .contextId("context_DC_id0")
                    .build();

            getTaskManager().addTask(List.of(task));
            logger.info("handle input end, successfully add tasks to task manager");

            // Write a processing chunk to stream from inside the handler
            ControllerOutputChunk outputChunk = new ControllerOutputChunk(0, "controller_output",
                    new ControllerOutputPayload("processing",
                            List.of(new TextDataFrame("成功调用hanle_input回调")), null),
                    false);
            inputs.getSession().writeStream(outputChunk);

            return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_created", 1));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            logger.info("handle task completion called");

            // Write a processing chunk from the handler
            ControllerOutputChunk outputChunk = new ControllerOutputChunk(0, "controller_output",
                    new ControllerOutputPayload("processing",
                            List.of(new TextDataFrame("成功调用handle_task_completion回调 event: "
                                    + inputs.getEvent().getEventId())), null),
                    false);
            inputs.getSession().writeStream(outputChunk);

            return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_created", 1));
        }

        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            logger.info("handle task failed called");
            return CompletableFuture.completedFuture(Map.of("status", "success", "tasks_failed", 1));
        }
    }

    // ==================== Helper ====================

    private static LocalFunction createAddTool() {
        ToolCard card = new ToolCard("add", "加法运算，计算两个数的和",
                Map.of("type", "object",
                        "properties", Map.of(
                                "a", Map.of("description", "第一个加数", "type", "number"),
                                "b", Map.of("description", "第二个加数", "type", "number")),
                        "required", List.of("a", "b")));
        return new LocalFunction(card, params -> {
            Number a = (Number) params.get("a");
            Number b = (Number) params.get("b");
            return a.doubleValue() + b.doubleValue();
        });
    }

    // ==================== Test ====================

    @Test
    @DisplayName("End-to-end tool invocation workflow")
    @Timeout(20)
    void testDeepsearchEndToEnd() {
        // Create agent
        AgentCard agentCard = new AgentCard("deepsearch", "DeepSearch",
                "Arxiv研究报告智能体", null);

        Controller controller = new Controller();
        ControllerConfig config = ControllerConfig.builder()
                .enableTaskPersistence(true)
                .eventQueueSize(5555)
                .scheduleInterval(0.1)
                .build();
        ControllerAgent agent = new ControllerAgent(agentCard, controller, config);

        controller.setEventHandler(new DeepSearchEventHandler());
        controller.addTaskExecutor("data_collect", DataCollectTaskExecutor::new);

        // Register add tool
        LocalFunction addTool = createAddTool();
        Runner.getResourceMgr().addTool(addTool, null, null);
        agent.addAbility(addTool.getCard());

        // Execute stream
        TaskSession session = new TaskSession("example_deepsearch");
        InputEvent inputEvent = InputEvent.fromUserInput("帮我计算1+2");

        List<String> outputTexts = new ArrayList<>();
        Iterator<ControllerOutputChunk> stream = controller.stream(inputEvent, session, null, null);
        while (stream.hasNext()) {
            ControllerOutputChunk chunk = stream.next();
            logger.info("chunk: {}", chunk);
            if (chunk.getPayload() != null && chunk.getPayload().getData() != null) {
                for (BaseDataFrame frame : chunk.getPayload().getData()) {
                    if (frame instanceof TextDataFrame tdf) {
                        outputTexts.add(tdf.getText());
                    }
                }
            }
        }

        // Verify output
        assertFalse(outputTexts.isEmpty(), "Should have output");
        logger.info("Collected {} outputs:", outputTexts.size());
        outputTexts.forEach(t -> logger.info("  - {}", t));

        // Verify handler callbacks were invoked
        assertTrue(outputTexts.stream().anyMatch(t -> t.contains("hanle_input")),
                "Should contain handle_input callback output");
        assertTrue(outputTexts.stream().anyMatch(t -> t.contains("handle_task_completion")),
                "Should contain handle_task_completion callback output");

        // Verify tool execution result (1 + 2 = 3.0)
        assertTrue(outputTexts.stream().anyMatch(t -> t.contains("3")),
                "Should contain the tool result (3)");

        controller.stop();
        logger.info("✅ testDeepsearchEndToEnd passed");
    }
}

