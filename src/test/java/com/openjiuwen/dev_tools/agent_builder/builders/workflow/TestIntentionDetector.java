/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.*;

/**
 * Test IntentionDetector functionality.
 * <p>
 * Mirrors Python's {@code test_intention_detector.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_intention_detector.py}.
 */
class TestIntentionDetector {

    /**
     * Test IntentionDetector.formatDialogHistory method.
     */
    static class TestFormatDialogHistory {

        @Test
        void testFormatSingleMessage() {
            List<Map<String, Object>> history = new ArrayList<>();
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", "user");
            msg.put("content", "Hello");
            history.add(msg);

            String result = IntentionDetector.formatDialogHistory(history);

            Assertions.assertTrue(result.contains("User: Hello"));
        }

        @Test
        void testFormatMultipleMessages() {
            List<Map<String, Object>> history = new ArrayList<>();
            Map<String, Object> msg1 = new LinkedHashMap<>();
            msg1.put("role", "user");
            msg1.put("content", "Hello");
            history.add(msg1);

            Map<String, Object> msg2 = new LinkedHashMap<>();
            msg2.put("role", "assistant");
            msg2.put("content", "Hi there!");
            history.add(msg2);

            String result = IntentionDetector.formatDialogHistory(history);

            Assertions.assertTrue(result.contains("User: Hello"));
            Assertions.assertTrue(result.contains("Assistant: Hi there!"));
        }

        @Test
        void testFormatDialogHistoryEmpty() {
            String result = IntentionDetector.formatDialogHistory(new ArrayList<>());
            Assertions.assertEquals("", result);
        }

        @Test
        void testFormatDialogHistoryNull() {
            String result = IntentionDetector.formatDialogHistory(null);
            Assertions.assertEquals("", result);
        }

        @Test
        void testFormatDialogHistoryUnknownRole() {
            List<Map<String, Object>> history = new ArrayList<>();
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", "unknown");
            msg.put("content", "Test");
            history.add(msg);

            String result = IntentionDetector.formatDialogHistory(history);

            Assertions.assertTrue(result.contains("User: Test")); // Unknown roles map to User
        }
    }

    /**
     * Test IntentionDetector.extractIntent method.
     */
    static class TestExtractIntent {

        @Test
        void testExtractIntentWithJsonBlock() {
            String input = "```json\n{\"has_instruction\": true}\n```";
            Map<String, Object> result = IntentionDetector.extractIntent(input);

            Assertions.assertEquals(true, result.get("has_instruction"));
        }

        @Test
        void testExtractIntentWithoutJsonBlock() {
            String input = "{\"has_instruction\": false}";
            Map<String, Object> result = IntentionDetector.extractIntent(input);

            Assertions.assertEquals(false, result.get("has_instruction"));
        }

        @Test
        void testExtractIntentWithProvideProcess() {
            String input = "{\"provide_process\": true}";
            Map<String, Object> result = IntentionDetector.extractIntent(input);

            Assertions.assertEquals(true, result.get("provide_process"));
        }

        @Test
        void testExtractIntentWithNeedRefined() {
            String input = "{\"need_refined\": true}";
            Map<String, Object> result = IntentionDetector.extractIntent(input);

            Assertions.assertEquals(true, result.get("need_refined"));
        }
    }

    /**
     * Test IntentionDetector.detect method.
     */
    static class TestDetect {

        @Test
        void testDetectCreateWorkflow() {
            IntentionDetector detector = new IntentionDetector();

            Assertions.assertEquals(IntentionDetector.Intention.CREATE_WORKFLOW,
                    detector.detect("创建一个数据处理工作流"));

            Assertions.assertEquals(IntentionDetector.Intention.CREATE_WORKFLOW,
                    detector.detect("Create a workflow"));
        }

        @Test
        void testDetectModifyWorkflow() {
            IntentionDetector detector = new IntentionDetector();

            Assertions.assertEquals(IntentionDetector.Intention.MODIFY_WORKFLOW,
                    detector.detect("修改工作流"));

            Assertions.assertEquals(IntentionDetector.Intention.MODIFY_WORKFLOW,
                    detector.detect("Modify the workflow"));
        }

        @Test
        void testDetectUnknown() {
            IntentionDetector detector = new IntentionDetector();

            Assertions.assertEquals(IntentionDetector.Intention.UNKNOWN,
                    detector.detect("这是一个问题"));

            Assertions.assertEquals(IntentionDetector.Intention.UNKNOWN,
                    detector.detect(""));
        }

        @Test
        void testDetectNull() {
            IntentionDetector detector = new IntentionDetector();

            Assertions.assertEquals(IntentionDetector.Intention.UNKNOWN,
                    detector.detect(null));
        }
    }
}