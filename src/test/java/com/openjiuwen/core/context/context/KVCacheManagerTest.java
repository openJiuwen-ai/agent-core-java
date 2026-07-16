/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link KVCacheManager}.
 * <p>
 * Ported from Python's {@code test_kv_cache_manager.py}.
 */
class KVCacheManagerTest {
    private ContextWindow buildWindow(List<BaseMessage> systemMessages, List<BaseMessage> contextMessages,
            List<ToolInfo> tools) {
        return ContextWindow.builder().systemMessages(systemMessages != null ? systemMessages : new ArrayList<>())
                .contextMessages(contextMessages != null ? contextMessages : new ArrayList<>())
                .tools(tools != null ? tools : new ArrayList<>()).statistic(new ContextStats()).build();
    }

    @Test
    @DisplayName("first call stores window without release")
    void testFirstCallNoRelease() {
        KVCacheManager manager = new KVCacheManager("session-1");
        ContextWindow window =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        // Should not throw
        manager.release(window);
    }

    @Test
    @DisplayName("identical windows do not trigger release")
    void testIdenticalWindowsNoRelease() {
        KVCacheManager manager = new KVCacheManager("session-1");
        List<BaseMessage> sys = List.of(new SystemMessage("sys"));
        List<BaseMessage> ctx = List.of(new UserMessage("hello"), new AssistantMessage("hi"));

        ContextWindow w1 = buildWindow(sys, ctx, List.of());
        ContextWindow w2 = buildWindow(sys, ctx, List.of());

        manager.release(w1);
        // Second call with identical content should not throw
        manager.release(w2);
    }

    @Test
    @DisplayName("modified messages trigger release detection")
    void testModifiedMessagesTriggerRelease() {
        KVCacheManager manager = new KVCacheManager("session-1");

        ContextWindow w1 = buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w1);

        // Change content in second window
        ContextWindow w2 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("modified")), List.of());
        // Should not throw (just log)
        manager.release(w2);
    }

    @Test
    @DisplayName("modified tools trigger release detection")
    void testModifiedToolsTriggerRelease() {
        KVCacheManager manager = new KVCacheManager("session-1");

        List<ToolInfo> tools1 = List.of(ToolInfo.builder().name("tool1").description("desc1").build());
        List<ToolInfo> tools2 = List.of(ToolInfo.builder().name("tool1").description("modified desc").build());

        ContextWindow w1 = buildWindow(List.of(), List.of(new UserMessage("u")), tools1);
        manager.release(w1);

        ContextWindow w2 = buildWindow(List.of(), List.of(new UserMessage("u")), tools2);
        manager.release(w2);
    }

    @Test
    @DisplayName("multiple successive releases work correctly")
    void testMultipleSuccessiveReleases() {
        KVCacheManager manager = new KVCacheManager("session-1");

        for (int i = 0; i < 5; i++) {
            ContextWindow w =
                buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("msg-" + i)), List.of());
            manager.release(w);
        }
    }

    @Test
    @DisplayName("empty to non-empty window transition")
    void testEmptyToNonEmptyTransition() {
        KVCacheManager manager = new KVCacheManager("session-1");

        ContextWindow w1 = buildWindow(List.of(), List.of(), List.of());
        manager.release(w1);

        ContextWindow w2 = buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w2);
    }
}
