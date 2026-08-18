/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.Model.ModelClientFactory;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.retrieval.TestModelClient;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression tests for the concurrency defects documented in the ReActAgent
 * concurrency analysis (v3) report. After the fixes landed these tests assert
 * the corrected state and behavior: a synchronized {@code clear()} on
 * {@link SystemPromptBuilder}, a {@code volatile} + double-checked locked
 * initialization for {@code llm} and {@code skillUtil}, and a per-session tool
 * override so the context reloader no longer collides across concurrent
 * sessions.
 *
 * <p>Scope: a single ReActAgent instance shared by concurrent sessions.
 *
 * @since 0.1.15
 */
class ReActConcurrencyIssueVerificationTest {

    private static final int RACE_THREADS = 8;
    private static final AtomicLong FACTORY_SEQ = new AtomicLong();

    private static ExecutorService newNamedPool(String prefix, int size) {
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName(prefix + "-" + thread.getId());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private static void awaitGate(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ReActAgent newReActAgent(String provider) {
        ReActAgent agent = new ReActAgent(AgentCard.builder().id("race-agent").name("race-agent")
                .description("concurrency verification").build());
        agent.configure(ReActAgentConfig.builder().maxIterations(1).build()
                .configureModelClient(provider, "key", "http://localhost", "race-model", false));
        return agent;
    }

    private static ModelClientFactory countingFactory(String provider, AtomicInteger createCount) {
        return new ModelClientFactory() {
            @Override
            public String providerName() {
                return provider;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                createCount.incrementAndGet();
                return new TestModelClient("race-model", "ok");
            }
        };
    }

    private static Tool newConstantTool(String name, String marker) {
        ToolCard card = ToolCard.builder().id(name + "_" + marker).name(name).build();
        return new LocalFunction(card, inputs -> Map.of("result", marker));
    }

    private static Session mockSession(String sessionId) {
        Session session = Mockito.mock(Session.class);
        Mockito.when(session.getSessionId()).thenReturn(sessionId);
        return session;
    }

    @Nested
    @DisplayName("P0 promptBuilder 跨会话数据残留已修复")
    class PromptBuilderDataResidue {

        @Test
        @DisplayName("SystemPromptBuilder 提供 synchronized clear() 方法")
        void systemPromptBuilderHasClearMethod() throws Exception {
            Method clear = SystemPromptBuilder.class.getDeclaredMethod("clear");
            assertThat(Modifier.isSynchronized(clear.getModifiers())).isTrue();
        }

        @Test
        @DisplayName("clearTransientPromptSections 清除 per-iteration 残留并保留 persistent section")
        void clearTransientRemovesResidueKeepsPersistent() throws Exception {
            ReActAgent agent = new ReActAgent(AgentCard.builder().id("p0-agent").name("p0-agent").build());
            ReActAgentConfig config = ReActAgentConfig.builder().build();
            config.configurePromptTemplate(List.of(Map.of("role", "system", "content", "IDENTITY-MARKER")));
            agent.configure(config);
            agent.addPromptBuilderSection("business_rules", "BUSINESS-RULES", 20);
            agent.getSystemPromptBuilder()
                    .addSection(new PromptSection("workspace", Map.of("cn", "CONV-A-RESIDUE"), 30));

            Method clearTransient = ReActAgent.class.getDeclaredMethod("clearTransientPromptSections");
            clearTransient.setAccessible(true);
            clearTransient.invoke(agent);

            String prompt = agent.getSystemPromptBuilder().build();
            assertThat(prompt).as("identity 与 business_rules 应保留").contains("IDENTITY-MARKER", "BUSINESS-RULES");
            assertThat(prompt).as("per-iteration 工作区残留应被清除").doesNotContain("CONV-A-RESIDUE");
        }
    }

    @Nested
    @DisplayName("P1 llm 字段 volatile + DCL 已修复")
    class LlmLazyInitRace {

        @Test
        @DisplayName("llm 字段声明为 volatile 且由独立锁保护")
        void llmFieldIsVolatileAndGuardedByLock() throws Exception {
            Field llmField = ReActAgent.class.getDeclaredField("llm");
            assertThat(Modifier.isVolatile(llmField.getModifiers())).as("llm 字段应为 volatile").isTrue();
            Field lockField = ReActAgent.class.getDeclaredField("llmLock");
            assertThat(Modifier.isFinal(lockField.getModifiers())).as("llmLock 应为 final 独立锁").isTrue();
        }

        @Test
        @DisplayName("并发 getLlm() 仅创建一个 Model 实例（DCL 修复后）")
        void concurrentGetLlmCreatesSingleModelInstance() throws Exception {
            AtomicInteger createCount = new AtomicInteger();
            String provider = "fixed-llm-" + FACTORY_SEQ.incrementAndGet();
            Model.registerFactory(countingFactory(provider, createCount));

            ReActAgent agent = newReActAgent(provider);
            Field llmField = ReActAgent.class.getDeclaredField("llm");
            llmField.setAccessible(true);
            llmField.set(agent, null);
            Method getLlm = ReActAgent.class.getDeclaredMethod("getLlm");
            getLlm.setAccessible(true);

            AtomicReference<Throwable> firstError = new AtomicReference<>();
            ExecutorService executor = newNamedPool("llm-fixed", RACE_THREADS);
            CountDownLatch ready = new CountDownLatch(RACE_THREADS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < RACE_THREADS; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        try {
                            getLlm.invoke(agent);
                        } catch (Exception e) {
                            firstError.compareAndSet(null, e);
                        }
                        return null;
                    }));
                }
                ready.await();
                start.countDown();
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                executor.shutdownNow();
            }
            assertThat(firstError.get()).as("并发 getLlm 不应抛出异常").isNull();
            assertThat(createCount.get()).as("DCL 修复后应只创建一个 Model 实例").isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("P1 skillUtil 字段 volatile + DCL 已修复")
    class SkillUtilLazyInitRace {

        @Test
        @DisplayName("skillUtil 字段声明为 volatile 且由独立锁保护")
        void skillUtilFieldIsVolatileAndGuardedByLock() throws Exception {
            Field skillUtilField = BaseAgent.class.getDeclaredField("skillUtil");
            assertThat(Modifier.isVolatile(skillUtilField.getModifiers())).as("skillUtil 字段应为 volatile").isTrue();
            Field lockField = BaseAgent.class.getDeclaredField("skillUtilLock");
            assertThat(Modifier.isFinal(lockField.getModifiers())).as("skillUtilLock 应为 final 独立锁").isTrue();
        }
    }

    @Nested
    @DisplayName("P2 abilityManager reloader per-session 解析已修复")
    class AbilityManagerReloaderPerSession {

        @Test
        @DisplayName("同名 per-session 工具按 sessionId 分槽存储，不互相覆盖")
        void registerSessionToolStoresPerSessionNotOverwriting() throws Exception {
            Tool toolA = newConstantTool("reload_original_context_messages", "A");
            Tool toolB = newConstantTool("reload_original_context_messages", "B");
            AbilityManager manager = new AbilityManager();
            manager.registerSessionTool("sessionA", toolA);
            manager.registerSessionTool("sessionB", toolB);

            Field sessionToolsField = AbilityManager.class.getDeclaredField("sessionTools");
            sessionToolsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Tool>> sessionTools =
                    (Map<String, Map<String, Tool>>) sessionToolsField.get(manager);

            assertThat(sessionTools).hasSize(2);
            assertThat(sessionTools.get("sessionA").get("reload_original_context_messages")).isSameAs(toolA);
            assertThat(sessionTools.get("sessionB").get("reload_original_context_messages")).isSameAs(toolB);
        }

        @Test
        @DisplayName("resolveSessionTool 按当前 session 返回正确的 per-session 工具")
        @SuppressWarnings("unchecked")
        void resolveSessionToolReturnsCorrectSession() throws Exception {
            Tool toolA = newConstantTool("reload_original_context_messages", "A");
            Tool toolB = newConstantTool("reload_original_context_messages", "B");
            AbilityManager manager = new AbilityManager();
            manager.registerSessionTool("sessionA", toolA);
            manager.registerSessionTool("sessionB", toolB);

            Method resolve = AbilityManager.class.getDeclaredMethod("resolveSessionTool", String.class,
                    Session.class);
            resolve.setAccessible(true);

            String name = "reload_original_context_messages";
            assertThat((Optional<Tool>) resolve.invoke(manager, name, mockSession("sessionA")))
                    .contains(toolA);
            assertThat((Optional<Tool>) resolve.invoke(manager, name, mockSession("sessionB")))
                    .contains(toolB);
            assertThat((Optional<Tool>) resolve.invoke(manager, name, mockSession("sessionC")))
                    .isEmpty();
        }
    }
}
