/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for LLM Agent Transformer module.
 * <p>
 * Mirrors Python's {@code test_llm_agent_transformer_e2e.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.llm_agent}.
 */
class TestTransformerE2E {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Transformer transformer;
    private Map<String, Map<String, Object>> samplePluginDict;
    private Map<String, String> sampleToolIdMap;
    private Map<String, Map<String, Object>> sampleWorkflowDict;

    @BeforeEach
    void setUp() {
        transformer = new Transformer();
        samplePluginDict = buildSamplePluginDict();
        sampleToolIdMap = buildSampleToolIdMap();
        sampleWorkflowDict = buildSampleWorkflowDict();
    }

    @Nested
    class TestTransformerEndToEnd {

        @Test
        void transformToDslCompleteFlow() throws Exception {
            Map<String, Object> agentInfo = new LinkedHashMap<>();
            agentInfo.put("name", "Smart Assistant");
            agentInfo.put("description", "An intelligent assistant with weather and calculation capabilities");
            agentInfo.put("opening_remarks", "Hello! How can I help you today?");
            agentInfo.put("plugin", List.of("tool_001", "tool_003"));
            agentInfo.put("workflow", List.of("wf_001"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("plugin_dict", samplePluginDict);
            resource.put("tool_id_map", sampleToolIdMap);
            resource.put("workflow_dict", sampleWorkflowDict);

            String result = transformer.transformToDsl(agentInfo, resource);
            @SuppressWarnings("unchecked")
            Map<String, Object> dsl = MAPPER.readValue(result, Map.class);

            assertThat(dsl.get("name")).isEqualTo("Smart Assistant");
            assertThat(dsl.get("description")).isEqualTo("An intelligent assistant with weather and calculation capabilities");
            assertThat(dsl.get("opening_remarks")).isEqualTo("Hello! How can I help you today?");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plugins = (List<Map<String, Object>>) dsl.get("plugins");
            assertThat(plugins).hasSize(2);
            List<String> pluginIds = plugins.stream().map(p -> (String) p.get("plugin_id")).toList();
            assertThat(pluginIds).containsExactlyInAnyOrder("plugin_001", "plugin_002");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> workflows = (List<Map<String, Object>>) dsl.get("workflows");
            assertThat(workflows).hasSize(1);
            assertThat(workflows.get(0).get("workflow_id")).isEqualTo("wf_001");

            assertThat(dsl).containsKey("dependencies");
            @SuppressWarnings("unchecked")
            Map<String, Object> deps = (Map<String, Object>) dsl.get("dependencies");
            @SuppressWarnings("unchecked")
            List<Object> depPlugins = (List<Object>) deps.get("plugins");
            @SuppressWarnings("unchecked")
            List<Object> depWorkflows = (List<Object>) deps.get("workflows");
            assertThat(depPlugins).hasSize(2);
            assertThat(depWorkflows).hasSize(1);

            assertThat(dsl.get("agent_id")).isNotEqualTo("");
            assertThat(dsl.get("agent_type")).isEqualTo("react");
            assertThat(dsl.get("create_time")).isNotNull();
            assertThat(dsl.get("update_time")).isNotNull();
        }

        @Test
        void transformToDslWithMultipleToolsSamePlugin() throws Exception {
            Map<String, Object> agentInfo = new LinkedHashMap<>();
            agentInfo.put("name", "Weather Expert");
            agentInfo.put("description", "Weather specialist");
            agentInfo.put("prompt", "You are a weather expert.");
            agentInfo.put("opening_remarks", "Ask me about weather!");
            agentInfo.put("plugin", List.of("tool_001", "tool_002"));
            agentInfo.put("workflow", List.of());

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("plugin_dict", samplePluginDict);
            resource.put("tool_id_map", sampleToolIdMap);
            resource.put("workflow_dict", sampleWorkflowDict);

            String result = transformer.transformToDsl(agentInfo, resource);
            @SuppressWarnings("unchecked")
            Map<String, Object> dsl = MAPPER.readValue(result, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> plugins = (List<Map<String, Object>>) dsl.get("plugins");
            assertThat(plugins).hasSize(2);
            List<String> pluginNames = plugins.stream().map(p -> (String) p.get("plugin_name")).toList();
            assertThat(pluginNames).contains("Weather Plugin");

            @SuppressWarnings("unchecked")
            Map<String, Object> deps = (Map<String, Object>) dsl.get("dependencies");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> depPlugins = (List<Map<String, Object>>) deps.get("plugins");
            assertThat(depPlugins).hasSize(1);
            assertThat(depPlugins.get(0).get("plugin_id")).isEqualTo("plugin_001");
        }

        @Test
        void transformToDslMinimalAgent() throws Exception {
            Map<String, Object> agentInfo = new LinkedHashMap<>();
            agentInfo.put("name", "Simple Agent");
            agentInfo.put("description", "A simple agent");
            agentInfo.put("prompt", "You are simple.");
            agentInfo.put("opening_remarks", "Hi!");
            agentInfo.put("plugin", List.of());
            agentInfo.put("workflow", List.of());

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("plugin_dict", Map.of());
            resource.put("tool_id_map", Map.of());
            resource.put("workflow_dict", Map.of());

            String result = transformer.transformToDsl(agentInfo, resource);
            @SuppressWarnings("unchecked")
            Map<String, Object> dsl = MAPPER.readValue(result, Map.class);

            assertThat(dsl.get("name")).isEqualTo("Simple Agent");
            assertThat(dsl.get("plugins")).isNotNull();
            assertThat(dsl.get("workflows")).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> deps = (Map<String, Object>) dsl.get("dependencies");
            assertThat((List<?>) deps.get("plugins")).isEmpty();
            assertThat((List<?>) deps.get("workflows")).isEmpty();
        }

        @Test
        void transformToDslGeneratesUniqueAgentIds() throws Exception {
            Map<String, Object> agentInfo = new LinkedHashMap<>();
            agentInfo.put("name", "Test Agent");
            agentInfo.put("description", "Test");
            agentInfo.put("prompt", "Test prompt");
            agentInfo.put("opening_remarks", "Hello");
            agentInfo.put("plugin", List.of());
            agentInfo.put("workflow", List.of());

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("plugin_dict", Map.of());
            resource.put("tool_id_map", Map.of());
            resource.put("workflow_dict", Map.of());

            String result1 = transformer.transformToDsl(agentInfo, resource);
            String result2 = transformer.transformToDsl(agentInfo, resource);

            @SuppressWarnings("unchecked")
            Map<String, Object> dsl1 = MAPPER.readValue(result1, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> dsl2 = MAPPER.readValue(result2, Map.class);

            assertThat(dsl1.get("agent_id")).isNotEqualTo(dsl2.get("agent_id"));
        }
    }

    @Nested
    class TestTransformerWithTemplateIntegration {

        @Test
        void dslInheritsTemplateStructure() throws Exception {
            Map<String, Object> agentInfo = buildMinimalAgentInfo();
            Map<String, Object> resource = buildEmptyResource();

            String result = transformer.transformToDsl(agentInfo, resource);
            @SuppressWarnings("unchecked")
            Map<String, Object> dsl = MAPPER.readValue(result, Map.class);

            for (String key : LlmAgentTemplate.create().keySet()) {
                assertThat(dsl).containsKey(key);
            }
            assertThat(dsl).containsKey("constraints");
            @SuppressWarnings("unchecked")
            Map<String, Object> constraints = (Map<String, Object>) dsl.get("constraints");
            assertThat(constraints).containsKey("max_iterations");
            assertThat(dsl).containsKey("memory");
            assertThat(dsl).containsKey("model");
            @SuppressWarnings("unchecked")
            Map<String, Object> model = (Map<String, Object>) dsl.get("model");
            assertThat(model).containsKey("model_info");
        }

        @Test
        void dslTemplateValuesPreserved() throws Exception {
            Map<String, Object> agentInfo = buildMinimalAgentInfo();
            Map<String, Object> resource = buildEmptyResource();

            String result = transformer.transformToDsl(agentInfo, resource);
            @SuppressWarnings("unchecked")
            Map<String, Object> dsl = MAPPER.readValue(result, Map.class);

            assertThat(dsl.get("agent_type")).isEqualTo("react");
            assertThat(dsl.get("edit_mode")).isEqualTo("manual");
            @SuppressWarnings("unchecked")
            Map<String, Object> constraints = (Map<String, Object>) dsl.get("constraints");
            assertThat(constraints.get("max_iterations")).isEqualTo(5);
            @SuppressWarnings("unchecked")
            Map<String, Object> memory = (Map<String, Object>) dsl.get("memory");
            assertThat(memory.get("max_tokens")).isEqualTo(1000);
        }
    }

    @Nested
    class TestTransformerPluginDependenciesIntegration {

        @Test
        void buildPluginDependenciesCompleteMetadata() {
            long currentTs = 1700000000000L;

            List<Map<String, Object>> dependencies = Transformer.buildPluginDependencies(
                    List.of("tool_001", "tool_003"),
                    samplePluginDict,
                    sampleToolIdMap,
                    currentTs
            );

            assertThat(dependencies).hasSize(2);

            Map<String, Object> weatherDep = dependencies.stream()
                    .filter(d -> "plugin_001".equals(d.get("plugin_id"))).findFirst().orElseThrow();
            assertThat(weatherDep.get("name")).isEqualTo("Weather Plugin");
            assertThat(weatherDep.get("desc")).isEqualTo("Get weather information");
            assertThat(weatherDep.get("plugin_version")).isEqualTo("1.0.0");
            assertThat(weatherDep.get("create_time")).isEqualTo(currentTs);
            assertThat(weatherDep.get("update_time")).isEqualTo(currentTs);
            @SuppressWarnings("unchecked")
            List<?> toolList = (List<?>) weatherDep.get("tool_list");
            assertThat(toolList).hasSize(1);

            Map<String, Object> calcDep = dependencies.stream()
                    .filter(d -> "plugin_002".equals(d.get("plugin_id"))).findFirst().orElseThrow();
            assertThat(calcDep.get("name")).isEqualTo("Calculator Plugin");
            @SuppressWarnings("unchecked")
            List<?> calcToolList = (List<?>) calcDep.get("tool_list");
            assertThat(calcToolList).hasSize(1);
        }

        @Test
        void buildPluginDependenciesToolConversion() {
            long currentTs = 1700000000000L;

            List<Map<String, Object>> dependencies = Transformer.buildPluginDependencies(
                    List.of("tool_001"),
                    samplePluginDict,
                    sampleToolIdMap,
                    currentTs
            );

            assertThat(dependencies).hasSize(1);
            @SuppressWarnings("unchecked")
            Map<String, Object> tool = ((List<Map<String, Object>>) dependencies.get(0).get("tool_list")).get(0);

            assertThat(tool.get("tool_id")).isEqualTo("tool_001");
            assertThat(tool.get("name")).isEqualTo("Get Weather");
            assertThat(tool.get("desc")).isEqualTo("Get current weather");
            assertThat(tool.get("language")).isEqualTo("python");
            assertThat(tool.get("plugin_id")).isEqualTo("plugin_001");
            assertThat(tool.get("available")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inputParams = (List<Map<String, Object>>) tool.get("input_parameters");
            assertThat(inputParams).hasSize(1);
            assertThat(inputParams.get(0).get("name")).isEqualTo("city");
        }

        @Test
        void buildPluginDependenciesHandlesMissingTool() {
            long currentTs = 1700000000000L;

            List<Map<String, Object>> dependencies = Transformer.buildPluginDependencies(
                    List.of("non_existent_tool"),
                    samplePluginDict,
                    sampleToolIdMap,
                    currentTs
            );

            assertThat(dependencies).isEmpty();
        }
    }

    @Nested
    class TestTransformerWorkflowDependenciesIntegration {

        @Test
        void buildWorkflowDependenciesCompleteMetadata() {
            long currentTs = 1700000000000L;

            List<Map<String, Object>> dependencies = Transformer.buildWorkflowDependencies(
                    List.of("wf_001", "wf_002"),
                    sampleWorkflowDict,
                    currentTs
            );

            assertThat(dependencies).hasSize(2);

            Map<String, Object> wf1 = dependencies.stream()
                    .filter(d -> "wf_001".equals(d.get("workflow_id"))).findFirst().orElseThrow();
            assertThat(wf1.get("name")).isEqualTo("Data Processing");
            assertThat(wf1.get("desc")).isEqualTo("Process and transform data");
            assertThat(wf1.get("workflow_version")).isEqualTo("1.0.0");
            assertThat((List<?>) wf1.get("input_parameters")).hasSize(1);
            assertThat((List<?>) wf1.get("output_parameters")).hasSize(1);

            Map<String, Object> wf2 = dependencies.stream()
                    .filter(d -> "wf_002".equals(d.get("workflow_id"))).findFirst().orElseThrow();
            assertThat(wf2.get("name")).isEqualTo("Report Generation");
        }

        @Test
        void buildWorkflowDependenciesHandlesMissingWorkflow() {
            long currentTs = 1700000000000L;

            List<Map<String, Object>> dependencies = Transformer.buildWorkflowDependencies(
                    List.of("non_existent_wf"),
                    sampleWorkflowDict,
                    currentTs
            );

            assertThat(dependencies).hasSize(1);
            assertThat(dependencies.get(0).get("workflow_id")).isEqualTo("non_existent_wf");
            assertThat(dependencies.get(0).get("name")).isEqualTo("");
        }
    }

    @Nested
    class TestTransformerJSONOutputIntegration {

        @Test
        void outputIsValidJson() throws Exception {
            Map<String, Object> agentInfo = new LinkedHashMap<>();
            agentInfo.put("name", "JSON Test Agent");
            agentInfo.put("description", "Test JSON output");
            agentInfo.put("prompt", "Test prompt with special chars: \n\t\"quotes\"");
            agentInfo.put("opening_remarks", "Hello!");
            agentInfo.put("plugin", List.of("tool_001"));
            agentInfo.put("workflow", List.of("wf_001"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("plugin_dict", samplePluginDict);
            resource.put("tool_id_map", sampleToolIdMap);
            resource.put("workflow_dict", sampleWorkflowDict);

            String result = transformer.transformToDsl(agentInfo, resource);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = MAPPER.readValue(result, Map.class);
            assertThat(parsed).isInstanceOf(Map.class);
        }

        @Test
        void outputHandlesUnicode() throws Exception {
            Map<String, Object> agentInfo = new LinkedHashMap<>();
            agentInfo.put("name", "中文助手");
            agentInfo.put("description", "这是一个中文描述，包含特殊字符：😊🎉");
            agentInfo.put("prompt", "你是一个智能助手。");
            agentInfo.put("opening_remarks", "你好！欢迎使用！");
            agentInfo.put("plugin", List.of());
            agentInfo.put("workflow", List.of());

            Map<String, Object> resource = buildEmptyResource();

            String result = transformer.transformToDsl(agentInfo, resource);
            @SuppressWarnings("unchecked")
            Map<String, Object> dsl = MAPPER.readValue(result, Map.class);

            assertThat(dsl.get("name")).isEqualTo("中文助手");
            assertThat((String) dsl.get("description")).contains("中文描述");
            assertThat((String) dsl.get("description")).contains("😊");
            assertThat(dsl.get("opening_remarks")).isEqualTo("你好！欢迎使用！");
        }
    }

    @Nested
    class TestTransformerCollectMethodsIntegration {

        @Test
        void collectPluginIntegration() {
            List<Map<String, Object>> result = Transformer.collectPlugin(
                    List.of("tool_001", "tool_002", "tool_003"),
                    samplePluginDict,
                    sampleToolIdMap
            );

            assertThat(result).hasSize(3);

            Map<String, Object> tool001 = result.stream()
                    .filter(r -> "tool_001".equals(r.get("tool_id"))).findFirst().orElseThrow();
            assertThat(tool001.get("plugin_id")).isEqualTo("plugin_001");
            assertThat(tool001.get("plugin_name")).isEqualTo("Weather Plugin");
            assertThat(tool001.get("tool_name")).isEqualTo("Get Weather");
        }

        @Test
        void collectWorkflowIntegration() {
            List<Map<String, Object>> result = Transformer.collectWorkflow(
                    List.of("wf_001", "wf_002"),
                    sampleWorkflowDict
            );

            assertThat(result).hasSize(2);

            Map<String, Object> wf1 = result.stream()
                    .filter(r -> "wf_001".equals(r.get("workflow_id"))).findFirst().orElseThrow();
            assertThat(wf1.get("workflow_name")).isEqualTo("Data Processing");
            assertThat(wf1.get("workflow_version")).isEqualTo("1.0.0");
            assertThat(wf1.get("description")).isEqualTo("Process and transform data");
        }
    }

    private Map<String, Object> buildMinimalAgentInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "Test");
        info.put("description", "Test");
        info.put("prompt", "Test");
        info.put("opening_remarks", "Test");
        info.put("plugin", List.of());
        info.put("workflow", List.of());
        return info;
    }

    private Map<String, Object> buildEmptyResource() {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("plugin_dict", Map.of());
        resource.put("tool_id_map", Map.of());
        resource.put("workflow_dict", Map.of());
        return resource;
    }

    private Map<String, Map<String, Object>> buildSamplePluginDict() {
        Map<String, Object> tool001 = new LinkedHashMap<>();
        tool001.put("tool_id", "tool_001");
        tool001.put("tool_name", "Get Weather");
        tool001.put("tool_desc", "Get current weather");
        tool001.put("code", "def get_weather(city): return f'Weather in {city}'");
        tool001.put("language", "python");
        tool001.put("input_parameters", List.of(
                Map.of("name", "city", "desc", "City name", "type", 1, "value", "", "is_required", true)
        ));
        tool001.put("output_parameters", List.of(
                Map.of("name", "result", "desc", "Weather result", "type", 1)
        ));

        Map<String, Object> tool002 = new LinkedHashMap<>();
        tool002.put("tool_id", "tool_002");
        tool002.put("tool_name", "Get Forecast");
        tool002.put("tool_desc", "Get weather forecast");
        tool002.put("code", "def get_forecast(city, days): return f'Forecast for {city}'");
        tool002.put("language", "python");
        tool002.put("input_parameters", List.of(
                Map.of("name", "city", "desc", "City name", "type", 1),
                Map.of("name", "days", "desc", "Number of days", "type", 2)
        ));
        tool002.put("output_parameters", List.of(
                Map.of("name", "forecast", "desc", "Forecast result", "type", 1)
        ));

        Map<String, Object> plugin001 = new LinkedHashMap<>();
        plugin001.put("plugin_id", "plugin_001");
        plugin001.put("plugin_name", "Weather Plugin");
        plugin001.put("plugin_desc", "Get weather information");
        plugin001.put("plugin_version", "1.0.0");
        plugin001.put("tools", Map.of("tool_001", tool001, "tool_002", tool002));

        Map<String, Object> tool003 = new LinkedHashMap<>();
        tool003.put("tool_id", "tool_003");
        tool003.put("tool_name", "Calculate");
        tool003.put("tool_desc", "Perform calculation");
        tool003.put("code", "def calculate(expr): return eval(expr)");
        tool003.put("language", "python");
        tool003.put("input_parameters", List.of(
                Map.of("name", "expression", "desc", "Math expression", "type", 1)
        ));
        tool003.put("output_parameters", List.of(
                Map.of("name", "result", "desc", "Calculation result", "type", 2)
        ));

        Map<String, Object> plugin002 = new LinkedHashMap<>();
        plugin002.put("plugin_id", "plugin_002");
        plugin002.put("plugin_name", "Calculator Plugin");
        plugin002.put("plugin_desc", "Perform calculations");
        plugin002.put("plugin_version", "2.0.0");
        plugin002.put("tools", Map.of("tool_003", tool003));

        Map<String, Map<String, Object>> dict = new LinkedHashMap<>();
        dict.put("plugin_001", plugin001);
        dict.put("plugin_002", plugin002);
        return dict;
    }

    private Map<String, String> buildSampleToolIdMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("tool_001", "plugin_001");
        map.put("tool_002", "plugin_001");
        map.put("tool_003", "plugin_002");
        return map;
    }

    private Map<String, Map<String, Object>> buildSampleWorkflowDict() {
        Map<String, Object> wf001 = new LinkedHashMap<>();
        wf001.put("workflow_id", "wf_001");
        wf001.put("workflow_name", "Data Processing");
        wf001.put("workflow_version", "1.0.0");
        wf001.put("workflow_desc", "Process and transform data");
        wf001.put("input_parameters", List.of(Map.of("name", "input_data", "desc", "Input data", "type", 1)));
        wf001.put("output_parameters", List.of(Map.of("name", "output_data", "desc", "Output data", "type", 1)));

        Map<String, Object> wf002 = new LinkedHashMap<>();
        wf002.put("workflow_id", "wf_002");
        wf002.put("workflow_name", "Report Generation");
        wf002.put("workflow_version", "2.0.0");
        wf002.put("workflow_desc", "Generate reports");
        wf002.put("input_parameters", List.of());
        wf002.put("output_parameters", List.of());

        Map<String, Map<String, Object>> dict = new LinkedHashMap<>();
        dict.put("wf_001", wf001);
        dict.put("wf_002", wf002);
        return dict;
    }
}
