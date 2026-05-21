/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DL Generator module.
 * <p>
 * Mirrors Python's {@code test_dl_generator_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestDlGeneratorIntegration {

    private DlGenerator dlGenerator;

    @BeforeEach
    void setUp() {
        dlGenerator = new DlGenerator(null);
    }

    @Nested
    class TestDLGeneratorIntegrationInner {

        @Test
        void dlGeneratorInitialization() {
            assertThat(dlGenerator).isNotNull();
        }

        @Test
        void generateSystemTemplateContent() {
            String result = DlGenerator.formatGenerateSystemTemplate(
                    "test components", "test schema", "test plugins", "test examples");
            assertThat(result).contains("test components");
            assertThat(result).contains("test schema");
        }

        @Test
        void refineUserTemplateContent() {
            String result = DlGenerator.formatRefineUserTemplate(
                    "test input", "test mermaid", "test dl");
            assertThat(result).contains("test input");
            assertThat(result).contains("test mermaid");
            assertThat(result).contains("test dl");
        }
    }
}
