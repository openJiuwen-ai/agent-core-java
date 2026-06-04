/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test DlReflector functionality.
 * <p>
 * Mirrors Python's {@code test_dl_reflector.py} in
 * {@code tests.unit_tests.dev_tools.agent_builder.builders.workflow.test_dl_reflector}.
 */
class TestDlReflector {

    @Nested
    class TestExtractPlaceholderContent {

        @Test
        void testExtractWithPlaceholder() {
            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent("${node_start.query}");
            assertTrue(result.hasPlaceholder());
            assertTrue(result.getMatches().contains("node_start.query"));
        }

        @Test
        void testExtractWithoutPlaceholder() {
            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent("plain text");
            assertFalse(result.hasPlaceholder());
            assertEquals(0, result.getMatches().size());
        }

        @Test
        void testExtractMultiplePlaceholders() {
            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent("${node1.var1} and ${node2.var2}");
            assertTrue(result.hasPlaceholder());
            assertEquals(2, result.getMatches().size());
        }

        @Test
        void testExtractEmptyString() {
            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent("");
            assertFalse(result.hasPlaceholder());
            assertEquals(0, result.getMatches().size());
        }
    }

    @Nested
    class TestReflectorInit {

        @Test
        void testInitSuccess() {
            DlReflector reflector = new DlReflector();
            assertTrue(DlReflector.AVAILABLE_NODE_TYPES.size() > 0);
            assertTrue(DlReflector.AVAILABLE_VARIABLE_TYPES.size() > 0);
            assertTrue(DlReflector.AVAILABLE_CONDITION_OPERATORS.size() > 0);
            assertEquals(0, reflector.getErrors().size());
        }

        @Test
        void testAvailableNodeTypes() {
            Set<String> expectedTypes = Set.of(
                    "Start", "End", "Output", "LLM", "Questioner", "Plugin", "Code", "Branch", "IntentDetection");
            assertEquals(expectedTypes, DlReflector.AVAILABLE_NODE_TYPES);
        }

        @Test
        void testAvailableVariableTypes() {
            Set<String> expectedTypes = Set.of(
                    "String", "Integer", "Number", "Boolean", "Object",
                    "Array<String>", "Array<Integer>", "Array<Number>", "Array<Boolean>", "Array<Object>");
            assertEquals(expectedTypes, DlReflector.AVAILABLE_VARIABLE_TYPES);
        }
    }

    @Nested
    class TestReflectorCheckFormat {

        @Test
        void testCheckFormatInvalidJson() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("invalid json");
            assertTrue(reflector.getErrors().size() > 0);
        }

        @Test
        void testCheckFormatNotList() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("{\"key\": \"value\"}");
            assertTrue(reflector.getErrors().size() > 0);
        }

        @Test
        void testCheckFormatMissingType() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("[{\"id\": \"node_1\"}]");
            assertTrue(reflector.getErrors().size() > 0);
        }

        @Test
        void testCheckFormatInvalidType() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("[{\"id\": \"node_1\", \"type\": \"InvalidType\", \"description\": \"x\", \"parameters\": {}}]");
            assertTrue(reflector.getErrors().size() > 0);
        }
    }

    @Nested
    class TestReflectorReset {

        @Test
        void testResetAfterCheck() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("invalid json");
            reflector.reset();
            assertEquals(0, reflector.getErrors().size());
            assertEquals(0, reflector.getNodeIds().size());
        }
    }
}
