/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ExternalTool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AbilityManagerExternalToolTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void addsListsFindsAndRemovesExternalTool() {
        AbilityManager manager = new AbilityManager();
        ExternalTool external = externalTool("frontend_read_text_input");

        AddAbilityResult addResult = manager.add(external);

        assertThat(addResult.isAdded()).isTrue();
        assertThat(addResult.getReason()).isEqualTo("added_external_tool");
        assertThat(manager.getExternalTool("frontend_read_text_input")).contains(external);
        assertThat(manager.isExternalTool("frontend_read_text_input")).isTrue();
        assertThat(manager.list()).contains(external);
        assertThat(manager.getAbilities()).containsEntry("frontend_read_text_input", external);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos).extracting(ToolInfo::getName).contains("frontend_read_text_input");

        Object removed = manager.remove("frontend_read_text_input");

        assertThat(removed).isSameAs(external);
        assertThat(manager.getExternalTool("frontend_read_text_input")).isEmpty();
        assertThat(manager.isExternalTool("frontend_read_text_input")).isFalse();
    }

    @Test
    void rejectsExternalToolNameCollisionsAcrossAllAbilityTypes() {
        AbilityManager manager = new AbilityManager();
        manager.add(new ToolCard("tool-1", "same", "tool", Map.of("type", "object")));
        assertThat(manager.add(externalTool("same")).getReason()).isEqualTo("duplicate_tool");

        AbilityManager workflowManager = new AbilityManager();
        workflowManager.add(new WorkflowCard("workflow-1", "same", "workflow", "1", Map.of()));
        assertThat(workflowManager.add(externalTool("same")).getReason()).isEqualTo("duplicate_workflow");

        AbilityManager agentManager = new AbilityManager();
        agentManager.add(new AgentCard("agent-1", "same", "agent"));
        assertThat(agentManager.add(externalTool("same")).getReason()).isEqualTo("duplicate_agent");

        AbilityManager mcpServerManager = new AbilityManager();
        mcpServerManager.add(new McpServerConfig("mcp-1", "same", "/mcp", "sse", Map.of(), Map.of(), Map.of()));
        assertThat(mcpServerManager.add(externalTool("same")).getReason()).isEqualTo("duplicate_mcp_server");

        AbilityManager externalManager = new AbilityManager();
        externalManager.add(externalTool("same"));
        assertThat(externalManager.add(externalTool("same")).getReason()).isEqualTo("duplicate_external_tool");
        assertThat(externalManager.add(new McpServerConfig("mcp-1", "same", "/mcp", "sse", Map.of(), Map.of(),
                Map.of())).getReason()).isEqualTo("duplicate_external_tool");
    }

    @Test
    void rejectsNormalToolRegistrationWhenExternalToolAlreadyUsesName() {
        AbilityManager manager = new AbilityManager();
        manager.add(externalTool("same"));

        AddAbilityResult result = manager.add(new ToolCard("tool-1", "same", "tool", Map.of("type", "object")));

        assertThat(result.isAdded()).isFalse();
        assertThat(result.getReason()).isEqualTo("duplicate_external_tool");
    }

    @Test
    void mcpGeneratedToolDoesNotOverrideExternalToolWithSameGeneratedName() {
        TestableAbilityManager manager = new TestableAbilityManager(List.of(
                ToolInfo.builder().name("forecast").description("Forecast weather").parameters(Map.of()).build()
        ));
        ExternalTool external = externalTool("mcp_weather_forecast");
        manager.add(external);
        manager.add(new McpServerConfig("mcp-1", "weather", "/mcp", "sse", Map.of(), Map.of(), Map.of()));

        List<ToolInfo> infos = manager.listToolInfo();

        List<String> names = infos.stream().map(ToolInfo::getName).toList();
        assertThat(Collections.frequency(names, "mcp_weather_forecast")).isEqualTo(1);
        assertThat(manager.get("mcp_weather_forecast"))
                .hasValueSatisfying(value -> assertThat(value).isSameAs(external));
        assertThat(manager.getTools()).doesNotContainKey("mcp_weather_forecast");
    }

    @Test
    void listToolInfoKeepsMcpGeneratedToolsAcrossRepeatedCalls() {
        TestableAbilityManager manager = new TestableAbilityManager(List.of(
                ToolInfo.builder().name("forecast").description("Forecast weather").parameters(Map.of()).build()
        ));
        manager.add(new McpServerConfig("mcp-1", "weather", "/mcp", "sse", Map.of(), Map.of(), Map.of()));

        List<ToolInfo> firstInfos = manager.listToolInfo();
        List<ToolInfo> secondInfos = manager.listToolInfo();

        assertThat(firstInfos).extracting(ToolInfo::getName).contains("mcp_weather_forecast");
        assertThat(secondInfos).extracting(ToolInfo::getName).contains("mcp_weather_forecast");
        assertThat(manager.getTools().get("mcp_weather_forecast").getId()).isEqualTo("mcp-1.weather.forecast");
    }

    @Test
    void addAbilityResultJsonDoesNotExposeSuccessAlias() throws JsonProcessingException {
        String json = JSON.writeValueAsString(new AddAbilityResult("external", true, "added_external_tool"));

        assertThat(json).contains("\"added\":true");
        assertThat(json).doesNotContain("success");
    }

    @Test
    void executeDoesNotReturnExternalToolAsExecutedAbility() {
        AbilityManager manager = new AbilityManager();
        ExternalTool external = externalTool("frontend_read_text_input");
        manager.add(external);
        ToolCall call = ToolCall.builder()
                .id("call-1")
                .name("frontend_read_text_input")
                .arguments("{\"field_id\":\"name\"}")
                .build();

        List<AbilityManager.ExecutionResult> results = manager.execute(call);

        assertThat(results).isEmpty();
    }

    @Test
    void executeDoesNotParseInvalidJsonArgumentsForExternalTool() {
        AbilityManager manager = new AbilityManager();
        manager.add(externalTool("frontend_read_text_input"));
        ToolCall call = ToolCall.builder()
                .id("call-1")
                .name("frontend_read_text_input")
                .arguments("{bad-json")
                .build();

        List<AbilityManager.ExecutionResult> results = manager.execute(call);

        assertThat(results).isEmpty();
    }

    private static ExternalTool externalTool(String name) {
        return new ExternalTool(ToolCard.builder()
                .id("external." + name)
                .name(name)
                .description("External " + name)
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build());
    }

    private static final class TestableAbilityManager extends AbilityManager {
        private final List<ToolInfo> mcpToolInfos;

        private TestableAbilityManager(List<ToolInfo> mcpToolInfos) {
            this.mcpToolInfos = mcpToolInfos;
        }

        @Override
        protected List<ToolInfo> loadMcpToolInfos(McpServerConfig mcpServer) {
            return mcpToolInfos;
        }
    }
}
