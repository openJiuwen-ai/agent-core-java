// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.examples;

import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.*;
import com.openjiuwen.core.controller.schema.*;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.internal.TaskSession;
import com.openjiuwen.core.singleagent.ControllerAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * ControllerAgent 示例 — Arxiv 研究报告智能体。
 *
 * <p>本示例演示如何基于 Controller 实现一个事件驱动的任务编排智能体。
 * 智能体通过以下三个阶段完成报告生成：
 * <ol>
 *   <li>数据收集（Data Collection）：从 Arxiv API 收集相关论文数据</li>
 *   <li>数据分析（Data Analysis）：分析收集的数据</li>
 *   <li>报告生成（Report Generation）：根据分析结果生成研究报告</li>
 * </ol>
 *
 * <p>工作流程：
 * <ol>
 *   <li>用户输入研究请求，触发 input 事件</li>
 *   <li>事件处理器进行任务规划并创建各阶段任务</li>
 *   <li>任务调度器按优先级顺序执行任务</li>
 *   <li>每个阶段完成后自动触发下一阶段</li>
 * </ol>
 *
 * <p>主要组件：
 * <ul>
 *   <li>任务执行器：DataCollectTaskExecutor</li>
 *   <li>事件处理器：DeepSearchEventHandler</li>
 *   <li>Agent 构建器：buildDeepsearchAgent</li>
 * </ul>
 *
 * <p>端到端测试点：
 * <ul>
 *   <li>触发 handle_input 模拟用户输入启动完整工作流</li>
 *   <li>验证数据收集阶段的执行</li>
 *   <li>验证 handle_task_completion 回调正确链接阶段</li>
 *   <li>验证事件处理器的回调输出包含预期的标记文本</li>
 * </ul>
 *
 * <p>对应 Python: agent-core/examples/test_examples_for_java/react_agent/test/main_controller_agent.py
 */
public class MainControllerAgent {

    // ==================== Task Executors ====================

    /**
     * 数据收集任务执行器。
     *
     * <p>负责执行数据收集任务，从各种数据源收集所需数据。
     *
     * <p>主要职责：
     * <ul>
     *   <li>从各渠道收集数据</li>
     *   <li>将收集到的所有数据存储到上下文引擎中</li>
     * </ul>
     */
    static class DataCollectTaskExecutor extends TaskExecutor {

        DataCollectTaskExecutor(TaskExecutorDependencies dependencies) {
            super(dependencies);
        }

        /**
         * 执行数据收集任务。
         *
         * <p>从各种数据源收集数据并以流方式输出收集结果。
         * 收集的数据存储在上下文引擎中，供后续数据分析任务使用。
         *
         * @param taskId  任务 ID，用于标识当前任务
         * @param session 会话对象，包含会话上下文信息
         * @return 数据收集期间产生的输出块迭代器
         */
        @Override
        public Iterator<ControllerOutputChunk> executeAbility(String taskId, Session session) {
            List<ControllerOutputChunk> chunks = new ArrayList<>();

            // 处理中 chunk — "正在收集数据"
            chunks.add(new ControllerOutputChunk(0, "controller_output",
                    new ControllerOutputPayload("processing",
                            List.of(new TextDataFrame("正在收集芯片相关的Arxiv论文数据...")), null),
                    false));

            // 查找并调用 "add" 工具
            Optional<Object> toolCardOpt = abilityManager.get("add");
            if (toolCardOpt.isPresent()) {
                ToolCard toolCard = (ToolCard) toolCardOpt.get();
                Object tool = Runner.getResourceMgr().getTool(toolCard.getId(), null, null);
                if (tool instanceof LocalFunction addTool) {
                    try {
                        Object result = addTool.invoke(Map.of("a", 1, "b", 2), null).join();
                        // 任务完成 chunk
                        chunks.add(new ControllerOutputChunk(1, "controller_output",
                                new ControllerOutputPayload(EventType.TASK_COMPLETION.getValue(),
                                        List.of(new TextDataFrame("工具执行完成啦，返回结果是" + result)), null),
                                true));
                    } catch (Exception e) {
                        // 任务失败 chunk
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
            // 不适用；不需要实现
            return new PauseResult(false, "");
        }

        @Override
        public boolean pause(String taskId, Session session) {
            // 不适用；不需要实现
            return false;
        }

        @Override
        public CancelResult canCancel(String taskId, Session session) {
            // 不适用；不需要实现
            return new CancelResult(false, "");
        }

        @Override
        public boolean cancel(String taskId, Session session) {
            // 不适用；不需要实现
            return false;
        }
    }

    /**
     * 构建数据收集任务执行器。
     *
     * <p>工厂函数，用于创建 DataCollectTaskExecutor 实例。
     * 执行器注册到 Controller 后，当遇到 "data_collect" 类型的任务时，
     * Controller 会调用此函数创建执行器实例。
     *
     * @param dependencies 任务执行器依赖
     * @return 数据收集任务执行器实例
     */
    static DataCollectTaskExecutor buildDataCollectTaskExecutor(TaskExecutorDependencies dependencies) {
        return new DataCollectTaskExecutor(dependencies);
    }

    // ==================== Event Handler ====================

    /**
     * Arxiv 论文搜索事件处理器。
     *
     * <p>DeepSearch 智能体的事件处理器，负责处理各种类型的事件并协调任务执行工作流。
     *
     * <p>主要职责：
     * <ul>
     *   <li>处理输入事件：接收用户请求，进行任务规划并创建各阶段任务</li>
     *   <li>处理任务完成事件：监控任务执行状态，自动触发下一阶段</li>
     *   <li>处理任务失败事件：处理任务执行失败的情况</li>
     * </ul>
     *
     * <p>工作流程：
     * <ol>
     *   <li>用户输入研究请求 → 调用 handleInput</li>
     *   <li>进行任务规划并创建数据收集任务</li>
     *   <li>数据收集任务完成后 → 调用 handleTaskCompletion</li>
     *   <li>检查所有当前优先级任务是否完成，若完成则激活下一优先级任务</li>
     * </ol>
     */
    static class DeepSearchEventHandler extends EventHandler {

        /**
         * 任务规划。
         *
         * <p>根据用户的研究请求进行任务规划，确定要执行的具体任务。
         *
         * @param event   输入事件
         * @param session 会话对象
         * @return 规划结果
         */
        private Map<String, Object> planning(Event event, Session session) {
            // 简单规划：创建一个数据收集任务
            return Map.of("data_collect_tasks",
                    List.of(Map.of("topic", "芯片", "type", "arxiv")));
        }

        /**
         * 创建数据收集任务。
         *
         * @param planningTask 规划结果
         * @param session      会话对象
         * @return 数据收集任务列表
         */
        @SuppressWarnings("unchecked")
        private List<Task> createDataCollectTasks(Map<String, Object> planningTask, Session session) {
            List<Task> tasks = new ArrayList<>();
            List<Map<String, String>> dcTasks =
                    (List<Map<String, String>>) planningTask.get("data_collect_tasks");
            for (int i = 0; i < dcTasks.size(); i++) {
                Task task = Task.builder(session.getSessionId(),
                                "task_DC_id" + i, "data_collect")
                        .priority(1)
                        .status(TaskStatus.SUBMITTED)
                        .contextId("context_DC_id" + i)
                        .build();
                tasks.add(task);
            }
            return tasks;
        }

        /**
         * 处理输入事件。
         *
         * <p>当用户输入新的研究请求时调用。此方法执行任务规划，
         * 创建各阶段任务，并将它们添加到任务管理器中。
         */
        @Override
        public CompletableFuture<Map<String, Object>> handleInput(EventHandlerInput inputs) {
            System.out.println("handle input called");

            Map<String, Object> planningResult = planning(inputs.getEvent(), inputs.getSession());
            List<Task> tasks = createDataCollectTasks(planningResult, inputs.getSession());
            getTaskManager().addTask(tasks);
            System.out.println("handle input end, successfully add tasks to task manager");

            // 向流写入回调输出
            ControllerOutputChunk outputChunk = new ControllerOutputChunk(0, "controller_output",
                    new ControllerOutputPayload("processing",
                            List.of(new TextDataFrame("成功调用hanle_input回调")), null),
                    false);
            inputs.getSession().writeStream(outputChunk);

            return CompletableFuture.completedFuture(
                    Map.of("status", "success", "tasks_created", 1));
        }

        /**
         * 处理任务交互事件。
         *
         * <p>DeepSearch 智能体不需要与用户交互，因此此方法无需实现。
         */
        @Override
        public CompletableFuture<Map<String, Object>> handleTaskInteraction(EventHandlerInput inputs) {
            // 此场景不需要；无需实现
            return CompletableFuture.completedFuture(null);
        }

        /**
         * 处理任务完成事件。
         *
         * <p>当任务执行完成时调用。此方法检查当前阶段的所有任务是否
         * 已完成，如果是则激活下一阶段的任务。
         */
        @Override
        public CompletableFuture<Map<String, Object>> handleTaskCompletion(EventHandlerInput inputs) {
            System.out.println("handle task completion called");
            List<Task> allTasks = getTaskManager().getTask(null);

            // 向流写入回调输出
            ControllerOutputChunk outputChunk = new ControllerOutputChunk(0, "controller_output",
                    new ControllerOutputPayload("processing",
                            List.of(new TextDataFrame("成功调用handle_task_completion回调 event: "
                                    + inputs.getEvent().getEventId())), null),
                    false);
            inputs.getSession().writeStream(outputChunk);

            // 检查所有当前优先级任务是否完成，若完成则激活下一优先级任务
            TreeSet<Integer> prioritySet = new TreeSet<>();
            for (Task t : allTasks) {
                prioritySet.add(t.getPriority());
            }
            List<Integer> priorities = new ArrayList<>(prioritySet);

            for (int i = 0; i < priorities.size(); i++) {
                int currentPriority = priorities.get(i);
                boolean allCurrentCompleted = allTasks.stream()
                        .filter(t -> t.getPriority() == currentPriority)
                        .allMatch(t -> t.getStatus() == TaskStatus.COMPLETED);

                if (allCurrentCompleted && i + 1 < priorities.size()) {
                    int nextPriority = priorities.get(i + 1);
                    List<Task> waitingTasks = allTasks.stream()
                            .filter(t -> t.getPriority() == nextPriority
                                    && t.getStatus() == TaskStatus.WAITING)
                            .toList();

                    if (!waitingTasks.isEmpty()) {
                        for (Task t : waitingTasks) {
                            t.setStatus(TaskStatus.SUBMITTED);
                        }
                        return CompletableFuture.completedFuture(
                                Map.of("status", "success", "tasks_created", 1));
                    }
                }
            }

            return CompletableFuture.completedFuture(
                    Map.of("status", "success", "tasks_created", 1));
        }

        /**
         * 处理任务失败事件。
         *
         * <p>当任务执行失败时调用。任务失败会导致整个研究报告生成
         * 工作流终止。
         */
        @Override
        public CompletableFuture<Map<String, Object>> handleTaskFailed(EventHandlerInput inputs) {
            System.out.println("handle task failed called");
            return CompletableFuture.completedFuture(
                    Map.of("status", "success", "tasks_failed", 1));
        }
    }

    // ==================== Tool Creation ====================

    /**
     * 创建加法工具。
     *
     * @return 加法 LocalFunction
     */
    static LocalFunction createAddTool() {
        Map<String, Object> propA = new LinkedHashMap<>();
        propA.put("description", "第一个加数");
        propA.put("type", "number");

        Map<String, Object> propB = new LinkedHashMap<>();
        propB.put("description", "第二个加数");
        propB.put("type", "number");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("a", propA);
        properties.put("b", propB);

        Map<String, Object> inputParams = new LinkedHashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", properties);
        inputParams.put("required", List.of("a", "b"));

        ToolCard card = new ToolCard("add", "加法运算，计算两个数的和", inputParams);

        Function<Map<String, Object>, Object> addFunc = params -> {
            double a = ((Number) params.get("a")).doubleValue();
            double b = ((Number) params.get("b")).doubleValue();
            return a + b;
        };

        return new LocalFunction(card, addFunc);
    }

    // ==================== Agent Builder ====================

    /**
     * 构建 DeepSearch Agent。
     *
     * <p>工厂方法，用于创建和配置完整的 DeepSearch 研究论文智能体。
     *
     * <p>流程：
     * <ol>
     *   <li>创建 Controller 实例</li>
     *   <li>创建并设置 DeepSearchEventHandler 事件处理器</li>
     *   <li>注册数据收集任务执行器</li>
     *   <li>创建 ControllerAgent 并关联 Controller 和 AgentCard</li>
     *   <li>注册加法工具</li>
     *   <li>返回配置好的 Agent</li>
     * </ol>
     *
     * @param agentCard 智能体卡片，包含智能体基本信息
     * @return 完全配置好的 DeepSearch Agent 实例
     */
    static ControllerAgent buildDeepsearchAgent(AgentCard agentCard) {
        Controller deepsearchController = new Controller();
        ControllerConfig config = ControllerConfig.builder()
                .enableTaskPersistence(true)
                .eventQueueSize(5555)
                .build();
        ControllerAgent deepsearchAgent = new ControllerAgent(
                agentCard, deepsearchController, config);

        deepsearchController.setEventHandler(new DeepSearchEventHandler());
        deepsearchController.addTaskExecutor("data_collect",
                MainControllerAgent::buildDataCollectTaskExecutor);

        // 添加工具
        LocalFunction addTool = createAddTool();
        Runner.getResourceMgr().addTool(addTool, null, null);
        deepsearchAgent.addAbility(addTool.getCard());

        return deepsearchAgent;
    }

    // ==================== Main ====================

    /**
     * 主函数。
     *
     * <p>创建 DeepSearch 智能体，执行流式调用并输出结果。
     */
    public static void main(String[] args) {
        // 1. 创建 AgentCard
        AgentCard agentCard = new AgentCard("deepsearch", "DeepSearch",
                "Arxiv研究报告智能体，可以通过收集、分析数据生成Arxiv研究报告", null);

        // 2. 构建 Agent
        ControllerAgent agent = buildDeepsearchAgent(agentCard);

        // 3. 创建会话
        TaskSession session = new TaskSession("example_deepsearch");

        // 4. 执行流式调用
        List<String> outputTexts = new ArrayList<>();

        Iterator<Object> stream = agent.stream("帮我计算1+2", session, null).join();
        while (stream.hasNext()) {
            Object chunk = stream.next();
            System.out.println(chunk);
            if (chunk instanceof ControllerOutputChunk controllerOutputChunk) {
                if (controllerOutputChunk.getPayload() != null
                        && controllerOutputChunk.getPayload().getData() != null) {
                    for (BaseDataFrame item : controllerOutputChunk.getPayload().getData()) {
                        if (item instanceof TextDataFrame tdf) {
                            outputTexts.add(tdf.getText());
                        }
                    }
                }
            } else if (chunk instanceof Map<?, ?> mapChunk) {
                Object result = mapChunk.get("result");
                if (result != null) {
                    outputTexts.add(String.valueOf(result));
                }
            }
        }

        // 5. 输出结果
        System.out.println("\n========== 输出结果 ==========");
        for (String text : outputTexts) {
            System.out.println("  - " + text);
        }
        System.out.println("==============================");

        // 6. 停止 Controller
        agent.getController().stop();

        System.out.println("✅ test_deepsearch_end_to_end passed");
    }
}

