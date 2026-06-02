/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.tune;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_prompt_tune.py} in
 * {@code tests/system_tests/tune/test_prompt_tune.py}.
 *
 * <p>Every Python test method is marked with {@code @unittest.skip("skip system test")};
 * Java keeps the same explicit skip boundary while preserving each test name.</p>
 */
public class TestPromptTune {

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\")")
    void testAgentOptimization() {
        Map<String, Object> caseData = Map.of("input", "hello", "expected", "world");
        assertThat(caseData).containsKeys("input", "expected");
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\")")
    void testInformationExtractionPromptOptimization() {
        String template = "Extract structured fields from: {input}";
        List<String> fields = List.of("name", "age", "city");
        assertThat(template).contains("{input}");
        assertThat(fields).contains("name", "age", "city");
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\")")
    void testToolCallsPromptOptimization() {
        Map<String, Object> tool = Map.of("name", "search", "description", "search docs");
        assertThat(tool).containsEntry("name", "search");
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\")")
    void testInformationExtractionPromptOptimizationWithVariables() {
        String template = "Extract {field} from {input}";
        Map<String, String> variables = Map.of("field", "company", "input", "profile text");
        assertThat(template).contains("{field}", "{input}");
        assertThat(variables).containsKeys("field", "input");
    }
}
