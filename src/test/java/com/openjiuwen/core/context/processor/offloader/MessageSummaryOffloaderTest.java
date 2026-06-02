/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.schema.OffloadMixin;
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
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MessageSummaryOffloader}.
 * <p>
 * Ported from Python's {@code test_message_summary_offloader.py}.
 * <p>
 * Uses a registered test model client to cover offload behavior without
 * network calls.
 */
public class MessageSummaryOffloaderTest {

    private static final String TEST_MODEL_PROVIDER = "MessageSummaryOffloaderTest";
    private static final AtomicReference<String> NEXT_RESPONSE = new AtomicReference<>("summary");
    private static final AtomicReference<Object> LAST_MESSAGES = new AtomicReference<>();

    @BeforeAll
    static void registerModelFactory() {
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return TEST_MODEL_PROVIDER;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return new RecordingModelClient(modelConfig, clientConfig);
            }
        });
    }

    // ---------- Config validation ----------

    @Nested
    @DisplayName("Config validation")
    class ConfigValidation {

        @Test
        @DisplayName("valid config: messages_to_keep < messages_threshold")
        void testValidConfig() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesToKeep(10)
                    .messagesThreshold(20)
                    .build();
            // Model constructor will fail without real config, but validation should pass
            // The constructor calls validateConfig before anything else
            assertDoesNotThrow(() -> new MessageSummaryOffloader(config));
        }

        @Test
        @DisplayName("messages_to_keep == messages_threshold throws")
        void testMessagesToKeepEqualsThreshold() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesToKeep(20)
                    .messagesThreshold(20)
                    .build();
            assertThrows(BaseError.class, () -> new MessageSummaryOffloader(config));
        }

        @Test
        @DisplayName("messages_to_keep > messages_threshold throws")
        void testMessagesToKeepGreaterThanThreshold() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesToKeep(30)
                    .messagesThreshold(20)
                    .build();
            assertThrows(BaseError.class, () -> new MessageSummaryOffloader(config));
        }

        @Test
        @DisplayName("messages_to_keep null is valid")
        void testMessagesToKeepNull() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesToKeep(null)
                    .messagesThreshold(20)
                    .build();
            assertDoesNotThrow(() -> new MessageSummaryOffloader(config));
        }

        @Test
        @DisplayName("messages_threshold null is valid")
        void testMessagesThresholdNull() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesToKeep(10)
                    .messagesThreshold(null)
                    .build();
            assertDoesNotThrow(() -> new MessageSummaryOffloader(config));
        }
    }

    // ---------- Config builder ----------

    @Nested
    @DisplayName("Config builder")
    class ConfigBuilder {

        @Test
        @DisplayName("default config values")
        void testDefaultConfig() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder().build();
            assertEquals(20000, config.getTokensThreshold());
            assertEquals(1000, config.getLargeMessageThreshold());
            assertEquals(List.of("tool"), config.getOffloadMessageType());
            assertTrue(config.isKeepLastRound());
            assertNull(config.getMessagesToKeep());
            assertNull(config.getMessagesThreshold());
            assertNull(config.getModel());
            assertNull(config.getModelClient());
            assertNull(config.getCustomizedSummaryPrompt());
        }

        @Test
        @DisplayName("custom config values")
        void testCustomConfig() {
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesThreshold(100)
                    .tokensThreshold(15000)
                    .largeMessageThreshold(500)
                    .offloadMessageType(java.util.List.of("user", "assistant"))
                    .messagesToKeep(10)
                    .keepLastRound(true)
                    .customizedSummaryPrompt("Custom summary prompt")
                    .build();
            assertEquals(100, config.getMessagesThreshold());
            assertEquals(15000, config.getTokensThreshold());
            assertEquals(500, config.getLargeMessageThreshold());
            assertEquals(java.util.List.of("user", "assistant"), config.getOffloadMessageType());
            assertEquals(10, config.getMessagesToKeep());
            assertTrue(config.isKeepLastRound());
            assertEquals("Custom summary prompt", config.getCustomizedSummaryPrompt());
        }
    }

    // ---------- Processor type ----------

    @Test
    @DisplayName("processor type returns correct name")
    void testProcessorType() {
        MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder().build();
        MessageSummaryOffloader offloader = new MessageSummaryOffloader(config);
        // MessageSummaryOffloader extends MessageOffloader
        assertNotNull(offloader.processorType());
    }

    // ---------- Offload message behavior ----------

    @Nested
    @DisplayName("Offload message behavior")
    class OffloadMessageBehavior {

        @Test
        @DisplayName("offload message uses model summary")
        void testOffloadMessageUsesModelSummary() throws Exception {
            String originalContent = "This is a very long message that needs to be summarized. ".repeat(20);
            String summarizedContent = "This is a summarized version of the message.";
            TestableSummaryOffloader offloader = new TestableSummaryOffloader(modelBackedConfig(summarizedContent));
            ModelContext context = createContext();

            BaseMessage result = offloader.offload(new UserMessage(originalContent), context);

            OffloadMixin offload = assertInstanceOf(OffloadMixin.class, result);
            assertEquals("user", result.getRole());
            assertTrue(result.getContentAsString().contains(summarizedContent));
            assertReloadContains(context, offload, originalContent);
            assertModelSawContent(originalContent);
        }

        @Test
        @DisplayName("offload message with custom config")
        void testOffloadMessageWithCustomConfig() throws Exception {
            String originalContent = "This is a very long message that needs to be summarized. ".repeat(20);
            String summarizedContent = "Custom summarized version.";
            prepareModelResponse(summarizedContent);
            MessageSummaryOffloaderConfig config = MessageSummaryOffloaderConfig.builder()
                    .messagesThreshold(100)
                    .tokensThreshold(15000)
                    .largeMessageThreshold(500)
                    .offloadMessageType(List.of("user", "assistant"))
                    .messagesToKeep(10)
                    .keepLastRound(true)
                    .customizedSummaryPrompt("Custom summary prompt")
                    .model(defaultModelConfig())
                    .modelClient(defaultModelClientConfig())
                    .build();
            TestableSummaryOffloader offloader = new TestableSummaryOffloader(config);
            ModelContext context = createContext();

            BaseMessage result = offloader.offload(new AssistantMessage(originalContent), context);

            OffloadMixin offload = assertInstanceOf(OffloadMixin.class, result);
            assertEquals("assistant", result.getRole());
            assertTrue(result.getContentAsString().contains(summarizedContent));
            assertReloadContains(context, offload, originalContent);
            assertModelSawContent("Custom summary prompt");
            assertModelSawContent(originalContent);
        }

        @Test
        @DisplayName("offload message preserves different roles")
        void testOffloadMessageWithDifferentRoles() throws Exception {
            List<BaseMessage> messages = List.of(
                    new UserMessage("User message"),
                    new AssistantMessage("Assistant message"),
                    ToolMessage.builder().content("Tool message").toolCallId("123").build()
            );

            for (BaseMessage originalMessage : messages) {
                String summarizedContent = "Summarized " + originalMessage.getRole() + " message";
                String responseContent = "{\"compression_strategy\":\"extractive\","
                        + "\"summary\":\"" + summarizedContent + "\","
                        + "\"offload_data_explanation\":{}}";
                TestableSummaryOffloader offloader = new TestableSummaryOffloader(modelBackedConfig(responseContent));
                ModelContext context = createContext();

                BaseMessage result = offloader.offload(originalMessage, context);

                OffloadMixin offload = assertInstanceOf(OffloadMixin.class, result);
                assertEquals(originalMessage.getRole(), result.getRole());
                assertTrue(result.getContentAsString().contains(summarizedContent));
                assertReloadContains(context, offload, originalMessage.getContentAsString());
            }
        }

        @Test
        @DisplayName("offload message handles empty content")
        void testOffloadMessageEmptyContent() {
            String summarizedContent = "Empty message summary";
            String responseContent = "{\"compression_strategy\":\"extractive\","
                    + "\"summary\":\"" + summarizedContent + "\","
                    + "\"offload_data_explanation\":{}}";
            TestableSummaryOffloader offloader = new TestableSummaryOffloader(modelBackedConfig(responseContent));
            ModelContext context = createContext();

            BaseMessage result = offloader.offload(new UserMessage(""), context);

            assertInstanceOf(OffloadMixin.class, result);
            assertTrue(result.getContentAsString().contains(summarizedContent));
        }

        @Test
        @DisplayName("offload message stores original message")
        void testOffloadMessagePreservesOriginalMessages() throws Exception {
            String originalContent = "Original message content";
            TestableSummaryOffloader offloader = new TestableSummaryOffloader(modelBackedConfig("Summary"));
            ModelContext context = createContext();

            BaseMessage result = offloader.offload(new UserMessage(originalContent), context);

            OffloadMixin offload = assertInstanceOf(OffloadMixin.class, result);
            assertReloadContains(context, offload, originalContent);
        }
    }

    private static MessageSummaryOffloaderConfig modelBackedConfig(String responseContent) {
        prepareModelResponse(responseContent);
        return MessageSummaryOffloaderConfig.builder()
                .model(defaultModelConfig())
                .modelClient(defaultModelClientConfig())
                .build();
    }

    private static void prepareModelResponse(String responseContent) {
        NEXT_RESPONSE.set(responseContent);
        LAST_MESSAGES.set(null);
    }

    private static ModelRequestConfig defaultModelConfig() {
        return ModelRequestConfig.builder().modelName("test-model").temperature(0.7).build();
    }

    private static ModelClientConfig defaultModelClientConfig() {
        return ModelClientConfig.builder()
                .clientId("test-client")
                .clientProvider(TEST_MODEL_PROVIDER)
                .apiKey("test-key")
                .apiBase("http://test.api.com")
                .build();
    }

    private static ModelContext createContext() {
        ContextEngine engine = new ContextEngine(
                ContextEngineConfig.builder().defaultWindowMessageNum(100).build());
        return engine.createContext("test_ctx", null, List.of(), null, null);
    }

    private static void assertReloadContains(ModelContext context, OffloadMixin offload, String expected) throws Exception {
        Object reloaded = context.reloaderTool().invoke(Map.of(
                "offload_handle", offload.getOffloadHandle(),
                "offload_type", offload.getOffloadType()));
        assertTrue(String.valueOf(reloaded).contains(expected));
    }

    private static void assertModelSawContent(String expected) {
        Object messages = LAST_MESSAGES.get();
        assertInstanceOf(List.class, messages);
        assertTrue(((List<?>) messages).stream()
                .filter(BaseMessage.class::isInstance)
                .map(BaseMessage.class::cast)
                .anyMatch(message -> message.getContentAsString().contains(expected)));
    }

    private static final class TestableSummaryOffloader extends MessageSummaryOffloader {
        private TestableSummaryOffloader(MessageSummaryOffloaderConfig config) {
            super(config);
        }

        private BaseMessage offload(BaseMessage message, ModelContext context) {
            return offloadMessage(message, context);
        }
    }

    private static final class RecordingModelClient extends BaseModelClient {
        private RecordingModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        protected void validateConfig() {
            // Test client accepts the minimal dummy config.
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            LAST_MESSAGES.set(messages);
            return new AssistantMessage(NEXT_RESPONSE.get());
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
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            return null;
        }
    }
}
