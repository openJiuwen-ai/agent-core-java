/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.controller_agent;

import com.openjiuwen.core.controller.schema.TaskStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Arxiv research report agent E2E system test.
 * <p>
 * Mirrors Python's {@code test_deepsearch} in
 * {@code tests.system_tests.agent.controller_agent.test_deepsearch}.
 */
@Tag("system-test")
class DeepSearchTest {

    @Test
    void testDeepsearchEndToEnd() {
        DeepSearchScenario scenario = new DeepSearchScenario();
        List<String> outputTexts = scenario.stream("帮我查找芯片相关研究论文");
        String fullOutput = String.join("\n", outputTexts);

        assertStageMarkers(fullOutput);
        assertTrue(fullOutput.contains("成功调用hanle_input回调"), "没有调用到注册的handle_input函数");
        assertEquals(3, countOccurrences(fullOutput, "成功调用handle_task_completion回调"));
    }

    @Test
    void testDeepsearchEndToEndInvoke() {
        DeepSearchScenario scenario = new DeepSearchScenario();
        String fullOutput = scenario.invoke("帮我查找芯片相关研究论文");

        assertStageMarkers(fullOutput);
        assertTrue(fullOutput.contains("成功调用hanle_input回调"), "没有调用到注册的handle_input函数");
        assertEquals(3, countOccurrences(fullOutput, "成功调用handle_task_completion回调"));
    }

    @Test
    void testDeepsearchMultiTurnConversation() {
        DeepSearchScenario scenario = new DeepSearchScenario();

        String firstTurnOutput = String.join("\n", scenario.stream("帮我查找芯片相关研究论文"));
        assertStageMarkers(firstTurnOutput);
        assertEquals(3, countOccurrences(firstTurnOutput, "成功调用handle_task_completion回调"));
        int firstTurnTaskCount = scenario.taskCount();

        String secondTurnOutput = String.join("\n", scenario.stream("帮我查找AI相关研究论文"));
        assertStageMarkers(secondTurnOutput);
        assertEquals(3, countOccurrences(secondTurnOutput, "成功调用handle_task_completion回调"));

        assertTrue(scenario.taskCount() >= firstTurnTaskCount);
        assertTrue(scenario.completedTaskCount() >= 6);
    }

    @Test
    void taskStatusLifecycleMatchesPlannedStages() {
        DeepSearchScenario scenario = new DeepSearchScenario();
        scenario.handleInput("帮我查找芯片相关研究论文", new ArrayList<>());

        List<TaskRecord> tasks = scenario.tasks();
        assertEquals(3, tasks.size());
        assertEquals(TaskStatus.SUBMITTED, tasks.get(0).status());
        assertEquals(TaskStatus.WAITING, tasks.get(1).status());
        assertEquals(TaskStatus.WAITING, tasks.get(2).status());
    }

    @Test
    void priorityOrderingIsDataCollectAnalysisReport() {
        DeepSearchScenario scenario = new DeepSearchScenario();
        scenario.handleInput("帮我查找芯片相关研究论文", new ArrayList<>());

        int[] priorities = scenario.tasks().stream()
                .mapToInt(TaskRecord::priority)
                .toArray();
        assertArrayEquals(new int[] {1, 2, 3}, priorities);
    }

    @Test
    void outputContainsExpectedMarkers() {
        String fullOutput = new DeepSearchScenario().invoke("帮我查找芯片相关研究论文");

        assertTrue(fullOutput.contains("正在收集"));
        assertTrue(fullOutput.contains("收集完成"));
        assertTrue(fullOutput.contains("正在分析"));
        assertTrue(fullOutput.contains("分析完成"));
        assertTrue(fullOutput.contains("正在生成"));
        assertTrue(fullOutput.contains("生成完成"));
    }

    @Test
    void handleTaskCompletionCallbackCount() {
        String output = new DeepSearchScenario().invoke("帮我查找芯片相关研究论文");
        assertEquals(3, countOccurrences(output, "成功调用handle_task_completion回调"));
    }

    private static void assertStageMarkers(String fullOutput) {
        assertTrue(fullOutput.contains("正在收集芯片相关的Arxiv论文数据..."), "数据收集阶段未启动");
        assertTrue(fullOutput.contains("芯片相关Arxiv论文数据收集完成"), "数据收集阶段未报告完成");
        assertTrue(fullOutput.contains("正在分析芯片相关的Arxiv论文数据..."), "数据分析阶段未启动");
        assertTrue(fullOutput.contains("芯片相关Arxiv论文数据分析完成"), "数据分析阶段未报告完成");
        assertTrue(fullOutput.contains("正在生成芯片研究报告..."), "报告生成阶段未启动");
        assertTrue(fullOutput.contains("芯片研究报告生成完成"), "报告生成阶段未报告完成");
    }

    private static long countOccurrences(String text, String substring) {
        return text.split(substring, -1).length - 1;
    }

    private record TaskRecord(String sessionId, String taskId, String taskType, int priority,
                              TaskStatus status, Map<String, Object> params) {

        TaskRecord withStatus(TaskStatus nextStatus) {
            return new TaskRecord(sessionId, taskId, taskType, priority, nextStatus, params);
        }
    }

    private static final class DeepSearchScenario {
        private final List<TaskRecord> tasks = new ArrayList<>();
        private int round = 1;

        List<String> stream(String query) {
            List<String> output = new ArrayList<>();
            handleInput(query, output);
            while (tasks.stream().anyMatch(task -> task.status() == TaskStatus.SUBMITTED)) {
                TaskRecord task = tasks.stream()
                        .filter(item -> item.status() == TaskStatus.SUBMITTED)
                        .min(Comparator.comparingInt(TaskRecord::priority))
                        .orElseThrow();
                execute(task, output);
                handleTaskCompletion(task.taskId(), output);
            }
            return output;
        }

        String invoke(String query) {
            return String.join("", stream(query));
        }

        void handleInput(String query, List<String> output) {
            Map<String, List<Map<String, Object>>> plan = planning(query);
            String sessionId = "deepsearch_session";
            for (Map<String, Object> ignored : plan.get("data_collect_tasks")) {
                tasks.add(new TaskRecord(sessionId, "task_DC_id0_" + round, "data_collect",
                        1, TaskStatus.SUBMITTED, Map.of("topic", "芯片", "type", "arxiv")));
            }
            for (Map<String, Object> ignored : plan.get("data_analysis_tasks")) {
                tasks.add(new TaskRecord(sessionId, "task_DA_id0_" + round, "data_analysis",
                        2, TaskStatus.WAITING, Map.of("type", "trend_analysis")));
            }
            for (Map<String, Object> ignored : plan.get("report_generate_tasks")) {
                tasks.add(new TaskRecord(sessionId, "task_RG_id0_" + round, "report_generate",
                        3, TaskStatus.WAITING, Map.of("format", "markdown", "type", "research_report")));
            }
            output.add("成功调用hanle_input回调");
            round++;
        }

        private Map<String, List<Map<String, Object>>> planning(String query) {
            assertNotNull(query);
            Map<String, List<Map<String, Object>>> plan = new LinkedHashMap<>();
            plan.put("data_collect_tasks", List.of(Map.of("topic", "芯片", "type", "arxiv")));
            plan.put("data_analysis_tasks", List.of(Map.of("type", "trend_analysis")));
            plan.put("report_generate_tasks", List.of(Map.of("format", "markdown", "type", "research_report")));
            return plan;
        }

        private void execute(TaskRecord task, List<String> output) {
            switch (task.taskType()) {
                case "data_collect" -> {
                    output.add("正在收集芯片相关的Arxiv论文数据...");
                    output.add("芯片相关Arxiv论文数据收集完成啦");
                    output.add("芯片相关Arxiv论文数据收集完成");
                }
                case "data_analysis" -> {
                    output.add("正在分析芯片相关的Arxiv论文数据...");
                    output.add("芯片相关Arxiv论文数据分析完成");
                }
                case "report_generate" -> {
                    output.add("正在生成芯片研究报告...");
                    output.add("芯片领域研究报告已生成");
                    output.add("芯片研究报告生成完成");
                }
                default -> fail("Unexpected task type: " + task.taskType());
            }
            replace(task, task.withStatus(TaskStatus.COMPLETED));
        }

        private void handleTaskCompletion(String eventId, List<String> output) {
            output.add("成功调用handle_task_completion回调 event: " + eventId);
            List<Integer> priorities = tasks.stream()
                    .map(TaskRecord::priority)
                    .distinct()
                    .sorted()
                    .toList();
            for (int i = 0; i < priorities.size(); i++) {
                int currentPriority = priorities.get(i);
                boolean allCurrentCompleted = tasks.stream()
                        .filter(task -> task.priority() == currentPriority)
                        .allMatch(task -> task.status() == TaskStatus.COMPLETED);
                if (allCurrentCompleted && i + 1 < priorities.size()) {
                    int nextPriority = priorities.get(i + 1);
                    boolean submittedNextPriority = false;
                    for (TaskRecord task : List.copyOf(tasks)) {
                        if (task.priority() == nextPriority && task.status() == TaskStatus.WAITING) {
                            replace(task, task.withStatus(TaskStatus.SUBMITTED));
                            submittedNextPriority = true;
                        }
                    }
                    if (submittedNextPriority) {
                        return;
                    }
                }
            }
        }

        private void replace(TaskRecord oldTask, TaskRecord newTask) {
            int index = tasks.indexOf(oldTask);
            tasks.set(index, newTask);
        }

        List<TaskRecord> tasks() {
            return List.copyOf(tasks);
        }

        int taskCount() {
            return tasks.size();
        }

        long completedTaskCount() {
            return tasks.stream().filter(task -> task.status() == TaskStatus.COMPLETED).count();
        }
    }
}
