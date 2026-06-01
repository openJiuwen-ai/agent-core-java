/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ReActAgent using new Card + Config pattern.
 *
 * <p>Mirrors Python's {@code test_new_react_agent_mock.py} in
 * {@code tests/unit_tests/agent/react_agent/}.</p>
 */
@DisplayName("New ReActAgent Mock")
class NewReActAgentMockTest {

    @Nested
    @DisplayName("ReActAgentConfig tests")
    class ConfigTests {

        @Test
        @DisplayName("config default values")
        void testConfigDefaultValues() {
            ReActAgentConfig config = ReActAgentConfig.builder().build();

            assertThat(config.getMemScopeId()).isEmpty();
            assertThat(config.getModelName()).isEmpty();
            assertThat(config.getModelProvider()).isEqualTo("openai");
            assertThat(config.getApiKey()).isEmpty();
            assertThat(config.getApiBase()).isEmpty();
            assertThat(config.getPromptTemplateName()).isEmpty();
            assertThat(config.getPromptTemplate()).isEmpty();
            assertThat(config.getMaxIterations()).isEqualTo(5);
            assertThat(config.getContextEngineConfig().getMaxContextMessageNum()).isEqualTo(200);
            assertThat(config.getContextEngineConfig().getDefaultWindowRoundNum()).isEqualTo(10);
        }

        @Test
        @DisplayName("chained configuration")
        void testConfigChainedConfiguration() {
            ReActAgentConfig config = ReActAgentConfig.builder().build();

            config.configureModel("gpt-4")
                    .configureModelProvider("openai", "test_key", "https://api.test.com")
                    .configurePromptTemplate(List.of(Map.of("role", "system", "content", "You are helpful.")))
                    .configureContextEngine(128, 6, true)
                    .configureMaxIterations(10);

            assertThat(config.getModelName()).isEqualTo("gpt-4");
            assertThat(config.getApiKey()).isEqualTo("test_key");
            assertThat(config.getApiBase()).isEqualTo("https://api.test.com");
            assertThat(config.getPromptTemplate()).hasSize(1);
            assertThat(config.getContextEngineConfig().getMaxContextMessageNum()).isEqualTo(128);
            assertThat(config.getContextEngineConfig().getDefaultWindowRoundNum()).isEqualTo(6);
            assertThat(config.getContextEngineConfig().isEnableReload()).isTrue();
            assertThat(config.getMaxIterations()).isEqualTo(10);
        }

        @Test
        @DisplayName("configure mem scope")
        void testConfigureMemScope() {
            ReActAgentConfig config = ReActAgentConfig.builder().build().configureMemScope("scope-1");
            assertThat(config.getMemScopeId()).isEqualTo("scope-1");
        }

        @Test
        @DisplayName("configure prompt name")
        void testConfigurePromptName() {
            ReActAgentConfig config = ReActAgentConfig.builder().build().configurePrompt("default_prompt");
            assertThat(config.getPromptTemplateName()).isEqualTo("default_prompt");
        }

        @Test
        @DisplayName("configure model client")
        void testConfigureModelClient() {
            ReActAgentConfig config = ReActAgentConfig.builder().build();
            config.configureModelClient("OpenAI", "sk-test", "https://api.openai.com/v1", "gpt-4", false);

            assertThat(config.getModelClientConfig()).isNotNull();
            assertThat(config.getModelConfigObj()).isNotNull();
            assertThat(config.getModelClientConfig().getApiKey()).isEqualTo("sk-test");
            assertThat(config.getModelConfigObj().getModelName()).isEqualTo("gpt-4");
        }

        @Test
        @DisplayName("configure custom headers")
        void testConfigureCustomHeaders() {
            ReActAgentConfig config = ReActAgentConfig.builder().build()
                    .configureCustomHeaders(Map.of("X-Test", "true"));

            assertThat(config.getCustomHeaders()).containsEntry("X-Test", "true");
        }
    }

    @Nested
    @DisplayName("ReActAgent creation tests")
    class CreationTests {

        @Test
        @DisplayName("create agent with card")
        void testCreateAgentWithCard() {
            AgentCard card = AgentCard.builder()
                    .name("test_agent")
                    .description("Test agent")
                    .build();

            ReActAgent agent = new ReActAgent(card);

            assertThat(agent.getCard().getId()).isNotBlank();
            assertThat(agent.getCard().getName()).isEqualTo("test_agent");
            assertThat(agent.getCard().getDescription()).isEqualTo("Test agent");
        }

        @Test
        @DisplayName("agent configure returns self and stores config")
        void testAgentConfigureMethod() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("test_agent").build());
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .modelName("gpt-4")
                    .maxIterations(10)
                    .build();

            BaseAgent returned = agent.configure(config);

            assertThat(returned).isSameAs(agent);
            assertThat((ReActAgentConfig) agent.getConfig()).isSameAs(config);
            assertThat(((ReActAgentConfig) agent.getConfig()).getModelName()).isEqualTo("gpt-4");
        }
    }

    @Nested
    @DisplayName("Ability manager tests")
    class AbilityTests {

        @Test
        @DisplayName("add ability single")
        void testAddAbilitySingle() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("agent").build());
            ToolCard tool = toolCard("tool_single");

            agent.getAbilityManager().add(tool);

            assertThat(agent.getAbilityManager().get("tool_single")).isSameAs(tool);
        }

        @Test
        @DisplayName("add ability list")
        void testAddAbilityList() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("agent").build());
            ToolCard toolA = toolCard("tool_a");
            ToolCard toolB = toolCard("tool_b");

            agent.getAbilityManager().add(List.of(toolA, toolB));

            assertThat(agent.getAbilityManager().list()).contains(toolA, toolB);
        }

        @Test
        @DisplayName("remove ability")
        void testRemoveAbility() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("agent").build());
            ToolCard tool = toolCard("tool_remove");
            agent.getAbilityManager().add(tool);

            Object removed = agent.getAbilityManager().remove("tool_remove");

            assertThat(removed).isSameAs(tool);
            assertThat(agent.getAbilityManager().get("tool_remove")).isNull();
        }

        @Test
        @DisplayName("get ability")
        void testGetAbility() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("agent").build());
            ToolCard tool = toolCard("tool_get");
            agent.getAbilityManager().add(tool);

            assertThat(agent.getAbilityManager().get("tool_get")).isSameAs(tool);
            assertThat(agent.getAbilityManager().get("missing")).isNull();
        }

        @Test
        @DisplayName("list tool info")
        void testListToolInfo() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("agent").build());
            agent.getAbilityManager().add(toolCard("tool_info"));

            List<ToolInfo> infos = agent.getAbilityManager().listToolInfo();

            assertThat(infos).extracting(ToolInfo::getName).contains("tool_info");
        }

        @Test
        @DisplayName("list tool info prioritizes paid search")
        void testListToolInfoPrioritizesPaidSearch() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("agent").build());
            agent.getAbilityManager().add(ToolCard.builder().id(unique("free")).name("free_search").build());
            agent.getAbilityManager().add(ToolCard.builder().id(unique("paid")).name("paid_search").build());

            List<String> names = agent.getAbilityManager().listToolInfo().stream()
                    .map(ToolInfo::getName)
                    .toList();

            assertThat(names.indexOf("paid_search")).isLessThan(names.indexOf("free_search"));
        }

        @Test
        @DisplayName("remove batch returns complete list")
        void testRemoveBatchReturnsCompleteList() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("agent").build());
            ToolCard tool = toolCard("tool_batch_remove");
            agent.getAbilityManager().add(tool);

            List<Object> removed = agent.getAbilityManager().remove(List.of("tool_batch_remove", "missing"));

            assertThat(removed).hasSize(2);
            assertThat(removed.get(0)).isSameAs(tool);
            assertThat(removed.get(1)).isNull();
        }

        @Test
        @DisplayName("remove MCP server also removes cached MCP tools")
        void testRemoveMcpServerAlsoRemovesMcpTools() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("agent").build());
            String serverId = unique("mcp");
            McpServerConfig server = McpServerConfig.builder()
                    .serverId(serverId)
                    .serverName("mcp_server")
                    .serverPath("stdio://mock")
                    .build();
            ToolCard cachedTool = ToolCard.builder()
                    .id(serverId + ".mcp_server.search")
                    .name("search")
                    .description("cached MCP tool")
                    .build();
            agent.getAbilityManager().add(server);
            agent.getAbilityManager().add(cachedTool);

            Object removed = agent.getAbilityManager().remove("mcp_server");

            assertThat(removed).isSameAs(server);
            assertThat(agent.getAbilityManager().get("search")).isNull();
        }
    }

    @Nested
    @DisplayName("Invoke and stream tests")
    class InvokeTests {

        @Test
        @DisplayName("invoke pure conversation")
        void testInvokePureConversation() {
            FakeModel model = new FakeModel(AssistantMessage.builder().content("Hello!").build());
            ReActAgent agent = new TestReActAgent(AgentCard.builder().name("agent").build(), model);

            Map<?, ?> result = (Map<?, ?>) agent.invoke(Map.of("query", "hi"), null);

            assertThat(result.get("result_type")).isEqualTo("answer");
            assertThat(result.get("output")).isEqualTo("Hello!");
            assertThat(model.invokeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("invoke renders prompt template with query")
        void testInvokeRendersPromptTemplateWithQuery() {
            FakeModel model = new FakeModel(AssistantMessage.builder().content("ok").build());
            ReActAgent agent = new TestReActAgent(AgentCard.builder().name("agent").build(), model);
            agent.configure(ReActAgentConfig.builder()
                    .promptTemplate(List.of(Map.of("role", "system", "content", "Task: {{query}}")))
                    .build());

            agent.invoke(Map.of("query", "render me"), null);

            assertThat(model.lastMessages()).isInstanceOf(List.class);
            assertThat((List<?>) model.lastMessages()).anySatisfy(message -> {
                assertThat(message).isInstanceOf(SystemMessage.class);
                assertThat(((BaseMessage) message).getContent()).isEqualTo("Task: render me");
            });
        }

        @Test
        @DisplayName("invoke with tool call")
        void testInvokeWithToolCall() {
            String toolName = unique("add");
            FakeModel model = new FakeModel(
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(toolCall("call_add", toolName, "{\"a\":1,\"b\":2}")))
                            .build(),
                    AssistantMessage.builder().content("3").build()
            );
            ReActAgent agent = new TestReActAgent(AgentCard.builder().name("agent").build(), model);
            registerTool(agent, toolName, inputs -> number(inputs.get("a")) + number(inputs.get("b")));

            Map<?, ?> result = (Map<?, ?>) agent.invoke(Map.of("query", "1+2?"), null);

            assertThat(result.get("result_type")).isEqualTo("answer");
            assertThat(result.get("output")).isEqualTo("3");
            assertThat(model.invokeCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("invoke multi-turn tool calls")
        void testInvokeMultiTurnToolCalls() {
            String addTool = unique("add");
            String multiplyTool = unique("multiply");
            FakeModel model = new FakeModel(
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(toolCall("call_add", addTool, "{\"a\":1,\"b\":3}")))
                            .build(),
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(toolCall("call_multiply", multiplyTool, "{\"a\":3,\"b\":3}")))
                            .build(),
                    AssistantMessage.builder().content("9").build()
            );
            ReActAgent agent = new TestReActAgent(AgentCard.builder().name("agent").build(), model);
            registerTool(agent, addTool, inputs -> number(inputs.get("a")) + number(inputs.get("b")));
            registerTool(agent, multiplyTool, inputs -> number(inputs.get("a")) * number(inputs.get("b")));

            Map<?, ?> result = (Map<?, ?>) agent.invoke(Map.of("query", "calc"), null);

            assertThat(result.get("result_type")).isEqualTo("answer");
            assertThat(result.get("output")).isEqualTo("9");
            assertThat(model.invokeCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("invoke max iterations reached")
        void testInvokeMaxIterationsReached() {
            String toolName = unique("loop_tool");
            FakeModel model = new FakeModel(
                    AssistantMessage.builder().content("").toolCalls(List.of(toolCall("c1", toolName, "{}"))).build(),
                    AssistantMessage.builder().content("").toolCalls(List.of(toolCall("c2", toolName, "{}"))).build(),
                    AssistantMessage.builder().content("").toolCalls(List.of(toolCall("c3", toolName, "{}"))).build()
            );
            ReActAgent agent = new TestReActAgent(AgentCard.builder().name("agent").build(), model);
            agent.configure(ReActAgentConfig.builder().maxIterations(2).build());
            registerTool(agent, toolName, inputs -> "ok");

            Map<?, ?> result = (Map<?, ?>) agent.invoke(Map.of("query", "loop"), null);

            assertThat(result.get("result_type")).isEqualTo("error");
            assertThat(result.get("output")).asString().contains("Max iterations");
        }

        @Test
        @DisplayName("invoke with string input")
        void testInvokeWithStringInput() {
            FakeModel model = new FakeModel(AssistantMessage.builder().content("String answer").build());
            ReActAgent agent = new TestReActAgent(AgentCard.builder().name("agent").build(), model);

            Map<?, ?> result = (Map<?, ?>) agent.invoke("hello", null);

            assertThat(result.get("result_type")).isEqualTo("answer");
            assertThat(result.get("output")).isEqualTo("String answer");
        }

        @Test
        @DisplayName("missing query raises error")
        void testInvokeMissingQueryRaisesError() {
            ReActAgent agent = new TestReActAgent(
                    AgentCard.builder().name("agent").build(),
                    new FakeModel(AssistantMessage.builder().content("unused").build()));

            assertThatThrownBy(() -> agent.invoke(Map.of("text", "hello"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("query");
        }

        @Test
        @DisplayName("invalid input raises error")
        void testInvokeInvalidInputRaisesError() {
            ReActAgent agent = new TestReActAgent(
                    AgentCard.builder().name("agent").build(),
                    new FakeModel(AssistantMessage.builder().content("unused").build()));

            assertThatThrownBy(() -> agent.invoke(42, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Input must be dict");
        }

        @Test
        @DisplayName("stream yields final result")
        void testStreamYieldsFinalResult() {
            FakeModel model = new FakeModel();
            model.addStreamContent("Stream answer");
            ReActAgent agent = new TestReActAgent(AgentCard.builder().id(unique("agent")).name("agent").build(), model);

            Iterator<Object> stream = agent.stream(Map.of("query", "hi"), null, List.of(StreamMode.OUTPUT));
            List<Object> chunks = new ArrayList<>();
            stream.forEachRemaining(chunks::add);

            assertThat(chunks).isNotEmpty();
            Object last = chunks.get(chunks.size() - 1);
            assertThat(last).isInstanceOf(OutputSchema.class);
            OutputSchema output = (OutputSchema) last;
            assertThat(output.getPayload()).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) output.getPayload()).get("result_type")).isEqualTo("answer");
        }
    }

    @Nested
    @DisplayName("Resource and AgentCard tests")
    class ResourceAndCardTests {

        @Test
        @DisplayName("get tool info returns agent as tool")
        void testGetToolInfoReturnsAgentAsTool() {
            AgentCard card = AgentCard.builder()
                    .name("child_agent")
                    .description("Child agent")
                    .inputParams(Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"))))
                    .build();

            ToolInfo info = (ToolInfo) card.toolInfo();

            assertThat(info.getName()).isEqualTo("child_agent");
            assertThat(info.getDescription()).isEqualTo("Child agent");
            assertThat(info.getParameters()).containsKey("properties");
        }

        @Test
        @DisplayName("configure resets llm on provider change")
        void testConfigureResetsLlmOnProviderChange() throws Exception {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("agent").build());
            writeField(agent, "llm", new FakeModel(AssistantMessage.builder().content("unused").build()));

            agent.configure(ReActAgentConfig.builder()
                    .modelProvider("provider2")
                    .apiKey("new-key")
                    .apiBase("https://new.example")
                    .build());

            assertThat(readField(agent, "llm")).isNull();
        }

        @Test
        @DisplayName("configure updates context engine on limit change")
        void testConfigureUpdatesContextEngineOnLimitChange() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().name("agent").build());
            ContextEngine before = agent.getContextEngine();
            ReActAgentConfig config = ReActAgentConfig.builder()
                    .contextEngineConfig(ContextEngineConfig.builder()
                            .maxContextMessageNum(20)
                            .defaultWindowRoundNum(4)
                            .build())
                    .build();

            agent.configure(config);

            assertThat(agent.getContextEngine()).isNotSameAs(before);
        }

        @Test
        @DisplayName("ReActAgent tool can be registered with agent tag")
        void testReactAgentAddToolWithAgentTag() {
            String agentId = unique("agent");
            String toolName = unique("tagged_tool");
            ToolCard card = toolCard(toolName);
            Runner.resourceMgr().addTool(new LocalFunction(card, inputs -> "ok"), agentId);

            assertThat(Runner.resourceMgr().resourceHasTag(card.getId(), agentId)).isTrue();
        }

        @Test
        @DisplayName("tools are isolated between agent tags")
        void testReactAgentToolsIsolatedBetweenAgents() {
            String agentA = unique("agentA");
            String agentB = unique("agentB");
            ToolCard toolA = toolCard(unique("toolA"));
            ToolCard toolB = toolCard(unique("toolB"));
            Runner.resourceMgr().addTool(new LocalFunction(toolA, inputs -> "A"), agentA);
            Runner.resourceMgr().addTool(new LocalFunction(toolB, inputs -> "B"), agentB);

            List<String> agentATools = Runner.resourceMgr().getToolInfos(null, null, agentA, TagMatchStrategy.ALL)
                    .stream()
                    .map(ToolInfo::getName)
                    .toList();

            assertThat(agentATools).contains(toolA.getName());
            assertThat(agentATools).doesNotContain(toolB.getName());
        }

        @Test
        @DisplayName("agent card with JSON schema dict")
        void testAgentCardWithJsonSchemaDict() {
            Map<String, Object> schema = Map.of(
                    "type", "object",
                    "properties", Map.of("query", Map.of("type", "string"))
            );
            AgentCard card = AgentCard.builder().name("schema_agent").inputParams(schema).build();

            assertThat(((ToolInfo) card.toolInfo()).getParameters()).isEqualTo(schema);
        }

        @Test
        @DisplayName("agent card with class input params converts to object schema")
        void testAgentCardWithBaseModelType() {
            AgentCard card = AgentCard.builder().name("class_agent").inputParams(SampleInput.class).build();

            Map<String, Object> parameters = ((ToolInfo) card.toolInfo()).getParameters();

            assertThat(parameters).containsEntry("type", "object");
            Map<?, ?> properties = (Map<?, ?>) parameters.get("properties");
            assertThat(properties.containsKey("query")).isTrue();
            assertThat(properties.containsKey("count")).isTrue();
        }

        @Test
        @DisplayName("agent card with null input params uses empty object schema")
        void testAgentCardWithNoneInputParams() {
            AgentCard card = AgentCard.builder().name("none_agent").build();

            assertThat(((ToolInfo) card.toolInfo()).getParameters()).isEqualTo(Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "required", List.of()
            ));
        }

        @Test
        @DisplayName("multiple agent cards with different input params")
        void testMultipleAgentCardsWithDifferentInputParams() {
            AgentCard dictCard = AgentCard.builder()
                    .name("dict_agent")
                    .inputParams(Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"))))
                    .build();
            AgentCard classCard = AgentCard.builder()
                    .name("class_agent")
                    .inputParams(SampleInput.class)
                    .build();
            AgentCard noneCard = AgentCard.builder().name("none_agent").build();

            assertThat(((ToolInfo) dictCard.toolInfo()).getParameters()).containsKey("properties");
            assertThat(((ToolInfo) classCard.toolInfo()).getParameters()).containsKey("properties");
            assertThat(((ToolInfo) noneCard.toolInfo()).getParameters()).containsEntry("type", "object");
        }
    }

    private static ToolCard toolCard(String name) {
        return ToolCard.builder()
                .id(unique(name))
                .name(name)
                .description("Tool " + name)
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build();
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments(arguments)
                .build();
    }

    private static void registerTool(ReActAgent agent, String toolName, Function<Map<String, Object>, Object> function) {
        ToolCard card = toolCard(toolName);
        agent.getAbilityManager().add(card);
        Runner.resourceMgr().addTool(new LocalFunction(card, function), null);
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = ReActAgent.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void writeField(Object target, String fieldName, Object value) throws Exception {
        Field field = ReActAgent.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    static final class TestReActAgent extends ReActAgent {
        private final Model model;

        TestReActAgent(AgentCard card, Model model) {
            super(card);
            this.model = model;
        }

        @Override
        protected Model getLlm() {
            return model;
        }
    }

    static class FakeModel extends Model {
        private final Queue<AssistantMessage> responses = new ArrayDeque<>();
        private final Queue<List<AssistantMessageChunk>> streamResponses = new ArrayDeque<>();
        private int invokeCount;
        private Object lastMessages;

        FakeModel(AssistantMessage... responses) {
            super(modelClientConfig(), ModelRequestConfig.builder().modelName("mock-model").build());
            this.responses.addAll(List.of(responses));
        }

        void addStreamContent(String content) {
            streamResponses.add(List.of(AssistantMessageChunk.builder()
                    .content(content)
                    .finishReason("stop")
                    .build()));
        }

        int invokeCount() {
            return invokeCount;
        }

        Object lastMessages() {
            return lastMessages;
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                        Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                        Map<String, Object> kwargs) {
            invokeCount++;
            lastMessages = messages;
            return responses.isEmpty()
                    ? AssistantMessage.builder().content("").build()
                    : responses.remove();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            List<AssistantMessageChunk> chunks = streamResponses.isEmpty()
                    ? List.of(AssistantMessageChunk.builder().content("").finishReason("stop").build())
                    : streamResponses.remove();
            return chunks.iterator();
        }
    }

    static final class SampleInput {
        public String query;
        public int count;
    }

    private static ModelClientConfig modelClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider("OpenAI")
                .apiKey("sk-test")
                .apiBase("https://mock.openai.local/v1")
                .verifySsl(false)
                .build();
    }
}
