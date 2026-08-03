/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
