/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.kvcache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.kvcache.AgentHint;
import com.openjiuwen.core.kvcache.KVCacheMetadata;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Regression tests for {@link KVCacheModelCallHook#handleContextWindowChange},
 * mirroring the window-diff eviction contract: a modified (not appended)
 * window evicts the stale message range, and the range must not carry tool
 * bounds when the tool list is unchanged.
 */
class KVCacheModelCallHookTest {
    private final KVCacheModelCallHook hook = new KVCacheModelCallHook();

    @Test
    void windowDiffEvictUsesMessagesOnlyRangeWhenToolsUnchanged() {
        Model llm = mock(Model.class);
        when(llm.evictKvc(anyString(), anyString(), any(AgentHint.KvCacheRange.class)))
                .thenReturn(CompletableFuture.completedFuture(true));

        ContextWindow previous = new ContextWindow();
        previous.setSystemMessages(List.of(new SystemMessage("sys")));
        previous.setContextMessages(List.of(new UserMessage("u1")));
        previous.setTools(List.of());

        ContextWindow next = new ContextWindow();
        next.setSystemMessages(List.of(new SystemMessage("sys")));
        next.setContextMessages(List.of(new UserMessage("u1-rewritten")));
        next.setTools(List.of());

        KVCacheMetadata.Lineage lineage = new KVCacheMetadata.Lineage("s-1", "s-1");
        KVCacheModelCallHook.KVCacheCallCapabilities capabilities =
                new KVCacheModelCallHook.KVCacheCallCapabilities(true, true);

        boolean isEvicted = hook.handleContextWindowChange(capabilities, llm, next, previous, lineage).join();

        assertThat(isEvicted).isTrue();
        ArgumentCaptor<AgentHint.KvCacheRange> rangeCaptor =
                ArgumentCaptor.forClass(AgentHint.KvCacheRange.class);
        verify(llm).evictKvc(eq("s-1"), eq("s-1"), rangeCaptor.capture());
        AgentHint.KvCacheRange range = rangeCaptor.getValue();
        assertThat(range.target()).isEqualTo("messages");
        assertThat(range.msgStart()).isEqualTo(1);
        assertThat(range.msgEnd()).isEqualTo(2);
        assertThat(range.toolsStart()).isNull();
        assertThat(range.toolsEnd()).isNull();
        assertThat(range.shouldIncludeTools()).isFalse();
    }
}
