/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentRoundCompressorTest {

    private static final String TEST_PROVIDER = "CurrentRoundCompressorTestProvider";

    @BeforeAll
    static void registerModelFactory() {
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return TEST_PROVIDER;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return new TestModelClient(modelConfig, clientConfig);
            }
        });
    }

    @Test
    void triggerUsesTokenThresholdAndMessagesToKeepGuard() {
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(configBuilder()
                .tokensThreshold(10)
                .messagesToKeep(3)
                .build());
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext(
                "test",
                null,
                null,
                List.of(new UserMessage("u1"), new AssistantMessage("a1")),
                tokenCounter(100));

        assertFalse(compressor.triggerAddMessages(context, List.of()));
        assertTrue(compressor.triggerAddMessages(context, List.of(new AssistantMessage("a2"))));
    }

    @Test
    void getCompressIdxReturnsLatestEligibleUserBeforeKeptTail() {
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(configBuilder()
                .messagesToKeep(1)
                .build());
        List<BaseMessage> messages = List.of(
                new UserMessage("u1"),
                new AssistantMessage("a1"),
                new UserMessage("u2"),
                new AssistantMessage("a2"));

        assertEquals(2, compressor.getCompressIdx(messages));
        assertEquals(-1, compressor.getCompressIdx(List.of(new UserMessage("tail user"))));
    }

    @Test
    void onAddMessagesReplacesSelectedCompletedRoundWithMemoryBlock() {
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(configBuilder()
                .tokensThreshold(1)
                .messagesToKeep(1)
                .minSelectedTokensForCompression(1)
                .build());
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext(
                "test",
                null,
                null,
                List.of(
                        new UserMessage("question"),
                        new AssistantMessage("safe-prefix-1"),
                        new AssistantMessage("safe-prefix-2"),
                        AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(toolCall("tc-1", "tool_a")))
                                .build(),
                        new ToolMessage("tool result", "tc-1"),
                        new AssistantMessage("final answer")),
                compressionBenefitTokenCounter());

        ContextProcessor.ProcessResult result = compressor.onAddMessages(context, List.of());

        assertNotNull(result.event());
        List<BaseMessage> updated = context.getMessages();
        assertTrue(updated.get(1).getContentAsString().startsWith(CurrentRoundCompressor.SUMMARY_MARKER));
        assertEquals("final answer", updated.get(2).getContentAsString());
        assertEquals(List.of(1, 2, 3, 4), result.event().getMessagesToModify());
    }

    @Test
    void compressSkipsWhenSelectedSpanBelowMinimumTokens() {
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(configBuilder()
                .minSelectedTokensForCompression(200)
                .build());
        ModelContext context = new ContextEngine(ContextEngineConfig.builder().build()).createContext(
                "test",
                null,
                null,
                List.of(),
                tokenCounter(1));

        BaseMessage compressed = compressor.compress(
                List.of(new AssistantMessage("small")),
                context,
                List.of(new UserMessage("u"), new AssistantMessage("small")),
                1,
                0);

        assertEquals(null, compressed);
    }

    @Test
    void iterSummaryMergeRangesReturnsContiguousSummaryBlocks() {
        List<BaseMessage> messages = List.of(
                new UserMessage(CurrentRoundCompressor.SUMMARY_MARKER + "\na"),
                new UserMessage(CurrentRoundCompressor.SUMMARY_MARKER + "\nb"),
                new AssistantMessage("break"),
                new UserMessage(CurrentRoundCompressor.SUMMARY_MARKER + "\nc"),
                new UserMessage(CurrentRoundCompressor.SUMMARY_MARKER + "\nd"),
                new UserMessage(CurrentRoundCompressor.SUMMARY_MARKER + "\ne"));

        List<int[]> ranges = CurrentRoundCompressor.iterSummaryMergeRanges(messages, 3);

        assertEquals(1, ranges.size());
        assertEquals(3, ranges.get(0)[0]);
        assertEquals(5, ranges.get(0)[1]);
    }

    @Test
    void configDefaultsMatchPythonCurrentConfig() {
        CurrentRoundCompressorConfig config = CurrentRoundCompressorConfig.builder().build();

        assertEquals(100000, config.getTokensThreshold());
        assertEquals(3, config.getMessagesToKeep());
        assertEquals(20000, config.getMinSelectedTokensForCompression());
        assertEquals(4000, config.getCompressionTargetTokens());
        assertEquals(4000, config.getSummaryMergeTargetTokens());
        assertEquals(20000, config.getAccumulatedSummaryTokenLimit());
        assertEquals(3, config.getSummaryMergeMinBlocks());
        assertEquals(10, config.getPriorContextWindowSize());
    }

    @Test
    void processorTypeAndStateAreStable() {
        CurrentRoundCompressor compressor = new CurrentRoundCompressor(configBuilder().build());

        assertEquals("CurrentRoundCompressor", compressor.processorType());
        assertTrue(compressor.saveState().isEmpty());
        compressor.loadState(java.util.Map.of());
    }

    private static ToolCall toolCall(String id, String name) {
        return ToolCall.builder().id(id).name(name).type("function").arguments("{}").build();
    }

    private static CurrentRoundCompressorConfig.CurrentRoundCompressorConfigBuilder configBuilder() {
        return CurrentRoundCompressorConfig.builder()
                .model(ModelRequestConfig.builder().modelName("test-model").build())
                .modelClient(ModelClientConfig.builder()
                        .clientProvider(TEST_PROVIDER)
                        .apiKey("test-key")
                        .apiBase("http://test.local")
                        .verifySsl(false)
                        .build());
    }

    private static TokenCounter tokenCounter(int returnValue) {
        return new TokenCounter() {
            @Override
            public int count(String text, String model) {
                return returnValue;
            }

            @Override
            public int countMessages(List<BaseMessage> messages, String model) {
                return returnValue;
            }

            @Override
            public int countTools(List<ToolInfo> tools, String model) {
                return 0;
            }
        };
    }

    private static TokenCounter compressionBenefitTokenCounter() {
        return new TokenCounter() {
            @Override
            public int count(String text, String model) {
                return text != null ? text.length() : 0;
            }

            @Override
            public int countMessages(List<BaseMessage> messages, String model) {
                if (messages.size() == 1 && messages.get(0) instanceof UserMessage
                        && (messages.get(0).getContentAsString().startsWith("role:")
                        || messages.get(0).getContentAsString().equals("compressed summary"))) {
                    return 1;
                }
                return 100;
            }

            @Override
            public int countTools(List<ToolInfo> tools, String model) {
                return 0;
            }
        };
    }

    private static final class TestModelClient extends BaseModelClient {
        private TestModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages,
                                       Object tools,
                                       Float temperature,
                                       Float topP,
                                       String model,
                                       Integer maxTokens,
                                       String stop,
                                       BaseOutputParser outputParser,
                                       Float timeout,
                                       Map<String, Object> kwargs) {
            return new AssistantMessage("compressed summary");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages,
                                                      Object tools,
                                                      Float temperature,
                                                      Float topP,
                                                      String model,
                                                      Integer maxTokens,
                                                      String stop,
                                                      BaseOutputParser outputParser,
                                                      Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                     String model,
                                                     String size,
                                                     String negativePrompt,
                                                     int n,
                                                     boolean promptExtend,
                                                     boolean watermark,
                                                     int seed,
                                                     Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                      String model,
                                                      String voice,
                                                      String languageType,
                                                      Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages,
                                                     String imgUrl,
                                                     String audioUrl,
                                                     String model,
                                                     String size,
                                                     String resolution,
                                                     int duration,
                                                     boolean promptExtend,
                                                     boolean watermark,
                                                     String negativePrompt,
                                                     Integer seed,
                                                     Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
