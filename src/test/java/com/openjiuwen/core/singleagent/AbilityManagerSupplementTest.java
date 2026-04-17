// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Supplementary tests for {@link AbilityManager} — execute, WorkflowCard, McpServerConfig.
 */
class AbilityManagerSupplementTest {

    private AbilityManager manager;

    @BeforeEach
    void setUp() {
        manager = new AbilityManager();
    }

    // ========== WorkflowCard ==========

    @Test
    void testAddAndGetWorkflowCard() {
        WorkflowCard wc = WorkflowCard.builder()
                .name("wf-1")
                .description("test workflow")
                .inputParams(Map.of("type", "object"))
                .build();

        manager.add(wc);

        Object result = manager.get("wf-1");
        assertThat(result).isInstanceOf(WorkflowCard.class);
        assertThat(((WorkflowCard) result).getName()).isEqualTo("wf-1");
    }

    @Test
    void testRemoveWorkflowCard() {
        WorkflowCard wc = WorkflowCard.builder().name("wf-rem").build();
        manager.add(wc);

        Object removed = manager.remove("wf-rem");
        assertThat(removed).isNotNull();
        assertThat(manager.get("wf-rem")).isNull();
    }

    @Test
    void testListToolInfoWorkflow() {
        WorkflowCard wc = WorkflowCard.builder()
                .name("wf-info")
                .description("workflow desc")
                .inputParams(Map.of("type", "object"))
                .build();

        manager.add(wc);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).getName()).isEqualTo("wf-info");
        assertThat(infos.get(0).getDescription()).isEqualTo("workflow desc");
    }

    // ========== McpServerConfig ==========

    @Test
    void testAddAndGetMcpServerConfig() {
        McpServerConfig mcp = McpServerConfig.builder()
                .serverName("mcp-server-1")
                .serverId("mcp-id-1")
                .build();

        manager.add(mcp);

        Object result = manager.get("mcp-server-1");
        assertThat(result).isInstanceOf(McpServerConfig.class);
    }

    @Test
    void testRemoveMcpServerAlsoRemovesAssociatedTools() {
        McpServerConfig mcp = McpServerConfig.builder()
                .serverName("mcp-svr")
                .serverId("mcp-prefix")
                .build();
        manager.add(mcp);

        // Add tool cards that belong to this MCP server (id prefixed with serverId)
        ToolCard tc1 = ToolCard.builder().name("tool1").id("mcp-prefix.tool1").build();
        ToolCard tc2 = ToolCard.builder().name("tool2").id("mcp-prefix.tool2").build();
        ToolCard tc3 = ToolCard.builder().name("tool3").id("other.tool3").build();
        manager.add(tc1);
        manager.add(tc2);
        manager.add(tc3);

        assertThat(manager.list()).hasSize(4);

        // Remove MCP server — should also remove tool1 and tool2
        Object removed = manager.remove("mcp-svr");
        assertThat(removed).isNotNull();
        assertThat(manager.get("mcp-svr")).isNull();
        assertThat(manager.get("tool1")).isNull();
        assertThat(manager.get("tool2")).isNull();
        assertThat(manager.get("tool3")).isNotNull(); // Not removed - different prefix
    }

    // ========== Mixed abilities ==========

    @Test
    void testListAllMixed() {
        ToolCard tc = ToolCard.builder().name("t1").build();
        WorkflowCard wc = WorkflowCard.builder().name("w1").build();
        AgentCard ac = AgentCard.builder().name("a1").build();
        McpServerConfig mcp = McpServerConfig.builder().serverName("m1").build();

        manager.add(tc);
        manager.add(wc);
        manager.add(ac);
        manager.add(mcp);

        assertThat(manager.list()).hasSize(4);
    }

    @Test
    void testGetSearchesAllMaps() {
        // Verify get() searches tools -> workflows -> agents -> mcpServers
        WorkflowCard wc = WorkflowCard.builder().name("unique-wf").build();
        manager.add(wc);
        assertThat(manager.get("unique-wf")).isNotNull();

        AgentCard ac = AgentCard.builder().name("unique-agent").build();
        manager.add(ac);
        assertThat(manager.get("unique-agent")).isNotNull();

        McpServerConfig mcp = McpServerConfig.builder().serverName("unique-mcp").build();
        manager.add(mcp);
        assertThat(manager.get("unique-mcp")).isNotNull();
    }

    @Test
    void testRemoveSearchesAllMaps() {
        WorkflowCard wc = WorkflowCard.builder().name("wf-to-remove").build();
        AgentCard ac = AgentCard.builder().name("ag-to-remove").build();
        manager.add(wc);
        manager.add(ac);

        Object removedWf = manager.remove("wf-to-remove");
        assertThat(removedWf).isNotNull();

        Object removedAg = manager.remove("ag-to-remove");
        assertThat(removedAg).isNotNull();
    }

    // ========== ToolInfo with nulls ==========

    @Test
    void testListToolInfoNullDescription() {
        ToolCard tc = ToolCard.builder().name("no-desc").build();
        manager.add(tc);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).getDescription()).isEqualTo("");
    }

    @Test
    void testListToolInfoNullInputParams() {
        ToolCard tc = ToolCard.builder().name("no-params").description("desc").build();
        manager.add(tc);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos.get(0).getParameters()).isNotNull();
    }

    @Test
    void testListToolInfoWorkflowNullDescription() {
        WorkflowCard wc = WorkflowCard.builder().name("wf-null-desc").build();
        manager.add(wc);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos.get(0).getDescription()).isEqualTo("");
    }

    @Test
    void testListToolInfoAgentWithInputParams() {
        AgentCard ac = AgentCard.builder()
                .name("agent-params")
                .description("desc")
                .inputParams(Map.of("query", Map.of("type", "string")))
                .build();
        manager.add(ac);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) infos.get(0).getParameters();
        assertThat(params).containsEntry("query", Map.of("type", "string"));
    }

    // ========== normalizeToolCalls ==========

    @Test
    void testExecuteWithEmptyList() {
        com.openjiuwen.core.singleagent.rail.AgentCallbackContext ctx =
                com.openjiuwen.core.singleagent.rail.AgentCallbackContext.builder().build();

        List<AbilityManager.ToolExecutionEntry> results = manager.execute(ctx, List.of(), null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void testExecuteWithInvalidToolCallType() {
        com.openjiuwen.core.singleagent.rail.AgentCallbackContext ctx =
                com.openjiuwen.core.singleagent.rail.AgentCallbackContext.builder().build();

        // Passing a string instead of ToolCall — normalizeToolCalls should log warning
        List<AbilityManager.ToolExecutionEntry> results = manager.execute(ctx, "not a tool call", null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void testExecuteWithNullToolCall() {
        com.openjiuwen.core.singleagent.rail.AgentCallbackContext ctx =
                com.openjiuwen.core.singleagent.rail.AgentCallbackContext.builder().build();

        List<AbilityManager.ToolExecutionEntry> results = manager.execute(ctx, null, null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void testExecuteSingleToolCallNotFound() {
        ToolCall tc = ToolCall.builder()
                .id("tc-1")
                .name("nonexistent-tool")
                .arguments("{}")
                .build();

        // Tool not registered and not in ResourceMgr — should throw
        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteSingleToolCallNullArguments() {
        ToolCall tc = ToolCall.builder()
                .id("tc-2")
                .name("nonexistent-tool")
                .arguments(null)
                .build();

        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteSingleToolCallBlankArguments() {
        ToolCall tc = ToolCall.builder()
                .id("tc-3")
                .name("nonexistent-tool")
                .arguments("   ")
                .build();

        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteSingleToolCallInvalidJson() {
        ToolCall tc = ToolCall.builder()
                .id("tc-4")
                .name("nonexistent-tool")
                .arguments("not json")
                .build();

        // Invalid JSON args should be handled gracefully, then fail on tool lookup
        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteAsToolExecutorWithNonToolCall() {
        var result = manager.executeAsToolExecutor("not a ToolCall", null);
        assertThat(result).isNotNull();
        assertThat(result.result()).isNull();
    }

    @Test
    void testExecuteSingleToolCallMcpServerNameRaisesExplicitError() {
        manager.add(McpServerConfig.builder()
                .serverName("mcp-server")
                .serverId("mcp-server-id")
                .build());

        ToolCall tc = ToolCall.builder()
                .id("tc-mcp")
                .name("mcp-server")
                .arguments("{}")
                .build();

        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class)
                .hasMessageContaining("MCP tool execution not yet implemented");
    }

    // ========== ToolExecutionEntry record ==========

    @Test
    void testToolExecutionEntryCreation() {
        ToolMessage msg = ToolMessage.builder().content("result").toolCallId("tc-1").build();
        AbilityManager.ToolExecutionEntry entry = new AbilityManager.ToolExecutionEntry("data", msg);

        assertThat(entry.result()).isEqualTo("data");
        assertThat(entry.toolMessage()).isSameAs(msg);
    }

    @Test
    void testToolExecutionEntryNulls() {
        AbilityManager.ToolExecutionEntry entry = new AbilityManager.ToolExecutionEntry(null, null);
        assertThat(entry.result()).isNull();
        assertThat(entry.toolMessage()).isNull();
    }
}
