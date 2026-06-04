/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test CycleChecker functionality.
 * <p>
 * Mirrors Python's {@code test_cycle_checker.py} in
 * {@code tests.unit_tests.dev_tools.agent_builder.builders.workflow.test_cycle_checker}.
 */
class TestCycleChecker {

    @Nested
    class TestCycleCheckerCases {

        private CycleChecker checker(MockLlm llm) {
            return new CycleChecker(llm);
        }

        @Test
        void testParseCycleResultJsonWithCycle() {
            String jsonInput = "```json\n{\"need_refined\": true, \"loop_desc\": \"A->B->A\"}\n```";

            CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(jsonInput);

            assertTrue(result.needRefined());
            assertEquals("A->B->A", result.loopDesc());
        }

        @Test
        void testParseCycleResultJsonNoCycle() {
            String jsonInput = "```json\n{\"need_refined\": false, \"loop_desc\": \"\"}\n```";

            CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(jsonInput);

            assertFalse(result.needRefined());
            assertEquals("", result.loopDesc());
        }

        @Test
        void testParseCycleResultJsonWithoutCodeBlock() {
            String jsonInput = "{\"need_refined\": true, \"loop_desc\": \"cycle detected\"}";

            CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(jsonInput);

            assertTrue(result.needRefined());
            assertEquals("cycle detected", result.loopDesc());
        }

        @Test
        void testParseCycleResultJsonMissingKeys() {
            String jsonInput = "{\"other_key\": \"value\"}";

            CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(jsonInput);

            assertFalse(result.needRefined());
            assertEquals("", result.loopDesc());
        }

        @Test
        void testCheckMermaidCycle() {
            MockLlm llm = new MockLlm("{\"need_refined\": false}");
            CycleChecker checker = checker(llm);

            String result = checker.checkMermaidCycle("graph TD; A-->B");

            assertEquals("{\"need_refined\": false}", result);
            assertTrue(llm.called);
        }

        @Test
        void testCheckAndParse() {
            MockLlm llm = new MockLlm("```json\n{\"need_refined\": true, \"loop_desc\": \"A->B->C->A\"}\n```");
            CycleChecker checker = checker(llm);

            CycleChecker.CycleResult result = checker.checkAndParse("graph TD; A-->B-->C-->A");

            assertTrue(result.needRefined());
            assertEquals("A->B->C->A", result.loopDesc());
        }

        @Test
        void testCheckMermaidCycleGraphUtility() {
            Map<String, List<String>> graph = new LinkedHashMap<>();
            graph.put("A", List.of("B"));
            graph.put("B", List.of("A"));

            assertTrue(CycleChecker.hasCycle(graph));
        }
    }

    static final class MockLlm {
        private final String content;
        private boolean called;

        MockLlm(String content) {
            this.content = content;
        }

        public MockResponse invoke(Object ignored) {
            called = true;
            return new MockResponse(content);
        }
    }

    record MockResponse(String content) {
        public String getContent() {
            return content;
        }
    }
}
