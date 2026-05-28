/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for IntentionDetector module.
 * <p>
 * Mirrors Python's {@code test_intention_detector_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestIntentionDetectorIntegration {

    private IntentionDetector detector;

    @BeforeEach
    void setUp() {
        detector = new IntentionDetector();
    }

    @Nested
    class TestIntentionDetectorIntegrationInner {

        @Test
        void detectCreateIntention() {
            IntentionDetector.Intention result = detector.detect("创建一个工作流");
            assertThat(result).isEqualTo(IntentionDetector.Intention.CREATE_WORKFLOW);
        }

        @Test
        void detectModifyIntention() {
            IntentionDetector.Intention result = detector.detect("修改工作流");
            assertThat(result).isEqualTo(IntentionDetector.Intention.MODIFY_WORKFLOW);
        }

        @Test
        void detectUnknownIntention() {
            IntentionDetector.Intention result = detector.detect("随便说说");
            assertThat(result).isEqualTo(IntentionDetector.Intention.UNKNOWN);
        }

        @Test
        void detectEmptyInput() {
            IntentionDetector.Intention result = detector.detect("");
            assertThat(result).isEqualTo(IntentionDetector.Intention.UNKNOWN);
        }

        @Test
        void detectNullInput() {
            IntentionDetector.Intention result = detector.detect(null);
            assertThat(result).isEqualTo(IntentionDetector.Intention.UNKNOWN);
        }
    }
}
