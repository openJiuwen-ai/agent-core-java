/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class MemberRuntimeTest {

    @Test
    void agentCustomizerReceivesAgentMemberAndRole() {
        List<String> seen = new ArrayList<>();
        AgentCustomizer customizer = (agent, memberName, roleValue) ->
                seen.add(agent + "|" + memberName + "|" + roleValue);

        customizer.customize("agent-1", null, "bridge_agent");

        assertThat(seen).containsExactly("agent-1|null|bridge_agent");
    }

    @Test
    void conformingRuntimePassesShapeCheck() {
        assertThat(MemberRuntime.isRuntime(new ConformingRuntime())).isTrue();
    }

    @Test
    void missingMethodFailsShapeCheck() {
        assertThat(MemberRuntime.isRuntime(new MissingCustomizerRuntime())).isFalse();
    }

    @Test
    void plainObjectFailsShapeCheck() {
        assertThat(MemberRuntime.isRuntime(new Object())).isFalse();
    }

    @Test
    void implementingRuntimeStreamsQueryAndRunsCustomizer() {
        ImplementingRuntime runtime = new ImplementingRuntime();

        List<Object> chunks = new ArrayList<>();
        runtime.runStreaming(Map.of("query", "hello"), "s-1").forEachRemaining(chunks::add);
        runtime.runAgentCustomizer((agent, memberName, roleValue) -> runtime.customizerTrace =
                agent + "|" + memberName + "|" + roleValue);

        assertThat(chunks).containsExactly("hello");
        assertThat(runtime.customizerTrace).isEqualTo("agent|demo|teammate");
        assertThat(runtime.workspace()).isEqualTo("workspace");
        assertThat(runtime.sysOperation()).isEqualTo("sys-operation");
    }

    static final class ConformingRuntime {

        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
            return List.<Object>of(inputs.get("query"), sessionId).iterator();
        }

        public CompletionStage<Void> steer(String content) {
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> followUp(String content) {
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> abort() {
            return CompletableFuture.completedFuture(null);
        }

        public void initCwdForRound() {
        }

        public boolean hasPendingInterrupt() {
            return false;
        }

        public boolean isPendingInterruptResumeValid(Object userInput) {
            return userInput != null;
        }

        public List<Object> findRails(Class<?> railType) {
            return List.of(railType.getSimpleName());
        }

        public CompletionStage<Void> registerRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> unregisterRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        public void registerMemberTools(Object memoryManager) {
        }

        public CompletionStage<Void> injectMemberMemory(Object memoryManager, String query) {
            return CompletableFuture.completedFuture(null);
        }

        public void runAgentCustomizer(AgentCustomizer customizer) {
            customizer.customize("agent", "demo", "teammate");
        }

        public Object workspace() {
            return null;
        }

        public Object sysOperation() {
            return null;
        }
    }

    static final class MissingCustomizerRuntime {

        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
            return List.<Object>of().iterator();
        }

        public CompletionStage<Void> steer(String content) {
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> followUp(String content) {
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> abort() {
            return CompletableFuture.completedFuture(null);
        }

        public void initCwdForRound() {
        }

        public boolean hasPendingInterrupt() {
            return false;
        }

        public boolean isPendingInterruptResumeValid(Object userInput) {
            return false;
        }

        public List<Object> findRails(Class<?> railType) {
            return List.of();
        }

        public CompletionStage<Void> registerRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> unregisterRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        public void registerMemberTools(Object memoryManager) {
        }

        public CompletionStage<Void> injectMemberMemory(Object memoryManager, String query) {
            return CompletableFuture.completedFuture(null);
        }

        public Object workspace() {
            return null;
        }

        public Object sysOperation() {
            return null;
        }
    }

    static final class ImplementingRuntime implements MemberRuntime {
        private String customizerTrace = "";

        @Override
        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
            return List.<Object>of(inputs.get("query")).iterator();
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
            return true;
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
            customizer.customize("agent", "demo", "teammate");
        }

        @Override
        public Object workspace() {
            return "workspace";
        }

        @Override
        public Object sysOperation() {
            return "sys-operation";
        }
    }
}
