/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent.workflow_agent;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.controller.schema.ControllerOutput;
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
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowUtils;
import com.openjiuwen.core.workflow.components.flow.EndComponent;
import com.openjiuwen.core.workflow.components.flow.StartComponent;
import com.openjiuwen.core.workflow.components.llm.FieldInfo;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;
import com.openjiuwen.tests.unit_tests.core.workflow.MockNodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workflow Agent mock tests using a deterministic mock LLM.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent.workflow_agent.test_workflow_agent_mock}.</p>
 */
@DisplayName("WorkflowAgent Mock Tests")
class TestWorkflowAgentMock {

    private static final String MOCK_PROVIDER = "MockBatch04WorkflowAgentOpenAI";
    private static final ArrayDeque<AssistantMessage> MOCK_RESPONSES = new ArrayDeque<>();

    private static final ModelConfig MODEL_CONFIG = new ModelConfig(
        MOCK_PROVIDER,
        BaseModelInfo.builder()
            .modelName("gpt-3.5-turbo")
            .apiBase("mock_url")
            .apiKey("mock_key")
            .temperature(0.7)
            .topP(0.9)
            .timeout(30)
            .build()
    );

    private static final ModelRequestConfig MODEL_REQUEST_CONFIG = ModelRequestConfig.builder()
        .modelName("gpt-3.5-turbo")
        .temperature(0.7)
        .topP(0.9)
        .build();

    private static final ModelClientConfig MODEL_CLIENT_CONFIG = ModelClientConfig.builder()
        .clientProvider(MOCK_PROVIDER)
        .apiKey("sk-fake")
        .apiBase("https://api.openai.com/v1")
        .timeout(30)
        .maxRetries(3)
        .verifySsl(false)
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
    @Tag("level0")
    @DisplayName("workflow agent basic execution")
    void testWorkflowAgentBasicExecution() {
        Workflow workflow = buildSimpleWorkflow("simple_workflow_" + suffix(), "simple workflow");
        WorkflowAgent agent = new WorkflowAgent(workflowAgentConfig("simple_workflow_agent_" + suffix()));
        agent.addWorkflows(List.of(workflow));

        ControllerOutput result = agent.invoke(Map.of("query", "hello"), null);

        Map<String, Object> data = result.getDataAsMap();
        assertEquals("answer", data.get("result_type"));
        WorkflowOutput output = (WorkflowOutput) data.get("output");
        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
    }

    @Test
    @Tag("level0")
    @DisplayName("workflow agent with interrupt")
    void testWorkflowAgentWithInterrupt() {
        setMockResponses(jsonResponse("{\"location\": null}"));
        Workflow workflow = buildQuestionerWorkflow("location_workflow_" + suffix(), "location query",
            "location", "location name");
        WorkflowAgent agent = new WorkflowAgent(workflowAgentConfig("location_workflow_agent_" + suffix()));
        agent.addWorkflows(List.of(workflow));

        ControllerOutput result = agent.invoke(
            Map.of("conversation_id", "test_interrupt_" + suffix(), "query", "weather query"),
            null
        );

        List<OutputSchema> interactions = interactionData(result);
        assertEquals(1, interactions.size());
        assertEquals("__interaction__", interactions.get(0).getType());
    }

    @Test
    @Tag("level0")
    @DisplayName("workflow agent interrupt resume")
    void testWorkflowAgentInterruptResume() {
        setMockResponses(
            jsonResponse("{\"location\": null}"),
            jsonResponse("{\"location\": \"Shanghai\"}")
        );
        String conversationId = "test_resume_" + suffix();
        Workflow workflow = buildQuestionerWorkflow("location_workflow_resume_" + suffix(), "location query",
            "location", "location name");
        WorkflowAgent agent = new WorkflowAgent(workflowAgentConfig("location_workflow_resume_agent_" + suffix()));
        agent.addWorkflows(List.of(workflow));

        ControllerOutput first = agent.invoke(
            Map.of("conversation_id", conversationId, "query", "weather query"),
            null
        );
        List<OutputSchema> interactions = interactionData(first);
        assertEquals(1, interactions.size());

        InteractiveInput input = new InteractiveInput();
        input.update("questioner", "Shanghai");
        ControllerOutput second = agent.invoke(
            Map.of("conversation_id", conversationId, "query", input),
            null
        );

        Map<String, Object> data = second.getDataAsMap();
        assertEquals("answer", data.get("result_type"));
        WorkflowOutput output = (WorkflowOutput) data.get("output");
        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
    }

    @Test
    @Tag("level0")
    @DisplayName("workflow tagged with agent id")
    void testWorkflowAgentWorkflowTaggedWithAgentId() {
        String agentId = "wf_agent_tag_test_" + suffix();
        String workflowId = "tag_test_wf_" + suffix();
        Workflow workflow = buildSimpleWorkflow(workflowId, "Tag Test WF");
        WorkflowAgent agent = new WorkflowAgent(workflowAgentConfig(agentId));
        agent.addWorkflows(List.of(workflow));

        String workflowKey = WorkflowUtils.generateWorkflowKey(workflowId, "1.0");
        assertTrue(Runner.resourceMgr().resourceHasTag(workflowKey, agentId));
        assertFalse(Runner.resourceMgr().resourceHasTag(workflowKey, com.openjiuwen.core.runner.base.Tag.GLOBAL));
    }

    @Test
    @Tag("level0")
    @DisplayName("two workflow agents isolated")
    void testTwoWorkflowAgentsIsolated() {
        String agentAId = "iso_agent_A_" + suffix();
        String agentBId = "iso_agent_B_" + suffix();
        String workflowAId = "wf_iso_a_" + suffix();
        String workflowBId = "wf_iso_b_" + suffix();

        Workflow wfA = buildSimpleWorkflow(workflowAId, "WF A");
        WorkflowAgent agentA = new WorkflowAgent(workflowAgentConfig(agentAId));
        agentA.addWorkflows(List.of(wfA));

        Workflow wfB = buildSimpleWorkflow(workflowBId, "WF B");
        WorkflowAgent agentB = new WorkflowAgent(workflowAgentConfig(agentBId));
        agentB.addWorkflows(List.of(wfB));

        String wfKeyA = WorkflowUtils.generateWorkflowKey(workflowAId, "1.0");
        String wfKeyB = WorkflowUtils.generateWorkflowKey(workflowBId, "1.0");

        assertTrue(Runner.resourceMgr().resourceHasTag(wfKeyA, agentAId));
        assertFalse(Runner.resourceMgr().resourceHasTag(wfKeyA, agentBId));
        assertTrue(Runner.resourceMgr().resourceHasTag(wfKeyB, agentBId));
        assertFalse(Runner.resourceMgr().resourceHasTag(wfKeyB, agentAId));
    }

    private static WorkflowAgentConfig workflowAgentConfig(String id) {
        return WorkflowAgentConfig.builder()
            .id(id)
            .version("1.0")
            .description("workflow agent test")
            .model(MODEL_CONFIG)
            .workflows(List.of())
            .controllerType(ControllerType.WORKFLOW_CONTROLLER)
            .build();
    }

    private static Workflow buildSimpleWorkflow(String workflowId, String workflowName) {
        WorkflowCard card = WorkflowCard.builder()
            .id(workflowId)
            .version("1.0")
            .name(workflowName)
            .description("Simple workflow: " + workflowName)
            .build();
        Workflow flow = new Workflow(card);
        flow.setStartComp("start", new MockNodes.MockStartNode("start"), Map.of("query", "${query}"));
        flow.addWorkflowComp("node_a", new MockNodes.Node1("node_a"), Map.of("output", "${start.query}"));
        flow.setEndComp("end", new MockNodes.MockEndNode("end"), Map.of("result", "${node_a.output}"));
        flow.addConnection("start", "node_a");
        flow.addConnection("node_a", "end");
        return flow;
    }

    private static Workflow buildQuestionerWorkflow(String workflowId, String workflowName, String fieldName,
                                                    String fieldDesc) {
        WorkflowCard card = WorkflowCard.builder()
            .id(workflowId)
            .version("1.0")
            .name(workflowName)
            .description("Questioner workflow: " + workflowName)
            .build();
        Workflow flow = new Workflow(card);
        List<FieldInfo> keyFields = List.of(new FieldInfo(fieldName, fieldDesc, true));
        QuestionerConfig questionerConfig = new QuestionerConfig(
            MODEL_REQUEST_CONFIG,
            MODEL_CLIENT_CONFIG,
            "",
            true,
            keyFields,
            false
        );
        QuestionerComponent questioner = new QuestionerComponent(questionerConfig);
        flow.setStartComp("start", new StartComponent(), Map.of("query", "${query}"));
        flow.addWorkflowComp("questioner", questioner, Map.of("query", "${start.query}"));
        flow.setEndComp("end", new EndComponent(Map.of("responseTemplate", workflowName + " done: {{" + fieldName + "}}")),
            Map.of(fieldName, "${questioner." + fieldName + "}"));
        flow.addConnection("start", "questioner");
        flow.addConnection("questioner", "end");
        return flow;
    }

    private static void setMockResponses(AssistantMessage... responses) {
        MOCK_RESPONSES.clear();
        MOCK_RESPONSES.addAll(Arrays.asList(responses));
    }

    private static AssistantMessage jsonResponse(String content) {
        return AssistantMessage.builder()
            .content(content)
            .usageMetadata(UsageMetadata.builder().modelName("mock").build())
            .finishReason("stop")
            .build();
    }

    private static List<OutputSchema> interactionData(ControllerOutput output) {
        if (output == null || !(output.getData() instanceof List<?> list)) {
            return List.of();
        }
        List<OutputSchema> interactions = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof OutputSchema os && "__interaction__".equals(os.getType())) {
                interactions.add(os);
            }
        }
        return interactions;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
            AssistantMessage response = MOCK_RESPONSES.pollFirst();
            return response != null ? response : jsonResponse("{\"location\": \"Shanghai\"}");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            AssistantMessage response = invoke(messages, tools, temperature, topP, model, maxTokens, stop,
                outputParser, timeout, kwargs);
            return List.of(AssistantMessageChunk.builder()
                .content(response.getContent())
                .usageMetadata(response.getUsageMetadata())
                .finishReason(response.getFinishReason())
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
}
