/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DlTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DL Transformer module.
 * <p>
 * Mirrors Python's {@code test_dl_transformer_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestDlTransformerIntegration {

    private DlTransformer dlTransformer;

    @BeforeEach
    void setUp() {
        dlTransformer = new DlTransformer();
    }

    @Nested
    class TestDlTransformerIntegrationInner {

        @Test
        void dlTransformerInitialization() {
            assertThat(dlTransformer).isNotNull();
        }

        @Test
        void transformBasicDesign() {
            Map<String, Object> design = new LinkedHashMap<>();
            design.put("nodes", List.of());
            design.put("edges", List.of());

            Map<String, Object> result = dlTransformer.transform(design);
            assertThat(result).containsKey("workflow");
            assertThat(result).containsKey("transformed");
        }

        @Test
        void transformReturnsTransformedFlag() {
            Map<String, Object> result = dlTransformer.transform(new LinkedHashMap<>());
            assertThat(result.get("transformed")).isEqualTo(true);
        }
    }

    @Nested
    class TestDlTransformerRegistryIntegration {

        @Test
        void registryContainsAllTypes() {
            Map<String, Class<?>> registry = DlTransformer.getDslConverterRegistry();
            assertThat(registry).containsKeys("Start", "End", "LLM", "IntentDetection",
                    "Questioner", "Code", "Plugin", "Output", "Branch");
        }
    }
}
