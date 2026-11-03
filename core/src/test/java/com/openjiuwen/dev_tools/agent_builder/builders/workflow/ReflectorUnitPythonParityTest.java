/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's reflector unit tests in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_dl_reflector.py}.
 */
class ReflectorUnitPythonParityTest {

    @Test
    void extractWithPlaceholderFindsPlaceholderContent() {
        Reflector.PlaceholderContent content = Reflector.extractPlaceholderContent("${node_start.query}");

        assertThat(content.hasPlaceholder()).isTrue();
        assertThat(content.matches()).contains("node_start.query");
    }

    @Test
    void extractWithoutPlaceholderReturnsEmptyMatches() {
        Reflector.PlaceholderContent content = Reflector.extractPlaceholderContent("plain text");

        assertThat(content.hasPlaceholder()).isFalse();
        assertThat(content.matches()).isEmpty();
    }

    @Test
    void extractMultiplePlaceholdersReturnsBothMatches() {
        Reflector.PlaceholderContent content = Reflector.extractPlaceholderContent("${node1.var1} and ${node2.var2}");

        assertThat(content.hasPlaceholder()).isTrue();
        assertThat(content.matches()).hasSize(2);
    }

    @Test
    void extractEmptyStringReturnsNoPlaceholder() {
        Reflector.PlaceholderContent content = Reflector.extractPlaceholderContent("");

        assertThat(content.hasPlaceholder()).isFalse();
        assertThat(content.matches()).isEmpty();
    }

    @Test
    void initSuccessPopulatesAvailableTypesAndStartsWithoutErrors() {
        Reflector reflector = new Reflector();

        assertThat(reflector.getAvailableNodeTypes()).isNotEmpty();
        assertThat(reflector.getAvailableVariableTypes()).isNotEmpty();
        assertThat(reflector.getAvailableConditionOperators()).isNotEmpty();
        assertThat(reflector.getErrors()).isEmpty();
    }

    @Test
    void availableNodeTypesMatchPythonSet() {
        Reflector reflector = new Reflector();

        assertThat(reflector.getAvailableNodeTypes()).isEqualTo(Set.of(
                "Start", "End", "Output", "LLM", "Questioner", "Plugin", "Code", "Branch", "IntentDetection"
        ));
    }

    @Test
    void availableVariableTypesMatchPythonSet() {
        Reflector reflector = new Reflector();

        assertThat(reflector.getAvailableVariableTypes()).isEqualTo(Set.of(
                "String",
                "Integer",
                "Number",
                "Boolean",
                "Object",
                "Array<String>",
                "Array<Integer>",
                "Array<Number>",
                "Array<Boolean>",
                "Array<Object>"
        ));
    }

    @Test
    void checkFormatInvalidJsonRecordsError() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("invalid json");

        assertThat(reflector.getErrors()).isNotEmpty();
    }

    @Test
    void checkFormatNonListJsonRecordsError() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("{\"key\": \"value\"}");

        assertThat(reflector.getErrors()).isNotEmpty();
    }

    @Test
    void checkFormatMissingTypeRecordsError() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("[{\"id\": \"node_1\"}]");

        assertThat(reflector.getErrors()).isNotEmpty();
    }

    @Test
    void checkFormatInvalidTypeRecordsError() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("[{\"id\": \"node_1\", \"type\": \"InvalidType\"}]");

        assertThat(reflector.getErrors()).isNotEmpty();
    }

    @Test
    void resetAfterCheckClearsErrorsAndNodeIds() {
        Reflector reflector = new Reflector();
        reflector.getErrors().add("error1");
        reflector.getErrors().add("error2");
        reflector.getNodeIds().add("id1");
        reflector.getNodeIds().add("id2");

        reflector.reset();

        assertThat(reflector.getErrors()).isEmpty();
        assertThat(reflector.getNodeIds()).isEmpty();
    }
}
