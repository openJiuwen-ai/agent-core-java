/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for Workflow CycleChecker module.
 * <p>
 * Mirrors Python's {@code test_cycle_checker_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.test_cycle_checker_integration}.
 */
class TestCycleCheckerIntegration {

    @Nested
    class TestCycleCheckerIntegrationInner {

        private MockLlm mockLlm(String content) {
            return new MockLlm(content);
        }

        private CycleChecker cycleChecker(MockLlm mockLlm) {
            return new CycleChecker(mockLlm);
        }

        @Test
        void testCycleCheckerInitialization() {
            MockLlm mockLlm = mockLlm("{\"need_refined\": false}");
            CycleChecker cycleChecker = cycleChecker(mockLlm);

            assertThat(cycleChecker.getLlm()).isSameAs(mockLlm);
        }

        @Test
        void testParseCycleResultJsonNoCycle() {
            CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(
                    "{\"need_refined\": false, \"loop_desc\": \"\"}");

            assertThat(result.needRefined()).isFalse();
            assertThat(result.loopDesc()).isEmpty();
        }

        @Test
        void testParseCycleResultJsonWithCycle() {
            CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(
                    "{\"need_refined\": true, \"loop_desc\": \"Found cycle\"}");

            assertThat(result.needRefined()).isTrue();
            assertThat(result.loopDesc()).isEqualTo("Found cycle");
        }

        @Test
        void testParseCycleResultJsonWithMarkdown() {
            CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(
                    "```json\n{\"need_refined\": true, \"loop_desc\": \"Cycle detected\"}\n```");

            assertThat(result.needRefined()).isTrue();
            assertThat(result.loopDesc()).isEqualTo("Cycle detected");
        }
    }

    @Nested
    class TestCycleCheckerMermaidCode {

        private MockLlm mockLlm(String content) {
            return new MockLlm(content);
        }

        private CycleChecker cycleChecker(MockLlm mockLlm) {
            return new CycleChecker(mockLlm);
        }

        @Test
        void testCheckMermaidCycleSimple() {
            MockLlm mockLlm = mockLlm("{\"need_refined\": false}");
            CycleChecker cycleChecker = cycleChecker(mockLlm);

            String result = cycleChecker.checkMermaidCycle("graph TD\n  A --> B");

            assertThat(result).isNotNull();
            assertThat(mockLlm.called).isTrue();
        }

        @Test
        void testCheckAndParseIntegration() {
            MockLlm mockLlm = mockLlm("{\"need_refined\": false, \"loop_desc\": \"\"}");
            CycleChecker cycleChecker = cycleChecker(mockLlm);

            CycleChecker.CycleResult result = cycleChecker.checkAndParse("graph TD\n  A --> B");

            assertThat(result.needRefined()).isFalse();
            assertThat(result.loopDesc()).isEmpty();
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
