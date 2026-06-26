/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.ModelAllocator;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.PrivateAgentResources;
import com.openjiuwen.harness.tools.worktree.WorktreeConfig;
import com.openjiuwen.harness.tools.worktree.WorktreeManager;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link PrivateAgentResources}.
 *
 * <p>Mirrors Python's {@code PrivateAgentResources} dataclass in
 * {@code openjiuwen/agent_teams/agent/resources.py}.</p>
 */
class PrivateAgentResourcesTest {

    @Test
    void resourcesDefaultToNullLikePythonDataclassDefaults() {
        PrivateAgentResources resources = new PrivateAgentResources();

        assertThat(resources.getHarness()).isNull();
        assertThat(resources.getWorktreeManager()).isNull();
        assertThat(resources.getMemoryManager()).isNull();
        assertThat(resources.getFirstIterGate()).isNull();
        assertThat(resources.getModelAllocator()).isNull();
    }

    @Test
    void settersPreserveResourceIdentities() {
        PrivateAgentResources resources = new PrivateAgentResources();
        MemberRuntime runtime = new RuntimeStub();
        WorktreeManager worktreeManager = new WorktreeManager(new WorktreeConfig());
        Object memoryManager = new Object();
        Object firstIterGate = new Object();
        ModelAllocator modelAllocator = new ModelAllocatorStub();

        resources.setHarness(runtime);
        resources.setWorktreeManager(worktreeManager);
        resources.setMemoryManager(memoryManager);
        resources.setFirstIterGate(firstIterGate);
        resources.setModelAllocator(modelAllocator);

        assertThat(resources.getHarness()).isSameAs(runtime);
        assertThat(resources.getWorktreeManager()).isSameAs(worktreeManager);
        assertThat(resources.getMemoryManager()).isSameAs(memoryManager);
        assertThat(resources.getFirstIterGate()).isSameAs(firstIterGate);
        assertThat(resources.getModelAllocator()).isSameAs(modelAllocator);
    }

    private static final class RuntimeStub implements MemberRuntime {
        @Override
        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
            return List.of().iterator();
        }

        @Override
        public CompletionStage<Void> steer(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> followUp(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> abort() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void initCwdForRound() {
        }

        @Override
        public boolean hasPendingInterrupt() {
            return false;
        }

        @Override
        public boolean isPendingInterruptResumeValid(Object userInput) {
            return false;
        }

        @Override
        public List<Object> findRails(Class<?> railType) {
            return List.of();
        }

        @Override
        public CompletionStage<Void> registerRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void registerMemberTools(Object memoryManager) {
        }

        @Override
        public CompletionStage<Void> injectMemberMemory(Object memoryManager, String query) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void runAgentCustomizer(AgentCustomizer customizer) {
        }

        @Override
        public Object workspace() {
            return null;
        }

        @Override
        public Object sysOperation() {
            return null;
        }
    }

    private static final class ModelAllocatorStub implements ModelAllocator {
        @Override
        public AgentConfigurator.Allocation allocate(String modelName) {
            return () -> modelName;
        }

        @Override
        public void loadStateDict(Map<String, Object> state) {
        }
    }
}
