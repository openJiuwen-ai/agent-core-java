/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openjiuwen.core.application.llm.LlmAgent;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
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
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LlmAgentExecutionRegressionTest {

    private static final String TEST_PROVIDER = "ApplicationRegressionMirror";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    private final Set<String> toolIds = ConcurrentHashMap.newKeySet();
    private final Set<String> workflowIds = ConcurrentHashMap.newKeySet();
    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

    public LlmAgentExecutionRegressionTest() {
        ensureFactoryRegistered();
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);
    }

    @AfterEach
    public void cleanup() {
        for (String toolId : toolIds) {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
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
        toolIds.clear();
        workflowIds.clear();
        sessionIds.clear();
    }

    @Test
    void llmAgentExecutesRegisteredLocalFunctionEndToEnd() {
        Tool addTool = createAddTool("tool-exec");
        LlmAgent agent = LlmAgent.createLlmAgent(
                baseConfig("tool-exec-agent", "你是一个计算助手"),
                List.of(),
                List.of(addTool));
        sessionIds.add("tool-exec-session");

        Map<String, Object> result = collectFinalPayload(Runner.runAgentStreaming(
                agent,
                Map.of("query", "请帮我计算 10 + 20", "conversation_id", "tool-exec-session"),
                null,
                null,
            List.of(StreamMode.OUTPUT)));

        assertEquals("30", result.get("output"));
        assertEquals("answer", result.get("result_type"));
    }

    @Test
    public void concurrentAgentsKeepToolInventoriesSeparated() throws ExecutionException, InterruptedException {
        Logger controllerLogger = (Logger) LoggerFactory.getLogger("controller");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previousLevel = controllerLogger.getLevel();
        controllerLogger.setLevel(Level.INFO);
        controllerLogger.addAppender(appender);
        try {
            Tool addTool = createAddTool("dual-agent");
            Workflow helperWorkflow = createHelperWorkflow("dual-agent");

            LlmAgent firstAgent = LlmAgent.createLlmAgent(
                    baseConfig("llm_agent_1", "你是AI助手，简单聊天"),
                    List.of(helperWorkflow),
                    List.of(addTool));
            LlmAgent secondAgent = new LlmAgent(baseConfig("llm_agent_2", "你是AI助手，简单聊天"));

            sessionIds.add("agent_001_1");
            sessionIds.add("agent_001");

                CompletableFuture<Map<String, Object>> first = CompletableFuture.supplyAsync(() -> collectFinalPayload(
                    Runner.runAgentStreaming(
                    firstAgent,
                    Map.of("query", "请帮我做一个加法计算", "conversation_id", "agent_001_1"),
                    null,
                    null,
                    List.of(StreamMode.OUTPUT))));
                CompletableFuture<Map<String, Object>> second = CompletableFuture.supplyAsync(() -> collectFinalPayload(
                    Runner.runAgentStreaming(
                    secondAgent,
                    Map.of("query", "请问世界上最高的山", "conversation_id", "agent_001"),
                    null,
                    null,
                    List.of(StreamMode.OUTPUT))));

            Map<String, Object> firstResult = first.get();
            Map<String, Object> secondResult = second.get();

            assertEquals("30", firstResult.get("output"));
            assertEquals("珠穆朗玛峰", secondResult.get("output"));

            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList());
            assertTrue(messages.stream().anyMatch(msg -> msg.contains("Loaded 2 Tool(s) for generating plans")));
            assertTrue(messages.stream().anyMatch(msg -> msg.contains("Loaded 0 Tool(s) for generating plans")));
        } finally {
            controllerLogger.detachAppender(appender);
            controllerLogger.setLevel(previousLevel);
        }
    }

    private LlmAgentConfig baseConfig(String agentId, String prompt) {
        return LlmAgentConfig.builder()
                .id(agentId)
                .version("0.0.1")
                .description("regression-agent")
                .model(testModelConfig())
                .promptTemplate(List.of(Map.of("role", "system", "content", prompt)))
                .build();
    }

    private ModelConfig testModelConfig() {
        return new ModelConfig(
                TEST_PROVIDER,
                BaseModelInfo.builder()
                        .apiKey("regression-key")
                        .apiBase("mirror://application-regression")
                        .modelName("application-regression-model")
                        .temperature(0.1)
                        .topP(0.9)
                        .timeout(30)
                        .build());
    }

    private Tool createAddTool(String prefix) {
        ToolCard card = ToolCard.builder()
                .id(prefix + "-add-id")
                .name("_add_2025")
                .description("加法")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "a", Map.of("type", "number", "description", "加数"),
                                "b", Map.of("type", "number", "description", "被加数")),
                        "required", List.of("a", "b")))
                .build();
        toolIds.add(card.getId());
        return new LocalFunction(card, inputs -> {
            Number a = (Number) inputs.get("a");
            Number b = (Number) inputs.get("b");
            return String.valueOf(a.intValue() + b.intValue());
        });
    }

    private Workflow createHelperWorkflow(String prefix) {
        WorkflowCard card = WorkflowCard.builder()
                .id(prefix + "-workflow-id")
                .name("questioner_workflow")
                .version("0.0.1")
                .description("helper workflow")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query")))
                .build();
        workflowIds.add(card.getId());
        Workflow workflow = new Workflow(card);
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.setEndComp("end", new End(), Map.of("output", "${start.query}"), null);
        workflow.addConnection("start", "end");
        return workflow;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> collectFinalPayload(Iterator<Object> iterator) {
        List<Object> chunks = new ArrayList<>();
        iterator.forEachRemaining(chunks::add);
        for (int index = chunks.size() - 1; index >= 0; index--) {
            Object item = chunks.get(index);
            if (item instanceof OutputSchema outputSchema && outputSchema.getPayload() instanceof Map<?, ?> payload) {
                return (Map<String, Object>) payload;
            }
        }
        throw new IllegalStateException("No terminal output payload found: " + chunks);
    }

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new TestModelFactory());
        }
    }

    private static final class TestModelFactory implements Model.ModelClientFactory {

        @Override
        public String providerName() {
            return TEST_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new TestModelClient(modelConfig, clientConfig);
        }
    }

    private static final class TestModelClient extends BaseModelClient {

        private TestModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser,
                                       Float timeout, Map<String, Object> kwargs) {
            List<MessageView> messageViews = toMessageViews(messages);
            boolean hasToolMessage = messageViews.stream().anyMatch(message -> "tool".equals(message.role));

            if (tools instanceof List<?> toolInfos && !toolInfos.isEmpty() && !hasToolMessage) {
                return AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call_" + UUID.randomUUID())
                                .name("_add_2025")
                                .arguments("{\"a\":10,\"b\":20}")
                                .build()))
                        .finishReason("tool_calls")
                        .build();
            }

            if (hasToolMessage) {
                String toolResult = messageViews.stream()
                        .filter(message -> "tool".equals(message.role))
                        .reduce((left, right) -> right)
                        .map(message -> message.content)
                        .orElse("30");
                return new AssistantMessage(toolResult);
            }

            String userContent = messageViews.stream()
                    .filter(message -> "user".equals(message.role))
                    .reduce((left, right) -> right)
                    .map(message -> message.content)
                    .orElse("");
            if (userContent.contains("最高的山")) {
                return new AssistantMessage("珠穆朗玛峰");
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

        private static List<MessageView> toMessageViews(Object messages) {
            List<MessageView> result = new ArrayList<>();
            if (messages instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof BaseMessage message) {
                        result.add(new MessageView(message.getRole(), message.getContentAsString()));
                    }
                }
            }
            return result;
        }
    }

    private record MessageView(String role, String content) {
    }
}
