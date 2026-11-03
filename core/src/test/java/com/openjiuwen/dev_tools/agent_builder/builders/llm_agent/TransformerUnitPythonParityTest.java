/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code TestTransformer} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/llm_agent/test_transformer.py}.</p>
 */
class TransformerUnitPythonParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long FIXED_TIMESTAMP = 1700000000000L;

    @TestFactory
    Collection<DynamicTest> pythonTestTransformerCases() {
        return List.of(
                dynamic("TestTransformer::test_collect_plugin", this::collectPlugin),
                dynamic("TestTransformer::test_collect_plugin_empty_list", this::collectPluginEmptyList),
                dynamic("TestTransformer::test_collect_plugin_invalid_tool_id", this::collectPluginInvalidToolId),
                dynamic("TestTransformer::test_collect_workflow", this::collectWorkflow),
                dynamic("TestTransformer::test_collect_workflow_empty_list", this::collectWorkflowEmptyList),
                dynamic("TestTransformer::test_collect_workflow_invalid_id_returns_entry",
                        this::collectWorkflowInvalidIdReturnsEntry),
                dynamic("TestTransformer::test_convert_input_parameters", this::convertInputParameters),
                dynamic("TestTransformer::test_convert_output_parameters", this::convertOutputParameters),
                dynamic("TestTransformer::test_build_plugin_dependencies", this::buildPluginDependencies),
                dynamic("TestTransformer::test_build_workflow_dependencies", this::buildWorkflowDependencies),
                dynamic("TestTransformer::test_transform_to_dsl", this::transformToDsl),
                dynamic("TestTransformer::test_transform_to_dsl_without_plugins", this::transformToDslWithoutPlugins),
                dynamic("TestTransformer::test_transform_to_dsl_generates_valid_json",
                        this::transformToDslGeneratesValidJson)
        );
    }

    private void collectPlugin() {
        List<Map<String, Object>> result = Transformer.collectPlugin(
                List.of("tool_001"),
                samplePluginDict(),
                sampleToolIdMap()
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("plugin_id", "plugin_001")
                .containsEntry("tool_id", "tool_001")
                .containsEntry("tool_name", "Test Tool");
    }

    private void collectPluginEmptyList() {
        List<Map<String, Object>> result = Transformer.collectPlugin(
                List.of(),
                samplePluginDict(),
                sampleToolIdMap()
        );

        assertThat(result).isEmpty();
    }

    private void collectPluginInvalidToolId() {
        List<Map<String, Object>> result = Transformer.collectPlugin(
                List.of("invalid_tool"),
                samplePluginDict(),
                sampleToolIdMap()
        );

        assertThat(result).isEmpty();
    }

    private void collectWorkflow() {
        List<Map<String, Object>> result = Transformer.collectWorkflow(
                List.of("wf_001"),
                sampleWorkflowDict()
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("workflow_id", "wf_001")
                .containsEntry("workflow_name", "Test Workflow");
    }

    private void collectWorkflowEmptyList() {
        List<Map<String, Object>> result = Transformer.collectWorkflow(List.of(), sampleWorkflowDict());

        assertThat(result).isEmpty();
    }

    private void collectWorkflowInvalidIdReturnsEntry() {
        List<Map<String, Object>> result = Transformer.collectWorkflow(List.of("invalid_id"), sampleWorkflowDict());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("workflow_id", "invalid_id");
    }

    private void convertInputParameters() {
        List<Map<String, Object>> params = List.of(
                map("name", "p1", "desc", "Param 1", "type", 1, "value", "v1"),
                map("name", "p2", "description", "Param 2", "type", 2)
        );

        List<Map<String, Object>> result = Transformer.convertInputParameters(params);

        assertThat(result).hasSize(2);
        assertThat(result.get(0))
                .containsEntry("name", "p1")
                .containsEntry("desc", "Param 1")
                .containsEntry("type", 1);
        assertThat(result.get(1)).containsEntry("desc", "Param 2");
    }

    private void convertOutputParameters() {
        List<Map<String, Object>> params = List.of(map("name", "result", "desc", "Output result", "type", 1));

        List<Map<String, Object>> result = Transformer.convertOutputParameters(params);

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("name", "result")
                .containsEntry("is_runtime", false);
    }

    private void buildPluginDependencies() {
        List<Map<String, Object>> result = Transformer.buildPluginDependencies(
                List.of("tool_001"),
                samplePluginDict(),
                sampleToolIdMap(),
                FIXED_TIMESTAMP
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("plugin_id", "plugin_001")
                .containsEntry("plugin_version", "1.0.0")
                .containsEntry("name", "Test Plugin")
                .containsEntry("create_time", FIXED_TIMESTAMP);
        assertThat((List<?>) result.get(0).get("tool_list")).hasSize(1);
    }

    private void buildWorkflowDependencies() {
        List<Map<String, Object>> result = Transformer.buildWorkflowDependencies(
                List.of("wf_001"),
                sampleWorkflowDict(),
                FIXED_TIMESTAMP
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("workflow_id", "wf_001")
                .containsEntry("workflow_version", "1.0.0")
                .containsEntry("name", "Test Workflow")
                .containsEntry("create_time", FIXED_TIMESTAMP);
    }

    private void transformToDsl() throws Exception {
        Map<String, Object> agentInfo = map(
                "name", "Test Agent",
                "description", "A test agent",
                "prompt", "You are a test agent.",
                "opening_remarks", "Hello!",
                "plugin", List.of("tool_001"),
                "workflow", List.of("wf_001")
        );
        Map<String, Object> resource = map(
                "plugin_dict", samplePluginDict(),
                "tool_id_map", sampleToolIdMap(),
                "workflow_dict", sampleWorkflowDict()
        );

        Map<String, Object> dsl = readDsl(new Transformer().transformToDsl(agentInfo, resource));

        assertThat(dsl)
                .containsEntry("name", "Test Agent")
                .containsEntry("description", "A test agent")
                .containsEntry("opening_remarks", "Hello!");
        assertThat(asMap(dsl.get("configs"))).containsEntry("system_prompt", "You are a test agent.");
        assertThat((List<?>) dsl.get("plugins")).hasSize(1);
        assertThat((List<?>) dsl.get("workflows")).hasSize(1);
        assertThat(dsl).containsKeys("dependencies", "agent_id", "create_time", "update_time");
        assertThat((String) dsl.get("agent_id")).isNotEmpty();
        assertThat(dsl.get("create_time")).isNotNull();
        assertThat(dsl.get("update_time")).isNotNull();
    }

    private void transformToDslWithoutPlugins() throws Exception {
        Map<String, Object> agentInfo = map(
                "name", "Simple Agent",
                "description", "A simple agent",
                "prompt", "You are a simple agent.",
                "opening_remarks", "Hi!",
                "plugin", List.of(),
                "workflow", List.of()
        );
        Map<String, Object> resource = map("plugin_dict", Map.of(), "tool_id_map", Map.of(), "workflow_dict", Map.of());

        Map<String, Object> dsl = readDsl(new Transformer().transformToDsl(agentInfo, resource));

        assertThat(dsl).containsEntry("name", "Simple Agent");
        assertThat((List<?>) dsl.get("plugins")).isEmpty();
        assertThat((List<?>) dsl.get("workflows")).isEmpty();
    }

    private void transformToDslGeneratesValidJson() throws Exception {
        Map<String, Object> agentInfo = map(
                "name", "Test",
                "description", "Test",
                "prompt", "Test",
                "opening_remarks", "Test",
                "plugin", List.of(),
                "workflow", List.of()
        );
        Map<String, Object> resource = map("plugin_dict", Map.of(), "tool_id_map", Map.of(), "workflow_dict", Map.of());

        Map<String, Object> parsed = readDsl(new Transformer().transformToDsl(agentInfo, resource));

        assertThat(parsed).isNotEmpty();
        assertThat(parsed).containsKeys("agent_id", "name");
    }

    private static DynamicTest dynamic(String name, Executable executable) {
        return DynamicTest.dynamicTest(name, executable);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readDsl(String value) throws Exception {
        return MAPPER.readValue(value, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, String> sampleToolIdMap() {
        return Map.of("tool_001", "plugin_001");
    }

    private static Map<String, Map<String, Object>> samplePluginDict() {
        Map<String, Object> tool = map(
                "tool_id", "tool_001",
                "tool_name", "Test Tool",
                "tool_desc", "A test tool",
                "code", "print('hello')",
                "language", "python",
                "input_parameters", List.of(map("name", "param1", "desc", "First param", "type", 1)),
                "output_parameters", List.of(map("name", "result", "desc", "Result", "type", 1))
        );
        Map<String, Object> plugin = map(
                "plugin_id", "plugin_001",
                "plugin_name", "Test Plugin",
                "plugin_desc", "A test plugin",
                "plugin_version", "1.0.0",
                "tools", Map.of("tool_001", tool)
        );
        return Map.of("plugin_001", plugin);
    }

    private static Map<String, Map<String, Object>> sampleWorkflowDict() {
        Map<String, Object> workflow = map(
                "workflow_id", "wf_001",
                "workflow_name", "Test Workflow",
                "workflow_version", "1.0.0",
                "workflow_desc", "A test workflow",
                "input_parameters", List.of(),
                "output_parameters", List.of()
        );
        return Map.of("wf_001", workflow);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }
}
