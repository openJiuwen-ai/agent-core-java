/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's package export behavior in
 * {@code openjiuwen/dev_tools/agent_builder/resource/__init__.py}.
 */
class AgentBuilderResourcePackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertThat(AgentBuilderResourcePackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/dev_tools/agent_builder/resource/__init__.py");
        assertThat(AgentBuilderResourcePackage.EXPORTED_SYMBOLS)
                .containsExactlyElementsOf(List.of("ResourceRetriever", "PluginProcessor"));
        assertThat(AgentBuilderResourcePackage.EXPORTED_TYPES)
                .containsEntry("ResourceRetriever", ResourceRetriever.class)
                .containsEntry("PluginProcessor", PluginProcessor.class);
    }
}
