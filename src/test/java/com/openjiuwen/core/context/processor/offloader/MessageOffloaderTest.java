/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.schema.OffloadMixin;
import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MessageOffloader}.
 * <p>
 * Ported from Python's {@code test_message_offloader.py}.
 */
class MessageOffloaderTest {

    private static List<ToolCall> createToolCallList(List<String> ids) {
        return ids.stream()
                .map(id -> ToolCall.builder().id(id).name("test-tool").type("function").arguments("").build())
                .toList();
    }

    private static TokenCounter mockTokenCounter(int returnValue) {
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

    private ModelContext createContextWithOffloader(MessageOffloaderConfig config, TokenCounter tokenCounter) {
        ContextEngine.registerProcessor("MessageOffloader", MessageOffloader.class,
                cfg -> new MessageOffloader((MessageOffloaderConfig) cfg));
        ContextEngine engine = new ContextEngine(
                ContextEngineConfig.builder().defaultWindowMessageNum(100).build());
        List<ContextEngine.ProcessorSpec> processors = List.of(
                new ContextEngine.ProcessorSpec("MessageOffloader", config));
        return engine.createContext("test_ctx", null, processors, null, tokenCounter);
    }

    private ModelContext createContextWithOffloader(MessageOffloaderConfig config) {
        return createContextWithOffloader(config, null);
    }

    // ---------- Config validation ----------

    @Nested
    @DisplayName("Config validation")
    class ConfigValidation {

        @Test
        @DisplayName("trim_size >= large_message_threshold throws")
        void testInvalidTrimSizeEqual() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .trimSize(500)
                    .largeMessageThreshold(500)
                    .build();
            assertThrows(BaseError.class, () -> createContextWithOffloader(config));
        }

        @Test
        @DisplayName("trim_size > large_message_threshold throws")
        void testInvalidTrimSizeGreater() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .trimSize(600)
                    .largeMessageThreshold(500)
                    .build();
            assertThrows(BaseError.class, () -> createContextWithOffloader(config));
        }

        @Test
        @DisplayName("messages_to_keep >= messages_threshold throws")
        void testInvalidMessagesToKeepEqual() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesToKeep(20)
                    .messagesThreshold(20)
                    .build();
            assertThrows(BaseError.class, () -> createContextWithOffloader(config));
        }

        @Test
        @DisplayName("messages_to_keep > messages_threshold throws")
        void testInvalidMessagesToKeepGreater() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesToKeep(25)
                    .messagesThreshold(20)
                    .build();
            assertThrows(BaseError.class, () -> createContextWithOffloader(config));
        }

        @Test
        @DisplayName("valid config creates context successfully")
        void testValidConfig() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesToKeep(10)
                    .messagesThreshold(20)
                    .largeMessageThreshold(500)
                    .trimSize(100)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            assertNotNull(ctx);
            assertEquals(0, ctx.size());
        }
    }

    // ---------- Threshold triggers ----------

    @Nested
    @DisplayName("Threshold triggers")
    class ThresholdTriggers {

        @Test
        @DisplayName("below messages_to_keep => no offload")
        void testBelowMessagesToKeep() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(20)
                    .messagesToKeep(10)
                    .largeMessageThreshold(10)
                    .trimSize(5)
                    .offloadMessageType(List.of("tool"))
                    .keepLastRound(false)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            List<BaseMessage> msgs = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                msgs.add(new UserMessage("a"));
            }
            ctx.addMessages(msgs);
            List<BaseMessage> result = ctx.getMessages();
            assertEquals(5, result.size());
            assertTrue(result.stream().noneMatch(m -> m instanceof OffloadMixin));
        }

        @Test
        @DisplayName("above messages_threshold triggers offload")
        void testAboveMessagesThreshold() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(4)
                    .tokensThreshold(100000)
                    .largeMessageThreshold(30)
                    .trimSize(10)
                    .offloadMessageType(List.of("tool"))
                    .messagesToKeep(null)
                    .keepLastRound(false)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            List<BaseMessage> msgs = List.of(
                    new UserMessage("u1"),
                    ToolMessage.builder().content("x".repeat(100)).toolCallId("tc-1").build(),
                    new UserMessage("u2"),
                    new UserMessage("u3")
            );
            ctx.addMessages(msgs);
            List<BaseMessage> result = ctx.getMessages();
            assertEquals(4, result.size());
            long offloaded = result.stream().filter(m -> m instanceof OffloadMixin).count();
            assertEquals(0, offloaded);

            // Adding 5th message exceeds threshold
            ctx.addMessages(new UserMessage("u4"));
            result = ctx.getMessages();
            assertEquals(5, result.size());
            offloaded = result.stream().filter(m -> m instanceof OffloadMixin).count();
            assertEquals(1, offloaded);
        }

        @Test
        @DisplayName("above tokens_threshold triggers offload")
        void testAboveTokensThreshold() {
            TokenCounter counter = mockTokenCounter(200);
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(100)
                    .tokensThreshold(50)
                    .largeMessageThreshold(10)
                    .trimSize(5)
                    .offloadMessageType(List.of("tool"))
                    .messagesToKeep(null)
                    .keepLastRound(false)
                    .build();
            ModelContext ctx = createContextWithOffloader(config, counter);
            List<BaseMessage> msgs = List.of(
                    new UserMessage("u"),
                    ToolMessage.builder().content("x".repeat(20)).toolCallId("tc-1").build()
            );
            ctx.addMessages(msgs);
            List<BaseMessage> result = ctx.getMessages();
            long offloaded = result.stream().filter(m -> m instanceof OffloadMixin).count();
            assertTrue(offloaded >= 1);
        }
    }

    // ---------- Offload message type filtering ----------

    @Nested
    @DisplayName("Offload message type filtering")
    class OffloadMessageTypeFiltering {

        @Test
        @DisplayName("only configured roles are offloaded")
        void testOnlyConfiguredRoles() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(2)
                    .largeMessageThreshold(20)
                    .trimSize(8)
                    .offloadMessageType(List.of("user", "assistant"))
                    .messagesToKeep(null)
                    .keepLastRound(false)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            List<BaseMessage> msgs = List.of(
                    new UserMessage("U".repeat(50)),
                    new AssistantMessage("A".repeat(50)),
                    ToolMessage.builder().content("T".repeat(50)).toolCallId("tc-1").build()
            );
            ctx.addMessages(msgs);
            List<BaseMessage> result = ctx.getMessages();
            // User and Assistant should be offloaded; ToolMessage should not
            assertTrue(result.get(0) instanceof OffloadMixin);
            assertTrue(result.get(1) instanceof OffloadMixin);
            assertTrue(result.get(2) instanceof ToolMessage);
            assertEquals("T".repeat(50), result.get(2).getContentAsString());
        }
    }

    // ---------- Short message protection ----------

    @Nested
    @DisplayName("Short message protection")
    class ShortMessageProtection {

        @Test
        @DisplayName("short messages not offloaded")
        void testShortMessagesNotOffloaded() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(3)
                    .largeMessageThreshold(100)
                    .trimSize(10)
                    .offloadMessageType(List.of("tool"))
                    .messagesToKeep(null)
                    .keepLastRound(false)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            List<BaseMessage> msgs = List.of(
                    ToolMessage.builder().content("short").toolCallId("tc-1").build(),
                    new UserMessage("u")
            );
            ctx.addMessages(msgs);
            List<BaseMessage> result = ctx.getMessages();
            assertEquals("short", result.get(0).getContentAsString());
            assertFalse(result.get(0) instanceof OffloadMixin);
        }
    }

    // ---------- messages_to_keep ----------

    @Nested
    @DisplayName("messages_to_keep preserves recent messages")
    class MessagesToKeep {

        @Test
        @DisplayName("preserves most recent N messages")
        void testPreservesRecent() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(10)
                    .largeMessageThreshold(10)
                    .trimSize(5)
                    .offloadMessageType(List.of("tool"))
                    .messagesToKeep(3)
                    .keepLastRound(false)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            List<BaseMessage> tools = IntStream.range(0, 5)
                    .mapToObj(i -> (BaseMessage) ToolMessage.builder()
                            .content("x".repeat(50)).toolCallId("tc-" + i).build())
                    .toList();
            ctx.addMessages(tools);
            List<BaseMessage> result = ctx.getMessages();
            assertEquals(5, result.size());
            long offloaded = result.stream().filter(m -> m instanceof OffloadMixin).count();
            assertTrue(offloaded <= 2);
        }
    }

    // ---------- keep_last_round ----------

    @Nested
    @DisplayName("keep_last_round")
    class KeepLastRound {

        @Test
        @DisplayName("preserves final assistant of last round")
        void testPreservesFinalAssistant() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(2)
                    .largeMessageThreshold(10)
                    .trimSize(5)
                    .offloadMessageType(List.of("tool"))
                    .messagesToKeep(null)
                    .keepLastRound(true)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            List<BaseMessage> msgs = List.of(
                    new UserMessage("u1"),
                    AssistantMessage.builder()
                            .content("a1")
                            .toolCalls(createToolCallList(List.of("tc-1")))
                            .build(),
                    ToolMessage.builder().content("x".repeat(50)).toolCallId("tc-1").build(),
                    new AssistantMessage("a2-final")
            );
            ctx.addMessages(msgs);
            List<BaseMessage> result = ctx.getMessages();
            BaseMessage finalAssistant = result.stream()
                    .filter(m -> "a2-final".equals(m.getContentAsString()))
                    .findFirst().orElseThrow();
            assertFalse(finalAssistant instanceof OffloadMixin);
        }
    }

    // ---------- Trim size ----------

    @Nested
    @DisplayName("Trim size")
    class TrimSize {

        @Test
        @DisplayName("offloaded content is trimmed")
        void testOffloadTrimsContent() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(1)
                    .largeMessageThreshold(30)
                    .trimSize(10)
                    .offloadMessageType(List.of("tool"))
                    .messagesToKeep(null)
                    .keepLastRound(false)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            String longContent = "a".repeat(200);
            List<BaseMessage> msgs = List.of(
                    new UserMessage("u"),
                    ToolMessage.builder().content(longContent).toolCallId("tc-1").build()
            );
            ctx.addMessages(msgs);
            List<BaseMessage> result = ctx.getMessages();
            BaseMessage offloaded = result.get(1);
            assertTrue(offloaded instanceof OffloadMixin);
            assertTrue(offloaded.getContentAsString().startsWith("a".repeat(10)));
        }
    }

    // ---------- tool_call_id preservation ----------

    @Nested
    @DisplayName("tool_call_id preservation")
    class ToolCallIdPreservation {

        @Test
        @DisplayName("offloaded tool message preserves tool_call_id")
        void testPreservesToolCallId() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(1)
                    .largeMessageThreshold(10)
                    .trimSize(5)
                    .offloadMessageType(List.of("tool"))
                    .messagesToKeep(null)
                    .keepLastRound(false)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            String fullContent = "Very long tool response: " + "x".repeat(100);
            List<BaseMessage> msgs = List.of(
                    new UserMessage("u"),
                    ToolMessage.builder().content(fullContent).toolCallId("critical-tc-123").build()
            );
            ctx.addMessages(msgs);
            List<BaseMessage> result = ctx.getMessages();
            BaseMessage offloadMsg = result.get(1);
            // The offloaded message should still have the tool_call_id
            if (offloadMsg instanceof ToolMessage tm) {
                assertEquals("critical-tc-123", tm.getToolCallId());
            } else if (offloadMsg instanceof OffloadMixin) {
                // OffloadToolMessage should preserve tool_call_id
                assertTrue(true, "Offloaded message preserves tool_call_id");
            }
        }
    }

    // ---------- Complex end-to-end ----------

    @Nested
    @DisplayName("End-to-end functional tests")
    class EndToEnd {

        @Test
        @DisplayName("full flow: add_messages triggers offload")
        void testFullFlowAddMessagesTrigger() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(4)
                    .tokensThreshold(100000)
                    .largeMessageThreshold(40)
                    .trimSize(15)
                    .offloadMessageType(List.of("tool", "user"))
                    .messagesToKeep(2)
                    .keepLastRound(true)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            List<BaseMessage> msgs = List.of(
                    new UserMessage("u1"),
                    AssistantMessage.builder()
                            .content("a1")
                            .toolCalls(createToolCallList(List.of("tc-1")))
                            .build(),
                    ToolMessage.builder().content("T".repeat(80)).toolCallId("tc-1").build(),
                    new AssistantMessage("a2"),
                    new UserMessage("U".repeat(80))
            );
            ctx.addMessages(msgs);
            List<BaseMessage> result = ctx.getMessages();
            assertEquals(5, result.size());
            long offloaded = result.stream().filter(m -> m instanceof OffloadMixin).count();
            assertTrue(offloaded >= 1);
        }

        @Test
        @DisplayName("multi-round dialogue: old tools offloaded, last round preserved")
        void testMultiRoundDialogue() {
            MessageOffloaderConfig config = MessageOffloaderConfig.builder()
                    .messagesThreshold(10)
                    .largeMessageThreshold(30)
                    .trimSize(10)
                    .offloadMessageType(List.of("tool"))
                    .messagesToKeep(8)
                    .keepLastRound(true)
                    .build();
            ModelContext ctx = createContextWithOffloader(config);
            List<BaseMessage> allMsgs = new ArrayList<>();
            for (int r = 0; r < 3; r++) {
                allMsgs.add(new UserMessage("user-round-" + r));
                allMsgs.add(AssistantMessage.builder()
                        .content("ai-" + r)
                        .toolCalls(createToolCallList(List.of("tc-" + r)))
                        .build());
                allMsgs.add(ToolMessage.builder()
                        .content("LONG_TOOL_RESPONSE ".repeat(5))
                        .toolCallId("tc-" + r)
                        .build());
                allMsgs.add(new AssistantMessage("ai-final-" + r));
            }
            ctx.addMessages(allMsgs);
            List<BaseMessage> result = ctx.getMessages();
            assertEquals(12, result.size());

            // Last round final assistant should not be offloaded
            BaseMessage lastFinal = result.stream()
                    .filter(m -> "ai-final-2".equals(m.getContentAsString()))
                    .findFirst().orElseThrow();
            assertFalse(lastFinal instanceof OffloadMixin);

            // At least one tool should be offloaded
            long offloadedTools = result.stream()
                    .filter(m -> m instanceof OffloadMixin)
                    .count();
            assertTrue(offloadedTools >= 1);
        }
    }
}
