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
 * Mirrors Python's unit coverage for
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/transformer.py}.
 */
class TransformerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Transformer transformer;
    private Map<String, Map<String, Object>> samplePluginDict;
    private Map<String, String> sampleToolIdMap;
    private Map<String, Map<String, Object>> sampleWorkflowDict;

    @BeforeEach
    void setUp() {
        transformer = new Transformer();
        samplePluginDict = buildSamplePluginDict();
        sampleToolIdMap = Map.of("tool_001", "plugin_001", "tool_002", "plugin_001");
        sampleWorkflowDict = buildSampleWorkflowDict();
    }

    @Test
    void collectPluginAndWorkflowMirrorPythonSelections() {
        List<Map<String, Object>> plugins = Transformer.collectPlugin(
                List.of("tool_001", "missing"),
                samplePluginDict,
                sampleToolIdMap
        );
        List<Map<String, Object>> workflows = Transformer.collectWorkflow(
                List.of("wf_001", "wf_missing"),
                sampleWorkflowDict
        );

        assertThat(plugins).hasSize(1);
        assertThat(plugins.getFirst())
                .containsEntry("plugin_id", "plugin_001")
                .containsEntry("plugin_name", "Test Plugin")
                .containsEntry("tool_id", "tool_001")
                .containsEntry("tool_name", "Test Tool");

        assertThat(workflows).hasSize(2);
        assertThat(workflows.getFirst())
                .containsEntry("workflow_id", "wf_001")
                .containsEntry("workflow_name", "Test Workflow")
                .containsEntry("description", "A test workflow");
        assertThat(workflows.get(1)).containsEntry("workflow_id", "wf_missing");
    }

    @Test
    void convertParametersPreserveDefaultsAndDescriptionFallbacks() {
        List<Map<String, Object>> input = Transformer.convertInputParameters(List.of(
                Map.of("name", "city", "description", "City name"),
                Map.of("name", "count", "desc", "Count", "is_required", true, "priority", 2)
        ));
        List<Map<String, Object>> output = Transformer.convertOutputParameters(List.of(
                Map.of("name", "result")
        ));

        assertThat(input).containsExactly(
                Map.of(
                        "name", "city",
                        "desc", "City name",
                        "type", 1,
                        "value", "",
                        "method", 0,
                        "priority", 0,
                        "is_runtime", true,
                        "is_required", false
                ),
                Map.of(
                        "name", "count",
                        "desc", "Count",
                        "type", 1,
                        "value", "",
                        "method", 0,
                        "priority", 2,
                        "is_runtime", true,
                        "is_required", true
                )
        );
        assertThat(output.getFirst()).containsEntry("is_runtime", false).containsEntry("name", "result");
    }

    @Test
    void buildDependenciesPreserveSinglePluginPerPluginIdAndWorkflowMetadata() {
        long now = 1700000000000L;

        List<Map<String, Object>> pluginDependencies = Transformer.buildPluginDependencies(
                List.of("tool_001", "tool_002"),
                samplePluginDict,
                sampleToolIdMap,
                now
        );
        List<Map<String, Object>> workflowDependencies = Transformer.buildWorkflowDependencies(
                List.of("wf_001"),
                sampleWorkflowDict,
                now
        );

        assertThat(pluginDependencies).hasSize(1);
        assertThat(pluginDependencies.getFirst())
                .containsEntry("plugin_id", "plugin_001")
                .containsEntry("plugin_version", "1.0.0")
                .containsEntry("name", "Test Plugin")
                .containsEntry("create_time", now)
                .containsEntry("update_time", now);
        assertThat((List<?>) pluginDependencies.getFirst().get("tool_list")).hasSize(1);

        assertThat(workflowDependencies).hasSize(1);
        assertThat(workflowDependencies.getFirst())
                .containsEntry("workflow_id", "wf_001")
                .containsEntry("workflow_version", "1.0.0")
                .containsEntry("name", "Test Workflow")
                .containsEntry("create_time", now);
    }

    @Test
    void transformToDslBuildsTemplateBackedJsonWithDependencies() throws Exception {
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

        Map<String, Object> dsl = MAPPER.readValue(transformer.transformToDsl(agentInfo, resource), Map.class);

        assertThat(dsl).containsEntry("name", "Test Agent");
        assertThat(dsl).containsEntry("description", "A test agent");
        assertThat(dsl).containsEntry("opening_remarks", "Hello!");
        assertThat(dsl.get("agent_id")).isNotEqualTo("");
        assertThat(dsl.get("create_time")).isNotNull();
        assertThat(dsl.get("update_time")).isNotNull();
        assertThat((Map<String, Object>) dsl.get("configs")).containsEntry("system_prompt", "You are a test agent.");
        assertThat((List<?>) dsl.get("plugins")).hasSize(1);
        assertThat((List<?>) dsl.get("workflows")).hasSize(1);
        assertThat((Map<String, Object>) dsl.get("dependencies")).containsKeys("plugins", "workflows", "knowledge_bases", "prompt_templates");
    }

    @Test
    void transformToDslKeepsEmptyPluginAndWorkflowCollectionsWhenNoSelectionsExist() throws Exception {
        Map<String, Object> agentInfo = new LinkedHashMap<>();
        agentInfo.put("name", "Simple Agent");
        agentInfo.put("description", "Simple");
        agentInfo.put("prompt", "Prompt");
        agentInfo.put("opening_remarks", "Hi");
        agentInfo.put("plugin", List.of());
        agentInfo.put("workflow", List.of());

        Map<String, Object> dsl = MAPPER.readValue(
                transformer.transformToDsl(
                        agentInfo,
                        Map.of("plugin_dict", Map.of(), "tool_id_map", Map.of(), "workflow_dict", Map.of())
                ),
                Map.class
        );

        assertThat((List<?>) dsl.get("plugins")).isEmpty();
        assertThat((List<?>) dsl.get("workflows")).isEmpty();
        assertThat((List<?>) ((Map<String, Object>) dsl.get("dependencies")).get("plugins")).isEmpty();
        assertThat((List<?>) ((Map<String, Object>) dsl.get("dependencies")).get("workflows")).isEmpty();
    }

    private Map<String, Map<String, Object>> buildSamplePluginDict() {
        Map<String, Object> tool001 = new LinkedHashMap<>();
        tool001.put("tool_id", "tool_001");
        tool001.put("tool_name", "Test Tool");
        tool001.put("tool_desc", "A test tool");
        tool001.put("code", "print('hello')");
        tool001.put("language", "python");
        tool001.put("input_parameters", List.of(Map.of("name", "param1", "desc", "First param", "type", 1)));
        tool001.put("output_parameters", List.of(Map.of("name", "result", "desc", "Result", "type", 1)));

        Map<String, Object> tool002 = new LinkedHashMap<>();
        tool002.put("tool_id", "tool_002");
        tool002.put("tool_name", "Another Tool");
        tool002.put("tool_desc", "Another test tool");
        tool002.put("code", "print('world')");
        tool002.put("language", "python");
        tool002.put("input_parameters", List.of());
        tool002.put("output_parameters", List.of());

        Map<String, Object> plugin = new LinkedHashMap<>();
        plugin.put("plugin_id", "plugin_001");
        plugin.put("plugin_name", "Test Plugin");
        plugin.put("plugin_desc", "A test plugin");
        plugin.put("plugin_version", "1.0.0");
        plugin.put("tools", Map.of("tool_001", tool001, "tool_002", tool002));

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
