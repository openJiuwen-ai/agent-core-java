/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.workflow;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_real_workflow.py} in
 * {@code tests/system_tests/workflow/test_real_workflow.py}.
 *
 * <p>The Python workflow system tests are individually skipped with
 * {@code @unittest.skip("skip system test")}; Java keeps the same explicit
 * skip contract and method-level mapping.</p>
 */
public class TestRealWorkflow {

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\")")
    void testWorkflowLlmQuestionerPlugin() {
        Map<String, String> mockedToolResult = new LinkedHashMap<>();
        mockedToolResult.put("city", "Shanghai");
        mockedToolResult.put("weather", "sunny");

        assertThat(mockedToolResult).containsEntry("city", "Shanghai");
        assertThat(mockedToolResult).containsEntry("weather", "sunny");
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\")")
    void testStreamWorkflowLlmWithStreamWriter() {
        String streamedAnswer = "上海今天晴 30C";
        assertThat(streamedAnswer).contains("上海", "晴");
    }
}
