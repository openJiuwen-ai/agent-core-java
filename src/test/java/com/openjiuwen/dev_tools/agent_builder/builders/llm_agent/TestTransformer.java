/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test LlmAgentBuilder transformer functionality.
 * <p>
 * Mirrors Python's {@code test_transformer.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/llm_agent/test_transformer.py}.
 */
class TestTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Transformer transformer;
    private Map<String, Map<String, Object>> samplePluginDict;
    private Map<String, String> sampleToolIdMap;
    private Map<String, Map<String, Object>> sampleWorkflowDict;

    @BeforeEach
    void setUp() {
        transformer = new Transformer();
        samplePluginDict = buildSamplePluginDict();
        sampleToolIdMap = Map.of("tool_001", "plugin_001");
        sampleWorkflowDict = buildSampleWorkflowDict();
    }

    @Test
    void testCollectPlugin() {
        List<Map<String, Object>> result = Transformer.collectPlugin(
                List.of("tool_001"), samplePluginDict, sampleToolIdMap);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("plugin_id", "plugin_001");
        assertThat(result.get(0)).containsEntry("tool_id", "tool_001");
        assertThat(result.get(0)).containsEntry("tool_name", "Test Tool");
    }

    @Test
    void testCollectPluginEmptyList() {
        List<Map<String, Object>> result = Transformer.collectPlugin(
                List.of(), samplePluginDict, sampleToolIdMap);

        assertThat(result).isEmpty();
    }

    @Test
    void testCollectPluginInvalidToolId() {
        List<Map<String, Object>> result = Transformer.collectPlugin(
                List.of("invalid_tool"), samplePluginDict, sampleToolIdMap);

        assertThat(result).isEmpty();
    }

    @Test
    void testCollectWorkflow() {
        List<Map<String, Object>> result = Transformer.collectWorkflow(List.of("wf_001"), sampleWorkflowDict);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("workflow_id", "wf_001");
        assertThat(result.get(0)).containsEntry("workflow_name", "Test Workflow");
    }

    @Test
    void testCollectWorkflowEmptyList() {
        List<Map<String, Object>> result = Transformer.collectWorkflow(List.of(), sampleWorkflowDict);

        assertThat(result).isEmpty();
    }

    @Test
    void testCollectWorkflowInvalidIdReturnsEntry() {
        List<Map<String, Object>> result = Transformer.collectWorkflow(List.of("invalid_id"), sampleWorkflowDict);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("workflow_id", "invalid_id");
    }

    @Test
    void testConvertInputParameters() {
        List<Map<String, Object>> params = List.of(
                Map.of("name", "p1", "desc", "Param 1", "type", 1, "value", "v1"),
                Map.of("name", "p2", "description", "Param 2", "type", 2)
        );

        List<Map<String, Object>> result = Transformer.convertInputParameters(params);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("name", "p1");
        assertThat(result.get(0)).containsEntry("desc", "Param 1");
        assertThat(result.get(0)).containsEntry("type", 1);
        assertThat(result.get(1)).containsEntry("desc", "Param 2");
    }

    @Test
    void testConvertOutputParameters() {
        List<Map<String, Object>> params = List.of(Map.of("name", "result", "desc", "Output result", "type", 1));

        List<Map<String, Object>> result = Transformer.convertOutputParameters(params);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("name", "result");
        assertThat(result.get(0)).containsEntry("is_runtime", false);
    }

    @Test
    void testBuildPluginDependencies() {
        long currentTs = 1700000000000L;

        List<Map<String, Object>> result = Transformer.buildPluginDependencies(
                List.of("tool_001"), samplePluginDict, sampleToolIdMap, currentTs);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("plugin_id", "plugin_001");
        assertThat(result.get(0)).containsEntry("plugin_version", "1.0.0");
        assertThat(result.get(0)).containsEntry("name", "Test Plugin");
        assertThat((List<?>) result.get(0).get("tool_list")).hasSize(1);
        assertThat(result.get(0)).containsEntry("create_time", currentTs);
    }

    @Test
    void testBuildWorkflowDependencies() {
        long currentTs = 1700000000000L;

        List<Map<String, Object>> result = Transformer.buildWorkflowDependencies(
                List.of("wf_001"), sampleWorkflowDict, currentTs);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("workflow_id", "wf_001");
        assertThat(result.get(0)).containsEntry("workflow_version", "1.0.0");
        assertThat(result.get(0)).containsEntry("name", "Test Workflow");
        assertThat(result.get(0)).containsEntry("create_time", currentTs);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTransformToDsl() throws Exception {
        Map<String, Object> agentInfo = new LinkedHashMap<>();
        agentInfo.put("name", "Test Agent");
        agentInfo.put("description", "A test agent");
        agentInfo.put("prompt", "You are a test agent.");
        agentInfo.put("opening_remarks", "Hello!");
        agentInfo.put("plugin", List.of("tool_001"));
        agentInfo.put("workflow", List.of("wf_001"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("plugin_dict", samplePluginDict);
        resource.put("tool_id_map", sampleToolIdMap);
        resource.put("workflow_dict", sampleWorkflowDict);

        String result = transformer.transformToDsl(agentInfo, resource);
        Map<String, Object> dsl = MAPPER.readValue(result, Map.class);

        assertThat(dsl).containsEntry("name", "Test Agent");
        assertThat(dsl).containsEntry("description", "A test agent");
        assertThat((Map<String, Object>) dsl.get("configs")).containsEntry("system_prompt", "You are a test agent.");
        assertThat(dsl).containsEntry("opening_remarks", "Hello!");
        assertThat((List<?>) dsl.get("plugins")).hasSize(1);
        assertThat((List<?>) dsl.get("workflows")).hasSize(1);
        assertThat(dsl).containsKey("dependencies");
        assertThat(dsl.get("agent_id")).isNotEqualTo("");
        assertThat(dsl.get("create_time")).isNotNull();
        assertThat(dsl.get("update_time")).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTransformToDslWithoutPlugins() throws Exception {
        Map<String, Object> agentInfo = new LinkedHashMap<>();
        agentInfo.put("name", "Simple Agent");
        agentInfo.put("description", "A simple agent");
        agentInfo.put("prompt", "You are a simple agent.");
        agentInfo.put("opening_remarks", "Hi!");
        agentInfo.put("plugin", List.of());
        agentInfo.put("workflow", List.of());

        Map<String, Object> resource = Map.of("plugin_dict", Map.of(), "tool_id_map", Map.of(), "workflow_dict", Map.of());

        String result = transformer.transformToDsl(agentInfo, resource);
        Map<String, Object> dsl = MAPPER.readValue(result, Map.class);

        assertThat(dsl).containsEntry("name", "Simple Agent");
        assertThat((List<?>) dsl.get("plugins")).isEmpty();
        assertThat((List<?>) dsl.get("workflows")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTransformToDslGeneratesValidJson() throws Exception {
        Map<String, Object> agentInfo = new LinkedHashMap<>();
        agentInfo.put("name", "Test");
        agentInfo.put("description", "Test");
        agentInfo.put("prompt", "Test");
        agentInfo.put("opening_remarks", "Test");
        agentInfo.put("plugin", List.of());
        agentInfo.put("workflow", List.of());

        String result = transformer.transformToDsl(
                agentInfo,
                Map.of("plugin_dict", Map.of(), "tool_id_map", Map.of(), "workflow_dict", Map.of())
        );
        Map<String, Object> parsed = MAPPER.readValue(result, Map.class);

        assertThat(parsed).containsKeys("agent_id", "name");
    }

    private Map<String, Map<String, Object>> buildSamplePluginDict() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("tool_id", "tool_001");
        tool.put("tool_name", "Test Tool");
        tool.put("tool_desc", "A test tool");
        tool.put("code", "print('hello')");
        tool.put("language", "python");
        tool.put("input_parameters", List.of(Map.of("name", "param1", "desc", "First param", "type", 1)));
        tool.put("output_parameters", List.of(Map.of("name", "result", "desc", "Result", "type", 1)));

        Map<String, Object> plugin = new LinkedHashMap<>();
        plugin.put("plugin_id", "plugin_001");
        plugin.put("plugin_name", "Test Plugin");
        plugin.put("plugin_desc", "A test plugin");
        plugin.put("plugin_version", "1.0.0");
        plugin.put("tools", Map.of("tool_001", tool));

        return Map.of("plugin_001", plugin);
    }

    private Map<String, Map<String, Object>> buildSampleWorkflowDict() {
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("workflow_id", "wf_001");
        workflow.put("workflow_name", "Test Workflow");
        workflow.put("workflow_version", "1.0.0");
        workflow.put("workflow_desc", "A test workflow");
        workflow.put("input_parameters", List.of());
        workflow.put("output_parameters", List.of());

        return Map.of("wf_001", workflow);
    }
}
