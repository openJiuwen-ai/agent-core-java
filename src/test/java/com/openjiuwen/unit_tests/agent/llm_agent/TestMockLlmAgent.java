/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.agent.llm_agent;

import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowUtils;
import com.openjiuwen.core.workflow.components.flow.EndComponent;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.llm.FieldInfo;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the LLM-agent mock/refactor behavior.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent.llm_agent.test_mock_llm_agent}.
 */
@DisplayName("TestMockLlmAgent")
class TestMockLlmAgent {

    private static final String MOCK_PROVIDER = "MockUnitTestsLlmAgentOpenAI";
    private static final Deque<AssistantMessage> MOCK_RESPONSES = new ArrayDeque<>();
    private static final List<List<BaseMessage>> MODEL_CALLS = new ArrayList<>();

    private static final ModelConfig MODEL_CONFIG = new ModelConfig(
        MOCK_PROVIDER,
        BaseModelInfo.builder()
            .modelName("gpt-3.5-turbo")
            .apiBase("https://mock.api")
            .apiKey("mock-key")
            .build()
    );

    private static final ModelClientConfig MODEL_CLIENT_CONFIG = ModelClientConfig.builder()
        .clientProvider(MOCK_PROVIDER)
        .apiKey("sk-fake")
        .apiBase("https://mock.api/v1")
        .verifySsl(false)
        .build();

    private static final ModelRequestConfig MODEL_REQUEST_CONFIG = ModelRequestConfig.builder()
        .modelName("gpt-3.5-turbo")
        .build();

    private String previousLlmSslVerify;
    private String previousIsSensitive;
    private LongTermMemory previousMemoryInstance;

    @BeforeEach
    void setUp() throws Exception {
        previousLlmSslVerify = System.getProperty("LLM_SSL_VERIFY");
        previousIsSensitive = System.getProperty("IS_SENSITIVE");
        System.setProperty("LLM_SSL_VERIFY", "false");
        System.setProperty("IS_SENSITIVE", "false");
        previousMemoryInstance = readLongTermMemoryInstance();
        registerMockModelFactory();
        MOCK_RESPONSES.clear();
        MODEL_CALLS.clear();
        Runner.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        MOCK_RESPONSES.clear();
        MODEL_CALLS.clear();
        replaceLongTermMemoryInstance(previousMemoryInstance);
        Runner.stop();
        restoreProperty("LLM_SSL_VERIFY", previousLlmSslVerify);
        restoreProperty("IS_SENSITIVE", previousIsSensitive);
    }

    @Test
    @Tag("level0")
    @DisplayName("workflow interrupt and resume")
    void testWorkflowInterruptAndResume() {
        Workflow flow = makeSingleQuestionerWorkflow("wf_weather_unit", "1.0");
        setMockResponses(
            toolCallResponse("call_001", "wf_weather_unit", "{\"query\":\"weather\"}"),
            textResponse("The weather in Shanghai is sunny.")
        );
        LlmAgentConfig agentConfig = makeAgentConfig("agent_s1", List.of(makeWorkflowSchema(flow)));
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());

        Object result = agent.invoke(Map.of("conversation_id", "conv_s1_unit", "query", "weather query"), null);
        List<OutputSchema> interactions = interactionChunks(outputItems(result));

        assertEquals(1, interactions.size());
        assertInstanceOf(InteractionOutput.class, interactions.get(0).getPayload());
        assertEquals("questioner", ((InteractionOutput) interactions.get(0).getPayload()).getId());

        InteractiveInput userInput = new InteractiveInput();
        userInput.update("questioner", "Shanghai");
        Object result2 = agent.invoke(Map.of("conversation_id", "conv_s1_unit", "query", userInput), null);

        assertEquals(0, interactionChunks(outputItems(result2)).size());
        assertTrue(collectOutputText(outputItems(result2)).contains("Shanghai"));
    }

    @Test
    @Tag("level0")
    @DisplayName("memory loads before invoke and writes after answer")
    void testMemoryLoadAndWriteOnAnswer() throws Exception {
        LongTermMemory memory = installMockMemory();
        when(memory.searchUserMem(anyString(), anyInt(), anyString(), anyString(), anyDouble())).thenReturn(List.of());

        setMockResponses(textResponse("Hello! I remember you."));
        LlmAgentConfig agentConfig = makeAgentConfig("agent_memory", List.of());
        AgentMemoryConfig memoryConfig = AgentMemoryConfig.builder()
            .enableLongTermMem(true)
            .enableUserProfile(true)
            .enableSemanticMemory(false)
            .enableEpisodicMemory(false)
            .enableSummaryMemory(false)
            .build();
        agentConfig.setMemoryScopeId("scope_001");
        agentConfig.setAgentMemoryConfig(memoryConfig);
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(), List.of());

        Object result = agent.invoke(
            Map.of("conversation_id", "conv_mem_unit", "query", "Hello", "user_id", "user_001"),
            null
        );

        assertEquals(0, interactionChunks(outputItems(result)).size());
        assertTrue(collectOutputText(outputItems(result)).contains("remember"));
        verify(memory).searchUserMem(eq("Hello"), eq(10), eq("user_001"), eq("scope_001"), eq(0.0));
        verify(memory, timeout(1000)).addMessages(
            anyList(), same(memoryConfig), eq("user_001"), eq("scope_001"), eq("conv_mem_unit")
        );
    }

    @Test
    @Tag("level0")
    @DisplayName("memory is not written when workflow interrupts")
    void testMemoryNotWrittenOnInterrupt() throws Exception {
        LongTermMemory memory = installMockMemory();
        when(memory.searchUserMem(anyString(), anyInt(), anyString(), anyString(), anyDouble())).thenReturn(List.of());

        Workflow flow = makeSingleQuestionerWorkflow("mem_wf_unit", "1.0");
        setMockResponses(toolCallResponse("call_mem", "mem_wf_unit", "{\"query\":\"q\"}"));
        LlmAgentConfig agentConfig = makeAgentConfig("agent_mem_interrupt", List.of(makeWorkflowSchema(flow)));
        AgentMemoryConfig memoryConfig = AgentMemoryConfig.builder()
            .enableLongTermMem(true)
            .enableUserProfile(true)
            .enableSemanticMemory(false)
            .enableEpisodicMemory(false)
            .enableSummaryMemory(false)
            .build();
        agentConfig.setMemoryScopeId("scope_001");
        agentConfig.setAgentMemoryConfig(memoryConfig);
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());

        Object result = agent.invoke(
            Map.of("conversation_id", "conv_mi_unit", "query", "q", "user_id", "user_001"),
            null
        );

        assertEquals(1, interactionChunks(outputItems(result)).size());
        Thread.sleep(200);
        verify(memory, never()).addMessages(
            anyList(), any(AgentMemoryConfig.class), anyString(), anyString(), anyString()
        );
    }

    @Test
    @Tag("level0")
    @DisplayName("parallel questioners resume one at a time")
    void testParallelQuestionersSequentialResume() {
        Workflow flow = buildParallelWorkflow("wf_parallel_unit");
        setMockResponses(
            toolCallResponse("call_p1", "wf_parallel_unit", "{\"query\":\"info\"}"),
            textResponse("Got both name and address.")
        );
        LlmAgentConfig agentConfig = makeAgentConfig("agent_s3", List.of(makeWorkflowSchema(flow)));
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());

        List<OutputSchema> chunks1 = interactionChunks(outputItems(
            agent.invoke(Map.of("conversation_id", "conv_s3_unit", "query", "collect info"), null)
        ));
        assertEquals(1, chunks1.size());
        String firstComponent = ((InteractionOutput) chunks1.get(0).getPayload()).getId();
        assertTrue(List.of("questioner", "questioner_2").contains(firstComponent));

        InteractiveInput userInput1 = new InteractiveInput();
        userInput1.update(firstComponent, "Alice");
        List<OutputSchema> chunks2 = interactionChunks(outputItems(
            agent.invoke(Map.of("conversation_id", "conv_s3_unit", "query", userInput1), null)
        ));
        assertEquals(1, chunks2.size());
        String secondComponent = ((InteractionOutput) chunks2.get(0).getPayload()).getId();
        assertFalse(firstComponent.equals(secondComponent));

        InteractiveInput userInput2 = new InteractiveInput();
        userInput2.update(secondComponent, "Beijing");
        Object result3 = agent.invoke(Map.of("conversation_id", "conv_s3_unit", "query", userInput2), null);

        assertEquals(0, interactionChunks(outputItems(result3)).size());
        assertNotNull(result3);
    }

    @Test
    @Tag("level0")
    @DisplayName("two workflows interrupt and resume serially")
    void testTwoWorkflowsSerialInterruptResume() {
        Workflow flowA = makeSingleQuestionerWorkflow("wf_city_a_unit", "1.0");
        Workflow flowB = makeSingleQuestionerWorkflow("wf_city_b_unit", "1.0");
        setMockResponses(
            multiToolCallResponse(
                List.of(
                    toolCall("call_a", "wf_city_a_unit", "{\"query\":\"city a\"}"),
                    toolCall("call_b", "wf_city_b_unit", "{\"query\":\"city b\"}")
                )
            ),
            textResponse("Cities collected: Shanghai and Beijing.")
        );
        LlmAgentConfig agentConfig = makeAgentConfig(
            "agent_s4", List.of(makeWorkflowSchema(flowA), makeWorkflowSchema(flowB))
        );
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(flowA, flowB), List.of());

        List<OutputSchema> chunks1 = interactionChunks(outputItems(
            agent.invoke(Map.of("conversation_id", "conv_s4_unit", "query", "collect cities"), null)
        ));
        assertEquals(1, chunks1.size());
        assertEquals("questioner", ((InteractionOutput) chunks1.get(0).getPayload()).getId());

        InteractiveInput userInput1 = new InteractiveInput();
        userInput1.update("questioner", "Shanghai");
        List<OutputSchema> chunks2 = interactionChunks(outputItems(
            agent.invoke(Map.of("conversation_id", "conv_s4_unit", "query", userInput1), null)
        ));
        assertEquals(1, chunks2.size());

        InteractiveInput userInput2 = new InteractiveInput();
        userInput2.update("questioner", "Beijing");
        Object result3 = agent.invoke(Map.of("conversation_id", "conv_s4_unit", "query", userInput2), null);

        assertEquals(0, interactionChunks(outputItems(result3)).size());
        String output = collectOutputText(outputItems(result3));
        assertTrue(output.contains("Shanghai"));
        assertTrue(output.contains("Beijing"));
    }

    @Test
    @Tag("level0")
    @DisplayName("set prompt template updates config and active handler")
    void testSetPromptTemplateUpdatesConfigAndInner() {
        setMockResponses(textResponse("Prompt updated."));
        LlmAgentConfig agentConfig = makeAgentConfig("agent_s5", List.of());
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(), List.of());

        List<Map<String, String>> newTemplate =
            List.of(Map.of("role", "system", "content", "You are a new assistant."));
        agent.setPromptTemplate(newTemplate);
        agent.invoke(Map.of("conversation_id", "conv_s5_unit", "query", "hello"), null);

        assertEquals(newTemplate, agent.getAgentConfig().getPromptTemplate());
        assertFalse(MODEL_CALLS.isEmpty());
        String firstSystem = MODEL_CALLS.get(0).stream()
            .filter(message -> "system".equals(message.getRole()))
            .findFirst()
            .map(BaseMessage::getContentAsString)
            .orElse("");
        assertEquals("You are a new assistant.", firstSystem);
    }

    @Test
    @Tag("level0")
    @DisplayName("add tools is idempotent")
    void testAddToolsIdempotent() {
        LlmAgentConfig agentConfig = makeAgentConfig("agent_s6", List.of());
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(), List.of());
        SimpleTool tool = new SimpleTool();

        try {
            agent.addTools(List.of(tool));
            agent.addTools(List.of(tool));

            long toolConfigCount = agent.getAgentConfig().getTools().stream()
                .filter("tool_alpha"::equals)
                .count();
            long pluginConfigCount = agent.getAgentConfig().getPlugins().stream()
                .filter(plugin -> "tool_alpha".equals(plugin.getName()))
                .count();

            assertEquals(1, toolConfigCount);
            assertEquals(1, pluginConfigCount);
            assertNotNull(agent.getAbilityManager().get("tool_alpha"));
        } finally {
            Runner.resourceMgr().removeTool(tool.getCard().getId(), null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("add workflows is idempotent and remove cleans up")
    void testAddWorkflowsIdempotentAndRemove() {
        LlmAgentConfig agentConfig = makeAgentConfig("agent_s7", List.of());
        MockLlmAgent agent = MockLlmAgent.createLlmAgent(agentConfig, List.of(), List.of());
        Workflow flow = makeSingleQuestionerWorkflow("wf_mgmt", "2.0");
        String workflowKey = WorkflowUtils.generateWorkflowKey("wf_mgmt", "2.0");

        agent.addWorkflows(List.of(flow));
        agent.addWorkflows(List.of(flow));

        long matching = agent.getAgentConfig().getWorkflows().stream()
            .filter(schema -> "wf_mgmt".equals(schema.getId()) && "2.0".equals(schema.getVersion()))
            .count();
        assertEquals(1, matching);
        assertNotNull(agent.getAbilityManager().get("wf_mgmt"));

        agent.removeWorkflows(Collections.singletonList(new String[] {"wf_mgmt", "2.0"}));

        long remaining = agent.getAgentConfig().getWorkflows().stream()
            .filter(schema -> "wf_mgmt".equals(schema.getId()) && "2.0".equals(schema.getVersion()))
            .count();
        assertEquals(0, remaining);
        assertNull(agent.getAbilityManager().get("wf_mgmt"));
        Runner.resourceMgr().removeWorkflow(workflowKey, null, TagMatchStrategy.ALL, true);
    }

    private static void setMockResponses(AssistantMessage... responses) {
        MOCK_RESPONSES.clear();
        MOCK_RESPONSES.addAll(Arrays.asList(responses));
    }

    private static AssistantMessage toolCallResponse(String id, String workflowName, String arguments) {
        return multiToolCallResponse(List.of(toolCall(id, workflowName, arguments)));
    }

    private static AssistantMessage multiToolCallResponse(List<ToolCall> toolCalls) {
        return AssistantMessage.builder()
            .content("")
            .toolCalls(toolCalls)
            .usageMetadata(UsageMetadata.builder().modelName("mock").build())
            .finishReason("tool_calls")
            .build();
    }

    private static ToolCall toolCall(String id, String workflowName, String arguments) {
        return ToolCall.builder()
            .id(id)
            .type("function")
            .name(workflowName)
            .arguments(arguments)
            .build();
    }

    private static AssistantMessage textResponse(String content) {
        return AssistantMessage.builder()
            .content(content)
            .usageMetadata(UsageMetadata.builder().modelName("mock").build())
            .finishReason("stop")
            .build();
    }

    private static void registerMockModelFactory() {
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return MOCK_PROVIDER;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return new MockModelClient(modelConfig, clientConfig);
            }
        });
    }

    private static LlmAgentConfig makeAgentConfig(String agentId, List<WorkflowSchema> workflows) {
        return MockLlmAgent.createLlmAgentConfig(
            agentId,
            "1.0",
            "test",
            workflows,
            List.of(),
            MODEL_CONFIG,
            List.of(Map.of("role", "system", "content", "You are a test assistant.")),
            List.of()
        );
    }

    private static QuestionerComponent makeQuestioner(String question) {
        QuestionerConfig cfg = new QuestionerConfig(
            MODEL_REQUEST_CONFIG,
            MODEL_CLIENT_CONFIG,
            question,
            false,
            List.of(new FieldInfo("user_response", "User response", true)),
            false
        );
        return new QuestionerComponent(cfg);
    }

    private static Workflow makeSingleQuestionerWorkflow(String wfId, String version) {
        WorkflowCard card = WorkflowCard.builder()
            .id(wfId)
            .name(wfId)
            .version(version)
            .inputParams(Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")
            ))
            .build();

        Workflow flow = new Workflow(card);
        flow.setStartComp("s", new StartComponent(), Map.of("query", "${query}"));
        flow.setEndComp(
            "e",
            new EndComponent(Map.of("responseTemplate", "done: {{user_response}}")),
            Map.of("user_response", "${questioner.user_response}")
        );
        flow.addWorkflowComp("questioner", makeQuestioner("What is your location?"), Map.of("query", "${s.query}"));
        flow.addConnection("s", "questioner");
        flow.addConnection("questioner", "e");
        return flow;
    }

    private static Workflow buildParallelWorkflow(String wfId) {
        WorkflowCard card = WorkflowCard.builder()
            .id(wfId)
            .name(wfId)
            .version("1.0")
            .inputParams(Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")
            ))
            .build();

        Workflow flow = new Workflow(card);
        flow.setStartComp("s", new StartComponent(), Map.of("query", "${query}"));
        flow.setEndComp(
            "e",
            new EndComponent(Map.of("responseTemplate", "name:{{name_resp}} addr:{{addr_resp}}")),
            Map.of(
                "name_resp", "${questioner.user_response}",
                "addr_resp", "${questioner_2.user_response}"
            )
        );
        flow.addWorkflowComp("questioner", makeQuestioner("What is your name?"), Map.of("query", "${s.query}"));
        flow.addWorkflowComp("questioner_2", makeQuestioner("What is your address?"), Map.of("query", "${s.query}"));
        flow.addConnection("s", "questioner");
        flow.addConnection("s", "questioner_2");
        flow.addConnection("questioner", "e");
        flow.addConnection("questioner_2", "e");
        return flow;
    }

    private static WorkflowSchema makeWorkflowSchema(Workflow flow) {
        return WorkflowSchema.builder()
            .id(flow.getCard().getId())
            .name(flow.getCard().getName())
            .version(flow.getCard().getVersion())
            .description(flow.getCard().getDescription() != null ? flow.getCard().getDescription() : "")
            .inputParams(Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string"))
            ))
            .build();
    }

    private static List<?> outputItems(Object result) {
        if (result instanceof com.openjiuwen.core.controller.schema.ControllerOutput controllerOutput
                && controllerOutput.getData() instanceof List<?> list) {
            return list;
        }
        if (result instanceof List<?> list) {
            return list;
        }
        return List.of(result);
    }

    private static List<OutputSchema> interactionChunks(List<?> chunks) {
        List<OutputSchema> interactions = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                interactions.add(os);
            }
        }
        return interactions;
    }

    @SuppressWarnings("unchecked")
    private static String collectOutputText(List<?> chunks) {
        StringBuilder text = new StringBuilder();
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema os && os.getPayload() instanceof Map<?, ?> payload) {
                Object output = ((Map<String, Object>) payload).get("output");
                if (output != null) {
                    text.append(output);
                }
            }
        }
        return text.toString();
    }

    private LongTermMemory installMockMemory() throws Exception {
        LongTermMemory memory = mock(LongTermMemory.class);
        replaceLongTermMemoryInstance(memory);
        return memory;
    }

    private static LongTermMemory readLongTermMemoryInstance() throws Exception {
        Field field = LongTermMemory.class.getDeclaredField("instance");
        field.setAccessible(true);
        return (LongTermMemory) field.get(null);
    }

    private static void replaceLongTermMemoryInstance(LongTermMemory memory) throws Exception {
        Field field = LongTermMemory.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, memory);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static final class MockModelClient extends BaseModelClient {
        private MockModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            if (messages instanceof List<?> list) {
                List<BaseMessage> copied = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof BaseMessage message) {
                        copied.add(message);
                    }
                }
                MODEL_CALLS.add(copied);
            }
            AssistantMessage response = MOCK_RESPONSES.pollFirst();
            return response != null ? response : textResponse("Task complete.");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            AssistantMessage response = invoke(messages, tools, temperature, topP, model, maxTokens, stop,
                outputParser, timeout, kwargs);
            AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                .content(response.getContent())
                .toolCalls(response.getToolCalls())
                .usageMetadata(response.getUsageMetadata())
                .finishReason(response.getFinishReason())
                .build();
            return List.of(chunk).iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("image generation is not used in this test");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("speech generation is not used in this test");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("video generation is not used in this test");
        }
    }

    private static final class SimpleTool extends Tool {
        private SimpleTool() {
            super(ToolCard.builder()
                .id("tool_alpha_id")
                .name("tool_alpha")
                .description("mock tool description")
                .inputParams(Map.of(
                    "type", "object",
                    "properties", Map.of()
                ))
                .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Map.of("ok", true);
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of(invoke(inputs, kwargs)).iterator();
        }
    }
}
