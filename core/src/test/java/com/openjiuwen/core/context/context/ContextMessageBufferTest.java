/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Focused parity tests for context message buffering.
 *
 * <p>Mirrors Python's {@code ContextMessageBuffer} in
 * {@code openjiuwen/core/context_engine/context/message_buffer.py}.</p>
 */
class ContextMessageBufferTest {

    @Test
    void getBackSeparatesHistoryFromCurrentMessages() {
        BaseMessage history = new BaseMessage("user", "history");
        BaseMessage current = new BaseMessage("assistant", "current");
        ContextMessageBuffer buffer = new ContextMessageBuffer(List.of(history), null);

        buffer.addBack(current);

        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.getBack(null, true)).containsExactly(history, current);
        assertThat(buffer.getBack(null, false)).containsExactly(current);
        assertThat(buffer.getBack(1, true)).containsExactly(current);
        assertThat(buffer.getBack(1, false)).containsExactly(current);
    }

    @Test
    void popBackUpdatesHistorySizeWhenHistoryIsPopped() {
        BaseMessage h1 = new BaseMessage("user", "h1");
        BaseMessage h2 = new BaseMessage("assistant", "h2");
        BaseMessage current = new BaseMessage("user", "current");
        ContextMessageBuffer buffer = new ContextMessageBuffer(List.of(h1, h2), null);
        buffer.addBack(current);

        List<BaseMessage> popped = buffer.popBack(3, true);

        assertThat(popped).containsExactly(h1, h2, current);
        assertThat(buffer.getBack(null, true)).isEmpty();
        assertThat(buffer.getBack(null, false)).isEmpty();
    }

    @Test
    void setMessagesCanReplaceOnlyNonHistoryMessages() {
        BaseMessage history = new BaseMessage("user", "history");
        ContextMessageBuffer buffer = new ContextMessageBuffer(List.of(history), null);

        buffer.setMessages(List.of(new BaseMessage("assistant", "new")), false);

        assertThat(buffer.getBack(null, true)).extracting(BaseMessage::getContent)
                .containsExactly("history", "new");
        assertThat(buffer.getBack(null, false)).extracting(BaseMessage::getContent)
                .containsExactly("new");
    }

    @Test
    void maxBufferSizeLimitsVisibleMessagesAndResizesWhenDoubleLimitExceeded() {
        ContextMessageBuffer buffer = new ContextMessageBuffer(List.of(
                new BaseMessage("user", "h1"),
                new BaseMessage("assistant", "h2"),
                new BaseMessage("user", "h3")
        ), 3);

        buffer.addBack(List.of(
                new BaseMessage("assistant", "c1"),
                new BaseMessage("user", "c2"),
                new BaseMessage("assistant", "c3"),
                new BaseMessage("user", "c4")
        ));

        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.getBack(null, true)).extracting(BaseMessage::getContent)
                .containsExactly("c2", "c3", "c4");
        assertThat(buffer.getBack(null, false)).extracting(BaseMessage::getContent)
                .containsExactly("c2", "c3", "c4");
    }

    @Test
    void rebuildKeepsPythonMisspelledAlias() {
        ContextMessageBuffer buffer = new ContextMessageBuffer(List.of(), null);

        buffer.rebulid(List.of(new BaseMessage("user", "rebuilt")));

        assertThat(buffer.getBack()).extracting(BaseMessage::getContent).containsExactly("rebuilt");
    }

    @Test
    void maxBufferSizeDoesNotSplitToolCallGroupsOnVisibleTail() {
        // issue #200 / Python #2000: naive last-N leaves orphan ToolMessages.
        List<BaseMessage> history = issue200History();
        ContextMessageBuffer buffer = new ContextMessageBuffer(history, 4);
        buffer.addBack(new UserMessage("按专家意见继续"));

        List<BaseMessage> stored = buffer.getBack(null, true);
        assertThat(stored).isNotEmpty();
        assertThat(stored.get(0)).isNotInstanceOf(ToolMessage.class);
        assertThat(show(stored)).doesNotStartWith("[tool:");
        assertThat(stored.size()).isLessThanOrEqualTo(4);
        // Either keep the complete search tool group or drop it entirely — never orphan tools.
        boolean hasSearchAssistant = stored.stream().anyMatch(ContextMessageBufferTest::isSearchAssistant);
        boolean hasSearchTool = stored.stream().anyMatch(message ->
                message instanceof ToolMessage tool
                        && ("c2".equals(tool.getToolCallId()) || "c3".equals(tool.getToolCallId())));
        assertThat(hasSearchTool).isEqualTo(hasSearchAssistant);
    }

    @Test
    void getBackWithExplicitSizeDoesNotStartOnOrphanToolMessages() {
        // Secondary count cut (e.g. IntentRecognizer getMessages(50)) must keep the same
        // tool-group contract as maxBufferSize tail — never lead with orphan ToolMessages.
        ContextMessageBuffer buffer = new ContextMessageBuffer(issue200History(), null);
        buffer.addBack(new UserMessage("按专家意见继续"));

        List<BaseMessage> sliced = buffer.getBack(4, true);

        assertThat(sliced).isNotEmpty();
        assertThat(sliced.get(0)).isNotInstanceOf(ToolMessage.class);
        assertThat(sliced.size()).isLessThanOrEqualTo(4);
        boolean hasSearchAssistant = sliced.stream().anyMatch(ContextMessageBufferTest::isSearchAssistant);
        boolean hasSearchTool = sliced.stream().anyMatch(message ->
                message instanceof ToolMessage tool
                        && ("c2".equals(tool.getToolCallId()) || "c3".equals(tool.getToolCallId())));
        assertThat(hasSearchTool).isEqualTo(hasSearchAssistant);
    }

    @Test
    void popBackWithExplicitSizeRemovesNaiveWindowAndReturnsAlignedSlice() {
        ContextMessageBuffer buffer = new ContextMessageBuffer(issue200History(), null);
        buffer.addBack(new UserMessage("按专家意见继续"));
        // Naive last-4: tool:c2, tool:c3, assistant:已整理, user — aligned drops the two tools.

        List<BaseMessage> popped = buffer.popBack(4, true);

        assertThat(popped).isNotEmpty();
        assertThat(popped.get(0)).isNotInstanceOf(ToolMessage.class);
        assertThat(popped.size()).isLessThanOrEqualTo(4);
        assertThat(buffer.getBack(null, true)).noneMatch(message ->
                message instanceof ToolMessage tool
                        && ("c2".equals(tool.getToolCallId()) || "c3".equals(tool.getToolCallId())));
        assertThat(buffer.getBack(null, true)).extracting(BaseMessage::getContentAsString)
                .doesNotContain("已整理", "按专家意见继续");
    }

    @Test
    void alignedTailDropsLeadingOrphanToolsWithoutExceedingLimit() {
        List<BaseMessage> messages = new ArrayList<>();
        messages.add(call(tc("c2", "search"), tc("c3", "search")));
        messages.add(new ToolMessage("检索结果2", "c2"));
        messages.add(new ToolMessage("检索结果3", "c3"));
        messages.add(new AssistantMessage("已整理"));
        messages.add(new UserMessage("继续"));

        List<BaseMessage> aligned = ContextUtils.alignedTail(messages, 3);

        assertThat(aligned).extracting(BaseMessage::getContentAsString)
                .containsExactly("已整理", "继续");
        assertThat(aligned).noneMatch(ToolMessage.class::isInstance);
        assertThat(aligned.size()).isLessThanOrEqualTo(3);
    }

    @Test
    void alignedTailKeepsCompleteToolGroupWhenItFits() {
        List<BaseMessage> messages = List.of(
                new UserMessage("q"),
                call(tc("c2", "search"), tc("c3", "search")),
                new ToolMessage("r2", "c2"),
                new ToolMessage("r3", "c3"),
                new AssistantMessage("done"));

        List<BaseMessage> aligned = ContextUtils.alignedTail(messages, 4);

        assertThat(show(aligned)).isEqualTo("[assistant:c2,c3, tool:c2, tool:c3, assistant:done]");
    }

    @Test
    void alignedTailLeavesPlainDialogueUnchanged() {
        List<BaseMessage> messages = List.of(
                new UserMessage("u1"),
                new AssistantMessage("a1"),
                new UserMessage("u2"),
                new AssistantMessage("a2"));

        assertThat(ContextUtils.alignedTail(messages, 2))
                .extracting(BaseMessage::getContentAsString)
                .containsExactly("u2", "a2");
    }

    @Test
    void sessionModelContextMaxMessageNumKeepsToolGroupsInStoredState() {
        com.openjiuwen.core.context.schema.ContextEngineConfig config =
                new com.openjiuwen.core.context.schema.ContextEngineConfig();
        config.setMaxContextMessageNum(4);
        SessionModelContext ctx = new SessionModelContext("c", "s", config, issue200History(), List.of(), null);
        ctx.addMessages(List.of(new UserMessage("按专家意见继续"))).toCompletableFuture().join();

        List<BaseMessage> stored = ctx.getMessages(null, true);
        assertThat(stored.get(0)).isNotInstanceOf(ToolMessage.class);
        assertThat(stored.stream().anyMatch(message ->
                message instanceof ToolMessage tool && "c1".equals(tool.getToolCallId()))).isFalse();
        List<BaseMessage> window = ctx.getContextWindow(List.of(), List.of(), null, null, java.util.Map.of())
                .toCompletableFuture().join().getContextMessages();
        assertThat(window).isNotEmpty();
        assertThat(window.get(0)).isNotInstanceOf(ToolMessage.class);
    }

    private static List<BaseMessage> issue200History() {
        return new ArrayList<>(List.of(
                new UserMessage("帮我确定方案"),
                call(tc("c1", "ask_user")),
                new ToolMessage("专家答复：采用方案B", "c1"),
                call(tc("c2", "search"), tc("c3", "search")),
                new ToolMessage("检索结果2", "c2"),
                new ToolMessage("检索结果3", "c3"),
                new AssistantMessage("已整理")));
    }

    private static boolean isSearchAssistant(BaseMessage message) {
        if (!(message instanceof AssistantMessage assistant) || assistant.getToolCalls() == null) {
            return false;
        }
        return assistant.getToolCalls().stream().anyMatch(call -> "c2".equals(call.getId()) || "c3".equals(call.getId()));
    }

    private static ToolCall tc(String id, String name) {
        return ToolCall.builder().id(id).name(name).arguments("{}").build();
    }

    private static AssistantMessage call(ToolCall... calls) {
        return AssistantMessage.builder().content("").toolCalls(List.of(calls)).build();
    }

    private static String show(List<BaseMessage> msgs) {
        return msgs.stream().map(message -> {
            if (message instanceof ToolMessage tool) {
                return "tool:" + tool.getToolCallId();
            }
            if (message instanceof AssistantMessage assistant
                    && assistant.getToolCalls() != null
                    && !assistant.getToolCalls().isEmpty()) {
                return "assistant:" + assistant.getToolCalls().stream()
                        .map(ToolCall::getId)
                        .collect(Collectors.joining(","));
            }
            String content = String.valueOf(message.getContent());
            return message.getRole() + ":" + content.substring(0, Math.min(6, content.length()));
        }).collect(Collectors.joining(", ", "[", "]"));
    }
}
