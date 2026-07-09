// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
        WorkflowCard wc = WorkflowCard.builder().name("wf-1").description("test workflow")
                .inputParams(Map.of("type", "object")).build();

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
        WorkflowCard wc = WorkflowCard.builder().name("wf-info").description("workflow desc")
                .inputParams(Map.of("type", "object")).build();

        manager.add(wc);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).getName()).isEqualTo("wf-info");
        assertThat(infos.get(0).getDescription()).isEqualTo("workflow desc");
    }

    // ========== McpServerConfig ==========

    @Test
    void testAddAndGetMcpServerConfig() {
        McpServerConfig mcp = McpServerConfig.builder().serverName("mcp-server-1").serverId("mcp-id-1").build();

        manager.add(mcp);

        Object result = manager.get("mcp-server-1");
        assertThat(result).isInstanceOf(McpServerConfig.class);
    }

    @Test
    void testRemoveMcpServerAlsoRemovesAssociatedTools() {
        McpServerConfig mcp = McpServerConfig.builder().serverName("mcp-svr").serverId("mcp-prefix").build();
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
        AgentCard ac = AgentCard.builder().name("agent-params").description("desc")
                .inputParams(Map.of("query", Map.of("type", "string"))).build();
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
    void testExecutePreservesSkipToolMarkerThroughAfterToolCallThenClearsIt() {
        class SkippingAgent extends BaseAgent {
            SkippingAgent() {
                super(AgentCard.builder().id("skip-agent").name("skip-agent").build());
            }

            @Override
            public BaseAgent configure(Object config) {
                return this;
            }

            @Override
            public Object getConfig() {
                return null;
            }

            @Override
            public Object invoke(Object inputs, com.openjiuwen.core.session.Session session) {
                return null;
            }

            @Override
            public java.util.Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.Session session,
                    List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
                return List.of().iterator();
            }
        }

        class SkipRail extends AgentRail {
            private boolean skipVisibleInAfter;

            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {
                ctx.getExtra().put("_skip_tool", Boolean.TRUE);
            }

            @Override
            public void afterToolCall(AgentCallbackContext ctx) {
                skipVisibleInAfter = Boolean.TRUE.equals(ctx.getExtra().get("_skip_tool"));
            }
        }

        String toolId = "skip-tool-" + UUID.randomUUID();
        LocalFunction tool =
            new LocalFunction(ToolCard.builder().id(toolId).name(toolId).description("skip test").build(),
                    inputs -> "should-not-run");
        Runner.resourceMgr().addTool(tool, null);
        try {
            SkippingAgent agent = new SkippingAgent();
            SkipRail rail = new SkipRail();
            agent.registerRail(rail);
            manager.add(tool.getCard());
            Map<String, Object> extra = new java.util.LinkedHashMap<>();
            AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).extra(extra).build();

            List<AbilityManager.ToolExecutionEntry> results =
                manager.execute(ctx, ToolCall.builder().id("tc-skip").name(toolId).arguments("{}").build(), null, null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).result()).isNull();
            assertThat(rail.skipVisibleInAfter).isTrue();
            assertThat(extra).doesNotContainKey("_skip_tool");
        } finally {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void testExecuteSingleToolCallNotFound() {
        ToolCall tc = ToolCall.builder().id("tc-1").name("nonexistent-tool").arguments("{}").build();

        // Tool not registered and not in ResourceMgr — should throw
        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteSingleToolCallNullArguments() {
        ToolCall tc = ToolCall.builder().id("tc-2").name("nonexistent-tool").arguments(null).build();

        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteSingleToolCallBlankArguments() {
        ToolCall tc = ToolCall.builder().id("tc-3").name("nonexistent-tool").arguments("   ").build();

        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteSingleToolCallInvalidJson() {
        ToolCall tc = ToolCall.builder().id("tc-4").name("nonexistent-tool").arguments("not json").build();

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
    void testExecuteSingleToolCallResolvesMcpToolByNameWithoutPreListing() throws Exception {
        String serverId = "mcp-server-id-" + UUID.randomUUID();
        String toolId = serverId + ".demo-server.browser_navigate";

        McpServerConfig server = McpServerConfig.builder().serverName("demo-server").serverId(serverId).build();
        manager.add(server);

        McpClient client = new McpClient() {
            @Override
            public boolean connect(int retryTimes, float timeout) {
                return true;
            }

            @Override
            public boolean disconnect(float timeout) {
                return true;
            }

            @Override
            public List<Object> listTools(float timeout) {
                return List.of();
            }

            @Override
            public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
                return Map.of("tool", toolName, "arguments", arguments);
            }

            @Override
            public Optional<Object> getToolInfo(String toolName, float timeout) {
                return Optional.empty();
            }

            @Override
            public String getServerPath() {
                return "mock://demo-server";
            }
        };

        McpToolCard card = McpToolCard.builder().id(toolId).name("browser_navigate").description("Navigate browser")
                .serverId(serverId).serverName("demo-server").build();
        Tool tool = new McpTool(client, card);
        Runner.resourceMgr().addTool(tool, "ut-mcp");

        try {
            ToolCall tc = ToolCall.builder().id("tc-mcp").name("browser_navigate")
                    .arguments("{\"url\":\"https://example.com\"}").build();

            AbilityManager.ToolExecutionEntry entry = manager.executeSingleToolCall(tc, null, null);

            assertThat(entry.result()).isEqualTo(Map.of("result",
                    Map.of("tool", "browser_navigate", "arguments", Map.of("url", "https://example.com"))));
            assertThat(manager.get("browser_navigate")).isInstanceOf(ToolCard.class);
        } finally {
            Runner.resourceMgr().removeTool(toolId, "ut-mcp", TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void testExecuteSingleToolCallMcpServerNameRaisesExplicitError() {
        manager.add(McpServerConfig.builder().serverName("mcp-server").serverId("mcp-server-id").build());

        ToolCall tc = ToolCall.builder().id("tc-mcp").name("mcp-server").arguments("{}").build();

        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class).hasMessageContaining("not directly executable");
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
