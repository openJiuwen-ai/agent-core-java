/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.*;

/**
 * Test DlReflector functionality.
 * <p>
 * Mirrors Python's {@code test_dl_reflector.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_dl_reflector.py}.
 */
class TestDlReflector {

    /**
     * Test extractPlaceholderContent function.
     */
    static class TestExtractPlaceholderContent {

        @Test
        void testExtractWithPlaceholder() {
            String input = "${node_start.query}";

            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent(input);

            Assertions.assertTrue(result.hasPlaceholder());
            Assertions.assertTrue(result.getMatches().contains("node_start.query"));
        }

        @Test
        void testExtractWithoutPlaceholder() {
            String input = "plain text";

            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent(input);

            Assertions.assertFalse(result.hasPlaceholder());
            Assertions.assertEquals(0, result.getMatches().size());
        }

        @Test
        void testExtractMultiplePlaceholders() {
            String input = "${node1.var1} and ${node2.var2}";

            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent(input);

            Assertions.assertTrue(result.hasPlaceholder());
            Assertions.assertEquals(2, result.getMatches().size());
            Assertions.assertTrue(result.getMatches().contains("node1.var1"));
            Assertions.assertTrue(result.getMatches().contains("node2.var2"));
        }

        @Test
        void testExtractEmptyString() {
            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent("");

            Assertions.assertFalse(result.hasPlaceholder());
            Assertions.assertEquals(0, result.getMatches().size());
        }

        @Test
        void testExtractNullString() {
            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent(null);

            Assertions.assertFalse(result.hasPlaceholder());
            Assertions.assertEquals(0, result.getMatches().size());
        }
    }

    /**
     * Test Reflector initialization.
     */
    static class TestInit {

        @Test
        void testInitSuccess() {
            DlReflector reflector = new DlReflector();

            Assertions.assertTrue(DlReflector.AVAILABLE_NODE_TYPES.size() > 0);
            Assertions.assertTrue(DlReflector.AVAILABLE_VARIABLE_TYPES.size() > 0);
            Assertions.assertTrue(DlReflector.AVAILABLE_CONDITION_OPERATORS.size() > 0);
            Assertions.assertEquals(0, reflector.getErrors().size());
        }

        @Test
        void testAvailableNodeTypes() {
            Set<String> expectedTypes = Set.of(
                    "Start", "End", "Output", "LLM", "Questioner",
                    "Plugin", "Code", "Branch", "IntentDetection"
            );

            Assertions.assertEquals(expectedTypes, DlReflector.AVAILABLE_NODE_TYPES);
        }

        @Test
        void testAvailableVariableTypes() {
            Set<String> expectedTypes = Set.of(
                    "String", "Integer", "Number", "Boolean", "Object",
                    "Array<String>", "Array<Integer>", "Array<Number>",
                    "Array<Boolean>", "Array<Object>"
            );

            Assertions.assertEquals(expectedTypes, DlReflector.AVAILABLE_VARIABLE_TYPES);
        }
    }

    /**
     * Test validation methods.
     */
    static class TestValidation {

        @Test
        void testIsValidNodeType() {
            DlReflector reflector = new DlReflector();

            Assertions.assertTrue(reflector.isValidNodeType("Start"));
            Assertions.assertTrue(reflector.isValidNodeType("LLM"));
            Assertions.assertFalse(reflector.isValidNodeType("InvalidType"));
        }

        @Test
        void testIsValidVariableType() {
            DlReflector reflector = new DlReflector();

            Assertions.assertTrue(reflector.isValidVariableType("String"));
            Assertions.assertTrue(reflector.isValidVariableType("Integer"));
            Assertions.assertFalse(reflector.isValidVariableType("InvalidType"));
        }

        @Test
        void testIsValidConditionOperator() {
            DlReflector reflector = new DlReflector();

            Assertions.assertTrue(reflector.isValidConditionOperator("eq"));
            Assertions.assertTrue(reflector.isValidConditionOperator("contain"));
            Assertions.assertFalse(reflector.isValidConditionOperator("invalid"));
        }

        @Test
        void testAddError() {
            DlReflector reflector = new DlReflector();

            reflector.addError("Test error");

            Assertions.assertEquals(1, reflector.getErrors().size());
            Assertions.assertFalse(reflector.isValid());
        }

        @Test
        void testReset() {
            DlReflector reflector = new DlReflector();
            reflector.addError("Test error");

            reflector.reset();

            Assertions.assertEquals(0, reflector.getErrors().size());
            Assertions.assertTrue(reflector.isValid());
        }
    }

    /**
     * Test checkFormat method.
     */
    static class TestCheckFormat {

        @Test
        void testCheckFormatValid() {
            DlReflector reflector = new DlReflector();

            String validDl = "[{\"id\": \"node_1\", \"type\": \"Start\"}]";
            reflector.checkFormat(validDl);

            Assertions.assertTrue(reflector.isValid());
        }

        @Test
        void testCheckFormatEmpty() {
            DlReflector reflector = new DlReflector();

            reflector.checkFormat("");

            Assertions.assertFalse(reflector.isValid());
        }

        @Test
        void testCheckFormatNull() {
            DlReflector reflector = new DlReflector();

            reflector.checkFormat(null);

            Assertions.assertFalse(reflector.isValid());
        }
    }
}