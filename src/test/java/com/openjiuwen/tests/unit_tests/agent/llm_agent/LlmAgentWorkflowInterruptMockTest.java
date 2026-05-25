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
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowUtils;
import com.openjiuwen.core.workflow.components.llm.FieldInfo;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.flow.EndComponent;

import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mirrors Python's {@code tests.unit_tests.agent.llm_agent.test_llm_agent_invoke_with_workflow_interrupt_mock}.
 */
class LlmAgentWorkflowInterruptMockTest {

    private static String buildCurrentDate() {
        return LocalDate.now().toString();
    }

    private static ModelConfig createModel() {
        return new ModelConfig(
                "OpenAI",
                BaseModelInfo.builder()
                        .modelName("gpt-3.5-turbo")
                        .apiBase("https://api.openai.com")
                        .apiKey("mock_key")
                        .temperature(0.7)
                        .topP(0.9)
                        .timeout(30)
                        .build()
        );
    }

    private static ModelRequestConfig createModelRequestConfig() {
        return ModelRequestConfig.builder()
                .modelName("gpt-3.5-turbo")
                .temperature(0.7)
                .topP(0.9)
                .build();
    }

    private static ModelClientConfig createModelClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider("OpenAI")
                .apiKey("sk-fake")
                .apiBase("https://api.openai.com/v1")
                .timeout(30)
                .maxRetries(3)
                .verifySsl(false)
                .build();
    }

    private static List<Map<String, String>> createPromptTemplate() {
        String systemPrompt = "你是一个AI助手，在适当的时候调用合适的工具，帮助我完成任务！今天的日期为：" + buildCurrentDate();
        return List.of(Map.of("role", "system", "content", systemPrompt));
    }

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @SuppressWarnings("unchecked")
    @Test
    void test_react_agent_invoke_with_workflow_interrupt_mock() throws Exception {
        com.openjiuwen.tests.unit_tests.fixtures.MockLLMModel mockLlm =
                new com.openjiuwen.tests.unit_tests.fixtures.MockLLMModel();

        List<AssistantMessage> allLlmResponses = List.of(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(
                                ToolCall.builder()
                                        .id("call_weather_001")
                                        .type("function")
                                        .name("questioner_weather_workflow")
                                        .arguments("{\"query\": \"今天天气查询\"}")
                                        .build()
                        ))
                        .usageMetadata(UsageMetadata.builder()
                                .modelName("gpt-3.5-turbo")
                                .inputTokens(150)
                                .outputTokens(25)
                                .build())
                        .build(),
                AssistantMessage.builder()
                        .content("{\n  \"location\": null,\n  \"time\": \"today\"\n}")
                        .usageMetadata(UsageMetadata.builder()
                                .modelName("gpt-3.5-turbo")
                                .build())
                        .build(),
                AssistantMessage.builder()
                        .content("{\n  \"location\": \"上海\",\n  \"time\": \"today\"\n}")
                        .usageMetadata(UsageMetadata.builder()
                                .modelName("gpt-3.5-turbo")
                                .build())
                        .build(),
                AssistantMessage.builder()
                        .content("我已经为您查询了上海的天气信息。根据返回的结果：上海 | today，这表明查询已成功完成。如果需要更详细的天气数据，请告诉我。")
                        .usageMetadata(UsageMetadata.builder()
                                .modelName("gpt-3.5-turbo")
                                .build())
                        .build()
        );

        mockLlm.setResponses(allLlmResponses);

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

        WorkflowCard questionerWorkflowCard = WorkflowCard.builder()
                .name("questioner_weather_workflow")
                .id("questioner_weather_workflow")
                .version("1.0")
                .description("天气查询")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query")
                ))
                .build();

        Workflow flow = new Workflow(questionerWorkflowCard);

        List<FieldInfo> keyFields = List.of(
                new FieldInfo("location", "地点", true),
                new FieldInfo("time", "时间", true)
        );

        StartComponent startComponent = new StartComponent();
        EndComponent endComponent = new EndComponent(Map.of("responseTemplate", "{{location}} | {{time}}"));

        QuestionerConfig questionerConfig = new QuestionerConfig(
                createModelRequestConfig(),
                createModelClientConfig(),
                "",
                true,
                keyFields,
                false
        );
        QuestionerComponent questionerComponent = new QuestionerComponent(questionerConfig);

        flow.setStartComp("s", startComponent, Map.of("query", "${query}"));
        flow.setEndComp("e", endComponent,
                Map.of("location", "${questioner.location}", "time", "${questioner.time}"));
        flow.addWorkflowComp("questioner", questionerComponent, Map.of("query", "${s.query}"));
        flow.addConnection("s", "questioner");
        flow.addConnection("questioner", "e");

        WorkflowSchema workflowSchema = WorkflowSchema.builder()
                .id(flow.getCard().getId())
                .name(flow.getCard().getName())
                .version(flow.getCard().getVersion())
                .description("追问器工作流")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string", "description", "用户输入", "required", true))
                ))
                .build();

        LlmAgentConfig reactAgentConfig = LlmAgent.createLlmAgentConfig(
                "react_agent_123", "0.0.1", "AI助手",
                List.of(workflowSchema), List.of(),
                createModel(), createPromptTemplate(), List.of()
        );

        LlmAgent reactAgent = LlmAgent.createLlmAgent(reactAgentConfig, List.of(flow), List.of());

        String workflowKey = WorkflowUtils.generateWorkflowKey(flow.getCard().getId(), flow.getCard().getVersion());
        WorkflowCard resourceCard = WorkflowCard.builder()
                .id(workflowKey)
                .name(flow.getCard().getName())
                .version(flow.getCard().getVersion())
                .build();
        Runner.resourceMgr().addWorkflow(resourceCard, () -> flow, reactAgentConfig.getId());

        try {
            Object result = Runner.runAgent(reactAgent,
                    Map.of("conversation_id", "12345", "query", "今天天气查询"),
                    null, null);

            assertTrue(result instanceof List, "First invoke should return a list");
            List<?> resultList = (List<?>) result;
            assertTrue(resultList.size() > 0, "Result list should not be empty");
            OutputSchema first = (OutputSchema) resultList.get(0);
            assertEquals("__interaction__", first.getType());
            assertEquals("questioner", first.getPayload());

            if (first.getType().equals("__interaction__")) {
                InteractiveInput interactiveInput = new InteractiveInput();
                interactiveInput.update("questioner", "上海");

                Object result2 = Runner.runAgent(reactAgent,
                        Map.of("conversation_id", "12345", "query", interactiveInput),
                        null, null);

                assertTrue(result2 instanceof Map, "Second invoke should return a map");
                Map<String, Object> resultMap = (Map<String, Object>) result2;
                assertEquals("answer", resultMap.get("result_type"));
                assertTrue(resultMap.containsKey("output"));
                assertTrue(resultMap.get("output").toString().contains("上海"));
            }
        } finally {
            Runner.resourceMgr().removeWorkflow(workflowKey, reactAgentConfig.getId(),
                    com.openjiuwen.core.runner.base.TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void test_llm_agent_workflow_tagged_with_agent_id() {
        ModelConfig modelConfig = new ModelConfig(
                "OpenAI",
                BaseModelInfo.builder()
                        .modelName("gpt-3.5-turbo")
                        .apiBase("mock_url")
                        .apiKey("mock_key")
                        .temperature(0.7)
                        .build()
        );

        WorkflowSchema workflowSchema = WorkflowSchema.builder()
                .id("test_wf")
                .name("test_wf")
                .version("1.0")
                .description("test workflow")
                .inputParams(Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"))))
                .build();

        LlmAgentConfig agentConfig = LlmAgent.createLlmAgentConfig(
                "llm_agent_tag_test", "0.0.1", "Tag Test",
                List.of(workflowSchema), List.of(),
                modelConfig, List.of(Map.of("role", "system", "content", "test")), List.of()
        );

        Workflow flow = new Workflow(WorkflowCard.builder().id("test_wf").name("test_wf").version("1.0").build());
        StartComponent start = new StartComponent();
        EndComponent end = new EndComponent(Map.of("responseTemplate", "{{output}}"));
        flow.setStartComp("s", start, Map.of("query", "${query}"));
        flow.setEndComp("e", end, Map.of("output", "${s.query}"));
        flow.addConnection("s", "e");

        LlmAgent agent = LlmAgent.createLlmAgent(agentConfig, List.of(flow), List.of());

        String workflowKey = WorkflowUtils.generateWorkflowKey("test_wf", "1.0");
        assertTrue(Runner.resourceMgr().resourceHasTag(workflowKey, "llm_agent_tag_test"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void test_llm_agent_two_agents_workflow_isolated() {
        var makeAgentWithWorkflow = new java.util.function.BiFunction<String, String, LlmAgent>() {
            @Override
            public LlmAgent apply(String agentId, String wfId) {
                ModelConfig mc = new ModelConfig(
                        "OpenAI",
                        BaseModelInfo.builder()
                                .modelName("gpt-3.5-turbo").apiBase("mock").apiKey("mock").build()
                );
                WorkflowSchema ws = WorkflowSchema.builder()
                        .id(wfId).name(wfId).version("1.0").description("wf").build();
                LlmAgentConfig config = LlmAgent.createLlmAgentConfig(
                        agentId, "1.0", "test", List.of(ws), List.of(),
                        mc, List.of(Map.of("role", "system", "content", "test")), List.of()
                );
                Workflow f = new Workflow(WorkflowCard.builder().id(wfId).name(wfId).version("1.0").build());
                StartComponent s = new StartComponent();
                EndComponent e = new EndComponent(Map.of("responseTemplate", "ok"));
                f.setStartComp("s", s, Map.of("q", "${query}"));
                f.setEndComp("e", e, Map.of("r", "${s.q}"));
                f.addConnection("s", "e");
                return LlmAgent.createLlmAgent(config, List.of(f), List.of());
            }
        };

        LlmAgent agentA = makeAgentWithWorkflow.apply("agent_A", "wf_a");
        LlmAgent agentB = makeAgentWithWorkflow.apply("agent_B", "wf_b");

        Object wfListA = Runner.resourceMgr().getWorkflow("wf_a", "agent_A",
                com.openjiuwen.core.runner.base.TagMatchStrategy.ALL);
        assertNotNull(wfListA);

        Object wfListB = Runner.resourceMgr().getWorkflow("wf_b", "agent_B",
                com.openjiuwen.core.runner.base.TagMatchStrategy.ALL);
        assertNotNull(wfListB);
    }
}
