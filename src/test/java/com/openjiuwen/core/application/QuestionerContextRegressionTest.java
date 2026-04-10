/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application;

import com.openjiuwen.core.application.llm.LlmAgent;
import com.openjiuwen.core.application.schema.ConstrainConfig;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.*;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for application-layer ConstrainConfig.reservedMaxChatRounds
 * wiring into ContextEngine, validated through a Questioner multi-turn workflow.
 * <p>
 * Mirrors Python system tests test_llm_agent_093 and test_llm_agent_095.
 */
public class QuestionerContextRegressionTest {

    private static final String AGENT_PROVIDER = "CtxAgentMirror";
    private static final String QUESTIONER_PROVIDER = "CtxQuestionMirror";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    private final Set<String> workflowIds = new HashSet<>();
    private final Set<String> sessionIds = new HashSet<>();

    public QuestionerContextRegressionTest() {
        ensureFactoryRegistered();
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);
    }

    @AfterEach
    public void cleanup() {
        for (String workflowId : workflowIds) {
            Runner.resourceMgr().removeWorkflow(workflowId, null, TagMatchStrategy.ALL, true);
        }
        for (String sessionId : sessionIds) {
            CheckpointerFactory.getCheckpointer().release(sessionId);
            Runner.release(sessionId);
        }
        Runner.stop();
        Runner.setConfig(RunnerConfig.DEFAULT);
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        workflowIds.clear();
        sessionIds.clear();
    }

    // ==================== Tests ====================

    @Test
    public void reservedMaxChatRoundsLimitsAgentContextMessages() {
        // Test 095 parity: reserved_max_chat_rounds=3 → agent context capped at 6 messages
        String sessionId = "agent_095";
        sessionIds.add(sessionId);

        Workflow flow = buildQuestionerWorkflow(
                "questioner095_workflow", "workflow_1.0", "my_questioner095",
                List.of(
                        field("location", "地点"),
                        field("date", "时间")));

        LlmAgent agent = LlmAgent.createLlmAgent(
                agentConfig("ctx-agent-095", 3,
                        List.of(workflowSchema("questioner095_workflow", "my_questioner095", "workflow_1.0"))),
                List.of(flow),
                List.of());

        // Round 1: initial query starts the workflow and reaches an interrupt.
        List<OutputSchema> firstRound = collectChunks(runStreaming(agent, "test_llm_agent_095", sessionId));
        assertFalse(firstRound.isEmpty(), "Initial round should emit stream output");

        // Round 2: resume from the actual interrupted workflow node and finish.
        List<OutputSchema> resumed = collectChunks(runStreamingResume(agent, "interactive", "确认", sessionId));
        assertFalse(resumed.isEmpty(), "Resume round should emit stream output");

        // Verify agent context message count is capped by reservedMaxChatRounds * 2 = 6
        var agentContext = agent.getContextEngine().getContext(null, sessionId);
        if (agentContext != null) {
            List<?> agentMessages = agentContext.getMessages(null, false);
            assertTrue(agentMessages.size() <= 6,
                    "Agent context messages should be capped at reservedMaxChatRounds*2=6, got " + agentMessages.size());
        }
    }

    @Test
    public void defaultConstrainDoesNotOverCapMessages() {
        // Test 093 parity: default reserved_max_chat_rounds=10 → agent context not over-capped
        String sessionId = "agent_093";
        sessionIds.add(sessionId);

        Workflow flow = buildQuestionerWorkflow(
                "questioner093_workflow", "1.0", "my_questioner093",
                List.of(field("location", "地点")));

        LlmAgent agent = LlmAgent.createLlmAgent(
                agentConfig("ctx-agent-093", 10,
                        List.of(workflowSchema("questioner093_workflow", "my_questioner093", "1.0"))),
                List.of(flow),
                List.of());

        collectChunks(runStreaming(agent, "test_llm_agent_093", sessionId));
        collectChunks(runStreamingResume(agent, "interactive", "确认", sessionId));

        // Verify agent context messages exist and are reasonable
        var agentContext = agent.getContextEngine().getContext(null, sessionId);
        if (agentContext != null) {
            List<?> agentMessages = agentContext.getMessages(null, false);
            assertTrue(agentMessages.size() >= 2,
                    "Agent context should have messages, got " + agentMessages.size());
            assertTrue(agentMessages.size() <= 20,
                    "With default max_rounds=10, buffer cap=20, got " + agentMessages.size());
        }
    }

    @Test
    public void contextEngineConfigDerivedFromConstrainConfig() {
        // Direct wiring verification: reservedMaxChatRounds = 3 → buffer caps at 6
        LlmAgentConfig config = LlmAgentConfig.builder()
                .id("wiring-test")
                .model(agentModelConfig())
                .constrain(ConstrainConfig.builder().reservedMaxChatRounds(3).build())
                .build();
        LlmAgent agent = new LlmAgent(config);

        // Create a context and add more than 6 messages
        var contextEngine = agent.getContextEngine();
        var context = contextEngine.createContext(null, null, null, null, null);

        for (int i = 0; i < 10; i++) {
            context.addMessages(List.of(new UserMessage("msg-" + i)));
        }

        List<? extends BaseMessage> messages = context.getMessages(null, false);
        assertEquals(6, messages.size(),
                "Buffer should cap at reservedMaxChatRounds*2=6");
    }

    @Test
    public void questionerModelFactoryCanInstantiateDirectly() throws Exception {
        Model model = new Model(questionerClientConfig(), questionerRequestConfig());
        AssistantMessage message = model.invoke(
                List.of(new UserMessage("杭州")),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        assertNotNull(message);
    }

    // ==================== Workflow Builder ====================

    private Workflow buildQuestionerWorkflow(String id, String version, String name, List<FieldInfo> fields) {
        WorkflowCard card = WorkflowCard.builder()
                .id(id)
                .version(version)
                .name(name)
                .description("questioner test workflow")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string", "description", "用户输入")),
                        "required", List.of("query")))
                .build();
        String workflowKey = id + "_" + version;
        workflowIds.add(workflowKey);

        QuestionerConfig questionerConfig = new QuestionerConfig();
        questionerConfig.setModelClientConfig(questionerClientConfig());
        questionerConfig.setModelConfig(questionerRequestConfig());
        questionerConfig.setExtractFieldsFromResponse(true);
        questionerConfig.setFieldNames(fields);
        questionerConfig.setWithChatHistory(false);
        questionerConfig.setMaxResponse(10);

        Workflow flow = new Workflow(card);
        flow.setStartComp("s", new Start(), Map.of("query", "${query}", "cmd", "${cmd}"), null);
        flow.addWorkflowComp("questioner", new QuestionerComponent(questionerConfig),
                Map.of("query", "${s.query}"), null);
        flow.addWorkflowComp("interactive", new InteractiveComponent(),
                Map.of("cmd", "${questioner}"), null);
        flow.setEndComp("e", new End(), Map.of("data", "${interactive}"), null);
        flow.addConnection("s", "questioner");
        flow.addConnection("questioner", "interactive");
        flow.addConnection("interactive", "e");
        return flow;
    }

    private static FieldInfo field(String name, String description) {
        return FieldInfo.builder()
                .fieldName(name)
                .description(description)
                .required(true)
                .build();
    }

    private static WorkflowSchema workflowSchema(String id, String name, String version) {
        return WorkflowSchema.builder()
                .id(id)
                .name(name)
                .version(version)
                .build();
    }

    // ==================== Agent Config ====================

    private LlmAgentConfig agentConfig(String agentId, int maxChatRounds, List<WorkflowSchema> workflows) {
        return LlmAgentConfig.builder()
                .id(agentId)
                .version("0.0.1")
                .description("context regression agent")
                .model(agentModelConfig())
                .promptTemplate(List.of(Map.of("role", "system", "content", "你是一个AI助手")))
                .constrain(ConstrainConfig.builder()
                        .reservedMaxChatRounds(maxChatRounds)
                        .maxIteration(10)
                        .build())
                .workflows(workflows)
                .build();
    }

    private static ModelConfig agentModelConfig() {
        return new ModelConfig(
                AGENT_PROVIDER,
                BaseModelInfo.builder()
                        .apiKey("fake-agent-key")
                        .apiBase("mirror://ctx-agent")
                        .modelName("ctx-agent-model")
                        .temperature(0.1)
                        .topP(0.9)
                        .timeout(30)
                        .build());
    }

    private static ModelClientConfig questionerClientConfig() {
        return ModelClientConfig.builder()
                .clientId("ctx-questioner-client")
                .clientProvider(QUESTIONER_PROVIDER)
                .apiKey("fake-questioner-key")
                .apiBase("mirror://ctx-questioner")
                .verifySsl(false)
                .build();
    }

    private static ModelRequestConfig questionerRequestConfig() {
        return ModelRequestConfig.builder()
                .modelName("ctx-questioner-model")
                .temperature(0.1)
                .topP(0.9)
                .build();
    }

    // ==================== Runner Helpers ====================

    private static Iterator<Object> runStreaming(LlmAgent agent, String query, String conversationId) {
        return Runner.runAgentStreaming(
                agent,
                Map.of("query", query, "conversation_id", conversationId),
                null, null,
                List.of(StreamMode.OUTPUT));
    }

    private static Iterator<Object> runStreamingResume(LlmAgent agent, String nodeId, String value,
                                                       String conversationId) {
        InteractiveInput input = new InteractiveInput();
        input.update(nodeId, value);
        return Runner.runAgentStreaming(
                agent,
                Map.of("query", input, "conversation_id", conversationId),
                null, null,
                List.of(StreamMode.OUTPUT));
    }

    @SuppressWarnings("unchecked")
    private static List<OutputSchema> collectChunks(Iterator<Object> iterator) {
        List<OutputSchema> result = new ArrayList<>();
        iterator.forEachRemaining(item -> {
            if (item instanceof OutputSchema os) {
                result.add(os);
            }
        });
        return result;
    }

    // ==================== Interactive Component ====================

    /**
     * Mimics Python's InteractiveNode: calls session.interact(cmd) and returns
     * {@code {"confirm_result": userResponse}}.
     */
    static class InteractiveComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object cmd = inputs;
            if (inputs instanceof Map<?, ?> map) {
                if (map.containsKey("cmd")) {
                    cmd = map.get("cmd");
                }
            }
            Object result = session.<Object>interact(cmd);
            return Map.of("confirm_result", result);
        }
    }

    // ==================== Model Client Factories ====================

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new AgentModelFactory());
            Model.registerFactory(new QuestionerModelFactory());
        }
    }

    private static final class AgentModelFactory implements Model.ModelClientFactory {
        @Override
        public String providerName() {
            return AGENT_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new AgentModelClient(modelConfig, clientConfig);
        }
    }

    private static final class QuestionerModelFactory implements Model.ModelClientFactory {
        @Override
        public String providerName() {
            return QUESTIONER_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new QuestionerModelClient(modelConfig, clientConfig);
        }
    }

    // ==================== Agent Model Client ====================

    /**
     * Fake model for the agent's planning/answering:
     * - When tools present and no tool message → return ToolCall for the first workflow
     * - When tool message present → return final answer
     * - Otherwise → return default answer
     */
    private static final class AgentModelClient extends BaseModelClient {

        AgentModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                       String model, Integer maxTokens, String stop,
                                       BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            List<MsgView> views = toViews(messages);
            boolean hasToolMessage = views.stream().anyMatch(v -> "tool".equals(v.role));
            String latestUserContent = views.stream()
                .filter(v -> "user".equals(v.role))
                .reduce((left, right) -> right)
                .map(MsgView::content)
                .orElse("");

            if (tools instanceof List<?> toolList && !toolList.isEmpty() && !hasToolMessage) {
            String workflowName = resolveWorkflowName(latestUserContent);
                return AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call_" + UUID.randomUUID())
                                .name(workflowName)
                                .arguments("{\"query\":\"用户查询\"}")
                                .build()))
                        .finishReason("tool_calls")
                        .build();
            }

            if (hasToolMessage) {
                return new AssistantMessage("西湖景点开放时间在早上8点");
            }

            return new AssistantMessage("默认回答");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        private static String resolveWorkflowName(String latestUserContent) {
            if (latestUserContent.contains("095")) {
                return "my_questioner095";
            }
            if (latestUserContent.contains("093")) {
                return "my_questioner093";
            }
            return "my_questioner095";
        }
    }

    // ==================== Questioner Model Client ====================

    /**
     * Fake model for questioner extraction prompts.
     * Returns JSON with extracted fields based on keywords in dialogue history.
     */
    private static final class QuestionerModelClient extends BaseModelClient {

        QuestionerModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                       String model, Integer maxTokens, String stop,
                                       BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            String dialogue = extractDialogue(messages);
            Map<String, String> fields = new LinkedHashMap<>();

            // Extract fields based on keywords in the user's dialogue
            if (dialogue.contains("地") || dialogue.contains("location")) {
                fields.put("location", "杭州");
            }
            if (dialogue.contains("时") || dialogue.contains("日") || dialogue.contains("date")) {
                fields.put("date", "明天");
            }
            if (dialogue.contains("气温") || dialogue.contains("temperature") || dialogue.contains("climate")) {
                fields.put("climate", "34度");
            }
            if (dialogue.contains("天气") || dialogue.contains("weather")) {
                fields.put("weather", "晴天");
            }
            if (dialogue.contains("湿度") || dialogue.contains("humidity")) {
                fields.put("humidity", "60%湿度");
            }

            // Build JSON response
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (!first) json.append(", ");
                json.append("\"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
                first = false;
            }
            json.append("}");

            return new AssistantMessage(json.toString());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        private static String extractDialogue(Object messages) {
            StringBuilder sb = new StringBuilder();
            if (messages instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof BaseMessage msg) {
                        sb.append(msg.getContentAsString()).append("\n");
                    }
                }
            }
            return sb.toString();
        }
    }

    // ==================== Utility ====================

    private record MsgView(String role, String content) {
    }

    private static List<MsgView> toViews(Object messages) {
        List<MsgView> result = new ArrayList<>();
        if (messages instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof BaseMessage msg) {
                    result.add(new MsgView(msg.getRole(), msg.getContentAsString()));
                }
            }
        }
        return result;
    }
}
