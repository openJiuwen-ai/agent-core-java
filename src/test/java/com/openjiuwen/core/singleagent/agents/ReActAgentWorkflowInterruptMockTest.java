/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.components.flow.EndComponent;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.llm.FieldInfo;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ReActAgent workflow interrupt/resume redesign.
 *
 * <p>Mirrors Python's
 * {@code tests/unit_tests/agent/react_agent/test_react_agent_workflow_interrupt_mock.py}.
 */
@DisplayName("ReActAgent Workflow Interrupt Mock")
class ReActAgentWorkflowInterruptMockTest {

    private static final String MOCK_PROVIDER = "MockReActWorkflowInterruptOpenAI";
    private static final ArrayDeque<AssistantMessage> MOCK_RESPONSES = new ArrayDeque<>();
    private static final ModelClientConfig MODEL_CLIENT_CONFIG = ModelClientConfig.builder()
            .clientProvider(MOCK_PROVIDER)
            .apiKey("sk-fake")
            .apiBase("https://mock.openai.local/v1")
            .verifySsl(false)
            .build();
    private static final ModelRequestConfig MODEL_REQUEST_CONFIG = ModelRequestConfig.builder()
            .modelName("gpt-4o-mock")
            .temperature(0.0)
            .build();

    @BeforeEach
    void setUp() {
        registerMockModelFactory();
        MOCK_RESPONSES.clear();
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        MOCK_RESPONSES.clear();
        Runner.stop();
    }

    @Test
    @DisplayName("test_single_interrupt_then_resume")
    void testSingleInterruptThenResume() {
        Workflow flow = buildSingleWorkflow("wf_single", "questioner", "done: {{user_response}}");
        registerWorkflow(flow);
        setMockResponses(
                toolCallResponse("call_001", "wf_single", "{\"query\": \"collect name\"}"),
                textResponse("Task complete: name collected.")
        );
        ReActAgent agent = makeAgent(flow.getCard());
        String conversationId = "TestScenario1SingleWorkflowSingleInterrupt";

        List<Object> firstRound = runStreaming(agent, Map.of("conversation_id", conversationId, "query", "collect name"));
        List<OutputSchema> firstInteractions = interactions(firstRound);
        assertThat(firstInteractions).hasSize(1);
        assertThat(interactionId(firstInteractions.get(0))).isEqualTo("questioner");

        InteractiveInput userInput = new InteractiveInput();
        userInput.update("questioner", "Zhang San");
        List<Object> secondRound = runStreaming(agent, userInput, conversationId);
        assertThat(interactions(secondRound)).isEmpty();
        assertThat(streamedText(secondRound)).contains("Task complete: name collected.");
    }

    @Test
    @DisplayName("test_parallel_interrupt_sequential_resume")
    void testParallelInterruptSequentialResume() {
        Workflow flow = buildParallelWorkflow();
        registerWorkflow(flow);
        setMockResponses(
                toolCallResponse("call_p_001", "wf_parallel", "{\"query\": \"collect info\"}"),
                textResponse("Task complete: name and address collected.")
        );
        ReActAgent agent = makeAgent(flow.getCard());
        String conversationId = "TestScenario2SingleWorkflowParallelInterrupt";

        List<Object> firstRound = runStreaming(agent, Map.of("conversation_id", conversationId, "query", "collect info"));
        List<OutputSchema> firstInteractions = interactions(firstRound);
        assertThat(firstInteractions).hasSize(1);
        assertThat(interactionId(firstInteractions.get(0))).isEqualTo("questioner_1");

        InteractiveInput firstAnswer = new InteractiveInput();
        firstAnswer.update("questioner_1", "Li Si");
        List<Object> secondRound = runStreaming(agent, firstAnswer, conversationId);
        List<OutputSchema> secondInteractions = interactions(secondRound);
        assertThat(secondInteractions).hasSize(1);
        assertThat(interactionId(secondInteractions.get(0))).isEqualTo("questioner_2");

        InteractiveInput secondAnswer = new InteractiveInput();
        secondAnswer.update("questioner_2", "Beijing Chaoyang");
        List<Object> thirdRound = runStreaming(agent, secondAnswer, conversationId);
        assertThat(interactions(thirdRound)).isEmpty();
        assertThat(streamedText(thirdRound)).contains("Task complete: name and address collected.");
    }

    @Test
    @DisplayName("test_two_workflows_sequential_interrupt_then_concurrent_resume")
    void testTwoWorkflowsSequentialInterruptThenConcurrentResume() {
        Workflow flowA = buildSingleWorkflow("wf_a", "questioner_a", "name: {{user_response}}");
        Workflow flowB = buildSingleWorkflow("wf_b", "questioner_b", "address: {{user_response}}");
        registerWorkflow(flowA);
        registerWorkflow(flowB);
        setMockResponses(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(
                                ToolCall.builder()
                                        .id("call_a_001")
                                        .type("function")
                                        .name("wf_a")
                                        .arguments("{\"query\": \"collect name\"}")
                                        .build(),
                                ToolCall.builder()
                                        .id("call_b_001")
                                        .type("function")
                                        .name("wf_b")
                                        .arguments("{\"query\": \"collect address\"}")
                                        .build()
                        ))
                        .usageMetadata(UsageMetadata.builder().modelName("mock").build())
                        .finishReason("tool_calls")
                        .build(),
                textResponse("Task complete: name and address collected.")
        );
        ReActAgent agent = makeAgent(flowA.getCard(), flowB.getCard());
        String conversationId = "TestScenario3TwoWorkflowsEachInterrupt";

        List<Object> firstRound = runStreaming(agent, Map.of("conversation_id", conversationId, "query", "collect both"));
        List<OutputSchema> firstInteractions = interactions(firstRound);
        assertThat(firstInteractions).hasSize(1);
        assertThat(interactionId(firstInteractions.get(0))).isEqualTo("questioner_a");

        InteractiveInput firstAnswer = new InteractiveInput();
        firstAnswer.update("questioner_a", "Li Si");
        List<Object> secondRound = runStreaming(agent, firstAnswer, conversationId);
        List<OutputSchema> secondInteractions = interactions(secondRound);
        assertThat(secondInteractions).hasSize(1);
        assertThat(interactionId(secondInteractions.get(0))).isEqualTo("questioner_b");

        InteractiveInput secondAnswer = new InteractiveInput();
        secondAnswer.update("questioner_b", "Beijing Chaoyang");
        List<Object> thirdRound = runStreaming(agent, secondAnswer, conversationId);
        assertThat(interactions(thirdRound)).isEmpty();
        assertThat(streamedText(thirdRound)).contains("Task complete: name and address collected.");
    }

    private static ReActAgent makeAgent(WorkflowCard... workflowCards) {
        AgentCard agentCard = AgentCard.builder()
                .id("react_agent_interrupt_test")
                .description("test agent")
                .build();
        ReActAgentConfig config = ReActAgentConfig.builder()
                .modelClientConfig(MODEL_CLIENT_CONFIG)
                .modelConfigObj(MODEL_REQUEST_CONFIG)
                .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
                .build();
        ReActAgent agent = new ReActAgent(agentCard);
        agent.configure(config);
        for (WorkflowCard workflowCard : workflowCards) {
            agent.getAbilityManager().add(workflowCard);
        }
        return agent;
    }

    private static Workflow buildSingleWorkflow(String workflowId, String questionerId, String responseTemplate) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(workflowId)
                .version("1.0")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query")
                ))
                .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("s", new StartComponent(), Map.of("query", "${query}"));
        flow.setEndComp("e", new EndComponent(Map.of("responseTemplate", responseTemplate)),
                Map.of("user_response", "${" + questionerId + ".user_response}"));
        flow.addWorkflowComp(questionerId, makeQuestioner("What is your name?"),
                Map.of("query", "${s.query}"));
        flow.addConnection("s", questionerId);
        flow.addConnection(questionerId, "e");
        return flow;
    }

    private static Workflow buildParallelWorkflow() {
        WorkflowCard card = WorkflowCard.builder()
                .id("wf_parallel")
                .name("wf_parallel")
                .version("1.0")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query")
                ))
                .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("s", new StartComponent(), Map.of("query", "${query}"));
        flow.setEndComp("e", new EndComponent(Map.of("responseTemplate", "name:{{name_resp}} address:{{addr_resp}}")),
                Map.of(
                        "name_resp", "${questioner_1.user_response}",
                        "addr_resp", "${questioner_2.user_response}"
                ));
        flow.addWorkflowComp("questioner_1", makeQuestioner("What is your name?"),
                Map.of("query", "${s.query}"));
        flow.addWorkflowComp("questioner_2", makeQuestioner("What is your address?"),
                Map.of("query", "${s.query}"));
        flow.addConnection("s", "questioner_1");
        flow.addConnection("s", "questioner_2");
        flow.addConnection("questioner_1", "e");
        flow.addConnection("questioner_2", "e");
        return flow;
    }

    private static QuestionerComponent makeQuestioner(String question) {
        QuestionerConfig config = new QuestionerConfig(
                MODEL_REQUEST_CONFIG,
                MODEL_CLIENT_CONFIG,
                question,
                false,
                List.of(new FieldInfo("user_response", "User response", true)),
                false
        );
        return new QuestionerComponent(config);
    }

    private static void registerWorkflow(Workflow workflow) {
        Runner.resourceMgr().addWorkflow(workflow.getCard(), () -> workflow, null);
    }

    private static List<Object> runStreaming(ReActAgent agent, Object inputs) {
        return collect(agent.stream(inputs, null, List.of(StreamMode.OUTPUT)));
    }

    private static List<Object> runStreaming(ReActAgent agent, InteractiveInput input, String conversationId) {
        AgentSessionApi session = AgentSessionApi.create(
                conversationId,
                null,
                agent.getCard(),
                List.of(StreamMode.OUTPUT)
        );
        return collect(agent.stream(input, session, List.of(StreamMode.OUTPUT)));
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }
        return chunks;
    }

    private static List<OutputSchema> interactions(List<Object> chunks) {
        List<OutputSchema> result = new ArrayList<>();
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema schema && "__interaction__".equals(schema.getType())) {
                result.add(schema);
            }
        }
        return result;
    }

    private static String interactionId(OutputSchema schema) {
        Object payload = schema.getPayload();
        if (payload instanceof InteractionOutput output) {
            return output.getId();
        }
        if (payload instanceof Map<?, ?> map) {
            Object id = map.get("id");
            return id != null ? String.valueOf(id) : null;
        }
        return payload != null ? String.valueOf(payload) : null;
    }

    @SuppressWarnings("unchecked")
    private static String streamedText(List<Object> chunks) {
        StringBuilder text = new StringBuilder();
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema schema && schema.getPayload() instanceof Map<?, ?> payload) {
                Object content = ((Map<String, Object>) payload).get("content");
                Object output = ((Map<String, Object>) payload).get("output");
                if (content != null) {
                    text.append(content);
                }
                if (output != null) {
                    text.append(output);
                }
            }
        }
        return text.toString();
    }

    private static void setMockResponses(AssistantMessage... responses) {
        MOCK_RESPONSES.clear();
        MOCK_RESPONSES.addAll(Arrays.asList(responses));
    }

    private static AssistantMessage toolCallResponse(String id, String workflowName, String arguments) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(id)
                        .type("function")
                        .name(workflowName)
                        .arguments(arguments)
                        .build()))
                .usageMetadata(UsageMetadata.builder().modelName("mock").build())
                .finishReason("tool_calls")
                .build();
    }

    private static AssistantMessage textResponse(String content) {
        return AssistantMessage.builder()
                .content(content)
                .usageMetadata(UsageMetadata.builder().modelName("mock").build())
                .finishReason("stop")
                .build();
    }

    private static AssistantMessage pollResponse() {
        AssistantMessage response = MOCK_RESPONSES.pollFirst();
        return response != null ? response : textResponse("Task complete.");
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

    private static final class MockModelClient extends BaseModelClient {
        private MockModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            return pollResponse();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            AssistantMessage response = pollResponse();
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
}
