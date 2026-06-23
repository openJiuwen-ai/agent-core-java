/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.subagent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code test_sessions_tools.py} in
 * {@code tests/unit_tests/harness/tools/test_sessions_tools.py}.
 */
class SessionToolsPythonParityTest {

    @Test
    void upsertAndGet() {
        SessionTools.InMemorySessionToolkit toolkit = new SessionTools.InMemorySessionToolkit();

        toolkit.upsertRunning("t1", "sub1", "desc");
        SessionTools.SessionTaskRow row = toolkit.get("t1");

        assertThat(row).isNotNull();
        assertThat(row.taskId()).isEqualTo("t1");
        assertThat(row.subSessionId()).isEqualTo("sub1");
        assertThat(row.description()).isEqualTo("desc");
        assertThat(row.status()).isEqualTo("running");
    }

    @Test
    void markCompletedFailedCanceledClear() {
        SessionTools.InMemorySessionToolkit toolkit = new SessionTools.InMemorySessionToolkit();

        toolkit.upsertRunning("t1", "sub1", "d");
        toolkit.markCompleted("t1", "ok");
        assertThat(toolkit.get("t1").status()).isEqualTo("completed");
        assertThat(toolkit.get("t1").result()).isEqualTo("ok");

        toolkit.upsertRunning("t2", "sub2", "d2");
        toolkit.markFailed("t2", "boom");
        assertThat(toolkit.get("t2").status()).isEqualTo("error");
        assertThat(toolkit.get("t2").error()).isEqualTo("boom");

        toolkit.upsertRunning("t3", "sub3", "d3");
        toolkit.markCanceled("t3");
        assertThat(toolkit.get("t3").status()).isEqualTo("canceled");

        toolkit.clear();
        assertThat(toolkit.listAll()).isEmpty();
    }

    @Test
    void emptyListReturnsChineseNoBackgroundMessage() throws Exception {
        SessionTools.SessionsListTool tool =
                new SessionTools.SessionsListTool(new SessionTools.InMemorySessionToolkit());

        ToolOutput output = (ToolOutput) tool.invoke(Map.of());

        assertThat(output.isSuccess()).isTrue();
        assertThat(String.valueOf(output.getData())).contains("\u6CA1\u6709\u540E\u53F0");
    }

    @Test
    void listOneRowIncludesTaskDescriptionAndStatus() throws Exception {
        SessionTools.InMemorySessionToolkit toolkit = new SessionTools.InMemorySessionToolkit();
        toolkit.upsertRunning("tid", "sid", "hello");
        SessionTools.SessionsListTool tool = new SessionTools.SessionsListTool(toolkit);

        ToolOutput output = (ToolOutput) tool.invoke(Map.of());

        assertThat(output.isSuccess()).isTrue();
        assertThat(String.valueOf(output.getData()))
                .contains("tid")
                .contains("hello")
                .contains("running");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelRejectsInvalidInputs() {
        SessionTools.SessionsCancelTool tool =
                new SessionTools.SessionsCancelTool(new SessionTools.InMemorySessionToolkit());

        assertThatThrownBy(() -> tool.invoke((Map<String, Object>) (Object) "not a dict"))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void cancelRequiresTaskId() {
        SessionTools.SessionsCancelTool tool =
                new SessionTools.SessionsCancelTool(new SessionTools.InMemorySessionToolkit());

        assertThatThrownBy(() -> tool.invoke(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("task_id");
    }

    @Test
    void cancelRejectsMissingTask() {
        SessionTools.SessionsCancelTool tool =
                new SessionTools.SessionsCancelTool(new SessionTools.InMemorySessionToolkit());

        assertThatThrownBy(() -> tool.invoke(Map.of("task_id", "nope")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelSuccessMarksTaskCanceled() throws Exception {
        SessionTools.InMemorySessionToolkit toolkit = new SessionTools.InMemorySessionToolkit();
        toolkit.upsertRunning("tid", "sid", "d");
        SessionTools.SessionsCancelTool tool = new SessionTools.SessionsCancelTool(toolkit);

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("task_id", "tid"));
        Map<String, Object> data = (Map<String, Object>) output.getData();

        assertThat(output.isSuccess()).isTrue();
        assertThat(toolkit.get("tid").status()).isEqualTo("canceled");
        assertThat(data).containsEntry("task_id", "tid").containsEntry("status", "canceled");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancelSchedulerFalseLeavesTaskRunning() throws Exception {
        SessionTools.InMemorySessionToolkit toolkit = new SessionTools.InMemorySessionToolkit();
        toolkit.upsertRunning("tid", "sid", "d");
        SessionTools.SessionsCancelTool tool = new SessionTools.SessionsCancelTool(toolkit);

        ToolOutput output = (ToolOutput) tool.invoke(
                Map.of("task_id", "tid"),
                Map.of("cancel_success", false)
        );
        Map<String, Object> data = (Map<String, Object>) output.getData();

        assertThat(output.isSuccess()).isFalse();
        assertThat(toolkit.get("tid").status()).isEqualTo("running");
        assertThat(data).containsEntry("status", "running");
    }

    @Test
    void spawnRequiresTaskLoopInput() {
        SessionTools.SessionsSpawnTool tool =
                new SessionTools.SessionsSpawnTool(new SessionTools.InMemorySessionToolkit());

        assertThatThrownBy(() -> tool.invoke(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("task_description");
    }

    @Test
    @SuppressWarnings("unchecked")
    void spawnSubmitsTaskAndRecordsRunningRow() throws Exception {
        SessionTools.InMemorySessionToolkit toolkit = new SessionTools.InMemorySessionToolkit();
        SessionTools.SessionsSpawnTool tool = new SessionTools.SessionsSpawnTool(toolkit);

        ToolOutput output = (ToolOutput) tool.invoke(
                Map.of("subagent_type", "foo", "task_description", "do work"),
                Map.of("session_id", "sess-1")
        );
        Map<String, Object> data = (Map<String, Object>) output.getData();

        assertThat(output.isSuccess()).isTrue();
        assertThat(data).containsEntry("status", "pending");
        assertThat(String.valueOf(data.get("sub_session_id"))).startsWith("sess-1_sub_");
        assertThat(toolkit.listAll()).hasSize(1);
        assertThat(toolkit.listAll().getFirst().status()).isEqualTo("running");
        assertThat(toolkit.listAll().getFirst().description()).isEqualTo("do work");
    }

    @Test
    void buildSessionToolsReturnsThreeStableTools() {
        List<Tool> tools = SessionTools.buildSessionTools(new SessionTools.InMemorySessionToolkit());

        assertThat(tools).hasSize(3);
        assertThat(tools)
                .extracting(tool -> tool.getCard().getName())
                .containsExactlyInAnyOrder("sessions_list", "sessions_spawn", "sessions_cancel");
    }
}
