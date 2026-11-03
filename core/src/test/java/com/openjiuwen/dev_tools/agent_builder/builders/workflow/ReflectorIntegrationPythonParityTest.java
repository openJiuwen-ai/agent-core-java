/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's
 * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_dl_reflector_integration.py}.
 */
class ReflectorIntegrationPythonParityTest {

    @Test
    void extractSinglePlaceholder() {
        Reflector.PlaceholderContent result = Reflector.extractPlaceholderContent("Hello ${name}!");

        assertThat(result.hasPlaceholder()).isTrue();
        assertThat(result.matches()).containsExactly("name");
    }

    @Test
    void extractMultiplePlaceholders() {
        Reflector.PlaceholderContent result =
                Reflector.extractPlaceholderContent("${node1.output} and ${node2.output}");

        assertThat(result.hasPlaceholder()).isTrue();
        assertThat(result.matches()).containsExactly("node1.output", "node2.output");
    }

    @Test
    void noPlaceholder() {
        Reflector.PlaceholderContent result = Reflector.extractPlaceholderContent("No placeholder here");

        assertThat(result.hasPlaceholder()).isFalse();
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void emptyStringHasNoPlaceholder() {
        Reflector.PlaceholderContent result = Reflector.extractPlaceholderContent("");

        assertThat(result.hasPlaceholder()).isFalse();
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void reflectorInitializationStartsEmpty() {
        Reflector reflector = new Reflector();

        assertThat(reflector.getErrors()).isEmpty();
        assertThat(reflector.getNodeIds()).isEmpty();
    }

    @Test
    void reflectorAvailableNodeTypes() {
        assertThat(new Reflector().getAvailableNodeTypes()).isEqualTo(Set.of(
                "Start", "End", "Output", "LLM", "Questioner", "Plugin", "Code", "Branch", "IntentDetection"
        ));
    }

    @Test
    void reflectorAvailableVariableTypes() {
        assertThat(new Reflector().getAvailableVariableTypes()).isEqualTo(Set.of(
                "String", "Integer", "Number", "Boolean", "Object",
                "Array<String>", "Array<Integer>", "Array<Number>",
                "Array<Boolean>", "Array<Object>"
        ));
    }

    @Test
    void reflectorAvailableConditionOperators() {
        assertThat(new Reflector().getAvailableConditionOperators()).isEqualTo(Set.of(
                "eq", "not_eq", "contain", "not_contain",
                "longer_than", "longer_than_or_eq",
                "short_than", "short_than_or_eq",
                "is_empty", "is_not_empty"
        ));
    }

    @Test
    void reflectorResetClearsErrorsAndNodeIds() {
        Reflector reflector = new Reflector();
        reflector.getErrors().add("error1");
        reflector.getErrors().add("error2");
        reflector.getNodeIds().add("node1");
        reflector.getNodeIds().add("node2");

        reflector.reset();

        assertThat(reflector.getErrors()).isEmpty();
        assertThat(reflector.getNodeIds()).isEmpty();
    }

    @Test
    void validateInvalidJson() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("not a json");

        assertThat(reflector.getErrors()).isNotEmpty();
        assertThat(reflector.getErrors()).anySatisfy(error -> assertThat(error).contains("JSON"));
    }

    @Test
    void validateEmptyJsonArray() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("[]");

        assertThat(reflector.getErrors()).isEmpty();
    }

    @Test
    void validateMissingRequiredFields() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("[{\"id\":\"node1\"}]");

        assertThat(reflector.getErrors()).isNotEmpty();
    }

    @Test
    void validateInvalidNodeType() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "node1",
                  "type": "InvalidType",
                  "description": "test",
                  "parameters": {}
                }]
                """);

        assertThat(reflector.getErrors()).isNotEmpty();
        assertThat(reflector.getErrors()).anySatisfy(error -> assertThat(error).contains("类型错误"));
    }

    @Test
    void validateDuplicateNodeId() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [
                  {"id": "node1", "type": "Start", "description": "test", "parameters": {"outputs": []}},
                  {"id": "node1", "type": "End", "description": "test", "parameters": {"inputs": []}}
                ]
                """);

        assertThat(reflector.getErrors()).isNotEmpty();
        assertThat(reflector.getErrors()).anySatisfy(error -> assertThat(error).contains("已存在"));
    }

    @Test
    void validStartNode() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "start",
                  "type": "Start",
                  "description": "开始节点",
                  "parameters": {
                    "outputs": [{"name": "query", "description": "用户输入"}]
                  },
                  "next": "end"
                },
                {
                  "id": "end",
                  "type": "End",
                  "description": "结束节点",
                  "parameters": {"inputs": [], "configs": {"template": ""}}
                }]
                """);

        assertThat(reflector.getErrors()).isEmpty();
    }

    @Test
    void startNodeMissingQueryOutput() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "start",
                  "type": "Start",
                  "description": "开始节点",
                  "parameters": {
                    "outputs": [{"name": "other", "description": "其他输出"}]
                  },
                  "next": "end"
                }]
                """);

        assertThat(reflector.getErrors()).isNotEmpty();
        assertThat(reflector.getErrors()).anySatisfy(error -> assertThat(error).contains("query"));
    }

    @Test
    void startNodeMissingNext() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "start",
                  "type": "Start",
                  "description": "开始节点",
                  "parameters": {
                    "outputs": [{"name": "query", "description": "用户输入"}]
                  }
                }]
                """);

        assertThat(reflector.getErrors()).isNotEmpty();
        assertThat(reflector.getErrors()).anySatisfy(error -> assertThat(error).contains("next"));
    }

    @Test
    void validEndNode() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "end",
                  "type": "End",
                  "description": "结束节点",
                  "parameters": {
                    "inputs": [{"name": "result", "value": "test"}],
                    "configs": {"template": "结果: test"}
                  }
                }]
                """);

        assertThat(reflector.getErrors()).isEmpty();
    }

    @Test
    void endNodeMissingConfigs() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "end",
                  "type": "End",
                  "description": "结束节点",
                  "parameters": {
                    "inputs": [{"name": "result", "value": "test"}]
                  }
                }]
                """);

        assertThat(reflector.getErrors()).isNotEmpty();
        assertThat(reflector.getErrors()).anySatisfy(error -> assertThat(error).contains("configs"));
    }

    @Test
    void validLlmNode() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "llm1",
                  "type": "LLM",
                  "description": "LLM节点",
                  "parameters": {
                    "inputs": [{"name": "prompt", "value": "test"}],
                    "outputs": [{"name": "response", "description": "响应"}],
                    "configs": {"system_prompt": "You are helpful", "user_prompt": "Hello"}
                  },
                  "next": "end"
                },
                {
                  "id": "end",
                  "type": "End",
                  "description": "结束节点",
                  "parameters": {"inputs": [], "configs": {"template": ""}}
                }]
                """);

        assertThat(reflector.getErrors()).isEmpty();
    }

    @Test
    void llmNodeMissingRequiredConfigs() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "llm1",
                  "type": "LLM",
                  "description": "LLM节点",
                  "parameters": {
                    "inputs": [],
                    "outputs": [],
                    "configs": {"system_prompt": "You are helpful"}
                  },
                  "next": "end"
                }]
                """);

        assertThat(reflector.getErrors()).isNotEmpty();
        assertThat(reflector.getErrors()).anySatisfy(error -> assertThat(error).contains("user_prompt"));
    }

    @Test
    void validBranchNode() {
        Reflector reflector = new Reflector();

        reflector.checkFormat(validBranchDl());

        assertThat(reflector.getErrors()).isEmpty();
    }

    @Test
    void branchNodeMissingDefaultBranch() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "branch1",
                  "type": "Branch",
                  "description": "分支节点",
                  "parameters": {
                    "inputs": [],
                    "conditions": [{
                      "branch": "branch1",
                      "description": "条件1",
                      "expression": "'test' eq 'yes'",
                      "next": "end1"
                    }]
                  }
                }]
                """);

        assertThat(reflector.getErrors()).isNotEmpty();
        assertThat(reflector.getErrors()).anySatisfy(error -> assertThat(error).contains("default"));
    }

    @Test
    void validIntentDetectionNode() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "intent1",
                  "type": "IntentDetection",
                  "description": "意图检测节点",
                  "parameters": {
                    "inputs": [{"name": "input", "value": "test"}],
                    "configs": {"prompt": "检测意图"},
                    "conditions": [
                      {
                        "branch": "intent1",
                        "description": "意图1",
                        "expression": "${intent1.rawOutput} contain 'intent1'",
                        "next": "end1"
                      },
                      {
                        "branch": "default",
                        "description": "默认",
                        "expression": "default",
                        "next": "end2"
                      }
                    ]
                  }
                },
                {"id": "end1", "type": "End", "description": "结束1", "parameters": {"inputs": [], "configs": {"template": ""}}},
                {"id": "end2", "type": "End", "description": "结束2", "parameters": {"inputs": [], "configs": {"template": ""}}}]
                """);

        assertThat(reflector.getErrors()).isEmpty();
    }

    @Test
    void intentDetectionMissingConditions() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "intent1",
                  "type": "IntentDetection",
                  "description": "意图检测节点",
                  "parameters": {
                    "inputs": [],
                    "configs": {"prompt": "检测意图"}
                  }
                }]
                """);

        assertThat(reflector.getErrors()).isNotEmpty();
        assertThat(reflector.getErrors()).anySatisfy(error -> assertThat(error).contains("conditions"));
    }

    @Test
    void referenceToNonExistentNode() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("""
                [{
                  "id": "start",
                  "type": "Start",
                  "description": "开始",
                  "parameters": {
                    "outputs": [{"name": "query", "description": "用户输入"}]
                  },
                  "next": "non_existent_node"
                }]
                """);

        assertThat(reflector.getErrors()).isNotEmpty();
        assertThat(reflector.getErrors()).anySatisfy(error -> assertThat(error).contains("不存在"));
    }

    private static String validBranchDl() {
        return """
                [{
                  "id": "start",
                  "type": "Start",
                  "description": "开始",
                  "parameters": {
                    "outputs": [{"name": "query", "description": "用户输入"}]
                  },
                  "next": "branch1"
                },
                {
                  "id": "branch1",
                  "type": "Branch",
                  "description": "分支节点",
                  "parameters": {
                    "inputs": [{"name": "input", "value": "${start.query}"}],
                    "conditions": [
                      {
                        "branch": "branch1",
                        "description": "条件1",
                        "expression": "${start.query} eq 'yes'",
                        "next": "end1"
                      },
                      {
                        "branch": "default",
                        "description": "默认",
                        "expression": "default",
                        "next": "end2"
                      }
                    ]
                  }
                },
                {"id": "end1", "type": "End", "description": "结束1", "parameters": {"inputs": [], "configs": {"template": ""}}},
                {"id": "end2", "type": "End", "description": "结束2", "parameters": {"inputs": [], "configs": {"template": ""}}}]
                """;
    }
}
