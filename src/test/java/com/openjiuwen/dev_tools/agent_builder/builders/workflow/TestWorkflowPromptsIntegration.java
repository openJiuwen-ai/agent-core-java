/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for workflow prompts integration.
 * <p>
 * Mirrors Python's {@code test_prompts_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestWorkflowPromptsIntegration {

    @Test
    void dlAssetsPromptsExist() {
        assertThat(DlAssets.COMPONENTS_INFO).isNotEmpty();
        assertThat(DlAssets.SCHEMA_INFO).isNotEmpty();
        assertThat(DlAssets.EXAMPLES).isNotNull();
    }

    @Test
    void generatorTemplatesExist() {
        String sys = DlGenerator.formatGenerateSystemTemplate("a", "b", "c", "d");
        assertThat(sys).isNotEmpty();
        String user = DlGenerator.formatRefineUserTemplate("a", "b", "c");
        assertThat(user).isNotEmpty();
    }
}
