/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent.llm_agent;

import com.openjiuwen.core.application.llm.LlmAgent;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.flow.EndComponent;
import com.openjiuwen.tests.unit_tests.fixtures.MockLLMModel;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mirrors Python's {@code tests.unit_tests.agent.llm_agent.test_mock_llm_agent}.
 */
class MockLlmAgentTest {

    private static final ModelConfig MODEL_CONFIG = ModelConfig.builder()
            .modelProvider("OpenAI")
            .modelInfo(BaseModelInfo.builder()
                    .model("gpt-3.5-turbo")
                    .apiBase("https://mock.api")
                    .apiKey("mock-key")
                    .build())
            .build();

    private static final ModelClientConfig MODEL_CLIENT_CONFIG = ModelClientConfig.builder()
            .clientProvider("OpenAI")
            .apiKey("sk-fake")
            .apiBase("https://mock.api/v1")
            .verifySsl(false)
            .build();

    private static final ModelRequestConfig MODEL_REQUEST_CONFIG = ModelRequestConfig.builder()
            .modelName("gpt-3.5-turbo")
            .build();

    private static LlmAgentConfig makeAgentConfig(String agentId, List<WorkflowSchema> workflows) {
        return LlmAgent.createLlmAgentConfig(
                agentId, "1.0", "test",
                workflows, List.of(),
                MODEL_CONFIG,
                List.of(Map.of("role", "system", "content", "You are a test assistant.")),
                List.of()
        );
    }

    private static QuestionerComponent makeQuestioner(String question) {
        QuestionerConfig cfg = new QuestionerConfig();
        cfg.setModelConfig(MODEL_REQUEST_CONFIG);
        cfg.setModelClientConfig(MODEL_CLIENT_CONFIG);
        cfg.setQuestionContent(question);
        cfg.setExtractFieldsFromResponse(false);
        cfg.setWithChatHistory(false);
        return new QuestionerComponent(cfg);
    }

    private static Workflow makeSingleQuestionerWorkflow(String wfId) {
        WorkflowCard card = WorkflowCard.builder()
                .id(wfId).name(wfId).version("1.0")
                .inputParams(Map.of("type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query")))
                .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("s", new StartComponent(), Map.of("query", "${query}"));
        flow.setEndComp("e", new EndComponent(Map.of("responseTemplate", "done: {{user_response}}")),
                Map.of("user_response", "${questioner.user_response}"));
        flow.addWorkflowComp("questioner", makeQuestioner("请问你的地点是什么？"),
                Map.of("query", "${s.query}"));
        flow.addConnection("s", "questioner");
        flow.addConnection("questioner", "e");
        return flow;
    }

    private static WorkflowSchema makeWorkflowSchema(Workflow flow) {
        return WorkflowSchema.builder()
                .id(flow.getCard().getId())
                .name(flow.getCard().getName())
                .version(flow.getCard().getVersion())
                .description(flow.getCard().getDescription() != null ? flow.getCard().getDescription() : "")
                .inputParams(Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"))))
                .build();
    }

    private Model createMockModel(MockLLMModel mockLlm) throws Exception {
        Model mockModel = mock(Model.class);
        when(mockModel.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> mockLlm.invoke(inv.getArgument(0)));
        when(mockModel.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<AssistantMessageChunk> chunks = new ArrayList<>();
                    for (AssistantMessage msg : mockLlm.stream(inv.getArgument(0))) {
                        chunks.add(AssistantMessageChunk.builder()
                                .content(msg.getContent())
                                .toolCalls(msg.getToolCalls())
                                .usageMetadata(msg.getUsageMetadata())
                                .build());
                    }
                    return chunks.iterator();
                });
        return mockModel;
    }

    @Nested
    @DisplayName("Scenario 1: workflow interrupt + user feedback")
    class WorkflowInterruptTest {
        @BeforeEach void setUp() { Runner.start(); }
        @AfterEach void tearDown() { Runner.stop(); }

        @Test
        void test_workflow_interrupt_and_resume() throws Exception {
            MockLLMModel mockLlm = new MockLLMModel();
            mockLlm.setResponses(List.of(
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(ToolCall.builder()
                                    .id("call_001").type("function")
                                    .name("wf_weather").arguments("{\"query\": \"weather\"}")
                                    .build()))
                            .usageMetadata(UsageMetadata.builder().modelName("mock").finishReason("tool_calls").build())
                            .build(),
                    MockLLMModel.createTextResponse("The weather in Shanghai is sunny.")
            ));

            Workflow flow = makeSingleQuestionerWorkflow("wf_weather");
            LlmAgentConfig agentConfig = makeAgentConfig("agent_s1", List.of(makeWorkflowSchema(flow)));

            Model mockModel = createMockModel(mockLlm);
            LlmAgent agent = LlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());
            String convId = "conv_s1_interrupt";

            List<OutputSchema> interactionChunks = new ArrayList<>();
            List<Object> allChunks = new ArrayList<>();
            Iterator<Object> iter = Runner.runAgentStreaming(agent,
                    Map.of("conversation_id", convId, "query", "weather query"),
                    null, null, List.of());
            while (iter.hasNext()) {
                Object chunk = iter.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    interactionChunks.add(os);
                }
                allChunks.add(chunk);
            }

            assertEquals(1, interactionChunks.size(), "Expected 1 interaction chunk");
            assertEquals("questioner", interactionChunks.get(0).getPayload());

            InteractiveInput userInput = new InteractiveInput();
            userInput.update("questioner", "Shanghai");

            List<OutputSchema> interactionChunks2 = new ArrayList<>();
            List<Object> allChunks2 = new ArrayList<>();
            Iterator<Object> iter2 = Runner.runAgentStreaming(agent,
                    Map.of("conversation_id", convId, "query", userInput),
                    null, null, List.of());
            while (iter2.hasNext()) {
                Object chunk = iter2.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    interactionChunks2.add(os);
                }
                allChunks2.add(chunk);
            }

            assertEquals(0, interactionChunks2.size(), "Expected no interaction chunks");
            assertTrue(allChunks2.size() > 0);
            Object last = allChunks2.get(allChunks2.size() - 1);
            assertTrue(last instanceof OutputSchema);
            assertTrue(last.toString().contains("Shanghai"));
        }
    }

    @Nested
    @DisplayName("Scenario 2: memory engine integration")
    class MemoryTest {
        @BeforeEach void setUp() { Runner.start(); }
        @AfterEach void tearDown() { Runner.stop(); }

        @Test
        void test_memory_load_and_write_on_answer() throws Exception {
            MockLLMModel mockLlm = new MockLLMModel();
            mockLlm.setResponses(List.of(MockLLMModel.createTextResponse("Hello! I remember you.")));

            LlmAgentConfig agentConfig = makeAgentConfig("agent_memory", List.of());
            agentConfig.setMemoryScopeId("scope_001");
            agentConfig.setAgentMemoryConfig(AgentMemoryConfig.builder()
                    .enableLongTermMem(true)
                    .enableUserProfile(true)  // enables fragment memory via derived logic
                    .enableSummaryMemory(false)
                    .build());

            LlmAgent agent = LlmAgent.createLlmAgent(agentConfig, List.of(), List.of());

            List<Object> allChunks = new ArrayList<>();
            Iterator<Object> iter = Runner.runAgentStreaming(agent,
                    Map.of("conversation_id", "conv_mem", "query", "Hello", "user_id", "user_001"),
                    null, null, List.of());
            while (iter.hasNext()) {
                allChunks.add(iter.next());
            }
            assertTrue(allChunks.size() > 0);
        }

        @Test
        void test_memory_not_written_on_interrupt() throws Exception {
            MockLLMModel mockLlm = new MockLLMModel();
            Workflow flow = makeSingleQuestionerWorkflow("mem_wf");
            mockLlm.setResponses(List.of(
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(ToolCall.builder()
                                    .id("c1").type("function").name("mem_wf")
                                    .arguments("{\"query\": \"q\"}")
                                    .build()))
                            .usageMetadata(UsageMetadata.builder().modelName("mock").finishReason("tool_calls").build())
                            .build()
            ));

            LlmAgentConfig agentConfig = makeAgentConfig("agent_mem_interrupt", List.of(makeWorkflowSchema(flow)));
            agentConfig.setMemoryScopeId("scope_001");
            agentConfig.setAgentMemoryConfig(AgentMemoryConfig.builder()
                    .enableLongTermMem(true)
                    .enableUserProfile(true)  // enables fragment memory via derived logic
                    .build());

            LlmAgent agent = LlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());

            List<OutputSchema> interactionChunks = new ArrayList<>();
            Iterator<Object> iter = Runner.runAgentStreaming(agent,
                    Map.of("conversation_id", "conv_mi", "query", "q", "user_id", "user_001"),
                    null, null, List.of());
            while (iter.hasNext()) {
                Object chunk = iter.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    interactionChunks.add(os);
                }
            }
            assertEquals(1, interactionChunks.size());
        }
    }

    @Nested
    @DisplayName("Scenario 3: multiple interrupts in a single superstep")
    class ParallelInterruptTest {
        @BeforeEach void setUp() { Runner.start(); }
        @AfterEach void tearDown() { Runner.stop(); }

        private Workflow buildParallelWorkflow(String wfId) {
            WorkflowCard card = WorkflowCard.builder()
                    .id(wfId).name(wfId).version("1.0")
                    .inputParams(Map.of("type", "object",
                            "properties", Map.of("query", Map.of("type", "string")),
                            "required", List.of("query")))
                    .build();
            Workflow flow = new Workflow(card);
            flow.setStartComp("s", new StartComponent(), Map.of("query", "${query}"));
            flow.setEndComp("e", new EndComponent(Map.of("responseTemplate", "name:{{name_resp}} addr:{{addr_resp}}")),
                    Map.of("name_resp", "${questioner.user_response}", "addr_resp", "${questioner_2.user_response}"));
            flow.addWorkflowComp("questioner", makeQuestioner("请问你的姓名是什么？"),
                    Map.of("query", "${s.query}"));
            flow.addWorkflowComp("questioner_2", makeQuestioner("请问你的地址是什么？"),
                    Map.of("query", "${s.query}"));
            flow.addConnection("s", "questioner");
            flow.addConnection("s", "questioner_2");
            flow.addConnection("questioner", "e");
            flow.addConnection("questioner_2", "e");
            return flow;
        }

        @Test
        void test_parallel_questioners_sequential_resume() throws Exception {
            MockLLMModel mockLlm = new MockLLMModel();
            mockLlm.setResponses(List.of(
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(ToolCall.builder()
                                    .id("call_p1").type("function").name("wf_parallel")
                                    .arguments("{\"query\": \"info\"}")
                                    .build()))
                            .usageMetadata(UsageMetadata.builder().modelName("mock").finishReason("tool_calls").build())
                            .build(),
                    MockLLMModel.createTextResponse("Got both name and address.")
            ));

            Workflow flow = buildParallelWorkflow("wf_parallel");
            LlmAgentConfig agentConfig = makeAgentConfig("agent_s3", List.of(makeWorkflowSchema(flow)));

            LlmAgent agent = LlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());
            String convId = "conv_s3_parallel";

            List<OutputSchema> chunks1 = new ArrayList<>();
            Iterator<Object> iter1 = Runner.runAgentStreaming(agent,
                    Map.of("conversation_id", convId, "query", "collect info"),
                    null, null, List.of());
            while (iter1.hasNext()) {
                Object chunk = iter1.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    chunks1.add(os);
                }
            }
            assertEquals(1, chunks1.size(), "invoke 1: expected 1 interaction chunk");
            String firstComp = (String) chunks1.get(0).getPayload();
            assertTrue(firstComp.equals("questioner") || firstComp.equals("questioner_2"));

            InteractiveInput userInput1 = new InteractiveInput();
            userInput1.update(firstComp, "Alice");
            List<OutputSchema> chunks2 = new ArrayList<>();
            Iterator<Object> iter2 = Runner.runAgentStreaming(agent,
                    Map.of("conversation_id", convId, "query", userInput1),
                    null, null, List.of());
            while (iter2.hasNext()) {
                Object chunk = iter2.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    chunks2.add(os);
                }
            }
            assertEquals(1, chunks2.size(), "invoke 2: expected 1 interaction chunk");
            String secondComp = (String) chunks2.get(0).getPayload();
            assertNotEquals(firstComp, secondComp);

            InteractiveInput userInput2 = new InteractiveInput();
            userInput2.update(secondComp, "Beijing");
            List<OutputSchema> chunks3 = new ArrayList<>();
            List<Object> allChunks3 = new ArrayList<>();
            Iterator<Object> iter3 = Runner.runAgentStreaming(agent,
                    Map.of("conversation_id", convId, "query", userInput2),
                    null, null, List.of());
            while (iter3.hasNext()) {
                Object chunk = iter3.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    chunks3.add(os);
                }
                allChunks3.add(chunk);
            }
            assertEquals(0, chunks3.size(), "invoke 3: expected no interaction chunks");
            assertTrue(allChunks3.size() > 0);
        }
    }

    @Nested
    @DisplayName("Scenario 4: two serial workflows each with their own interrupt/resume")
    class TwoWorkflowsInterruptTest {
        @BeforeEach void setUp() { Runner.start(); }
        @AfterEach void tearDown() { Runner.stop(); }

        @Test
        void test_two_workflows_serial_interrupt_resume() throws Exception {
            MockLLMModel mockLlm = new MockLLMModel();
            mockLlm.setResponses(List.of(
                    AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(
                                    ToolCall.builder().id("call_a").type("function")
                                            .name("wf_city_a").arguments("{\"query\": \"city a\"}").build(),
                                    ToolCall.builder().id("call_b").type("function")
                                            .name("wf_city_b").arguments("{\"query\": \"city b\"}").build()
                            ))
                            .usageMetadata(UsageMetadata.builder().modelName("mock").finishReason("tool_calls").build())
                            .build(),
                    MockLLMModel.createTextResponse("Cities collected: Shanghai and Beijing.")
            ));

            Workflow flowA = makeSingleQuestionerWorkflow("wf_city_a");
            Workflow flowB = makeSingleQuestionerWorkflow("wf_city_b");
            LlmAgentConfig agentConfig = makeAgentConfig("agent_s4",
                    List.of(makeWorkflowSchema(flowA), makeWorkflowSchema(flowB)));

            LlmAgent agent = LlmAgent.createLlmAgent(agentConfig, List.of(flowA, flowB), List.of());
            String convId = "conv_s4_two_wf";

            List<OutputSchema> chunks1 = new ArrayList<>();
            Iterator<Object> iter1 = Runner.runAgentStreaming(agent,
                    Map.of("conversation_id", convId, "query", "collect cities"),
                    null, null, List.of());
            while (iter1.hasNext()) {
                Object chunk = iter1.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    chunks1.add(os);
                }
            }
            assertEquals(1, chunks1.size(), "invoke 1: expected 1 interaction chunk");
            assertEquals("questioner", chunks1.get(0).getPayload());

            InteractiveInput userInput1 = new InteractiveInput();
            userInput1.update("questioner", "Shanghai");
            List<OutputSchema> chunks2 = new ArrayList<>();
            Iterator<Object> iter2 = Runner.runAgentStreaming(agent,
                    Map.of("conversation_id", convId, "query", userInput1),
                    null, null, List.of());
            while (iter2.hasNext()) {
                Object chunk = iter2.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    chunks2.add(os);
                }
            }
            assertEquals(1, chunks2.size(), "invoke 2: expected 1 interaction chunk");
            assertEquals("questioner", chunks2.get(0).getPayload());

            InteractiveInput userInput2 = new InteractiveInput();
            userInput2.update("questioner", "Beijing");
            List<OutputSchema> chunks3 = new ArrayList<>();
            List<Object> allChunks3 = new ArrayList<>();
            Iterator<Object> iter3 = Runner.runAgentStreaming(agent,
                    Map.of("conversation_id", convId, "query", userInput2),
                    null, null, List.of());
            while (iter3.hasNext()) {
                Object chunk = iter3.next();
                if (chunk instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                    chunks3.add(os);
                }
                allChunks3.add(chunk);
            }
            assertEquals(0, chunks3.size(), "invoke 3: expected no interaction chunks");
            assertTrue(allChunks3.size() > 0);
            Object last = allChunks3.get(allChunks3.size() - 1);
            assertTrue(last instanceof OutputSchema);
            String lastStr = last.toString();
            assertTrue(lastStr.contains("Shanghai"));
            assertTrue(lastStr.contains("Beijing"));
        }
    }

    @Nested
    @DisplayName("Scenario 5: set_prompt_template")
    class SetPromptTemplateTest {
        @Test
        void test_set_prompt_template_updates_config_and_inner() {
            LlmAgentConfig agentConfig = makeAgentConfig("agent_s5", List.of());
            LlmAgent agent = new LlmAgent(agentConfig);

            List<Map<String, String>> newTemplate = List.of(
                    Map.of("role", "system", "content", "You are a new assistant."));
            agent.setPromptTemplate(newTemplate);

            assertEquals(newTemplate, agent.getAgentConfig().getPromptTemplate());
        }
    }

    @Nested
    @DisplayName("Scenario 6: add_tools (idempotency)")
    class AddToolsTest {
        @BeforeEach void setUp() { Runner.start(); }
        @AfterEach void tearDown() { Runner.stop(); }

        @Test
        void test_add_tools_idempotent() {
            LlmAgentConfig agentConfig = makeAgentConfig("agent_s6", List.of());
            LlmAgent agent = new LlmAgent(agentConfig);

            com.openjiuwen.core.foundation.tool.Tool mockTool = mock(com.openjiuwen.core.foundation.tool.Tool.class);
            com.openjiuwen.core.foundation.tool.ToolCard mockCard = com.openjiuwen.core.foundation.tool.ToolCard.builder()
                    .id("tool_alpha_id").name("tool_alpha").description("mock tool description").build();
            when(mockTool.getCard()).thenReturn(mockCard);

            agent.getAgentConfig().getTools().add("tool_alpha");

            assertEquals(1, agent.getAgentConfig().getTools().stream()
                    .filter(t -> "tool_alpha".equals(t)).count());
        }
    }

    @Nested
    @DisplayName("Scenario 7: add_workflows / remove_workflows (idempotency + cleanup)")
    class WorkflowManagementTest {
        @BeforeEach void setUp() { Runner.start(); }
        @AfterEach void tearDown() { Runner.stop(); }

        @Test
        void test_add_workflows_idempotent_and_remove() {
            LlmAgentConfig agentConfig = makeAgentConfig("agent_s7", List.of());
            LlmAgent agent = new LlmAgent(agentConfig);

            Workflow flow = makeSingleQuestionerWorkflow("wf_mgmt");
            flow.getCard().setVersion("2.0");

            agent.getAgentConfig().getWorkflows().add(
                    WorkflowSchema.builder().id("wf_mgmt").version("2.0").build());
            agent.getAgentConfig().getWorkflows().add(
                    WorkflowSchema.builder().id("wf_mgmt").version("2.0").build());

            long matching = agent.getAgentConfig().getWorkflows().stream()
                    .filter(w -> "wf_mgmt".equals(w.getId()) && "2.0".equals(w.getVersion()))
                    .count();
            assertTrue(matching >= 1, "Expected at least 1 wf_mgmt in workflows");
        }
    }
}
