/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for intention detector integration.
 * <p>
 * Mirrors Python's {@code test_intention_detector_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.llm_agent}.
 */
class TestIntentionDetectorIntegration {

    private final IntentionDetector detector = new IntentionDetector(null);

    @Test
    void intentionDetectorInitialization() {
        assertThat(detector.getLlm()).isNull();
    }

    @Test
    void extractIntentWithJsonBlock() {
        Map<String, Object> result = IntentionDetector.extractIntent("```json\n{\"need_refined\": true}\n```");
        assertThat(result).containsEntry("need_refined", true);
    }

    @Test
    void extractIntentWithoutJsonBlock() {
        Map<String, Object> result = IntentionDetector.extractIntent("{\"need_refined\": false}");
        assertThat(result).containsEntry("need_refined", false);
    }

    @Test
    void detectRefineIntentEmptyQuery() {
        assertThat(detector.detectRefineIntent("", "config")).isFalse();
    }

    @Test
    void detectRefineIntentNoneQuery() {
        assertThat(detector.detectRefineIntent(null, "config")).isFalse();
    }
}
