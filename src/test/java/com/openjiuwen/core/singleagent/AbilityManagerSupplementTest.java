// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.singleagent;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Supplementary tests for {@link AbilityManager} — execute, WorkflowCard, McpServerConfig.
 */
class AbilityManagerSupplementTest {

    private static final AgentCallbackContext EMPTY_CALLBACK_CONTEXT = AgentCallbackContext.builder().build();

    private AbilityManager manager;

    @BeforeEach
    void setUp() {
        manager = new AbilityManager();
    }

    @Test
    void executeSingleToolCallShouldReturnStructuredSuccessFact() {
        String toolName = "structured-success-tool";
        ToolCard toolCard = ToolCard.builder()
                .id(toolName)
                .name(toolName)
                .description("returns structured success")
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build();
        manager.add(toolCard);
        Runner.resourceMgr().addTool(new LocalFunction(toolCard, inputs -> Map.of("status", "ok")), null);

        try {
            ToolCall toolCall = ToolCall.builder()
                    .id("tc-success")
                    .name(toolName)
                    .arguments("{}")
                    .build();

            AbilityManager.ToolExecutionEntry entry = manager.executeSingleToolCall(toolCall, null, null);

            assertThat(entry.toolCall()).isSameAs(toolCall);
            assertThat(entry.result()).isEqualTo(Map.of("status", "ok"));
            assertThat(entry.toolMessage()).isNotNull();
            assertThat(entry.toolMessage().getToolCallId()).isEqualTo("tc-success");
            assertThat(entry.classification()).isEqualTo(AbilityManager.ToolExecutionClassification.SUCCESS);
            assertThat(entry.errorMessage()).isNull();
        } finally {
            Runner.resourceMgr().removeTool(toolName, null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void executeSingleToolCallShouldLogOnlySanitizedResultSummary() {
        String toolName = "sanitized-log-tool";
        ToolCard toolCard = ToolCard.builder()
                .id(toolName)
                .name(toolName)
                .description("returns secret result")
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build();
        manager.add(toolCard);
        Runner.resourceMgr().addTool(new LocalFunction(toolCard, inputs -> Map.of(
                "token", "secret-value",
                "nested", Map.of("password", "hidden")
        )), null);

        ListAppender<ILoggingEvent> appender = attachToolAppender();
        try {
            ToolCall toolCall = ToolCall.builder()
                    .id("tc-sanitized-log")
                    .name(toolName)
                    .arguments("{}")
                    .build();

            manager.executeSingleToolCall(toolCall, null, null);

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("Tool result summary: Map(keys=[")
                            .contains("token")
                            .contains("nested")
                            .doesNotContain("secret-value")
                            .doesNotContain("hidden"));
        } finally {
            Runner.resourceMgr().removeTool(toolName, null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void executeShouldReturnStructuredErrorFactInsteadOfBareTuple() {
        ToolCall toolCall = ToolCall.builder()
                .id("tc-error")
                .name("missing-structured-tool")
                .arguments("{}")
                .build();

        List<AbilityManager.ToolExecutionEntry> results = manager.execute(EMPTY_CALLBACK_CONTEXT, toolCall, null, null);

        assertThat(results).singleElement().satisfies(entry -> {
            assertThat(entry.toolCall()).isSameAs(toolCall);
            assertThat(entry.result()).isNull();
            assertThat(entry.toolMessage()).isNotNull();
            assertThat(entry.toolMessage().getToolCallId()).isEqualTo("tc-error");
            assertThat(entry.classification()).isEqualTo(AbilityManager.ToolExecutionClassification.ERROR);
            assertThat(entry.errorMessage()).contains("Ability execution error");
        });
    }

    @Test
    void toolExecutionEntryShouldAllowInterruptPendingCandidateFacts() {
        ToolCall toolCall = ToolCall.builder()
                .id("tc-interrupt")
                .name("interrupt-tool")
                .arguments("{}")
                .build();
        ToolMessage toolMessage = ToolMessage.builder().content("waiting for resume").toolCallId("tc-interrupt").build();

        AbilityManager.ToolExecutionEntry entry = new AbilityManager.ToolExecutionEntry(
                toolCall,
                null,
                toolMessage,
                AbilityManager.ToolExecutionClassification.INTERRUPT_PENDING_CANDIDATE,
                "waiting for resume"
        );

        assertThat(entry.toolCall()).isSameAs(toolCall);
        assertThat(entry.toolMessage()).isSameAs(toolMessage);
        assertThat(entry.classification()).isEqualTo(AbilityManager.ToolExecutionClassification.INTERRUPT_PENDING_CANDIDATE);
        assertThat(entry.errorMessage()).isEqualTo("waiting for resume");
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
        List<AbilityManager.ToolExecutionEntry> results = manager.execute(EMPTY_CALLBACK_CONTEXT, List.of(), null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void testExecuteWithInvalidToolCallType() {
        // Passing a string instead of ToolCall — normalizeToolCalls should log warning
        List<AbilityManager.ToolExecutionEntry> results = manager.execute(EMPTY_CALLBACK_CONTEXT, "not a tool call", null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void testExecuteWithNullToolCall() {
        List<AbilityManager.ToolExecutionEntry> results = manager.execute(EMPTY_CALLBACK_CONTEXT, null, null, null);
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
        String toolName = "invalid-json-tool";
        ToolCard toolCard = ToolCard.builder()
                .id(toolName)
                .name(toolName)
                .description("used to verify malformed args fail fast")
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build();
        manager.add(toolCard);
        Runner.resourceMgr().addTool(new LocalFunction(toolCard, inputs -> Map.of("received", inputs)), null);

        ToolCall tc = ToolCall.builder()
                .id("tc-4")
                .name(toolName)
                .arguments("not json")
                .build();

        try {
            assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                    .isInstanceOf(AbilityExecutionError.class)
                    .hasMessageContaining("Malformed tool arguments JSON");
        } finally {
            Runner.resourceMgr().removeTool(toolName, null, TagMatchStrategy.ALL, true);
        }
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

    @Test
    void executeSingleToolCallCreatesChildSessionForAgentAbilities() {
        AgentCard childAgentCard = AgentCard.builder()
                .id("child-agent-id")
                .name("child-agent")
                .description("child agent")
                .build();
        RecordingChildAgent childAgent = new RecordingChildAgent();
        manager.add(childAgentCard);
        Runner.resourceMgr().addAgent(childAgentCard, () -> childAgent, null);

        AgentSessionApi parentSession = AgentSessionApi.create("parent-session", Map.of("ENV", "value"), null);
        parentSession.updateState(Map.of("interrupt_auto_confirm", Map.of("read_file", true)));

        try {
            ToolCall tc = ToolCall.builder()
                    .id("tc-child")
                    .name("child-agent")
                    .arguments("{\"query\":\"hello\"}")
                    .build();

            AbilityManager.ToolExecutionEntry entry = manager.executeSingleToolCall(tc, parentSession, null);

            assertThat(entry.result()).isEqualTo(Map.of(
                    "session_id", "parent-session:tc-child",
                    "conversation_id", "parent-session:tc-child",
                    "state", Map.of("read_file", true)
            ));
            assertThat(childAgent.seenSession).isNotSameAs(parentSession);
            assertThat(childAgent.seenSession.getSessionId()).isEqualTo("parent-session:tc-child");
            assertThat(childAgent.seenInputs).containsEntry("conversation_id", "parent-session:tc-child");
            assertThat(childAgent.seenSession.getState("interrupt_auto_confirm"))
                    .isEqualTo(Map.of("read_file", true));
        } finally {
            Runner.resourceMgr().removeAgent(childAgentCard.getId(), null, TagMatchStrategy.ALL, true);
        }
    }

    // ========== ToolExecutionEntry record ==========

    @Test
    void testToolExecutionEntryCreation() {
        ToolMessage msg = ToolMessage.builder().content("result").toolCallId("tc-1").build();
        ToolCall toolCall = ToolCall.builder().id("tc-1").name("tool").arguments("{}").build();
        AbilityManager.ToolExecutionEntry entry = new AbilityManager.ToolExecutionEntry(
                toolCall,
                "data",
                msg,
                AbilityManager.ToolExecutionClassification.SUCCESS,
                null
        );

        assertThat(entry.toolCall()).isSameAs(toolCall);
        assertThat(entry.result()).isEqualTo("data");
        assertThat(entry.toolMessage()).isSameAs(msg);
        assertThat(entry.classification()).isEqualTo(AbilityManager.ToolExecutionClassification.SUCCESS);
        assertThat(entry.errorMessage()).isNull();
    }

    @Test
    void testToolExecutionEntryNulls() {
        AbilityManager.ToolExecutionEntry entry = new AbilityManager.ToolExecutionEntry(
                null,
                null,
                null,
                AbilityManager.ToolExecutionClassification.ERROR,
                "boom"
        );
        assertThat(entry.toolCall()).isNull();
        assertThat(entry.result()).isNull();
        assertThat(entry.toolMessage()).isNull();
        assertThat(entry.classification()).isEqualTo(AbilityManager.ToolExecutionClassification.ERROR);
        assertThat(entry.errorMessage()).isEqualTo("boom");
    }

    private static final class RecordingChildAgent {
        private AgentSessionApi seenSession;
        private Map<String, Object> seenInputs;

        private Map<String, Object> invoke(Object inputs, AgentSessionApi session) {
            seenSession = session;
            @SuppressWarnings("unchecked")
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            seenInputs = inputMap;
            return Map.of(
                    "session_id", session.getSessionId(),
                    "conversation_id", inputMap.get("conversation_id"),
                    "state", session.getState("interrupt_auto_confirm")
            );
        }
    }

    private ListAppender<ILoggingEvent> attachToolAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger("tool");
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
