/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.systemtest;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for ReAct tool execution through the agent runtime.
 */
@Tag("system-test")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ToolParallelExecutionSystemTest extends SystemTestSupport {

    private static final String TEST_PROVIDER = "tool-parallel-system-test-provider";
    private static final String SEAT_TOOL = "st_open_seat_massage";
    private static final String AC_TOOL = "st_open_air_conditioner";

    @Test
    @DisplayName("ReActAgent executes multiple tool calls in parallel and preserves observations")
    void testReActAgentExecutesMultipleToolCallsInParallel() {
        CountDownLatch bothToolsStarted = new CountDownLatch(2);
        SequencedToolCallingClient modelClient = new SequencedToolCallingClient();
        ReActAgent agent = newAgent(modelClient);
        LocalFunction seatTool = carControlTool(SEAT_TOOL, "seat_massage_on", bothToolsStarted);
        LocalFunction acTool = carControlTool(AC_TOOL, "air_conditioner_on", bothToolsStarted);

        Runner.resourceMgr().addTool(seatTool, null);
        Runner.resourceMgr().addTool(acTool, null);
        agent.getAbilityManager().add(List.of(seatTool.getCard(), acTool.getCard()));

        try {
            String sessionId = trackSessionId("parallel-tools-session");
            InvocationCapture capture = invokeAgent(
                    agent,
                    Map.of(
                            "query", "帮我打开座椅按摩，帮我打开空调",
                            "conversation_id", sessionId
                    ),
                    sessionId
            );

            assertThat(modelClient.invokeCount).isEqualTo(2);
            assertThat(modelClient.firstCallToolNames).contains(SEAT_TOOL, AC_TOOL);
            assertThat(modelClient.observedToolCallIds).containsExactly("tc-seat", "tc-ac");
            assertThat(modelClient.observedToolMessages)
                    .allMatch(content -> content.contains("parallel=true"))
                    .allMatch(content -> content.contains("session=" + sessionId));
            assertThat(capture.flattenedText())
                    .contains("seat_massage_on:parallel=true")
                    .contains("air_conditioner_on:parallel=true")
                    .contains("tc-seat>tc-ac");
        } finally {
            removeTool(SEAT_TOOL);
            removeTool(AC_TOOL);
        }
    }

    private static ReActAgent newAgent(SequencedToolCallingClient modelClient) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id("parallel-tools-agent")
                .name("parallel-tools-agent")
                .description("system test agent for parallel tool calls")
                .build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(3)
                .promptTemplate(List.of(Map.of(
                        "role", "system",
                        "content", "You are a deterministic vehicle control assistant."
                )))
                .build());
        agent.setLlm(newModel(modelClient));
        return agent;
    }

    private static Model newModel(SequencedToolCallingClient modelClient) {
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return TEST_PROVIDER;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return modelClient;
            }
        });
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientId("tool-parallel-system-test")
                .clientProvider(TEST_PROVIDER)
                .apiKey("test-key")
                .apiBase("mirror://tool-parallel-system-test")
                .build();
        return new Model(clientConfig, ModelRequestConfig.builder().modelName("fake-model").build());
    }

    private static LocalFunction carControlTool(String toolName, String resultPrefix, CountDownLatch bothToolsStarted) {
        return new LocalFunction(
                ToolCard.builder()
                        .id(toolName)
                        .name(toolName)
                        .description("System-test vehicle control tool")
                        .inputParams(Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        ))
                        .build(),
                (LocalFunction.ContextFunction) (inputs, kwargs) -> {
                    bothToolsStarted.countDown();
                    boolean parallel = await(bothToolsStarted, 2, TimeUnit.SECONDS);
                    Session currentSession = SessionContextHolder.getCurrentSession();
                    String sessionId = currentSession != null ? currentSession.getSessionId() : "missing";
                    return resultPrefix + ":parallel=" + parallel + ":session=" + sessionId;
                }
        );
    }

    private static boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void removeTool(String toolName) {
        Runner.resourceMgr().removeTool(toolName, null, TagMatchStrategy.ALL, true);
    }

    private static final class SequencedToolCallingClient extends BaseModelClient {
        private int invokeCount;
        private List<String> firstCallToolNames = List.of();
        private final List<String> observedToolCallIds = new ArrayList<>();
        private final List<String> observedToolMessages = new ArrayList<>();

        private SequencedToolCallingClient() {
            super(ModelRequestConfig.builder().modelName("fake-model").build(),
                    ModelClientConfig.builder()
                            .clientId("tool-parallel-system-test")
                            .clientProvider(TEST_PROVIDER)
                            .apiKey("test-key")
                            .apiBase("mirror://tool-parallel-system-test")
                            .build());
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                       String model, Integer maxTokens, String stop,
                                       BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            invokeCount++;
            if (invokeCount == 1) {
                firstCallToolNames = extractToolNames(tools);
                return AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(
                                ToolCall.builder()
                                        .id("tc-seat")
                                        .name(SEAT_TOOL)
                                        .arguments("{}")
                                        .index(0)
                                        .build(),
                                ToolCall.builder()
                                        .id("tc-ac")
                                        .name(AC_TOOL)
                                        .arguments("{}")
                                        .index(1)
                                        .build()
                        ))
                        .finishReason("tool_calls")
                        .build();
            }

            collectToolMessages(messages);
            return AssistantMessage.builder()
                    .content("tool observations: " + String.join(" | ", observedToolMessages)
                            + " order=" + String.join(">", observedToolCallIds))
                    .finishReason("stop")
                    .build();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                                                      Float topP, String model, Integer maxTokens,
                                                      String stop, BaseOutputParser outputParser,
                                                      Float timeout, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("stream is not used in this system test");
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model,
                                                     String size, String negativePrompt, int n,
                                                     boolean promptExtend, boolean watermark, int seed,
                                                     Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("image generation is not used in this system test");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model,
                                                      String voice, String languageType,
                                                      Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("speech generation is not used in this system test");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl,
                                                     String audioUrl, String model, String size,
                                                     String resolution, int duration, boolean promptExtend,
                                                     boolean watermark, String negativePrompt, Integer seed,
                                                     Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("video generation is not used in this system test");
        }

        private static List<String> extractToolNames(Object tools) {
            if (!(tools instanceof List<?> toolList)) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (Object tool : toolList) {
                if (tool instanceof ToolInfo toolInfo) {
                    names.add(toolInfo.getName());
                }
            }
            return names;
        }

        private void collectToolMessages(Object messages) {
            observedToolCallIds.clear();
            observedToolMessages.clear();
            if (!(messages instanceof List<?> messageList)) {
                return;
            }
            for (Object message : messageList) {
                if (message instanceof ToolMessage toolMessage) {
                    observedToolCallIds.add(toolMessage.getToolCallId());
                    observedToolMessages.add(String.valueOf(toolMessage.getContent()));
                } else if (message instanceof BaseMessage) {
                    // Other message types are irrelevant for this assertion.
                }
            }
        }
    }
}
