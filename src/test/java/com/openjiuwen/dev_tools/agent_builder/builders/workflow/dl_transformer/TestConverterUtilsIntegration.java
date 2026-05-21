/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * System tests for DL Transformer ConverterUtils module.
 * <p>
 * Mirrors Python's {@code test_converter_utils_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.dl_transformer}.
 */
class TestConverterUtilsIntegration {

    @Nested
    class TestGenerateNodeId {

        @Test
        void generateNodeIdUniqueness() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                ids.add(ConverterUtils.generateNodeId("node"));
            }
            assertThat(ids).hasSize(100);
        }

        @Test
        void generateNodeIdWithPrefix() {
            String nodeId = ConverterUtils.generateNodeId("start");
            assertThat(nodeId).startsWith("start_");
        }
    }

    @Nested
    class TestExtractVariable {

        @Test
        void extractVariableValid() {
            String[] result = ConverterUtils.extractVariable("${node_start.query}");
            assertThat(result).containsExactly("node_start", "query");
        }

        @Test
        void extractVariableInvalid() {
            String[] result = ConverterUtils.extractVariable("invalid");
            assertThat(result).isNull();
        }
    }

    @Nested
    class TestConvertRefVariable {

        @Test
        void convertRefVariableValid() {
            Map<String, Object> result = ConverterUtils.convertRefVariable("${node_start.query}");
            assertThat(result.get("type")).isEqualTo("ref");
            assertThat(result.get("content")).isEqualTo(List.of("node_start", "query"));
        }

        @Test
        void convertRefVariableNested() {
            Map<String, Object> result = ConverterUtils.convertRefVariable("${node_llm.output_of_result}");
            assertThat(result.get("type")).isEqualTo("ref");
            assertThat(result.get("content")).isEqualTo(List.of("node_llm", "result", "output"));
        }

        @Test
        void convertRefVariableInvalid() {
            assertThatThrownBy(() -> ConverterUtils.convertRefVariable("invalid"))
                    .isInstanceOf(Exception.class);
        }
    }
}
