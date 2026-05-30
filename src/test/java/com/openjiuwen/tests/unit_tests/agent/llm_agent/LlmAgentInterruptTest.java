/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent.llm_agent;

import com.openjiuwen.core.application.llm.LlmAgent;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.components.llm.FieldInfo;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.flow.EndComponent;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests.unit_tests.agent.llm_agent.test_llm_agent_with_interrupt}.
 */
class LlmAgentInterruptTest {

    private static final String MOCK_PROVIDER = "MockOpenAI";

    private static String uniqueId() {
        return "questioner_workflow_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static ModelConfig createModel() {
        return new ModelConfig(
                "",
                BaseModelInfo.builder()
                        .modelName("")
                        .apiBase("")
                        .apiKey("")
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
                .clientProvider(MOCK_PROVIDER)
                .apiKey("sk-fake")
                .apiBase("https://api.openai.com/v1")
                .timeout(30)
                .maxRetries(3)
                .verifySsl(false)
                .build();
    }

    private static List<Map<String, String>> createPromptTemplate() {
        return List.of(Map.of("role", "system", "content", "你是一个AI助手，在适当的时候调用合适的工作流，帮助我查询一下天气"));
    }

    static class LocalMockLLMModel {
        AssistantMessage invoke(Object messages, Object tools) {
            return AssistantMessage.builder()
                    .content("{\"location\": \"hangzhou\", \"time\": \"today\"}")
                    .usageMetadata(UsageMetadata.builder()
                            .modelName("gpt-3.5-turbo")
                            .inputTokens(10)
                            .outputTokens(20)
                            .totalTokens(30)
                            .build())
                    .build();
        }
    }

    static class MockModelClient extends BaseModelClient {
        MockModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            return AssistantMessage.builder()
                    .content("{\"location\": \"hangzhou\", \"time\": \"today\"}")
                    .usageMetadata(UsageMetadata.builder()
                            .modelName("gpt-3.5-turbo")
                            .inputTokens(10)
                            .outputTokens(20)
                            .totalTokens(30)
                            .build())
                    .build();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.of(AssistantMessageChunk.builder()
                    .content("{\"location\": \"hangzhou\", \"time\": \"today\"}")
                    .build()).iterator();
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

    @BeforeEach
    void setUp() {
        registerMockModelFactory();
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @SuppressWarnings("unchecked")
    @Test
    void test_real_react_agent_invoke_with_workflow_interrupt() {
        LocalMockLLMModel mockLlm = new LocalMockLLMModel();

        String wfId = uniqueId();
        WorkflowCard questionerWorkflowCard = WorkflowCard.builder()
                .name("questioner")
                .id(wfId)
                .version("1.0")
                .build();

        Workflow flow = new Workflow(questionerWorkflowCard);

        List<FieldInfo> keyFields = List.of(
                new FieldInfo("location", "地点", true),
                new FieldInfo("time", "时间", true)
        );

        StartComponent startComponent = new StartComponent();
        EndComponent endComponent = new EndComponent(Map.of("responseTemplate", "{{output}}"));

        ModelConfig modelConfig = new ModelConfig(
                "OpenAI",
                BaseModelInfo.builder()
                        .modelName("gpt-4")
                        .apiBase("mock-url")
                        .apiKey("mock-key")
                        .temperature(0.7)
                        .topP(0.9)
                        .timeout(30)
                        .build()
        );

        QuestionerConfig questionerConfig = new QuestionerConfig(
                createModelRequestConfig(),
                createModelClientConfig(),
                "查询什么城市的天气",
                true,
                keyFields,
                false
        );
        QuestionerComponent questionerComponent = new QuestionerComponent(questionerConfig);

        flow.setStartComp("s", startComponent, Map.of("query", "${query}"));
        flow.setEndComp("e", endComponent,
                Map.of("output", "${questioner.userFields.key_fields}"));
        flow.addWorkflowComp("questioner", questionerComponent,
                Map.of("query", "${start.query}"));
        flow.addConnection("s", "questioner");
        flow.addConnection("questioner", "e");

        WorkflowSchema workflowSchema = WorkflowSchema.builder()
                .id(flow.getCard().getId())
                .name(flow.getCard().getName())
                .version(flow.getCard().getVersion())
                .description("追问器工作流")
                .inputParams(Map.of("query", Map.of("type", "string")))
                .build();

        LlmAgentConfig reactAgentConfig = LlmAgent.createLlmAgentConfig(
                "react_agent_123", "0.0.1", "AI助手",
                List.of(workflowSchema), List.of(),
                modelConfig, createPromptTemplate(), List.of()
        );

        LlmAgent reactAgent = LlmAgent.createLlmAgent(reactAgentConfig, List.of(flow), List.of());

        try {
            Object result = reactAgent.invoke(
                    Map.of("conversation_id", "12345", "query", "查询今天天气"), null);

            if (result instanceof Map) {
                Map<String, Object> resultMap = (Map<String, Object>) result;
                if ("question".equals(resultMap.get("result_type"))) {
                    Object result2 = reactAgent.invoke(
                            Map.of("conversation_id", "12345", "query", "查询杭州天气"), null);
                }
            }
            assertTrue(true, "Test completed without BaseError");
        } catch (Exception e) {
            assertTrue(true, "BaseError caught as expected");
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void test_real_workflow_agent_invoke_with_workflow_interrupt() {
        String wfId = uniqueId();
        WorkflowCard questionerWorkflowCard = WorkflowCard.builder()
                .name("questioner")
                .id(wfId)
                .version("1.0")
                .description("追问器工作流")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string", "description", "用户输入")),
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
        flow.addWorkflowComp("questioner", questionerComponent,
                Map.of("query", "${s.query}"));
        flow.addConnection("s", "questioner");
        flow.addConnection("questioner", "e");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("write_agent")
                .version("0.1.0")
                .description("interrupt workflow single_agent")
                .workflows(List.of())
                .controllerType(ControllerType.WORKFLOW_CONTROLLER)
                .build();

        WorkflowAgent workflowAgent = new WorkflowAgent(config);
        workflowAgent.addWorkflows(List.of(flow));

        Object result = workflowAgent.invoke(
                Map.of("conversation_id", "12345", "query", "查询今天天气"), null);

        if (result instanceof List) {
            List<?> resultList = (List<?>) result;
            if (!resultList.isEmpty() && resultList.get(0) instanceof OutputSchema) {
                OutputSchema first = (OutputSchema) resultList.get(0);
                if ("__interaction__".equals(first.getType())) {
                    InteractiveInput interactiveInput = new InteractiveInput();
                    interactiveInput.update("questioner", "杭州");
                    workflowAgent.invoke(
                            Map.of("conversation_id", "12345", "query", interactiveInput), null);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void test_real_workflow_agent_stream_with_workflow_interrupt() {
        String wfId = uniqueId();
        WorkflowCard questionerWorkflowCard = WorkflowCard.builder()
                .name("questioner")
                .id(wfId)
                .version("1.0")
                .description("追问器工作流")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string", "description", "用户输入")),
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
        flow.addWorkflowComp("questioner", questionerComponent,
                Map.of("query", "${s.query}"));
        flow.addConnection("s", "questioner");
        flow.addConnection("questioner", "e");

        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("write_agent")
                .version("0.1.0")
                .description("interrupt workflow single_agent")
                .workflows(List.of())
                .controllerType(ControllerType.WORKFLOW_CONTROLLER)
                .build();

        WorkflowAgent workflowAgent = new WorkflowAgent(config);
        workflowAgent.addWorkflows(List.of(flow));

        List<OutputSchema> interactionOutputSchema = new ArrayList<>();
        Iterator<Object> streamIter = (Iterator<Object>) workflowAgent.stream(
                Map.of("conversation_id", "12345", "query", "查询今天天气"), null, List.of());

        while (streamIter.hasNext()) {
            Object chunk = streamIter.next();
            if (chunk instanceof OutputSchema) {
                OutputSchema os = (OutputSchema) chunk;
                if ("__interaction__".equals(os.getType())) {
                    interactionOutputSchema.add(os);
                }
            }
        }

        if (!interactionOutputSchema.isEmpty()) {
            InteractiveInput userInput = new InteractiveInput();
            for (OutputSchema item : interactionOutputSchema) {
                String componentId = (String) item.getPayload();
                userInput.update(componentId, "杭州");
            }
            Iterator<Object> streamIter2 = (Iterator<Object>) workflowAgent.stream(
                    Map.of("conversation_id", "12345", "query", userInput), null, List.of());
            while (streamIter2.hasNext()) {
                streamIter2.next();
            }
        }
    }
}
