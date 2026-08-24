// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.singleagent;

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
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

        Object result = manager.get("wf-1").orElse(null);
        assertThat(result).isInstanceOf(WorkflowCard.class);
        assertThat(((WorkflowCard) result).getName()).isEqualTo("wf-1");
    }

    @Test
    void testRemoveWorkflowCard() {
        WorkflowCard wc = WorkflowCard.builder().name("wf-rem").build();
        manager.add(wc);

        Object removed = manager.remove("wf-rem");
        assertThat(removed).isNotNull();
        assertThat(manager.get("wf-rem")).isEmpty();
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

        Object result = manager.get("mcp-server-1").orElse(null);
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
        assertThat(manager.get("mcp-svr")).isEmpty();
        assertThat(manager.get("tool1")).isEmpty();
        assertThat(manager.get("tool2")).isEmpty();
        assertThat(manager.get("tool3")).isPresent(); // Not removed - different prefix
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
        assertThat(manager.get("unique-wf")).isPresent();

        AgentCard ac = AgentCard.builder().name("unique-agent").build();
        manager.add(ac);
        assertThat(manager.get("unique-agent")).isPresent();

        McpServerConfig mcp = McpServerConfig.builder().serverName("unique-mcp").build();
        manager.add(mcp);
        assertThat(manager.get("unique-mcp")).isPresent();
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
        List<AbilityManager.ExecutionResult> results = manager.execute((ToolCall) null);
        assertThat(results).isEmpty();
    }

    @Test
    void testExecuteWithInvalidToolCallType() {
        // normalizeToolCalls returns empty list for non-ToolCall objects
        List<AbilityManager.ExecutionResult> results = manager.normalizeToolCalls("not a tool call")
                .stream().flatMap(tc -> manager.execute(tc).stream()).toList();
        assertThat(results).isEmpty();
    }

    @Test
    void testExecuteWithNullToolCall() {
        List<AbilityManager.ExecutionResult> results = manager.execute((ToolCall) null);
        assertThat(results).isEmpty();
    }

    @Test
    void executeWithCallbackContextFiresBeforeToolCallAndHonorsSkipTool() {
        class ProbeAgent extends BaseAgent {
            ProbeAgent() {
                super(AgentCard.builder().id("probe-agent").name("probe-agent").build());
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
            public Object invoke(Object inputs, com.openjiuwen.core.session.AgentSession session) {
                return null;
            }

            @Override
            public java.util.Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.AgentSession session,
                    List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
                return List.of().iterator();
            }
        }

        class SkipRail extends AgentRail {
            private Object capturedInputs;

            @Override
            public java.util.concurrent.CompletionStage<Void> beforeToolCall(AgentCallbackContext ctx) {
                capturedInputs = ctx.getInputs();
                ctx.getExtra().put("_skip_tool", Boolean.TRUE);
                if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                    inputs.setToolResult("skipped");
                    inputs.setToolMsg(new ToolMessage("skipped", "tc-skip", "skip-tool"));
                }
                return completed();
            }
        }

        String toolId = "skip-tool-" + UUID.randomUUID();
        LocalFunction tool = new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("skip test").build(),
                inputs -> {
                    throw new IllegalStateException("should-not-run");
                }
        );
        Runner.resourceMgr().addTool(tool, null);
        try {
            ProbeAgent agent = new ProbeAgent();
            SkipRail rail = new SkipRail();
            agent.registerRail(rail).toCompletableFuture().join();
            manager.add(tool.getCard());
            AgentCallbackContext ctx = new AgentCallbackContext(agent);

            List<AbilityManager.ExecutionResult> results = manager.execute(
                    ctx,
                    ToolCall.builder().id("tc-skip").name(toolId).arguments("{}").build(),
                    false,
                    null
            );

            assertThat(rail.capturedInputs).isInstanceOf(ToolCallInputs.class);
            assertThat(((ToolCallInputs) rail.capturedInputs).getToolName())
                    .isEqualTo(toolId);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).result()).isEqualTo("skipped");
            assertThat(results.get(0).toolMessage().getContent()).isEqualTo("skipped");
        } finally {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void testExecuteSingleToolCallNotFound() {
        ToolCall tc = ToolCall.builder()
                .id("tc-1")
                .name("nonexistent-tool")
                .arguments("{}")
                .build();

        List<AbilityManager.ExecutionResult> results = manager.execute(tc);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).result()).isNull();
        assertThat(String.valueOf(results.get(0).toolMessage().getContent()))
                .contains("Ability not found in resource_mgr: nonexistent-tool");
    }

    @Test
    void testExecuteSingleToolCallNullArguments() {
        ToolCall tc = ToolCall.builder()
                .id("tc-2")
                .name("nonexistent-tool")
                .arguments(null)
                .build();

        List<AbilityManager.ExecutionResult> results = manager.execute(tc);
        assertThat(results).hasSize(1);
        assertThat(String.valueOf(results.get(0).toolMessage().getContent()))
                .contains("Ability not found in resource_mgr: nonexistent-tool");
    }

    @Test
    void testExecuteSingleToolCallBlankArguments() {
        ToolCall tc = ToolCall.builder()
                .id("tc-3")
                .name("nonexistent-tool")
                .arguments("   ")
                .build();

        List<AbilityManager.ExecutionResult> results = manager.execute(tc);
        assertThat(results).isNotEmpty();
    }

    @Test
    void testExecuteSingleToolCallInvalidJson() {
        ToolCall tc = ToolCall.builder()
                .id("tc-4")
                .name("nonexistent-tool")
                .arguments("not json")
                .build();

        List<AbilityManager.ExecutionResult> results = manager.execute(tc);
        assertThat(results).hasSize(1);
        assertThat(String.valueOf(results.get(0).toolMessage().getContent()))
                .contains("Invalid tool arguments JSON:");
    }

    @Test
    void testExecuteAsToolExecutorWithNonToolCall() {
        // normalizeToolCalls returns empty for non-ToolCall objects
        List<ToolCall> calls = manager.normalizeToolCalls("not a ToolCall");
        assertThat(calls).isEmpty();
    }

    @Test
    void executeUsesGeneratedMcpToolCardIdToResolveResourceManagerInstance() {
        String serverId = "mcp-server-id-" + UUID.randomUUID();
        String generatedName = "mcp_demo-server_browser_navigate";
        String toolId = serverId + ".demo-server.browser_navigate";
        McpServerConfig server = McpServerConfig.builder()
                .serverName("demo-server")
                .serverId(serverId)
                .build();
        manager.add(server);
        manager.add(ToolCard.builder()
                .id(toolId)
                .name(generatedName)
                .description("Navigate browser")
                .inputParams(Map.of("type", "object"))
                .build());

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
        Tool tool = new McpTool(client, McpToolCard.builder()
                .id(toolId)
                .name("browser_navigate")
                .description("Navigate browser")
                .serverId(serverId)
                .serverName("demo-server")
                .build());
        Runner.resourceMgr().addTool(tool, "ut-mcp");
        try {
            List<AbilityManager.ExecutionResult> results = manager.execute(ToolCall.builder()
                    .id("tc-mcp")
                    .name(generatedName)
                    .arguments("{\"url\":\"https://example.com\"}")
                    .build());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).result()).isNotNull();
            assertThat(String.valueOf(results.get(0).result())).contains("browser_navigate");
        } finally {
            Runner.resourceMgr().removeTool(toolId, "ut-mcp", TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void mcpAllowlistFiltersListedToolsAndRejectsDisallowedExecution() {
        McpServerConfig server = McpServerConfig.builder()
                .serverName("weather")
                .serverId("mcp-weather")
                .build();
        TestableAbilityManager allowlistManager = new TestableAbilityManager(List.of(
                ToolInfo.builder().name("forecast").description("forecast").parameters(Map.of()).build(),
                ToolInfo.builder().name("alerts").description("alerts").parameters(Map.of()).build()
        ));
        allowlistManager.add(server);
        allowlistManager.setMcpToolAllowlist(server, List.of("forecast"));

        List<String> names = allowlistManager.listToolInfo().stream().map(ToolInfo::getName).toList();
        assertThat(names).contains("mcp_weather_forecast");
        assertThat(names).doesNotContain("mcp_weather_alerts");

        List<AbilityManager.ExecutionResult> results = allowlistManager.execute(ToolCall.builder()
                .id("tc-alert")
                .name("mcp_weather_alerts")
                .arguments("{}")
                .build());
        assertThat(String.valueOf(results.get(0).toolMessage().getContent()))
                .contains("MCP tool 'alerts' is not allowed for server 'mcp-weather'");
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

        List<AbilityManager.ExecutionResult> results = manager.execute(tc);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).result()).isNull();
        assertThat(String.valueOf(results.get(0).toolMessage().getContent()))
                .contains("MCP tool execution not yet implemented: mcp-server");
    }

    // ========== ToolExecutionEntry record ==========

    @Test
    void testToolExecutionEntryCreation() {
        ToolMessage msg = ToolMessage.builder().content("result").toolCallId("tc-1").build();
        AbilityManager.ExecutionResult entry = new AbilityManager.ExecutionResult("data", msg);

        assertThat(entry.result()).isEqualTo("data");
        assertThat(entry.toolMessage()).isSameAs(msg);
    }

    @Test
    void testToolExecutionEntryNulls() {
        AbilityManager.ExecutionResult entry = new AbilityManager.ExecutionResult(null, null);
        assertThat(entry.result()).isNull();
        assertThat(entry.toolMessage()).isNull();
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
