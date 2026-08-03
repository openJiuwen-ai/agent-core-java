/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for context utility helpers.
 *
 * <p>Mirrors Python's {@code ContextUtils} in
 * {@code openjiuwen/core/context_engine/context/context_utils.py}.</p>
 */
class ContextUtilsTest {

    @Test
    void parseOpenrouterModelsAddsOnlyUnambiguousAliases() {
        List<Map<String, Object>> models = List.of(
                Map.of("id", "openai/shared", "context_length", 100),
                Map.of("id", "other/shared", "context_length", 200),
                Map.of("id", "anthropic/unique", "context_length", 300),
                Map.of("id", "plain", "context_length", 400),
                Map.of("id", "bad", "context_length", -1)
        );

        Map<String, Integer> tokens = ContextUtils.parseOpenrouterModelContextWindowTokens(models);

        assertThat(tokens).containsEntry("openai/shared", 100)
                .containsEntry("other/shared", 200)
                .containsEntry("anthropic/unique", 300)
                .containsEntry("unique", 300)
                .containsEntry("plain", 400);
        assertThat(tokens).doesNotContainKey("shared");
        assertThat(ContextUtils.parseOpenrouterModel(Map.of("context_length", 10))).isEmpty();
    }

    @Test
    void buildAndResolveModelContextWindowTokensFollowPythonPriority() {
        Map<String, Integer> resolved = ContextUtils.buildModelContextWindowTokens(
                Map.of("custom", 1234), false, 3.0d);

        assertThat(resolved).containsEntry("custom", 1234);
        assertThat(ContextUtils.resolveContextMax("custom", 99, resolved)).isEqualTo(99);
        assertThat(ContextUtils.resolveContextMax("custom", null, resolved)).isEqualTo(1234);
        assertThat(ContextUtils.resolveContextMax("gpt-5.4", null, resolved)).isEqualTo(1050000);
        assertThat(ContextUtils.resolveContextMax("unknown", null, resolved))
                .isEqualTo(ContextUtils.DEFAULT_CONTEXT_MAX_TOKENS);
    }

    @Test
    void validateAndEnsureMessagesMatchBaseMessageContract() {
        BaseMessage message = new BaseMessage("user", "hello");

        ContextUtils.validateMessages(message);
        ContextUtils.validateMessages(List.of(message));
        ContextUtils.ensureContextMessageIds(List.of(message));

        assertThat(message.getMetadata()).containsKey(ContextUtils.CONTEXT_MESSAGE_ID_KEY);
        assertThatThrownBy(() -> ContextUtils.validateMessages(List.of("bad")))
                .hasMessageContaining("messages should be a BaseMessage");
        assertThatThrownBy(() -> ContextUtils.validateMessages("bad"))
                .hasMessageContaining("messages should be a BaseMessage");
    }

    @Test
    void validateAndFixContextWindowDropsLeadingToolMessages() {
        ToolMessage tool = new ToolMessage("tool result", "call-1");
        BaseMessage user = new BaseMessage("user", "question");
        ContextWindow mixedWindow = new ContextWindow(List.of(), List.of(tool, user), List.of(), null);
        ContextWindow onlyToolsWindow = new ContextWindow(List.of(), List.of(tool), List.of(), null);

        ContextUtils.validateAndFixContextWindow(mixedWindow);
        ContextUtils.validateAndFixContextWindow(onlyToolsWindow);

        assertThat(mixedWindow.getContextMessages()).containsExactly(user);
        assertThat(onlyToolsWindow.getContextMessages()).isEmpty();
    }

    @Test
    void findLastAiMessageUsesPythonSingularToolCallCheck() {
        AssistantMessage assistantWithToolCalls = AssistantMessage.builder()
                .role("assistant")
                .content("tool call")
                .toolCalls(List.of(ToolCall.builder().id("call-1").name("search").arguments("{}").build()))
                .build();
        BaseMessage assistantWithSingularToolCall = new BaseMessage("assistant", "singular");
        assistantWithSingularToolCall.setMetadata(Map.of("tool_call", Map.of("id", "call-2")));

        Optional<Integer> index = ContextUtils.findLastAiMessageWithoutToolCall(List.of(
                new BaseMessage("user", "q"),
                assistantWithToolCalls,
                assistantWithSingularToolCall
        ));

        assertThat(index).contains(1);
    }

    @Test
    void replaceMessagesAndDialogueRoundsMirrorPythonBoundaries() {
        List<BaseMessage> messages = List.of(
                new BaseMessage("user", "q1a"),
                new BaseMessage("user", "q1b"),
                new AssistantMessage("a1"),
                new BaseMessage("user", "q2"),
                new AssistantMessage("a2"),
                new BaseMessage("user", "q3")
        );

        List<BaseMessage> replaced = ContextUtils.replaceMessages(messages, List.of(new BaseMessage("user", "x")),
                1, 3);
        List<ContextUtils.DialogueRound> rounds = ContextUtils.findAllDialogueRound(messages);

        assertThat(replaced).extracting(BaseMessage::getContent).containsExactly("q1a", "x", "a2", "q3");
        assertThat(rounds).containsExactly(
                new ContextUtils.DialogueRound(5, null),
                new ContextUtils.DialogueRound(3, 4),
                new ContextUtils.DialogueRound(0, 2)
        );
        assertThat(ContextUtils.findLastNDialogueRound(messages, 2)).isEqualTo(3);
        assertThatThrownBy(() -> ContextUtils.replaceMessages(messages, List.of(), 4, 3))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void toolCallHelpersResolveIdsAndNamesFromMessagesAndMaps() {
        ToolCall call = ToolCall.builder().id("call-1").name("search").arguments("{}").build();
        AssistantMessage assistant = AssistantMessage.builder()
                .role("assistant")
                .content("calling")
                .toolCalls(List.of(call))
                .build();
        ToolMessage toolMessage = new ToolMessage("result", "call-1");

        assertThat(ContextUtils.toolCallMatchesId(Map.of("id", "call-2"), "call-2")).isTrue();
        assertThat(ContextUtils.extractToolName(Map.of("function", Map.of("name", "lookup")))).contains("lookup");
        assertThat(ContextUtils.resolveToolCallFromMessage(toolMessage, List.of(assistant))).contains(call);
        assertThat(ContextUtils.resolveToolNameFromMessage(toolMessage, List.of(assistant))).contains("search");
        assertThat(ContextUtils.resolveToolNameFromMessage(new BaseMessage("user", "q"), List.of(assistant)))
                .isEmpty();
    }

    @Test
    void compressionProcessorDetectionAndTokenEstimationFollowHeuristics() {
        assertThat(ContextUtils.isCompressionProcessor(new CompactProcessor())).isTrue();
        assertThat(ContextUtils.isCompressionProcessor(new PlainProcessor())).isFalse();
        assertThat(ContextUtils.estimateTokens("abcdef")).isEqualTo(2);
        assertThat(ContextUtils.estimateTokens("")).isEqualTo(1);
        assertThat(ContextUtils.estimateMessageTokens(new BaseMessage("user", "abcdefghi"))).isEqualTo(3);
    }

    /**
     * Compression processor test double.
     *
     * <p>Mirrors Python's processor object in
     * {@code openjiuwen/core/context_engine/context/context_utils.py}.</p>
     */
    private static final class CompactProcessor {
        public String processorType() {
            return "compact";
        }
    }

    /**
     * Non-compression processor test double.
     *
     * <p>Mirrors Python's processor object in
     * {@code openjiuwen/core/context_engine/context/context_utils.py}.</p>
     */
    private static final class PlainProcessor {
        public String processorType() {
            return "validator";
        }
    }
}
