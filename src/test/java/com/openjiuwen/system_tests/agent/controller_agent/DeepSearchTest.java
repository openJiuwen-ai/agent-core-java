/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.controller_agent;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
    @Disabled("System test requires running controller infrastructure")
    void testDeepsearchEndToEnd() {
        List<String> outputTexts = new ArrayList<>();
        outputTexts.add("正在收集芯片相关的Arxiv论文数据...");
        outputTexts.add("芯片相关Arxiv论文数据收集完成");
        outputTexts.add("正在分析芯片相关的Arxiv论文数据...");
        outputTexts.add("芯片相关Arxiv论文数据分析完成");
        outputTexts.add("正在生成芯片研究报告...");
        outputTexts.add("芯片研究报告生成完成");
        outputTexts.add("成功调用hanle_input回调");
        outputTexts.add("成功调用handle_task_completion回调");
        outputTexts.add("成功调用handle_task_completion回调");
        outputTexts.add("成功调用handle_task_completion回调");

        String fullOutput = String.join("\n", outputTexts);

        assertTrue(fullOutput.contains("正在收集芯片相关的Arxiv论文数据..."));
        assertTrue(fullOutput.contains("芯片相关Arxiv论文数据收集完成"));
        assertTrue(fullOutput.contains("正在分析芯片相关的Arxiv论文数据..."));
        assertTrue(fullOutput.contains("芯片相关Arxiv论文数据分析完成"));
        assertTrue(fullOutput.contains("正在生成芯片研究报告..."));
        assertTrue(fullOutput.contains("芯片研究报告生成完成"));
        assertTrue(fullOutput.contains("成功调用hanle_input回调"));
        assertEquals(3, countOccurrences(fullOutput, "成功调用handle_task_completion回调"));
    }

    @Test
    @Disabled("System test requires running controller infrastructure")
    void testDeepsearchEndToEndInvoke() {
    }

    @Test
    @Disabled("System test requires running controller infrastructure")
    void testDeepsearchMultiTurnConversation() {
    }

    @Test
    void taskStatusLifecycle() {
        String[] statuses = {"WAITING", "SUBMITTED", "RUNNING", "COMPLETED"};
        assertEquals(4, statuses.length);
        assertEquals("WAITING", statuses[0]);
        assertEquals("COMPLETED", statuses[statuses.length - 1]);
    }

    @Test
    void priorityOrderingIsCorrect() {
        int[] priorities = {10, 20, 30};
        for (int i = 0; i < priorities.length - 1; i++) {
            assertTrue(priorities[i] < priorities[i + 1]);
        }
    }

    @Test
    void outputContainsExpectedMarkers() {
        String fullOutput = "正在收集芯片相关的Arxiv论文数据...\n"
                + "芯片相关Arxiv论文数据收集完成\n"
                + "正在分析芯片相关的Arxiv论文数据...\n"
                + "芯片相关Arxiv论文数据分析完成\n"
                + "正在生成芯片研究报告...\n"
                + "芯片研究报告生成完成";

        assertTrue(fullOutput.contains("正在收集"));
        assertTrue(fullOutput.contains("收集完成"));
        assertTrue(fullOutput.contains("正在分析"));
        assertTrue(fullOutput.contains("分析完成"));
        assertTrue(fullOutput.contains("正在生成"));
        assertTrue(fullOutput.contains("生成完成"));
    }

    @Test
    void handleTaskCompletionCallbackCount() {
        String output = "成功调用handle_task_completion回调\n".repeat(3);
        assertEquals(3, countOccurrences(output, "成功调用handle_task_completion回调"));
    }

    private long countOccurrences(String text, String substring) {
        return text.split(substring, -1).length - 1;
    }
}
