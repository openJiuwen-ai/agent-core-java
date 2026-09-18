/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.memory.LongTermMemory;
import com.openjiuwen.memory.config.MemoryScopeConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

class ReActAgentConfigureScopeTest {

    @AfterEach
    void resetLongTermMemorySingleton() {
        LongTermMemory.resetInstance();
    }

    @Test
    void configureWithMemScopeIdAppliesDefaultScopeConfig() {
        RecordingLongTermMemory memory = installRecordingMemory();
        ReActAgent agent = new ReActAgent(agentCard());

        agent.configure(new ReActAgentConfig().configureMemScope("scope-a"));

        assertThat(memory.configuredScopeIds()).containsExactly("scope-a");
    }

    @Test
    void configureReappliesScopeWhenMemScopeIdChanges() {
        RecordingLongTermMemory memory = installRecordingMemory();
        ReActAgent agent = new ReActAgent(agentCard());

        agent.configure(new ReActAgentConfig().configureMemScope("scope-a"));
        agent.configure(new ReActAgentConfig().configureMemScope("scope-a"));
        agent.configure(new ReActAgentConfig().configureMemScope("scope-b"));

        assertThat(memory.configuredScopeIds()).containsExactly("scope-a", "scope-b");
    }

    @Test
    void evolveConfigureWithMemScopeIdAppliesDefaultScopeConfig() {
        RecordingLongTermMemory memory = installRecordingMemory();
        ReActAgentEvolve agent = new ReActAgentEvolve(agentCard());

        agent.configure(new ReActAgentConfig().configureMemScope("evolve-scope"));

        assertThat(memory.configuredScopeIds()).containsExactly("evolve-scope");
    }

    private static AgentCard agentCard() {
        return new AgentCard("react-configure-scope", "react-configure-scope", "memory scope wiring");
    }

    private static RecordingLongTermMemory installRecordingMemory() {
        try {
            RecordingLongTermMemory memory = new RecordingLongTermMemory();
            Field instanceField = LongTermMemory.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, memory);
            return memory;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class RecordingLongTermMemory extends LongTermMemory {
        private final List<String> configuredScopeIds = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<Boolean> setScopeConfig(String scopeId, MemoryScopeConfig memoryScopeConfig) {
            configuredScopeIds.add(scopeId);
            return CompletableFuture.completedFuture(true);
        }

        private List<String> configuredScopeIds() {
            return configuredScopeIds;
        }
    }
}
