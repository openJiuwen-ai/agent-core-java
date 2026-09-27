/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.modules.TaskScheduler;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link TaskScheduler#cancelSessionTasks(String)}（会话终止防僵尸清扫）。
 *
 * <p>背景：DeepAgent 会话终止（stopTaskLoopRuntime）原先只移除会话不清任务——残留 SUBMITTED
 * 被调度循环每轮 skip 刷屏，残留 WORKING 在已退订事件流上空转。清扫在移除会话前收尾全部
 * 未终结任务；本测试钉死各状态的清扫口径与跨会话隔离。
 */
class TaskSchedulerCancelSessionTasksTest {

    private TaskManager taskManager;
    private TaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        ControllerConfig config = new ControllerConfig();
        config.setDefaultTaskPriority(1);
        taskManager = new TaskManager(config);
        taskScheduler = new TaskScheduler(config, taskManager, new ContextEngine(), null,
                new EventQueue(config), new BaseCard());
    }

    private Task addTask(String sessionId, String taskId, TaskStatus status) {
        Task task = new Task(sessionId, taskId, "test_task");
        task.setDescription("zombie-sweep test " + taskId);
        task.setPriority(1);
        task.setStatus(status);
        taskManager.addTask(task);
        return task;
    }

    @Test
    @DisplayName("清扫取消未终结任务、跳过终结态、不越会话")
    void sweepCancelsNonTerminalSkipsTerminalAndStaysInSession() {
        addTask("session1", "taskA", TaskStatus.SUBMITTED);
        addTask("session1", "taskB", TaskStatus.WORKING);
        addTask("session1", "taskC", TaskStatus.COMPLETED);
        addTask("session2", "taskD", TaskStatus.SUBMITTED);

        int cleaned = taskScheduler.cancelSessionTasks("session1");

        assertEquals(2, cleaned, "只清扫 SUBMITTED+WORKING 两条");

        Map<String, TaskStatus> statuses = statusesOf("session1", "session2");
        assertEquals(TaskStatus.CANCELED, statuses.get("taskA"), "SUBMITTED 落 CANCELED（不再被调度刷屏）");
        assertEquals(TaskStatus.CANCELED, statuses.get("taskB"), "WORKING 经 cancelTask/兜底落 CANCELED");
        assertEquals(TaskStatus.COMPLETED, statuses.get("taskC"), "终结态不动");
        assertEquals(TaskStatus.SUBMITTED, statuses.get("taskD"), "其他会话任务不受影响");
    }

    @Test
    @DisplayName("PAUSED/WAITING/INPUT_REQUIRED 一并清扫（会话已终止无处恢复）")
    void sweepCancelsPausedWaitingAndInputRequired() {
        addTask("session1", "taskP", TaskStatus.PAUSED);
        addTask("session1", "taskW", TaskStatus.WAITING);
        addTask("session1", "taskI", TaskStatus.INPUT_REQUIRED);

        int cleaned = taskScheduler.cancelSessionTasks("session1");

        assertEquals(3, cleaned);
        Map<String, TaskStatus> statuses = statusesOf("session1");
        assertEquals(TaskStatus.CANCELED, statuses.get("taskP"));
        assertEquals(TaskStatus.CANCELED, statuses.get("taskW"));
        assertEquals(TaskStatus.CANCELED, statuses.get("taskI"));
    }

    @Test
    @DisplayName("未知会话/空会话 id 安全返回 0")
    void sweepSafeForUnknownOrBlankSession() {
        addTask("session1", "taskA", TaskStatus.SUBMITTED);

        assertEquals(0, taskScheduler.cancelSessionTasks("no-such-session"));
        assertEquals(0, taskScheduler.cancelSessionTasks(null));
        assertEquals(0, taskScheduler.cancelSessionTasks(""));

        Map<String, TaskStatus> statuses = statusesOf("session1");
        assertEquals(TaskStatus.SUBMITTED, statuses.get("taskA"), "空入参不动任何任务");
        assertNotNull(statuses.get("taskA"));
    }

    private Map<String, TaskStatus> statusesOf(String... sessionIds) {
        Map<String, TaskStatus> out = new java.util.LinkedHashMap<>();
        for (String sessionId : sessionIds) {
            List<Task> tasks = taskManager.getTask(TaskFilter.bySessionId(sessionId));
            out.putAll(tasks.stream().collect(Collectors.toMap(Task::getTaskId, Task::getStatus)));
        }
        return out;
    }
}
