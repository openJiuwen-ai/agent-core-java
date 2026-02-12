/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.contextengine.context;

import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ContextMessageBuffer}.
 * 
 * <p>Converted from Python: test_message_buffer.py</p>
 */
class ContextMessageBufferTest {

    /**
     * Test history boundary behavior with the withHistory flag.
     * 
     * <p>Python: test_message_buffer_history_boundary_and_with_history_flag</p>
     * <p>Assertions: 3</p>
     */
    @Test
    void testHistoryBoundaryAndWithHistoryFlag() {
        var history = List.of(
            UserMessage.of("h0"),
            UserMessage.of("h1")
        );
        var buf = new ContextMessageBuffer(history);
        buf.addBack(List.of(
            UserMessage.of("n0"),
            UserMessage.of("n1"),
            UserMessage.of("n2")
        ));

        // with_history=True returns from the full tail
        assertEquals(
            List.of(UserMessage.of("n1"), UserMessage.of("n2")),
            buf.getBack(2, true)
        );
        
        // with_history=False returns only new segment tail
        assertEquals(
            List.of(UserMessage.of("n1"), UserMessage.of("n2")),
            buf.getBack(2, false)
        );
        
        // cannot exceed new segment length
        assertEquals(
            List.of(UserMessage.of("n0"), UserMessage.of("n1"), UserMessage.of("n2")),
            buf.getBack(10, false)
        );
    }

    /**
     * Test that pop_back only affects new segment when withHistory is false.
     * 
     * <p>Python: test_message_buffer_pop_back_only_affects_new_segment_when_with_history_false</p>
     * <p>Assertions: 3</p>
     */
    @Test
    void testPopBackOnlyAffectsNewSegmentWhenWithHistoryFalse() {
        var history = List.of(
            UserMessage.of("h0"),
            UserMessage.of("h1")
        );
        var buf = new ContextMessageBuffer(history);
        buf.addBack(List.of(
            UserMessage.of("n0"),
            UserMessage.of("n1")
        ));

        var popped = buf.popBack(10, false);
        assertEquals(
            List.of(UserMessage.of("n0"), UserMessage.of("n1")),
            popped
        );
        
        // history remains
        assertEquals(history, buf.getBack(null, true));
        assertEquals(List.of(), buf.getBack(null, false));
    }

    /**
     * Test that set_messages preserves or resets history boundary.
     * 
     * <p>Python: test_message_buffer_set_messages_preserves_or_resets_history_boundary</p>
     * <p>Assertions: 4</p>
     */
    @Test
    void testSetMessagesPreservesOrResetsHistoryBoundary() {
        var history = List.of(
            UserMessage.of("h0"),
            UserMessage.of("h1")
        );
        var buf = new ContextMessageBuffer(history);
        buf.addBack(List.of(UserMessage.of("n0")));

        // Replace only new segment; preserve history.
        buf.setMessages(List.of(UserMessage.of("n1")), false);
        
        var expectedWithHistory = List.of(
            UserMessage.of("h0"),
            UserMessage.of("h1"),
            UserMessage.of("n1")
        );
        assertEquals(expectedWithHistory, buf.getBack(null, true));
        assertEquals(List.of(UserMessage.of("n1")), buf.getBack(null, false));

        // Replace entire list; history boundary resets to 0.
        buf.setMessages(List.of(UserMessage.of("all")), true);
        assertEquals(List.of(UserMessage.of("all")), buf.getBack(null, true));
        assertEquals(List.of(UserMessage.of("all")), buf.getBack(null, false));
    }
}









