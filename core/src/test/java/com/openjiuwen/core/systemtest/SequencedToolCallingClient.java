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
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic fake model client for system tests that must drive the
 * real execution chain without an LLM.
 *
 * <p>The client plays a scripted conversation per request conversation key
 * (the first user message content of the passed message list): the leading
 * {@code toolCallTurns} turns answer with tool calls, every later turn
 * answers with a final message that embeds the observed tool messages, so
 * tests can assert end to end that tool observations reached the model and
 * came back. Two construction shapes are supported:</p>
 *
 * <ul>
 *   <li>{@link #withFixedFirstTurn(List, String, String)} — fixed tool
 *       calls on the first turn (single-conversation shape, used by
 *       {@code ToolParallelExecutionSystemTest});</li>
 *   <li>{@link #withRequestDerivedTool(int, String, String, String)} —
 *       the called tool is derived per turn from the request's tool list,
 *       matched uniquely by name prefix (multi-instance shape, used by the
 *       the multi-instance execution system tests). An ambiguous or
 *       missing prefix match fails the model call, which doubles as the
 *       cross-instance tool leakage tripwire.</li>
 * </ul>
 *
 * <p>All observable counters and records are thread safe: one shared
 * instance may serve many agents concurrently. The {@code generate*}
 * methods are unsupported, mirroring the original fixture.</p>
 *
 * @since 0.1.16
 */
public final class SequencedToolCallingClient extends BaseModelClient {
    private static final String FINAL_ANSWER_PREFIX = "tool observations: ";

    private final AtomicInteger invokeCount = new AtomicInteger();

    private final List<String> firstCallToolNames = new ArrayList<>();

    private final List<String> observedToolCallIds = new ArrayList<>();

    private final List<String> observedToolMessages = new ArrayList<>();

    private final ConcurrentHashMap<String, AtomicInteger> turnsByConversation = new ConcurrentHashMap<>();

    private final List<ToolCall> fixedFirstTurnCalls;

    private final int toolCallTurns;

    private final String toolNamePrefix;

    private final String callIdPrefix;

    private final String finalPrefix;

    private SequencedToolCallingClient(Builder builder) {
        super(ModelRequestConfig.builder().modelName("fake-model").build(),
                ModelClientConfig.builder()
                        .clientId(builder.clientId)
                        .clientProvider(builder.provider)
                        .apiKey("test-key")
                        .apiBase("mirror://" + builder.provider)
                        .build());
        this.fixedFirstTurnCalls = builder.fixedFirstTurnCalls;
        this.toolCallTurns = builder.toolCallTurns;
        this.toolNamePrefix = builder.toolNamePrefix;
        this.callIdPrefix = builder.callIdPrefix;
        this.finalPrefix = builder.finalPrefix == null ? FINAL_ANSWER_PREFIX : builder.finalPrefix;
    }

    /**
     * Returns a fresh builder for a scripted client (the builder
     * replaces the former seven-parameter construction shape).
     *
     * @return new builder with neutral defaults
     * @since 0.1.16
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fixed-first-turn shape: the first model turn of every conversation
     * answers with the given tool calls, later turns answer with the final
     * message. Tool ids and names are fully caller controlled.
     *
     * @param firstTurnCalls tool calls emitted on the first turn
     * @param finalPrefix prefix of the final answer content
     * @param provider provider name used for model registration
     * @return scripted client
     * @since 0.1.16
     */
    public static SequencedToolCallingClient withFixedFirstTurn(List<ToolCall> firstTurnCalls, String finalPrefix,
            String provider) {
        return builder()
                .fixedFirstTurnCalls(List.copyOf(firstTurnCalls))
                .toolCallTurns(1)
                .finalPrefix(finalPrefix)
                .provider(provider)
                .clientId(provider + "-client")
                .build();
    }

    /**
     * Request-derived shape: each of the first {@code toolCallTurns} turns
     * calls the one request tool whose name starts with {@code toolNamePrefix}
     * (an ambiguous or missing match fails the call), later turns answer with
     * the final message.
     *
     * @param toolCallTurns number of leading tool-call turns per conversation
     * @param toolNamePrefix unique name prefix of the tool to call
     * @param callIdPrefix tool call id prefix, suffixed with the turn number
     * @param provider provider name used for model registration
     * @return scripted client
     * @since 0.1.16
     */
    public static SequencedToolCallingClient withRequestDerivedTool(int toolCallTurns, String toolNamePrefix,
            String callIdPrefix, String provider) {
        return builder()
                .toolCallTurns(toolCallTurns)
                .toolNamePrefix(toolNamePrefix)
                .callIdPrefix(callIdPrefix)
                .provider(provider)
                .clientId(provider + "-client")
                .build();
    }

    /**
     * Registers a model factory returning this client under its provider
     * name and wraps the result as a shareable {@link Model}.
     *
     * @return model bound to this client
     * @since 0.1.16
     */
    public Model registerSharedModel() {
        String provider = modelClientConfig.getClientProvider();
        SequencedToolCallingClient shared = this;
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return provider;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return shared;
            }
        });
        return new Model(modelClientConfig, ModelRequestConfig.builder().modelName("fake-model").build());
    }

    /**
     * Total model invocations served (invoke plus stream) across all
     * conversations.
     *
     * @return invocation count
     * @since 0.1.16
     */
    public int modelCallCount() {
        return invokeCount.get();
    }

    /**
     * Tool names visible to the first model call ever served.
     *
     * @return tool names of the first call
     * @since 0.1.16
     */
    public synchronized List<String> firstCallToolNames() {
        return List.copyOf(firstCallToolNames);
    }

    /**
     * Tool call ids observed by the latest final-answer turn.
     *
     * @return observed tool call ids
     * @since 0.1.16
     */
    public synchronized List<String> observedToolCallIds() {
        return List.copyOf(observedToolCallIds);
    }

    /**
     * Tool message contents observed by the latest final-answer turn.
     *
     * @return observed tool message contents
     * @since 0.1.16
     */
    public synchronized List<String> observedToolMessages() {
        return List.copyOf(observedToolMessages);
    }

    @Override
    public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
            String model, Integer maxTokens, String stop,
            BaseOutputParser outputParser, Float timeout,
            Map<String, Object> kwargs) {
        invokeCount.incrementAndGet();
        return scriptedAnswer(messages, tools);
    }

    @Override
    public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                                                  Float topP, String model, Integer maxTokens,
                                                  String stop, BaseOutputParser outputParser,
                                                  Float timeout, Map<String, Object> kwargs) {
        invokeCount.incrementAndGet();
        AssistantMessage answer = scriptedAnswer(messages, tools);
        AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                .content(answer.getContent())
                .toolCalls(answer.getToolCalls())
                .finishReason(answer.getFinishReason())
                .build();
        return List.of(chunk).iterator();
    }

    @Override
    public ImageGenerationResponse generateImage(List<UserMessage> messages, String model,
                                                  String size, String negativePrompt, int n,
                                                  boolean isPromptExtend, boolean isWatermark, int seed,
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
                                                  String resolution, int duration, boolean isPromptExtend,
                                                  boolean isWatermark, String negativePrompt, Integer seed,
                                                  Map<String, Object> kwargs) {
        throw new UnsupportedOperationException("video generation is not used in this system test");
    }

    private AssistantMessage scriptedAnswer(Object messages, Object tools) {
        int turn = turnsByConversation
                .computeIfAbsent(conversationKey(messages), key -> new AtomicInteger())
                .incrementAndGet();
        if (turn <= toolCallTurns) {
            return toolCallAnswer(tools, turn);
        }
        return finalAnswer(messages);
    }

    private AssistantMessage toolCallAnswer(Object tools, int turn) {
        if (fixedFirstTurnCalls != null) {
            recordFirstCallToolNames(tools);
            return AssistantMessage.builder()
                    .content("")
                    .toolCalls(fixedFirstTurnCalls)
                    .finishReason("tool_calls")
                    .build();
        }
        ToolCall call = ToolCall.builder()
                .id(callIdPrefix + turn)
                .name(uniquePrefixedTool(tools))
                .arguments("{}")
                .index(0)
                .build();
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(call))
                .finishReason("tool_calls")
                .build();
    }

    private AssistantMessage finalAnswer(Object messages) {
        List<String> callIds = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        if (messages instanceof List<?> messageList) {
            for (Object message : messageList) {
                if (message instanceof ToolMessage toolMessage) {
                    callIds.add(toolMessage.getToolCallId());
                    contents.add(String.valueOf(toolMessage.getContent()));
                }
            }
        }
        recordObserved(callIds, contents);
        return AssistantMessage.builder()
                .content(finalPrefix + String.join(" | ", contents) + " order=" + String.join(">", callIds))
                .finishReason("stop")
                .build();
    }

    private static String conversationKey(Object messages) {
        if (messages instanceof List<?> messageList) {
            for (Object message : messageList) {
                if (message instanceof UserMessage userMessage && userMessage.getContent() != null) {
                    return String.valueOf(userMessage.getContent());
                }
            }
        }
        return "anonymous-conversation";
    }

    private String uniquePrefixedTool(Object tools) {
        List<String> matches = new ArrayList<>();
        if (tools instanceof List<?> toolList) {
            for (Object tool : toolList) {
                if (tool instanceof ToolInfo toolInfo && toolInfo.getName().startsWith(toolNamePrefix)) {
                    matches.add(toolInfo.getName());
                }
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("expected exactly one tool with prefix " + toolNamePrefix
                    + " but saw " + matches);
        }
        return matches.get(0);
    }

    private synchronized void recordFirstCallToolNames(Object tools) {
        if (!firstCallToolNames.isEmpty()) {
            return;
        }
        if (tools instanceof List<?> toolList) {
            for (Object tool : toolList) {
                if (tool instanceof ToolInfo toolInfo) {
                    firstCallToolNames.add(toolInfo.getName());
                }
            }
        }
    }

    private synchronized void recordObserved(List<String> callIds, List<String> contents) {
        observedToolCallIds.clear();
        observedToolCallIds.addAll(callIds);
        observedToolMessages.clear();
        observedToolMessages.addAll(contents);
    }

    /**
     * Builder for the scripted client: one-parameter steps
     * replace the former seven-parameter constructor. Only the two public
     * factory shapes construct it.
     *
     * @since 0.1.16
     */
    private static final class Builder {
        private List<ToolCall> fixedFirstTurnCalls;
        private int toolCallTurns;
        private String toolNamePrefix;
        private String callIdPrefix;
        private String finalPrefix;
        private String provider;
        private String clientId;

        /**
         * Sets the fixed tool calls answered on every first turn.
         *
         * @param fixedFirstTurnCalls the tool calls answered on every first turn
         * @return this builder
         */
        Builder fixedFirstTurnCalls(List<ToolCall> fixedFirstTurnCalls) {
            this.fixedFirstTurnCalls = fixedFirstTurnCalls;
            return this;
        }

        /**
         * Sets the number of leading tool-call turns per conversation.
         *
         * @param toolCallTurns the number of leading tool-call turns per conversation
         * @return this builder
         */
        Builder toolCallTurns(int toolCallTurns) {
            this.toolCallTurns = toolCallTurns;
            return this;
        }

        /**
         * Sets the unique name prefix of the request-derived tool to call.
         *
         * @param toolNamePrefix the unique name prefix of the request-derived tool
         * @return this builder
         */
        Builder toolNamePrefix(String toolNamePrefix) {
            this.toolNamePrefix = toolNamePrefix;
            return this;
        }

        /**
         * Sets the tool call id prefix, suffixed with the turn number.
         *
         * @param callIdPrefix the tool call id prefix, suffixed with the turn number
         * @return this builder
         */
        Builder callIdPrefix(String callIdPrefix) {
            this.callIdPrefix = callIdPrefix;
            return this;
        }

        /**
         * Sets the final answer prefix (null keeps the default).
         *
         * @param finalPrefix the final answer prefix; null keeps the default
         * @return this builder
         */
        Builder finalPrefix(String finalPrefix) {
            this.finalPrefix = finalPrefix;
            return this;
        }

        /**
         * Sets the provider name used for model registration.
         *
         * @param provider the provider name used for model registration
         * @return this builder
         */
        Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * Sets the model client id.
         *
         * @param clientId the model client id
         * @return this builder
         */
        Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Builds the configured client.
         *
         * @return a new sequenced tool-calling client
         */
        SequencedToolCallingClient build() {
            return new SequencedToolCallingClient(this);
        }
    }
}
