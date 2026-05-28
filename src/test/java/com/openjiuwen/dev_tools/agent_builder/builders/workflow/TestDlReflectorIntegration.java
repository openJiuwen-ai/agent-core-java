/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DL Reflector module.
 * <p>
 * Mirrors Python's {@code test_dl_reflector_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestDlReflectorIntegration {

    @Nested
    class TestExtractPlaceholderContent {

        @Test
        void extractSinglePlaceholder() {
            List<String> matches = DlReflector.extractPlaceholderNames("Hello ${name}!");
            assertThat(matches).containsExactly("name");
        }

        @Test
        void extractMultiplePlaceholders() {
            List<String> matches = DlReflector.extractPlaceholderNames("${node1.output} and ${node2.output}");
            assertThat(matches).containsExactly("node1.output", "node2.output");
        }

        @Test
        void noPlaceholder() {
            List<String> matches = DlReflector.extractPlaceholderNames("No placeholder here");
            assertThat(matches).isEmpty();
        }

        @Test
        void emptyString() {
            List<String> matches = DlReflector.extractPlaceholderNames("");
            assertThat(matches).isEmpty();
        }
    }

    @Nested
    class TestReflectorIntegration {

        private DlReflector reflector;

        @BeforeEach
        void setUp() {
            reflector = new DlReflector();
        }

        @Test
        void reflectorInitialization() {
            assertThat(reflector).isNotNull();
            assertThat(reflector.getErrors()).isEmpty();
            assertThat(reflector.getNodeIds()).isEmpty();
        }

        @Test
        void reflectorAvailableNodeTypes() {
            assertThat(DlReflector.AVAILABLE_NODE_TYPES)
                    .contains("Start", "End", "Output", "LLM", "Questioner",
                            "Plugin", "Code", "Branch", "IntentDetection");
        }

        @Test
        void reflectorAvailableVariableTypes() {
            assertThat(DlReflector.AVAILABLE_VARIABLE_TYPES)
                    .contains("String", "Integer", "Number", "Boolean", "Object");
        }

        @Test
        void reflectorAvailableConditionOperators() {
            assertThat(DlReflector.AVAILABLE_CONDITION_OPERATORS)
                    .contains("eq", "not_eq", "contain", "not_contain");
        }
    }
}
