/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DL Reflector module.
 * <p>
 * Mirrors Python's {@code test_dl_reflector_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.test_dl_reflector_integration}.
 */
class TestDlReflectorIntegration {

    @Nested
    class TestExtractPlaceholderContent {

        @Test
        void testExtractSinglePlaceholder() {
            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent("Hello ${name}!");
            assertThat(result.hasPlaceholder()).isTrue();
            assertThat(result.getMatches()).containsExactly("name");
        }

        @Test
        void testExtractMultiplePlaceholders() {
            DlReflector.PlaceholderResult result =
                    DlReflector.extractPlaceholderContent("${node1.output} and ${node2.output}");
            assertThat(result.hasPlaceholder()).isTrue();
            assertThat(result.getMatches()).containsExactly("node1.output", "node2.output");
        }

        @Test
        void testNoPlaceholder() {
            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent("No placeholder here");
            assertThat(result.hasPlaceholder()).isFalse();
            assertThat(result.getMatches()).isEmpty();
        }

        @Test
        void testEmptyString() {
            DlReflector.PlaceholderResult result = DlReflector.extractPlaceholderContent("");
            assertThat(result.hasPlaceholder()).isFalse();
            assertThat(result.getMatches()).isEmpty();
        }
    }

    @Nested
    class TestReflectorIntegrationInner {

        @Test
        void testReflectorInitialization() {
            DlReflector reflector = new DlReflector();
            assertThat(reflector).isNotNull();
            assertThat(reflector.getErrors()).isEmpty();
            assertThat(reflector.getNodeIds()).isEmpty();
        }

        @Test
        void testReflectorAvailableNodeTypes() {
            assertThat(DlReflector.AVAILABLE_NODE_TYPES)
                    .containsExactlyInAnyOrder("Start", "End", "Output", "LLM", "Questioner",
                            "Plugin", "Code", "Branch", "IntentDetection");
        }

        @Test
        void testReflectorAvailableVariableTypes() {
            assertThat(DlReflector.AVAILABLE_VARIABLE_TYPES)
                    .containsExactlyInAnyOrder("String", "Integer", "Number", "Boolean", "Object",
                            "Array<String>", "Array<Integer>", "Array<Number>", "Array<Boolean>", "Array<Object>");
        }

        @Test
        void testReflectorAvailableConditionOperators() {
            assertThat(DlReflector.AVAILABLE_CONDITION_OPERATORS)
                    .containsExactlyInAnyOrder("eq", "not_eq", "contain", "not_contain",
                            "longer_than", "longer_than_or_eq", "short_than", "short_than_or_eq",
                            "is_empty", "is_not_empty");
        }

        @Test
        void testReflectorReset() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("invalid json");
            reflector.reset();
            assertThat(reflector.getErrors()).isEmpty();
            assertThat(reflector.getNodeIds()).isEmpty();
        }
    }

    @Nested
    class TestReflectorValidation {

        @Test
        void testValidateInvalidJson() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("not a json");
            assertThat(reflector.getErrors()).isNotEmpty();
            assertThat(reflector.getErrors().getFirst()).contains("JSON");
        }

        @Test
        void testValidateEmptyJson() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("[]");
            assertThat(reflector.getErrors()).isEmpty();
        }

        @Test
        void testValidateMissingRequiredFields() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("[{\"id\": \"node1\"}]");
            assertThat(reflector.getErrors()).isNotEmpty();
        }

        @Test
        void testValidateInvalidNodeType() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [{
                      "id":"node1",
                      "type":"InvalidType",
                      "description":"test",
                      "parameters":{}
                    }]
                    """);
            assertThat(reflector.getErrors()).isNotEmpty();
        }

        @Test
        void testValidateDuplicateNodeId() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [
                      {"id":"node1","type":"Start","description":"test","parameters":{"outputs":[{"name":"query","description":"用户输入"}]},"next":"node2"},
                      {"id":"node1","type":"End","description":"test","parameters":{"inputs":[],"configs":{"template":""}}}
                    ]
                    """);
            assertThat(reflector.getErrors()).isNotEmpty();
        }
    }

    @Nested
    class TestReflectorStartNode {

        @Test
        void testValidStartNode() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [
                      {"id":"start","type":"Start","description":"开始节点","parameters":{"outputs":[{"name":"query","description":"用户输入"}]},"next":"end"},
                      {"id":"end","type":"End","description":"结束节点","parameters":{"inputs":[],"configs":{"template":""}}}
                    ]
                    """);
            assertThat(reflector.getErrors()).isEmpty();
        }

        @Test
        void testStartNodeMissingQueryOutput() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [{"id":"start","type":"Start","description":"开始节点","parameters":{"outputs":[{"name":"other","description":"其他输出"}]},"next":"end"}]
                    """);
            assertThat(reflector.getErrors()).anyMatch(msg -> msg.contains("query"));
        }

        @Test
        void testStartNodeMissingNext() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [{"id":"start","type":"Start","description":"开始节点","parameters":{"outputs":[{"name":"query","description":"用户输入"}]}}]
                    """);
            assertThat(reflector.getErrors()).anyMatch(msg -> msg.contains("next"));
        }
    }

    @Nested
    class TestReflectorEndNode {

        @Test
        void testValidEndNode() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [{"id":"end","type":"End","description":"结束节点","parameters":{"inputs":[{"name":"result","value":"test"}],"configs":{"template":"结果: test"}}}]
                    """);
            assertThat(reflector.getErrors()).isEmpty();
        }

        @Test
        void testEndNodeMissingConfigs() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [{"id":"end","type":"End","description":"结束节点","parameters":{"inputs":[{"name":"result","value":"test"}]}}]
                    """);
            assertThat(reflector.getErrors()).anyMatch(msg -> msg.contains("configs"));
        }
    }

    @Nested
    class TestReflectorLLMNode {

        @Test
        void testValidLlmNode() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [
                      {"id":"start","type":"Start","description":"开始节点","parameters":{"outputs":[{"name":"query","description":"用户输入"}]},"next":"llm1"},
                      {"id":"llm1","type":"LLM","description":"LLM节点","parameters":{"inputs":[{"name":"prompt","value":"${start.query}"}],"outputs":[{"name":"response","description":"响应"}],"configs":{"system_prompt":"You are helpful","user_prompt":"Hello"}},"next":"end"},
                      {"id":"end","type":"End","description":"结束节点","parameters":{"inputs":[],"configs":{"template":""}}}
                    ]
                    """);
            assertThat(reflector.getErrors()).isEmpty();
        }

        @Test
        void testLlmNodeMissingRequiredConfigs() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [{"id":"llm1","type":"LLM","description":"LLM节点","parameters":{"inputs":[],"outputs":[],"configs":{"system_prompt":"You are helpful"}}},"next":"end"]
                    """);
            assertThat(reflector.getErrors()).isNotEmpty();
        }
    }

    @Nested
    class TestReflectorBranchNode {

        @Test
        void testValidBranchNode() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [
                      {"id":"start","type":"Start","description":"开始","parameters":{"outputs":[{"name":"query","description":"用户输入"}]},"next":"branch1"},
                      {"id":"branch1","type":"Branch","description":"分支节点","parameters":{"inputs":[{"name":"input","value":"${start.query}"}],"conditions":[{"branch":"branch1","description":"条件1","expression":"${start.query} eq 'yes'","next":"end1"},{"branch":"default","description":"默认","expression":"default","next":"end2"}]}},
                      {"id":"end1","type":"End","description":"结束1","parameters":{"inputs":[],"configs":{"template":""}}},
                      {"id":"end2","type":"End","description":"结束2","parameters":{"inputs":[],"configs":{"template":""}}}
                    ]
                    """);
            assertThat(reflector.getErrors()).isEmpty();
        }

        @Test
        void testBranchNodeMissingDefaultBranch() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [{"id":"branch1","type":"Branch","description":"分支节点","parameters":{"inputs":[],"conditions":[{"branch":"branch1","description":"条件1","expression":"'test' eq 'yes'","next":"end1"}]}}]
                    """);
            assertThat(reflector.getErrors()).anyMatch(msg -> msg.contains("default"));
        }
    }

    @Nested
    class TestReflectorIntentDetectionNode {

        @Test
        void testValidIntentDetectionNode() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [
                      {"id":"intent1","type":"IntentDetection","description":"意图检测节点","parameters":{"inputs":[{"name":"input","value":"test"}],"configs":{"prompt":"检测意图"},"conditions":[{"branch":"intent1","description":"意图1","expression":"${intent1.rawOutput} contain 'intent1'","next":"end1"},{"branch":"default","description":"默认","expression":"default","next":"end2"}]}},
                      {"id":"end1","type":"End","description":"结束1","parameters":{"inputs":[],"configs":{"template":""}}},
                      {"id":"end2","type":"End","description":"结束2","parameters":{"inputs":[],"configs":{"template":""}}}
                    ]
                    """);
            assertThat(reflector.getErrors()).isEmpty();
        }

        @Test
        void testIntentDetectionMissingConditions() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [{"id":"intent1","type":"IntentDetection","description":"意图检测节点","parameters":{"inputs":[],"configs":{"prompt":"检测意图"}}}]
                    """);
            assertThat(reflector.getErrors()).anyMatch(msg -> msg.contains("conditions"));
        }
    }

    @Nested
    class TestReflectorNonExistentNodeReference {

        @Test
        void testReferenceToNonExistentNode() {
            DlReflector reflector = new DlReflector();
            reflector.checkFormat("""
                    [{"id":"start","type":"Start","description":"开始","parameters":{"outputs":[{"name":"query","description":"用户输入"}]},"next":"non_existent_node"}]
                    """);
            assertThat(reflector.getErrors()).anyMatch(msg -> msg.contains("不存在"));
        }
    }
}
