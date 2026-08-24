/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
 * Regression tests for ReActAgent singleton concurrency fixes that still apply
 * on develop: {@code SystemPromptBuilder.clearTransient()}, volatile DCL for
 * {@code llm}, and {@code BaseAgent.skillUtil} DCL. Per-session tool slots are
 * skipped because develop stores reload tools on {@code SessionModelContext}.
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

    @Nested
    @DisplayName("promptBuilder 跨会话数据残留已修复")
    class PromptBuilderDataResidue {

        @Test
        @DisplayName("SystemPromptBuilder.clearTransient() 是 synchronized")
        void systemPromptBuilderClearTransientIsSynchronized() throws Exception {
            Method clearTransient = SystemPromptBuilder.class.getDeclaredMethod("clearTransient");
            assertThat(Modifier.isSynchronized(clearTransient.getModifiers())).isTrue();
        }

        @Test
        @DisplayName("clearTransient 清除 per-iteration 残留并保留 persistent section")
        void clearTransientRemovesResidueKeepsPersistent() {
            ReActAgent agent = new ReActAgent(AgentCard.builder().id("p0-agent").name("p0-agent").build());
            ReActAgentConfig config = ReActAgentConfig.builder().build();
            config.configurePromptTemplate(List.of(Map.of("role", "system", "content", "IDENTITY-MARKER")));
            agent.configure(config);
            agent.addPersistentPromptBuilderSection("business_rules", "BUSINESS-RULES", 20);
            agent.getSystemPromptBuilder()
                    .addSection(new PromptSection("workspace", Map.of("cn", "CONV-A-RESIDUE"), 30));

            agent.getSystemPromptBuilder().clearTransient();

            String prompt = agent.getSystemPromptBuilder().build();
            assertThat(prompt).as("identity 与 business_rules 应保留").contains("IDENTITY-MARKER", "BUSINESS-RULES");
            assertThat(prompt).as("per-iteration 工作区残留应被清除").doesNotContain("CONV-A-RESIDUE");
        }
    }

    @Nested
    @DisplayName("llm 字段 volatile + DCL 已修复")
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
    @DisplayName("skillUtil 字段 volatile + DCL 已修复")
    class SkillUtilLazyInitRace {

        @Test
        @DisplayName("skillUtil 字段声明为 volatile 且由独立锁保护")
        void skillUtilFieldIsVolatileAndGuardedByLock() throws Exception {
            Field skillUtilField = BaseAgent.class.getDeclaredField("skillUtil");
            assertThat(Modifier.isVolatile(skillUtilField.getModifiers())).as("skillUtil 字段应为 volatile").isTrue();
            Field lockField = BaseAgent.class.getDeclaredField("skillUtilLock");
            assertThat(Modifier.isFinal(lockField.getModifiers())).as("skillUtilLock 应为 final 独立锁").isTrue();
        }

        @Test
        @DisplayName("并发 lazyInitSkill 只创建一个 SkillUtil")
        void concurrentLazyInitSkillCreatesSingleInstance() throws Exception {
            AtomicInteger createCount = new AtomicInteger();
            CountingSkillReActAgent agent = new CountingSkillReActAgent(createCount);
            agent.configure(ReActAgentConfig.builder().sysOperationId("skill-sys-op").build());

            Field skillUtilField = BaseAgent.class.getDeclaredField("skillUtil");
            skillUtilField.setAccessible(true);
            skillUtilField.set(agent, null);
            createCount.set(0);

            AtomicReference<Throwable> firstError = new AtomicReference<>();
            ExecutorService executor = newNamedPool("skill-util", RACE_THREADS);
            CountDownLatch ready = new CountDownLatch(RACE_THREADS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < RACE_THREADS; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        try {
                            agent.lazyInitSkill();
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
            assertThat(firstError.get()).as("并发 lazyInitSkill 不应抛出异常").isNull();
            assertThat(createCount.get()).as("DCL 修复后应只创建一个 SkillUtil").isEqualTo(1);
            assertThat(agent.getSkillUtil()).isNotNull();
        }
    }

    private static final class CountingSkillReActAgent extends ReActAgent {
        private final AtomicInteger createCount;

        private CountingSkillReActAgent(AtomicInteger createCount) {
            super(AgentCard.builder().id("skill-race-agent").name("skill-race-agent")
                    .description("skillUtil DCL").build());
            this.createCount = createCount;
        }

        @Override
        protected SkillUtil createSkillUtil(String sysOperationId) {
            createCount.incrementAndGet();
            return super.createSkillUtil(sysOperationId);
        }
    }

    private static Model.ModelClientFactory countingFactory(String provider, AtomicInteger createCount) {
        return new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return provider;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                createCount.incrementAndGet();
                return new BaseModelClient(modelConfig, clientConfig) {
                    @Override
                    public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                                  String model, Integer maxTokens, String stop,
                                                  BaseOutputParser outputParser, Float timeout,
                                                  Map<String, Object> kwargs) {
                        return new AssistantMessage("ok");
                    }

                    @Override
                    public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                                                                  Float topP, String model, Integer maxTokens,
                                                                  String stop, BaseOutputParser outputParser,
                                                                  Float timeout, Map<String, Object> kwargs) {
                        return List.<AssistantMessageChunk>of().iterator();
                    }

                    @Override
                    public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                                 String negativePrompt, int n, boolean promptExtend,
                                                                 boolean watermark, int seed,
                                                                 Map<String, Object> kwargs) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                                  String languageType, Map<String, Object> kwargs) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl,
                                                                 String audioUrl, String model, String size,
                                                                 String resolution, int duration,
                                                                 boolean promptExtend, boolean watermark,
                                                                 String negativePrompt, Integer seed,
                                                                 Map<String, Object> kwargs) {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }
}
