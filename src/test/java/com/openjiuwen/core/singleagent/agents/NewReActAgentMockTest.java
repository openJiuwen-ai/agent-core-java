/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.context_engine.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code tests.unit_tests.agent.react_agent.test_new_react_agent_mock} in
 * {@code tests/unit_tests/agent/react_agent/test_new_react_agent_mock.py}.
 */
class NewReActAgentMockTest {

    @Test
    void configDefaultValues() {
        ReActAgentConfig config = new ReActAgentConfig();

        assertThat(config.getMemScopeId()).isEmpty();
        assertThat(config.getModelName()).isEmpty();
        assertThat(config.getModelProvider()).isEqualTo("openai");
        assertThat(config.getApiKey()).isEmpty();
        assertThat(config.getApiBase()).isEmpty();
        assertThat(config.getPromptTemplateName()).isEmpty();
        assertThat(config.getPromptTemplate()).isEmpty();
        assertThat(config.getContextEngineConfig()).isEqualTo(defaultContextConfig());
        assertThat(config.getMaxIterations()).isEqualTo(5);
    }

    @Test
    void configChainedConfiguration() {
        ReActAgentConfig config = new ReActAgentConfig()
                .configureModel("gpt-4")
                .configureModelProvider("openai", "test_key", "https://api.test.com")
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", "You are an assistant.")))
                .configureContextEngine(100, 20, true, false)
                .configureMaxIterations(10);

        assertThat(config.getModelName()).isEqualTo("gpt-4");
        assertThat(config.getModelProvider()).isEqualTo("openai");
        assertThat(config.getApiKey()).isEqualTo("test_key");
        assertThat(config.getApiBase()).isEqualTo("https://api.test.com");
        assertThat(config.getPromptTemplate()).hasSize(1);
        assertThat(config.getContextEngineConfig().getMaxContextMessageNum()).isEqualTo(100);
        assertThat(config.getContextEngineConfig().getDefaultWindowRoundNum()).isEqualTo(20);
        assertThat(config.getContextEngineConfig().isEnableReload()).isTrue();
        assertThat(config.getMaxIterations()).isEqualTo(10);
    }

    @Test
    void configureMemScope() {
        ReActAgentConfig config = new ReActAgentConfig().configureMemScope("test_scope");

        assertThat(config.getMemScopeId()).isEqualTo("test_scope");
    }

    @Test
    void configurePromptName() {
        ReActAgentConfig config = new ReActAgentConfig().configurePrompt("test_prompt");

        assertThat(config.getPromptTemplateName()).isEqualTo("test_prompt");
    }

    @Test
    void agentCreationWithCard() {
        AgentCard card = agentCard("test_agent", "test_agent", "Test agent");

        ReActAgent agent = new ReActAgent(card);

        assertThat(agent.getCard().getName()).isEqualTo("test_agent");
        assertThat(agent.getCard().getDescription()).isEqualTo("Test agent");
        assertThat(agent.getCard().getId()).isNotBlank();
    }

    @Test
    void agentConfigureMethod() {
        ReActAgent agent = new ReActAgent(agentCard("test_agent", "test_agent", "Test agent"));
        ReActAgentConfig config = new ReActAgentConfig()
                .configureModel("gpt-4")
                .configureMaxIterations(10);

        ReActAgent result = agent.configure(config);

        assertThat(result).isSameAs(agent);
        assertThat(agent.getConfig().getModelName()).isEqualTo("gpt-4");
        assertThat(agent.getConfig().getMaxIterations()).isEqualTo(10);
    }

    @Test
    void addAbilitySingle() {
        ReActAgent agent = new ReActAgent(agentCard("test_agent", "test_agent", "Test agent"));

        agent.getAbilityManager().add(addToolCard());

        assertThat(agent.getAbilityManager().list())
                .extracting(item -> ((ToolCard) item).getName())
                .containsExactly("add");
    }

    @Test
    void addAbilityList() {
        ReActAgent agent = new ReActAgent(agentCard("test_agent", "test_agent", "Test agent"));

        agent.getAbilityManager().add(List.of(addToolCard(), multiplyToolCard()));

        assertThat(agent.getAbilityManager().list())
                .extracting(item -> ((ToolCard) item).getName())
                .containsExactly("add", "multiply");
    }

    @Test
    void removeAbility() {
        ReActAgent agent = new ReActAgent(agentCard("test_agent", "test_agent", "Test agent"));
        agent.getAbilityManager().add(List.of(addToolCard(), multiplyToolCard()));

        Object removed = agent.getAbilityManager().remove("add");

        assertThat(removed).isInstanceOf(ToolCard.class);
        assertThat(agent.getAbilityManager().list())
                .extracting(item -> ((ToolCard) item).getName())
                .containsExactly("multiply");
    }

    @Test
    void getAbility() {
        ReActAgent agent = new ReActAgent(agentCard("test_agent", "test_agent", "Test agent"));
        agent.getAbilityManager().add(addToolCard());

        assertThat(agent.getAbilityManager().get("add")).isPresent();
        assertThat(agent.getAbilityManager().get("not_exist")).isEmpty();
    }

    @Test
    void listToolInfo() {
        ReActAgent agent = new ReActAgent(agentCard("test_agent", "test_agent", "Test agent"));
        agent.getAbilityManager().add(List.of(addToolCard(), multiplyToolCard()));

        List<ToolInfo> toolInfos = agent.getAbilityManager().listToolInfo();

        assertThat(toolInfos).extracting(ToolInfo::getName).containsExactly("add", "multiply");
    }

    @Test
    void listToolInfoPrioritizesPaidSearch() {
        ReActAgent agent = new ReActAgent(agentCard("test_agent", "test_agent", "Test agent"));
        agent.getAbilityManager().add(new ToolCard("free_search_id", "free_search", "free"));
        agent.getAbilityManager().add(new ToolCard("paid_search_id", "paid_search", "paid"));

        List<String> names = agent.getAbilityManager().listToolInfo().stream()
                .map(ToolInfo::getName)
                .toList();

        assertThat(names.indexOf("paid_search")).isLessThan(names.indexOf("free_search"));
    }

    @Test
    void invokePureConversation() {
        ScriptedReActAgent agent = scriptedAgent(new AssistantMessage("hello from assistant"));

        Map<String, Object> result = invokeMap(agent, Map.of("conversation_id", "test_session", "query", "hello"));

        assertThat(result).containsEntry("result_type", "answer")
                .containsEntry("output", "hello from assistant");
        assertThat(agent.getCallCount()).isEqualTo(1);
    }

    @Test
    void invokeSyncsRenderedIdentityAndPromptBuilder() {
        ScriptedReActAgent agent = scriptedAgent(new AssistantMessage("done"));
        ReActAgentConfig config = new ReActAgentConfig()
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", "Task: {query}")))
                .configureMaxIterations(1);
        agent.configure(config);

        Map<String, Object> result = invokeMap(agent, Map.of("conversation_id", "test_session", "query", "compute"));

        assertThat(result).containsEntry("result_type", "answer");
        assertThat(agent.getSystemPromptBuilder()).isSameAs(agent.getPromptBuilder());
        assertThat(agent.getPromptBuilder().getSection(ReActAgent.IDENTITY_SECTION))
                .hasValueSatisfying(section -> assertThat(section.render("cn")).isEqualTo("Task: compute"));
    }

    @Test
    void invokeBuildsContextWindowOnlyOncePerIteration() {
        ScriptedReActAgent agent = scriptedAgent(new AssistantMessage("done"));

        Map<String, Object> result = invokeMap(agent, Map.of("conversation_id", "test_session", "query", "compute"));

        assertThat(result).containsEntry("output", "done");
        assertThat(agent.getCallCount()).isEqualTo(1);
    }

    @Test
    void invokeWithToolCall() {
        ScriptedReActAgent agent = scriptedAgent(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(toolCall("call-1", "add", "{\"a\":1,\"b\":2}")))
                        .build(),
                new AssistantMessage("1 + 2 = 3")
        );
        agent.getAbilityManager().add(addToolCard());

        Map<String, Object> result = invokeMap(agent, Map.of("conversation_id", "test_session", "query", "1+2"));

        assertThat(result).containsEntry("result_type", "answer")
                .containsEntry("output", "1 + 2 = 3");
        assertThat(agent.getCallCount()).isEqualTo(2);
    }

    @Test
    void invokeMultiTurnToolCalls() {
        ScriptedReActAgent agent = scriptedAgent(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(toolCall("call-1", "add", "{\"a\":1,\"b\":2}")))
                        .build(),
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(toolCall("call-2", "multiply", "{\"a\":3,\"b\":4}")))
                        .build(),
                new AssistantMessage("final answer")
        );
        agent.getAbilityManager().add(List.of(addToolCard(), multiplyToolCard()));

        Map<String, Object> result = invokeMap(agent, Map.of("conversation_id", "test_session", "query", "math"));

        assertThat(result).containsEntry("output", "final answer");
        assertThat(agent.getCallCount()).isEqualTo(3);
    }

    @Test
    void invokeMaxIterationsReached() {
        ScriptedReActAgent agent = scriptedAgent(AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall("call-1", "add", "{\"a\":1,\"b\":2}")))
                .build());
        agent.configure(new ReActAgentConfig().configureMaxIterations(1));
        agent.getAbilityManager().add(addToolCard());

        Map<String, Object> result = invokeMap(agent, Map.of("conversation_id", "test_session", "query", "math"));

        assertThat(result).containsEntry("result_type", "error")
                .containsEntry("output", "Max iterations reached without completion");
    }

    @Test
    void invokeWithStringInput() {
        ScriptedReActAgent agent = scriptedAgent(new AssistantMessage("string answer"));

        Map<String, Object> result = invokeMap(agent, "hello");

        assertThat(result).containsEntry("result_type", "answer")
                .containsEntry("output", "string answer");
    }

    @Test
    void invokeMissingQueryRaisesError() {
        ScriptedReActAgent agent = scriptedAgent(new AssistantMessage("unused"));

        assertThatThrownBy(() -> agent.invoke(Map.of("conversation_id", "test_session"), new MemorySession())
                .toCompletableFuture()
                .join())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Input must contain 'query'");
    }

    @Test
    void invokeInvalidInputRaisesError() {
        ScriptedReActAgent agent = scriptedAgent(new AssistantMessage("unused"));

        assertThatThrownBy(() -> agent.invoke(42, new MemorySession()).toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Input must be dict with 'query' or str");
    }

    @Test
    void streamYieldsFinalResult() {
        ScriptedReActAgent agent = scriptedAgent(new AssistantMessage("streamed"));
        MemorySession session = new MemorySession();

        Iterator<Object> iterator = agent.stream(Map.of("conversation_id", "test_session", "query", "hello"),
                session, List.of());

        assertThat(iterator).toIterable().hasSize(1);
        OutputSchema output = (OutputSchema) session.stream.getFirst();
        assertThat(output.getType()).isEqualTo("answer");
        assertThat(stringObjectMap((Map<?, ?>) output.getPayload())).containsEntry("output", "streamed");
    }

    @Test
    void getToolInfoReturnsAgentAsTool() {
        AgentCard card = agentCard("agent_tool", "agent_tool", "Agent as a tool");

        ToolInfo toolInfo = card.toolInfo();

        assertThat(toolInfo.getName()).isEqualTo("agent_tool");
        assertThat(toolInfo.getDescription()).isEqualTo("Agent as a tool");
    }

    @Test
    void configureResetsLlmOnProviderChange() {
        ReActAgent agent = new ReActAgent(agentCard("test_agent", "test_agent", "Test agent"));
        agent.setLlm(new com.openjiuwen.core.foundation.llm.Model((messages, options) ->
                java.util.concurrent.CompletableFuture.completedFuture(new AssistantMessage("ready"))));

        agent.configure(new ReActAgentConfig().configureModelProvider("azure", "key2", "base2"));

        assertThatThrownBy(agent::getLlm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model_client_config is required");
        assertThat(agent.getConfig().getModelProvider()).isEqualTo("azure");
        assertThat(agent.getConfig().getApiKey()).isEqualTo("key2");
        assertThat(agent.getConfig().getApiBase()).isEqualTo("base2");
    }

    @Test
    void configureUpdatesContextEngineOnLimitChange() {
        ReActAgent agent = new ReActAgent(agentCard("test_agent", "test_agent", "Test agent"));
        Object oldContextEngine = agent.getContextEngine();

        agent.configure(new ReActAgentConfig().configureContextEngine(null, 20, false, false));

        assertThat(agent.getContextEngine()).isNotSameAs(oldContextEngine);
        assertThat(agent.getConfig().getContextEngineConfig().getDefaultWindowRoundNum()).isEqualTo(20);
    }

    @Test
    void reactAgentAddToolWithAgentTag() {
        String tag = unique("agenttag");
        FakeTool tool = new FakeTool(unique("tool"), "add");
        try {
            Runner.resourceMgr.addTool(tool, List.of(tag), false);

            assertThat(Runner.resourceMgr.resourceHasTag(tool.getCard().getId(), tag)).isTrue();
        } finally {
            Runner.resourceMgr.removeTool(tool.getCard().getId());
        }
    }

    @Test
    void reactAgentToolsIsolatedBetweenAgents() {
        String tagA = unique("agenta");
        String tagB = unique("agentb");
        FakeTool toolA = new FakeTool(unique("toola"), "tool_a");
        FakeTool toolB = new FakeTool(unique("toolb"), "tool_b");
        try {
            Runner.resourceMgr.addTool(toolA, List.of(tagA), false);
            Runner.resourceMgr.addTool(toolB, List.of(tagB), false);

            List<String> namesA = Runner.resourceMgr.getToolInfos(null, null, List.of(tagA), TagMatchStrategy.ALL)
                    .stream()
                    .map(ToolInfo::getName)
                    .toList();
            List<String> namesB = Runner.resourceMgr.getToolInfos(null, null, List.of(tagB), TagMatchStrategy.ALL)
                    .stream()
                    .map(ToolInfo::getName)
                    .toList();

            assertThat(namesA).contains("tool_a").doesNotContain("tool_b");
            assertThat(namesB).contains("tool_b").doesNotContain("tool_a");
        } finally {
            Runner.resourceMgr.removeTool(toolA.getCard().getId());
            Runner.resourceMgr.removeTool(toolB.getCard().getId());
        }
    }

    @Test
    void removeBatchReturnsCompleteList() {
        AbilityManager abilityManager = new AbilityManager();
        abilityManager.add(List.of(
                new ToolCard("tool1_id", "tool1", "Tool 1"),
                new ToolCard("tool2_id", "tool2", "Tool 2"),
                new ToolCard("tool3_id", "tool3", "Tool 3")
        ));

        List<Object> removed = abilityManager.remove(List.of("tool1", "tool2"));

        assertThat(removed).hasSize(2);
        assertThat(abilityManager.list())
                .extracting(item -> ((ToolCard) item).getName())
                .containsExactly("tool3");
    }

    @Test
    void listToolInfoAddsMcpToolsToToolsDict() {
        TestAbilityManager abilityManager = new TestAbilityManager(List.of(
                ToolInfo.builder().name("mcp_tool").description("MCP tool").parameters(querySchema()).build()
        ));
        abilityManager.add(mcpServer());

        List<ToolInfo> toolInfos = abilityManager.listToolInfo();

        assertThat(toolInfos).extracting(ToolInfo::getName).containsExactly("mcp_test_mcp_mcp_tool");
        assertThat(abilityManager.getTools()).containsKey("mcp_test_mcp_mcp_tool");
        assertThat(abilityManager.getTools().get("mcp_test_mcp_mcp_tool").getId())
                .isEqualTo("mcp_001.test_mcp.mcp_tool");
    }

    @Test
    void removeMcpServerAlsoRemovesMcpTools() {
        TestAbilityManager abilityManager = new TestAbilityManager(List.of(
                ToolInfo.builder().name("tool1").description("Tool 1").parameters(Map.of()).build(),
                ToolInfo.builder().name("tool2").description("Tool 2").parameters(Map.of()).build()
        ));
        abilityManager.add(mcpServer());
        abilityManager.listToolInfo();

        Object removed = abilityManager.remove("test_mcp");

        assertThat(removed).isInstanceOf(McpServerConfig.class);
        assertThat(abilityManager.getTools()).isEmpty();
    }

    @Test
    void agentCardWithJsonSchemaDict() {
        AbilityManager abilityManager = new AbilityManager();
        AgentCard card = agentCard("sub_agent", "sub_agent", "Sub agent");
        card.setInputParams(querySchema());
        abilityManager.add(card);

        ToolInfo toolInfo = abilityManager.listToolInfo().getFirst();

        assertThat(toolInfo.getName()).isEqualTo("sub_agent");
        assertThat(toolInfo.getParameters()).containsEntry("type", "object");
        assertThat(stringObjectMap((Map<?, ?>) toolInfo.getParameters().get("properties"))).containsKey("query");
    }

    @Test
    void agentCardWithClassInputParamsUsesDefaultSchema() {
        AbilityManager abilityManager = new AbilityManager();
        AgentCard card = agentCard("sub_agent", "sub_agent", "Sub agent");
        card.setInputParams(AgentInputParams.class);
        abilityManager.add(card);

        ToolInfo toolInfo = abilityManager.listToolInfo().getFirst();

        assertThat(toolInfo.getParameters()).isEqualTo(defaultObjectSchema());
    }

    @Test
    void agentCardWithNoneInputParams() {
        AbilityManager abilityManager = new AbilityManager();
        AgentCard card = agentCard("sub_agent", "sub_agent", "Sub agent");
        card.setInputParams(null);
        abilityManager.add(card);

        ToolInfo toolInfo = abilityManager.listToolInfo().getFirst();

        assertThat(toolInfo.getParameters()).isEqualTo(defaultObjectSchema());
    }

    @Test
    void multipleAgentCardsWithDifferentInputParams() {
        AbilityManager abilityManager = new AbilityManager();
        AgentCard jsonSchema = agentCard("agent1", "agent1", "Agent with JSON Schema");
        jsonSchema.setInputParams(querySchema());
        AgentCard classSchema = agentCard("agent2", "agent2", "Agent with class input");
        classSchema.setInputParams(AgentInputParams.class);
        AgentCard noneSchema = agentCard("agent3", "agent3", "Agent with none");
        abilityManager.add(List.of(jsonSchema, classSchema, noneSchema));

        List<ToolInfo> toolInfos = abilityManager.listToolInfo();

        assertThat(toolInfos).extracting(ToolInfo::getName).containsExactly("agent1", "agent2", "agent3");
        assertThat(toolInfos.get(0).getParameters()).containsEntry("type", "object");
        assertThat(toolInfos.get(1).getParameters()).isEqualTo(defaultObjectSchema());
        assertThat(toolInfos.get(2).getParameters()).isEqualTo(defaultObjectSchema());
    }

    @Test
    void executeToolCallAddsToolMessageToContext() {
        ScriptedReActAgent agent = scriptedAgent(new AssistantMessage("unused"));
        agent.getAbilityManager().add(addToolCard());
        MemorySession session = new MemorySession();
        ModelContext context = agent.initContext(session);

        List<AbilityManager.ExecutionResult> results = agent.executeToolCall(
                new AgentCallbackContext(agent),
                List.of(toolCall("call-1", "add", "{\"a\":1,\"b\":2}")),
                session,
                context
        );

        assertThat(results).hasSize(1);
        assertThat(String.valueOf(results.getFirst().toolMessage().getContent())).contains("add");
        assertThat(context.getMessages(null, true)).hasSize(1);
    }

    @Test
    void agentCardAbilityExecutionReturnsToolMessage() {
        AbilityManager abilityManager = new AbilityManager();
        abilityManager.add(agentCard("child_agent", "child_agent", "Child agent"));

        List<AbilityManager.ExecutionResult> results = abilityManager.execute(
                toolCall("call-1", "child_agent", "{\"query\":\"hello\"}")
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().result()).isInstanceOf(AgentCard.class);
        assertThat(results.getFirst().toolMessage().getToolCallId()).isEqualTo("call-1");
    }

    private static ContextEngineConfig defaultContextConfig() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setMaxContextMessageNum(200);
        config.setDefaultWindowRoundNum(10);
        return config;
    }

    private static ScriptedReActAgent scriptedAgent(Object... responses) {
        ScriptedReActAgent agent = new ScriptedReActAgent(agentCard("test_agent", "test_agent", "Test agent"),
                List.of(responses));
        agent.configure(new ReActAgentConfig().configureMaxIterations(5));
        return agent;
    }

    private static AgentCard agentCard(String id, String name, String description) {
        return new AgentCard(id, name, description);
    }

    private static ToolCard addToolCard() {
        return new ToolCard("add_id", "add", "Addition", binaryNumberSchema());
    }

    private static ToolCard multiplyToolCard() {
        return new ToolCard("multiply_id", "multiply", "Multiplication", binaryNumberSchema());
    }

    private static Map<String, Object> binaryNumberSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "a", Map.of("type", "number", "description", "First number"),
                        "b", Map.of("type", "number", "description", "Second number")
                ),
                "required", List.of("a", "b")
        );
    }

    private static Map<String, Object> querySchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string", "description", "User input")),
                "required", List.of("query")
        );
    }

    private static Map<String, Object> defaultObjectSchema() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments(arguments)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeMap(ReActAgent agent, Object inputs) {
        return (Map<String, Object>) agent.invoke(inputs, new MemorySession())
                .toCompletableFuture()
                .join();
    }

    private static McpServerConfig mcpServer() {
        return new McpServerConfig("mcp_001", "test_mcp", "/test/path", "stdio", Map.of(), Map.of(), Map.of());
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static final class ScriptedReActAgent extends ReActAgent {
        private final List<Object> responses;
        private int callCount;

        private ScriptedReActAgent(AgentCard card, List<Object> responses) {
            super(card);
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
            int index = Math.min(callCount, responses.size() - 1);
            callCount++;
            return responses.get(index);
        }

        private int getCallCount() {
            return callCount;
        }
    }

    private static final class MemorySession implements AgentSessionApi, com.openjiuwen.core.context_engine.ContextEngine.SessionPort {
        private final String sessionId = unique("session");
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }

    private static final class FakeTool extends Tool {
        private FakeTool(String id, String name) {
            super(new ToolCard(id, name, name + " tool", Map.of()));
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return inputs;
        }
    }

    private static final class TestAbilityManager extends AbilityManager {
        private final List<ToolInfo> mcpToolInfos;

        private TestAbilityManager(List<ToolInfo> mcpToolInfos) {
            this.mcpToolInfos = mcpToolInfos;
        }

        @Override
        protected List<ToolInfo> loadMcpToolInfos(McpServerConfig mcpServer) {
            List<ToolInfo> copies = new ArrayList<>();
            for (ToolInfo info : mcpToolInfos) {
                copies.add(ToolInfo.builder()
                            .name(info.getName())
                            .description(info.getDescription())
                            .parameters(info.getParameters())
                            .build());
            }
            return copies;
        }
    }

    private static final class AgentInputParams {
        private String query;

        private String getQuery() {
            return query;
        }
    }
}
