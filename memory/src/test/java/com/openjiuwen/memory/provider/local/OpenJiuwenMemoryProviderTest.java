/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.provider.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.application.schema.AgentMemoryConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.memory.MemInfo;
import com.openjiuwen.memory.MemResult;
import com.openjiuwen.memory.manage.mem_model.MemoryType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class OpenJiuwenMemoryProviderTest {
    @Test
    void providerShouldFormatSearchAndSyncMessages() throws Exception {
        FakeBackend backend = new FakeBackend();
        OpenJiuwenMemoryProvider provider =
                new OpenJiuwenMemoryProvider(Map.of(), backend, AgentMemoryConfig.builder().build());
        provider.initialize(Map.of("user_id", "u1", "scope_id", "s1", "session_id", "ss1"));

        String search = provider.handleToolCall("ltm_search", Map.of("query", "project"));
        provider.syncTurn("hello", "world", Map.of());
        String prefetch = provider.prefetch("history", Map.of());

        assertThat(search).contains("project context");
        assertThat(prefetch).contains("summary memory");
        assertThat(backend.addMessagesCalls.get()).isEqualTo(1);
        assertThat(backend.lastMessages).hasSize(2);
    }

    private static final class FakeBackend implements OpenJiuwenMemoryProvider.Backend {
        private final AtomicInteger addMessagesCalls = new AtomicInteger();
        private List<BaseMessage> lastMessages = new ArrayList<>();

        @Override
        public List<MemResult> searchUserMem(String query, int num, String userId, String scopeId, double threshold) {
            return List.of(MemResult.builder().memInfo(
                    MemInfo.builder().memId("1").content("project context").type(MemoryType.USER_PROFILE).build())
                    .score(0.9).build());
        }

        @Override
        public List<MemResult> searchUserHistorySummary(String query, int num, String userId, String scopeId,
                double threshold) {
            return List.of(MemResult.builder()
                    .memInfo(MemInfo.builder().memId("2").content("summary memory").type(MemoryType.SUMMARY).build())
                    .score(0.8).build());
        }

        @Override
        public void addMessages(List<BaseMessage> messages, AgentMemoryConfig config, String userId, String scopeId,
                String sessionId) {
            addMessagesCalls.incrementAndGet();
            lastMessages = new ArrayList<>(messages);
        }
    }
}
