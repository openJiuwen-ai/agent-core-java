/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.harness;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System tests for inner-loop steer injection.
 *
 * <p>Mirrors Python's {@code test_steer_inner_loop.py} in
 * {@code tests.system_tests.harness.test_steer_inner_loop}.</p>
 */
@DisplayName("SteerInnerLoop tests")
@Tag("system-test")
public class TestSteerInnerLoop {

    @Test
    @DisplayName("External steering during tool execution is visible in the same invoke")
    @Tag("level0")
    void testSteerVisibleInSameInvoke() throws Exception {
        Runner.start();
        BlockingTool blockingTool = new BlockingTool("blocking_tool");
        Runner.resourceMgr().addTool(blockingTool, null);
        try {
            String steerText = "Please answer with concise bullet points.";
            QueueModel model = new QueueModel(
                    toolResponse("tc_1", "blocking_tool", "{}"),
                    textResponse("first step done")
            );
            DeepAgent agent = buildAgent(model, blockingTool.getCard());
            Session session = new SimpleSession("steer_inner_" + UUID.randomUUID().toString().replace("-", ""));

            CompletableFuture<Object> invokeTask = CompletableFuture.supplyAsync(() ->
                    agent.invoke(Map.of("query", "Execute a two-step plan"), session)
            );

            assertTrue(blockingTool.awaitEntered(), "blocking_tool should be running before steering is injected.");
            agent.steer(steerText, session);
            blockingTool.release();

            Object result = invokeTask.get(10, TimeUnit.SECONDS);
            Map<?, ?> resultMap = assertInstanceOf(Map.class, result);
            assertEquals("answer", resultMap.get("result_type"));
            assertEquals(1, blockingTool.callCount());

            List<List<String>> messageCalls = model.messageCalls();
            assertEquals(2, messageCalls.size(), "model should be called once before and once after the tool.");
            assertFalse(containsSteering(messageCalls.get(0), steerText));
            assertTrue(
                    containsSteering(messageCalls.get(1), steerText),
                    "steering injected while the tool is running must be visible on the next model call."
            );
        } finally {
            Runner.resourceMgr().removeTool(blockingTool.getCard().getId(), null, TagMatchStrategy.ALL, true);
            Runner.stop();
        }
    }

    private static DeepAgent buildAgent(Model model, ToolCard toolCard) {
        AgentCard card = AgentCard.builder()
                .id("steer-agent")
                .name("steer-agent")
                .description("Steer inner-loop test agent")
                .build();
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setModel(model);
        config.setTools(List.of(toolCard));
        config.setSystemPrompt("You are a deterministic test assistant.");
        config.setMaxIterations(4);
        DeepAgent agent = new DeepAgent(card);
        agent.configure(config);
        return agent;
    }

    private static boolean containsSteering(List<String> messages, String steerText) {
        return messages.stream().anyMatch(message ->
                message.contains("[STEERING]") && message.contains(steerText)
        );
    }

    private static AssistantMessage textResponse(String content) {
        return AssistantMessage.builder().content(content).build();
    }

    private static AssistantMessage toolResponse(String id, String name, String arguments) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id(id)
                        .type("function")
                        .name(name)
                        .arguments(arguments)
                        .build()))
                .build();
    }

    private static final class QueueModel extends Model {
        private final Queue<AssistantMessage> responses = new ArrayDeque<>();
        private final List<List<String>> messageCalls = Collections.synchronizedList(new ArrayList<>());

        QueueModel(AssistantMessage... responses) {
            super(
                    ModelClientConfig.builder()
                            .clientProvider("OpenAI")
                            .apiKey("sk-test")
                            .apiBase("https://mock.openai.local/v1")
                            .verifySsl(false)
                            .build(),
                    ModelRequestConfig.builder().modelName("mock-model").build()
            );
            this.responses.addAll(List.of(responses));
        }

        List<List<String>> messageCalls() {
            return messageCalls;
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            messageCalls.add(extractContents(messages));
            return responses.isEmpty() ? textResponse("default response") : responses.remove();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        private static List<String> extractContents(Object messages) {
            if (!(messages instanceof List<?> rawMessages)) {
                return List.of(String.valueOf(messages));
            }
            List<String> contents = new ArrayList<>();
            for (Object message : rawMessages) {
                if (message instanceof BaseMessage baseMessage) {
                    contents.add(baseMessage.getContentAsString());
                } else {
                    contents.add(String.valueOf(message));
                }
            }
            return contents;
        }
    }

    private static final class BlockingTool extends Tool {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger callCount = new AtomicInteger();

        BlockingTool(String name) {
            super(ToolCard.builder()
                    .id(name)
                    .name(name)
                    .description("Blocks until the test releases it")
                    .inputParams(Map.of("type", "object", "properties", Map.of()))
                    .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            callCount.incrementAndGet();
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("blocking tool was not released");
            }
            return "tool done";
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.<Object>of().iterator();
        }

        boolean awaitEntered() throws InterruptedException {
            return entered.await(5, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }

        int callCount() {
            return callCount.get();
        }
    }

    private static final class SimpleSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

        SimpleSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> newState) {
            state.putAll(newState);
        }
    }
}
