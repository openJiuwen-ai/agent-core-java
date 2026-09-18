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
 * <p>Mirrors Python's {@code test_llm_agent_transformer_e2e} in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/llm_agent/test_llm_agent_transformer_e2e.py}.</p>
 */
class TransformerEndToEndPythonParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long FIXED_TIMESTAMP = 1700000000000L;

    @TestFactory
    Collection<DynamicTest> pythonTransformerEndToEndCases() {
        return List.of(
                dynamic("TestTransformerEndToEnd::test_transform_to_dsl_complete_flow",
                        this::transformToDslCompleteFlow),
                dynamic("TestTransformerEndToEnd::test_transform_to_dsl_with_multiple_tools_same_plugin",
                        this::transformToDslWithMultipleToolsSamePlugin),
                dynamic("TestTransformerEndToEnd::test_transform_to_dsl_minimal_agent",
                        this::transformToDslMinimalAgent),
                dynamic("TestTransformerEndToEnd::test_transform_to_dsl_generates_unique_agent_ids",
                        this::transformToDslGeneratesUniqueAgentIds),
                dynamic("TestTransformerWithTemplateIntegration::test_dsl_inherits_template_structure",
                        this::dslInheritsTemplateStructure),
                dynamic("TestTransformerWithTemplateIntegration::test_dsl_template_values_preserved",
                        this::dslTemplateValuesPreserved),
                dynamic("TestTransformerPluginDependenciesIntegration::test_build_plugin_dependencies_complete_metadata",
                        this::buildPluginDependenciesCompleteMetadata),
                dynamic("TestTransformerPluginDependenciesIntegration::test_build_plugin_dependencies_tool_conversion",
                        this::buildPluginDependenciesToolConversion),
                dynamic("TestTransformerPluginDependenciesIntegration::test_build_plugin_dependencies_handles_missing_tool",
                        this::buildPluginDependenciesHandlesMissingTool),
                dynamic("TestTransformerWorkflowDependenciesIntegration::test_build_workflow_dependencies_complete_metadata",
                        this::buildWorkflowDependenciesCompleteMetadata),
                dynamic("TestTransformerWorkflowDependenciesIntegration::test_build_workflow_dependencies_handles_missing_workflow",
                        this::buildWorkflowDependenciesHandlesMissingWorkflow),
                dynamic("TestTransformerJSONOutputIntegration::test_output_is_valid_json",
                        this::outputIsValidJson),
                dynamic("TestTransformerJSONOutputIntegration::test_output_handles_unicode",
                        this::outputHandlesUnicode),
                dynamic("TestTransformerCollectMethodsIntegration::test_collect_plugin_integration",
                        this::collectPluginIntegration),
                dynamic("TestTransformerCollectMethodsIntegration::test_collect_workflow_integration",
                        this::collectWorkflowIntegration)
        );
    }

    private void transformToDslCompleteFlow() throws Exception {
        Map<String, Object> agentInfo = map(
                "name", "Smart Assistant",
                "description", "An intelligent assistant with weather and calculation capabilities",
                "opening_remarks", "Hello! How can I help you today?",
                "plugin", List.of("tool_001", "tool_003"),
                "workflow", List.of("wf_001")
        );

        Map<String, Object> dsl = transform(agentInfo, sampleResource());

        assertThat(dsl).containsEntry("name", "Smart Assistant")
                .containsEntry("description", "An intelligent assistant with weather and calculation capabilities")
                .containsEntry("opening_remarks", "Hello! How can I help you today?")
                .containsEntry("agent_type", "react");
        assertThat((List<?>) dsl.get("plugins")).hasSize(2);
        assertThat((List<?>) dsl.get("workflows")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) dsl.get("workflows")).get(0)).get("workflow_id")).isEqualTo("wf_001");
        Map<String, Object> dependencies = stringObjectMap(dsl.get("dependencies"));
        assertThat((List<?>) dependencies.get("plugins")).hasSize(2);
        assertThat((List<?>) dependencies.get("workflows")).hasSize(1);
        assertThat((String) dsl.get("agent_id")).isNotEmpty();
        assertThat(dsl.get("create_time")).isNotNull();
        assertThat(dsl.get("update_time")).isNotNull();
    }

    private void transformToDslWithMultipleToolsSamePlugin() throws Exception {
        Map<String, Object> agentInfo = map(
                "name", "Weather Expert",
                "description", "Weather specialist",
                "prompt", "You are a weather expert.",
                "opening_remarks", "Ask me about weather!",
                "plugin", List.of("tool_001", "tool_002"),
                "workflow", List.of()
        );

        Map<String, Object> dsl = transform(agentInfo, sampleResource());

        List<?> plugins = (List<?>) dsl.get("plugins");
        assertThat(plugins).hasSize(2);
        assertThat(plugins.stream().anyMatch(item -> "Weather Plugin".equals(((Map<?, ?>) item).get("plugin_name"))))
                .isTrue();
        List<?> pluginDependencies = (List<?>) stringObjectMap(dsl.get("dependencies")).get("plugins");
        assertThat(pluginDependencies).hasSize(1);
        assertThat((Object) ((Map<?, ?>) pluginDependencies.get(0)).get("plugin_id")).isEqualTo("plugin_001");
    }

    private void transformToDslMinimalAgent() throws Exception {
        Map<String, Object> agentInfo = map(
                "name", "Simple Agent",
                "description", "A simple agent",
                "prompt", "You are simple.",
                "opening_remarks", "Hi!",
                "plugin", List.of(),
                "workflow", List.of()
        );

        Map<String, Object> dsl = transform(agentInfo, emptyResource());

        assertThat(dsl).containsEntry("name", "Simple Agent");
        assertThat((List<?>) dsl.get("plugins")).isEmpty();
        assertThat((List<?>) dsl.get("workflows")).isEmpty();
        Map<String, Object> dependencies = stringObjectMap(dsl.get("dependencies"));
        assertThat((List<?>) dependencies.get("plugins")).isEmpty();
        assertThat((List<?>) dependencies.get("workflows")).isEmpty();
    }

    private void transformToDslGeneratesUniqueAgentIds() throws Exception {
        Map<String, Object> agentInfo = map(
                "name", "Test Agent",
                "description", "Test",
                "prompt", "Test prompt",
                "opening_remarks", "Hello",
                "plugin", List.of(),
                "workflow", List.of()
        );

        Map<String, Object> first = transform(agentInfo, emptyResource());
        Map<String, Object> second = transform(agentInfo, emptyResource());

        assertThat(first.get("agent_id")).isNotEqualTo(second.get("agent_id"));
    }

    private void dslInheritsTemplateStructure() throws Exception {
        Map<String, Object> agentInfo = minimalAgentInfo("Test");

        Map<String, Object> dsl = transform(agentInfo, emptyResource());

        for (String key : LlmAgentTemplate.create().keySet()) {
            assertThat(dsl).containsKey(key);
        }
        assertThat(dsl).containsKey("constraints").containsKey("memory").containsKey("model");
        assertThat(stringObjectMap(dsl.get("constraints"))).containsKey("max_iterations");
        assertThat(stringObjectMap(dsl.get("memory"))).containsKey("max_tokens");
        assertThat(stringObjectMap(dsl.get("model"))).containsKey("model_info");
    }

    private void dslTemplateValuesPreserved() throws Exception {
        Map<String, Object> dsl = transform(minimalAgentInfo("Test"), emptyResource());

        assertThat(dsl).containsEntry("agent_type", "react").containsEntry("edit_mode", "manual");
        assertThat(stringObjectMap(dsl.get("constraints"))).containsEntry("max_iterations", 5);
        assertThat(stringObjectMap(dsl.get("memory"))).containsEntry("max_tokens", 1000);
    }

    private void buildPluginDependenciesCompleteMetadata() {
        List<Map<String, Object>> dependencies = Transformer.buildPluginDependencies(
                List.of("tool_001", "tool_003"),
                samplePluginDict(),
                sampleToolIdMap(),
                FIXED_TIMESTAMP
        );

        assertThat(dependencies).hasSize(2);
        Map<String, Object> weather = findById(dependencies, "plugin_id", "plugin_001");
        assertThat(weather).containsEntry("name", "Weather Plugin")
                .containsEntry("desc", "Get weather information")
                .containsEntry("plugin_version", "1.0.0")
                .containsEntry("create_time", FIXED_TIMESTAMP)
                .containsEntry("update_time", FIXED_TIMESTAMP);
        assertThat((List<?>) weather.get("tool_list")).hasSize(1);

        Map<String, Object> calculator = findById(dependencies, "plugin_id", "plugin_002");
        assertThat(calculator).containsEntry("name", "Calculator Plugin");
        assertThat((List<?>) calculator.get("tool_list")).hasSize(1);
    }

    private void buildPluginDependenciesToolConversion() {
        List<Map<String, Object>> dependencies = Transformer.buildPluginDependencies(
                List.of("tool_001"),
                samplePluginDict(),
                sampleToolIdMap(),
                FIXED_TIMESTAMP
        );

        Map<String, Object> tool = stringObjectMap(((List<?>) dependencies.get(0).get("tool_list")).get(0));

        assertThat(tool).containsEntry("tool_id", "tool_001")
                .containsEntry("name", "Get Weather")
                .containsEntry("desc", "Get current weather")
                .containsEntry("language", "python")
                .containsEntry("plugin_id", "plugin_001")
                .containsEntry("available", true);
        assertThat((List<?>) tool.get("input_parameters")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) tool.get("input_parameters")).get(0)).get("name")).isEqualTo("city");
    }

    private void buildPluginDependenciesHandlesMissingTool() {
        List<Map<String, Object>> dependencies = Transformer.buildPluginDependencies(
                List.of("non_existent_tool"),
                samplePluginDict(),
                sampleToolIdMap(),
                FIXED_TIMESTAMP
        );

        assertThat(dependencies).isEmpty();
    }

    private void buildWorkflowDependenciesCompleteMetadata() {
        List<Map<String, Object>> dependencies = Transformer.buildWorkflowDependencies(
                List.of("wf_001", "wf_002"),
                sampleWorkflowDict(),
                FIXED_TIMESTAMP
        );

        assertThat(dependencies).hasSize(2);
        Map<String, Object> firstWorkflow = findById(dependencies, "workflow_id", "wf_001");
        assertThat(firstWorkflow).containsEntry("name", "Data Processing")
                .containsEntry("desc", "Process and transform data")
                .containsEntry("workflow_version", "1.0.0");
        assertThat((List<?>) firstWorkflow.get("input_parameters")).hasSize(1);
        assertThat((List<?>) firstWorkflow.get("output_parameters")).hasSize(1);

        Map<String, Object> secondWorkflow = findById(dependencies, "workflow_id", "wf_002");
        assertThat(secondWorkflow).containsEntry("name", "Report Generation");
    }

    private void buildWorkflowDependenciesHandlesMissingWorkflow() {
        List<Map<String, Object>> dependencies = Transformer.buildWorkflowDependencies(
                List.of("non_existent_wf"),
                sampleWorkflowDict(),
                FIXED_TIMESTAMP
        );

        assertThat(dependencies).hasSize(1);
        assertThat(dependencies.get(0)).containsEntry("workflow_id", "non_existent_wf").containsEntry("name", "");
    }

    private void outputIsValidJson() throws Exception {
        Map<String, Object> agentInfo = map(
                "name", "JSON Test Agent",
                "description", "Test JSON output",
                "prompt", "Test prompt with special chars: \n\t\"quotes\"",
                "opening_remarks", "Hello!",
                "plugin", List.of("tool_001"),
                "workflow", List.of("wf_001")
        );

        Map<String, Object> parsed = transform(agentInfo, sampleResource());

        assertThat(parsed).isNotEmpty();
    }

    private void outputHandlesUnicode() throws Exception {
        Map<String, Object> agentInfo = map(
                "name", "\u4e2d\u6587\u52a9\u624b",
                "description", "\u8fd9\u662f\u4e00\u4e2a\u4e2d\u6587\u63cf\u8ff0\uff0c\u5305\u542b"
                        + "\u7279\u6b8a\u5b57\u7b26\uff1a\uD83D\uDE0A\uD83C\uDF89",
                "prompt", "\u4f60\u662f\u4e00\u4e2a\u667a\u80fd\u52a9\u624b\u3002",
                "opening_remarks", "\u4f60\u597d\uff01\u6b22\u8fce\u4f7f\u7528\uff01",
                "plugin", List.of(),
                "workflow", List.of()
        );

        Map<String, Object> dsl = transform(agentInfo, emptyResource());

        assertThat(dsl).containsEntry("name", "\u4e2d\u6587\u52a9\u624b")
                .containsEntry("opening_remarks", "\u4f60\u597d\uff01\u6b22\u8fce\u4f7f\u7528\uff01");
        assertThat((String) dsl.get("description"))
                .contains("\u4e2d\u6587\u63cf\u8ff0")
                .contains("\uD83D\uDE0A");
    }

    private void collectPluginIntegration() {
        List<Map<String, Object>> result = Transformer.collectPlugin(
                List.of("tool_001", "tool_002", "tool_003"),
                samplePluginDict(),
                sampleToolIdMap()
        );

        assertThat(result).hasSize(3);
        Map<String, Object> tool001 = findById(result, "tool_id", "tool_001");
        assertThat(tool001).containsEntry("plugin_id", "plugin_001")
                .containsEntry("plugin_name", "Weather Plugin")
                .containsEntry("tool_name", "Get Weather");
    }

    private void collectWorkflowIntegration() {
        List<Map<String, Object>> result = Transformer.collectWorkflow(
                List.of("wf_001", "wf_002"),
                sampleWorkflowDict()
        );

        assertThat(result).hasSize(2);
        Map<String, Object> workflow = findById(result, "workflow_id", "wf_001");
        assertThat(workflow).containsEntry("workflow_name", "Data Processing")
                .containsEntry("workflow_version", "1.0.0")
                .containsEntry("description", "Process and transform data");
    }

    private static DynamicTest dynamic(String name, Executable executable) {
        return DynamicTest.dynamicTest(name, executable);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> transform(Map<String, Object> agentInfo, Map<String, Object> resource)
            throws Exception {
        return MAPPER.readValue(new Transformer().transformToDsl(agentInfo, resource), Map.class);
    }

    private static Map<String, Object> sampleResource() {
        return map(
                "plugin_dict", samplePluginDict(),
                "tool_id_map", sampleToolIdMap(),
                "workflow_dict", sampleWorkflowDict()
        );
    }

    private static Map<String, Object> emptyResource() {
        return map("plugin_dict", Map.of(), "tool_id_map", Map.of(), "workflow_dict", Map.of());
    }

    private static Map<String, Object> minimalAgentInfo(String value) {
        return map(
                "name", value,
                "description", value,
                "prompt", value,
                "opening_remarks", value,
                "plugin", List.of(),
                "workflow", List.of()
        );
    }

    private static Map<String, String> sampleToolIdMap() {
        return Map.of("tool_001", "plugin_001", "tool_002", "plugin_001", "tool_003", "plugin_002");
    }

    private static Map<String, Map<String, Object>> samplePluginDict() {
        Map<String, Object> weatherTool = map(
                "tool_id", "tool_001",
                "tool_name", "Get Weather",
                "tool_desc", "Get current weather",
                "code", "def get_weather(city): return f'Weather in {city}'",
                "language", "python",
                "input_parameters", List.of(map("name", "city", "desc", "City name", "type", 1, "value", "",
                        "is_required", true)),
                "output_parameters", List.of(map("name", "result", "desc", "Weather result", "type", 1))
        );
        Map<String, Object> forecastTool = map(
                "tool_id", "tool_002",
                "tool_name", "Get Forecast",
                "tool_desc", "Get weather forecast",
                "code", "def get_forecast(city, days): return f'Forecast for {city}'",
                "language", "python",
                "input_parameters", List.of(
                        map("name", "city", "desc", "City name", "type", 1),
                        map("name", "days", "desc", "Number of days", "type", 2)
                ),
                "output_parameters", List.of(map("name", "forecast", "desc", "Forecast result", "type", 1))
        );
        Map<String, Object> calculatorTool = map(
                "tool_id", "tool_003",
                "tool_name", "Calculate",
                "tool_desc", "Perform calculation",
                "code", "def calculate(expr): return eval(expr)",
                "language", "python",
                "input_parameters", List.of(map("name", "expression", "desc", "Math expression", "type", 1)),
                "output_parameters", List.of(map("name", "result", "desc", "Calculation result", "type", 2))
        );

        return Map.of(
                "plugin_001", map(
                        "plugin_id", "plugin_001",
                        "plugin_name", "Weather Plugin",
                        "plugin_desc", "Get weather information",
                        "plugin_version", "1.0.0",
                        "tools", Map.of("tool_001", weatherTool, "tool_002", forecastTool)
                ),
                "plugin_002", map(
                        "plugin_id", "plugin_002",
                        "plugin_name", "Calculator Plugin",
                        "plugin_desc", "Perform calculations",
                        "plugin_version", "2.0.0",
                        "tools", Map.of("tool_003", calculatorTool)
                )
        );
    }

    private static Map<String, Map<String, Object>> sampleWorkflowDict() {
        return Map.of(
                "wf_001", map(
                        "workflow_id", "wf_001",
                        "workflow_name", "Data Processing",
                        "workflow_version", "1.0.0",
                        "workflow_desc", "Process and transform data",
                        "input_parameters", List.of(map("name", "input_data", "desc", "Input data", "type", 1)),
                        "output_parameters", List.of(map("name", "output_data", "desc", "Output data", "type", 1))
                ),
                "wf_002", map(
                        "workflow_id", "wf_002",
                        "workflow_name", "Report Generation",
                        "workflow_version", "2.0.0",
                        "workflow_desc", "Generate reports",
                        "input_parameters", List.of(),
                        "output_parameters", List.of()
                )
        );
    }

    private static Map<String, Object> findById(List<Map<String, Object>> items, String key, String expected) {
        return items.stream()
                .filter(item -> expected.equals(item.get(key)))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stringObjectMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }
}
