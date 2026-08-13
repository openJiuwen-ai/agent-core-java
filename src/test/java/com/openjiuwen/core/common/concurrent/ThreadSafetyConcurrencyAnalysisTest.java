/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agentteams.agent.Allocation;
import com.openjiuwen.agentteams.agent.ModelAllocators;
import com.openjiuwen.agentteams.agent.ModelAllocators.ByModelNameAllocator;
import com.openjiuwen.agentteams.agent.ModelAllocators.RoundRobinModelAllocator;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.autoharness.registry.BaseRegistry;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.Model.ModelClientFactory;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.memory.graph.extraction.EntityDef;
import com.openjiuwen.core.memory.graph.extraction.RelationDef;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.workflow.WorkflowSpec;
import com.openjiuwen.core.workflow.component.llm.LLMExecutableState;
import com.openjiuwen.harness.lsp.core.LSPServerManager;
import com.openjiuwen.harness.lsp.core.LspDiagnosticRegistry;
import com.openjiuwen.harness.task_loop.LoopQueues;
import com.openjiuwen.harness.task_loop.TaskLoopController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression tests verifying that the thread-safety defects documented in the
 * agent-core-java thread-safety analysis report have been fixed.
 *
 * <p>Each nested class targets one reported issue and asserts the fixed state:
 * the field/method now uses a thread-safe construct (volatile / Atomic /
 * ConcurrentHashMap / synchronized) and concurrent stress workloads no longer
 * lose updates or throw {@link java.util.ConcurrentModificationException}.
 *
 * @since 0.1.7
 */
class ThreadSafetyConcurrencyAnalysisTest {

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

    private static ModelPoolEntry poolEntry(String modelName, String apiBaseUrl) {
        return ModelPoolEntry.builder().modelName(modelName).provider("OpenAI")
                .apiKey("key-" + modelName + "-" + apiBaseUrl).apiBaseUrl(apiBaseUrl).build();
    }

    @Nested
    @DisplayName("P0-1 ModelAllocators 轮询分配器原子计数器与并发集合")
    class ModelAllocatorsRace {

        @Test
        @DisplayName("RoundRobin index 为 AtomicInteger 且并发自增不丢失更新")
        void roundRobinIndexIsAtomicAndLosslessUnderConcurrency() throws Exception {
            Field indexField = RoundRobinModelAllocator.class.getDeclaredField("index");
            indexField.setAccessible(true);
            assertThat(indexField.getType()).isEqualTo(AtomicInteger.class);

            List<ModelPoolEntry> pool = List.of(poolEntry("gpt-4", "http://a1"),
                    poolEntry("claude", "http://c1"), poolEntry("gpt-4", "http://a2"));
            RoundRobinModelAllocator allocator = new RoundRobinModelAllocator(pool);

            int threadCount = 16;
            int perThread = 5000;
            int expected = threadCount * perThread;
            ExecutorService executor = newNamedPool("rr-allocator", threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threadCount; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        for (int j = 0; j < perThread; j++) {
                            Allocation allocation = allocator.allocate();
                            assertThat(allocation).isNotNull();
                        }
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

            AtomicInteger index = (AtomicInteger) indexField.get(allocator);
            assertThat(index.get()).isEqualTo(expected);
        }

        @Test
        @DisplayName("ByModelName innerIndexes 为 ConcurrentHashMap 且并发分配无 CME/损坏")
        void byModelNameInnerIndexesIsConcurrentAndRaceFree() throws Exception {
            Field innerField = ByModelNameAllocator.class.getDeclaredField("innerIndexes");
            innerField.setAccessible(true);

            List<ModelPoolEntry> pool = List.of(poolEntry("gpt-4", "http://a1"),
                    poolEntry("gpt-4", "http://a2"), poolEntry("claude", "http://c1"));
            ByModelNameAllocator allocator = new ByModelNameAllocator(pool);
            Object innerIndexes = innerField.get(allocator);
            assertThat(innerIndexes).isInstanceOf(ConcurrentHashMap.class);

            int threadCount = 16;
            int perThread = 2000;
            ExecutorService executor = newNamedPool("bn-allocator", threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> firstError = new AtomicReference<>();
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threadCount; i++) {
                    String modelName = i % 2 == 0 ? "gpt-4" : "claude";
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        for (int j = 0; j < perThread; j++) {
                            try {
                                Allocation allocation = allocator.allocate(modelName);
                                assertThat(allocation).isNotNull();
                            } catch (Throwable throwable) {
                                firstError.compareAndSet(null, throwable);
                            }
                        }
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
            assertThat(firstError.get()).as("concurrent allocate must not throw").isNull();
        }
    }

    @Nested
    @DisplayName("P0-2 TeamDatabase.droppedSessionIds 并发集合")
    class TeamDatabaseDroppedSessionIds {

        @Test
        @DisplayName("droppedSessionIds 由 ConcurrentHashMap.newKeySet 支撑")
        void droppedSessionIdsIsConcurrentSet() throws Exception {
            Field droppedField = TeamDatabase.class.getDeclaredField("droppedSessionIds");
            droppedField.setAccessible(true);
            TeamDatabase db = new TeamDatabase(null);
            Object dropped = droppedField.get(db);
            assertThat(dropped).isNotNull();
            assertThat(dropped.getClass().getName()).contains("ConcurrentHashMap");
        }
    }

    @Nested
    @DisplayName("P0-3 Model.FACTORY_REGISTRY 并发注册表")
    class ModelFactoryRegistry {

        @Test
        @DisplayName("FACTORY_REGISTRY 为 ConcurrentHashMap")
        void factoryRegistryIsConcurrentMap() throws Exception {
            Field registryField = Model.class.getDeclaredField("FACTORY_REGISTRY");
            registryField.setAccessible(true);
            Object registry = registryField.get(null);
            assertThat(registry).isInstanceOf(ConcurrentHashMap.class);
        }

        @Test
        @DisplayName("并发 registerFactory + entrySet 遍历不抛 ConcurrentModificationException")
        void concurrentRegisterAndTraversalNoCme() throws Exception {
            Field registryField = Model.class.getDeclaredField("FACTORY_REGISTRY");
            registryField.setAccessible(true);

            int writers = 8;
            int readers = 8;
            ExecutorService executor = newNamedPool("factory-reg", writers + readers);
            CountDownLatch ready = new CountDownLatch(writers + readers);
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> firstError = new AtomicReference<>();
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < writers; i++) {
                    int id = i;
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        for (int j = 0; j < 2000; j++) {
                            try {
                                Model.registerFactory(stubFactory("stub-" + id + "-" + j));
                            } catch (Throwable throwable) {
                                firstError.compareAndSet(null, throwable);
                            }
                        }
                    }));
                }
                for (int i = 0; i < readers; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        for (int j = 0; j < 2000; j++) {
                            try {
                                Map<String, ModelClientFactory> map =
                                        (Map<String, ModelClientFactory>) registryField.get(null);
                                int count = 0;
                                for (Map.Entry<String, ModelClientFactory> entry : map.entrySet()) {
                                    if (entry.getKey() != null) {
                                        count++;
                                    }
                                }
                                assertThat(count).isNotNegative();
                            } catch (Throwable throwable) {
                                firstError.compareAndSet(null, throwable);
                            }
                        }
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
            assertThat(firstError.get()).as("concurrent register + traversal must not throw").isNull();
        }

        private ModelClientFactory stubFactory(String name) {
            return new ModelClientFactory() {
                @Override
                public String providerName() {
                    return name;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return null;
                }
            };
        }
    }

    @Nested
    @DisplayName("P1-1 Vertex.callCount/streamCallCount 原子计数器")
    class VertexCounters {

        @Test
        @DisplayName("callCount 与 streamCallCount 为 AtomicInteger")
        void countersAreAtomicInteger() throws Exception {
            Field callCountField = Vertex.class.getDeclaredField("callCount");
            Field streamCallCountField = Vertex.class.getDeclaredField("streamCallCount");
            assertThat(callCountField.getType()).isEqualTo(AtomicInteger.class);
            assertThat(streamCallCountField.getType()).isEqualTo(AtomicInteger.class);
        }

        @Test
        @DisplayName("logMessage 为 ConcurrentHashMap（initLog 路径无锁写入安全）")
        void logMessageIsConcurrentMap() throws Exception {
            Vertex vertex = new Vertex("test-node", new Executable<Object, Object>() {
            });
            Field logMessageField = Vertex.class.getDeclaredField("logMessage");
            logMessageField.setAccessible(true);
            assertThat(logMessageField.get(vertex)).isInstanceOf(ConcurrentHashMap.class);
        }
    }

    @Nested
    @DisplayName("P1-2 LspDiagnosticRegistry.instance 可见性")
    class LspDiagnosticRegistryPublication {

        @Test
        @DisplayName("instance 字段声明为 volatile")
        void instanceFieldIsVolatile() throws Exception {
            Field instanceField = LspDiagnosticRegistry.class.getDeclaredField("instance");
            assertThat(Modifier.isVolatile(instanceField.getModifiers())).isTrue();
        }
    }

    @Nested
    @DisplayName("P2-1 InMemoryKVStore 双 Map 复合操作原子性")
    class InMemoryKVStoreAtomicity {

        @Test
        @DisplayName("过期信息与值合并为单一 store Map（消除双 Map TOCTOU）")
        void mergedIntoSingleStoreMap() throws Exception {
            assertThatThrownBy(() -> InMemoryKVStore.class.getDeclaredField("expiryAt"))
                    .isInstanceOf(NoSuchFieldException.class);
            Field storeField = InMemoryKVStore.class.getDeclaredField("store");
            storeField.setAccessible(true);
            Object store = storeField.get(new InMemoryKVStore());
            assertThat(store).isInstanceOf(ConcurrentHashMap.class);
        }

        @Test
        @DisplayName("并发 set + cleanup 不丢失覆盖写入的新值")
        void setOverExpiredKeyIsNotLostUnderConcurrentCleanup() throws Exception {
            InMemoryKVStore store = new InMemoryKVStore();
            store.exclusiveSet("race-key", "old", 1);
            Thread.sleep(1200);

            int threads = 8;
            int iterations = 1000;
            ExecutorService executor = newNamedPool("kv-race", threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> firstError = new AtomicReference<>();
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    int id = i;
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        for (int j = 0; j < iterations; j++) {
                            try {
                                if (id % 2 == 0) {
                                    store.set("race-key", "fresh");
                                } else {
                                    store.get("race-key");
                                }
                            } catch (Throwable throwable) {
                                firstError.compareAndSet(null, throwable);
                            }
                        }
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
            assertThat(firstError.get()).as("concurrent set/get must not throw").isNull();
            assertThat(store.get("race-key")).as("last set value must survive concurrent cleanup")
                    .isEqualTo("fresh");
        }
    }

    @Nested
    @DisplayName("P2-2 EntityDef/RelationDef 静态描述注册表")
    class EntityDefRelationDefRegistry {

        @Test
        @DisplayName("ENTITY_DEFINITION_DESCRIPTION 与 RELATION_DEFINITION_DESCRIPTION 为 ConcurrentHashMap")
        void descriptionMapsAreConcurrent() throws Exception {
            Field entityField = EntityDef.class.getDeclaredField("ENTITY_DEFINITION_DESCRIPTION");
            entityField.setAccessible(true);
            assertThat(entityField.get(null)).isInstanceOf(ConcurrentHashMap.class);

            Field relationField = RelationDef.class.getDeclaredField("RELATION_DEFINITION_DESCRIPTION");
            relationField.setAccessible(true);
            assertThat(relationField.get(null)).isInstanceOf(ConcurrentHashMap.class);
        }

        @Test
        @DisplayName("并发 registerDescription 不抛异常且值可见")
        void concurrentRegisterDescriptionIsSafe() throws Exception {
            int threads = 8;
            int perThread = 1000;
            ExecutorService executor = newNamedPool("desc-reg", threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> firstError = new AtomicReference<>();
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    int id = i;
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        for (int j = 0; j < perThread; j++) {
                            try {
                                String lang = "lang-" + id + "-" + j;
                                EntityDef.registerDescription(lang, "desc-" + j);
                                RelationDef.registerDescription(lang, "desc-" + j);
                            } catch (Throwable throwable) {
                                firstError.compareAndSet(null, throwable);
                            }
                        }
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
            assertThat(firstError.get()).as("concurrent registerDescription must not throw").isNull();
            Field entityMapField = EntityDef.class.getDeclaredField("ENTITY_DEFINITION_DESCRIPTION");
            entityMapField.setAccessible(true);
            Map<String, String> entityMap = (Map<String, String>) entityMapField.get(null);
            assertThat(entityMap.get("lang-0-0")).isEqualTo("desc-0");
        }
    }

    @Nested
    @DisplayName("P2-3 LLMExecutableState.accumulatedContent 同步保护")
    class LLMExecutableStateSync {

        @Test
        @DisplayName("accumulateContent/buildFinalResult/clear 为 synchronized")
        void methodsAreSynchronized() throws Exception {
            assertThat(Modifier.isSynchronized(
                    LLMExecutableState.class.getDeclaredMethod("accumulateContent", String.class).getModifiers()))
                    .isTrue();
            assertThat(Modifier.isSynchronized(LLMExecutableState.class
                    .getDeclaredMethod("buildFinalResult", Map.class, Map.class).getModifiers())).isTrue();
            assertThat(Modifier.isSynchronized(
                    LLMExecutableState.class.getDeclaredMethod("clear").getModifiers())).isTrue();
        }

        @Test
        @DisplayName("并发 accumulateContent 不丢失字符")
        void concurrentAccumulateContentIsLossless() throws Exception {
            LLMExecutableState state = new LLMExecutableState();
            int threads = 16;
            int perThread = 1000;
            String chunk = "ab";
            ExecutorService executor = newNamedPool("llm-state", threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        for (int j = 0; j < perThread; j++) {
                            state.accumulateContent(chunk);
                        }
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
            String expected = chunk.repeat(threads * perThread);
            Map<String, Object> result = state.buildFinalResult(Map.of("type", "text"), Map.of("content", ""));
            assertThat(result).containsEntry("content", expected);
        }
    }

    @Nested
    @DisplayName("P2-4 SystemPromptBuilder 同步保护（已修复回归）")
    class SystemPromptBuilderSync {

        @Test
        @DisplayName("addSection/removeSection/build 等方法为 synchronized")
        void methodsAreSynchronized() throws Exception {
            assertThat(Modifier.isSynchronized(SystemPromptBuilder.class
                    .getDeclaredMethod("addSection", PromptSection.class).getModifiers())).isTrue();
            assertThat(Modifier.isSynchronized(
                    SystemPromptBuilder.class.getDeclaredMethod("removeSection", String.class).getModifiers()))
                    .isTrue();
            assertThat(Modifier.isSynchronized(SystemPromptBuilder.class.getDeclaredMethod("build").getModifiers()))
                    .isTrue();
            assertThat(Modifier.isSynchronized(
                    SystemPromptBuilder.class.getDeclaredMethod("getAllSections").getModifiers())).isTrue();
        }

        @Test
        @DisplayName("并发 addSection/removeSection/build 不抛 CME")
        void concurrentMutateAndBuildNoCme() throws Exception {
            SystemPromptBuilder builder = new SystemPromptBuilder();
            int threads = 8;
            int perThread = 500;
            ExecutorService executor = newNamedPool("prompt-builder", threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> firstError = new AtomicReference<>();
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    int id = i;
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        for (int j = 0; j < perThread; j++) {
                            try {
                                String name = "section-" + id + "-" + j;
                                builder.addSection(new PromptSection(name, Map.of("cn", "x"), 1));
                                builder.build();
                                builder.removeSection(name);
                            } catch (Throwable throwable) {
                                firstError.compareAndSet(null, throwable);
                            }
                        }
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
            assertThat(firstError.get()).as("concurrent build must not throw").isNull();
        }
    }

    @Nested
    @DisplayName("P2-5 WorkflowSpec 边表/组件表并发集合")
    class WorkflowSpecConcurrentFields {

        @Test
        @DisplayName("edges/streamEdges/compConfigs 为 ConcurrentHashMap")
        void fieldsAreConcurrentMaps() throws Exception {
            WorkflowSpec spec = new WorkflowSpec();
            Field edgesField = WorkflowSpec.class.getDeclaredField("edges");
            Field streamEdgesField = WorkflowSpec.class.getDeclaredField("streamEdges");
            Field compConfigsField = WorkflowSpec.class.getDeclaredField("compConfigs");
            edgesField.setAccessible(true);
            streamEdgesField.setAccessible(true);
            compConfigsField.setAccessible(true);
            assertThat(edgesField.get(spec)).isInstanceOf(ConcurrentHashMap.class);
            assertThat(streamEdgesField.get(spec)).isInstanceOf(ConcurrentHashMap.class);
            assertThat(compConfigsField.get(spec)).isInstanceOf(ConcurrentHashMap.class);
        }
    }

    @Nested
    @DisplayName("P2-6 BaseRegistry 原子注册与不可变视图")
    class BaseRegistryAtomicity {

        @Test
        @DisplayName("items 为 ConcurrentHashMap 且 names() 返回不可修改视图")
        void itemsIsConcurrentAndNamesUnmodifiable() throws Exception {
            Field itemsField = BaseRegistry.class.getDeclaredField("items");
            itemsField.setAccessible(true);
            BaseRegistry<String> registry = new BaseRegistry<>();
            assertThat(itemsField.get(registry)).isInstanceOf(ConcurrentHashMap.class);
            assertThatThrownBy(() -> registry.names().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("并发 register 同名仅一个成功（putIfAbsent 原子化）")
        void concurrentRegisterSameNameOnlyOneSucceeds() throws Exception {
            BaseRegistry<String> registry = new BaseRegistry<>();
            int threads = 16;
            ExecutorService executor = newNamedPool("registry-reg", threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger winners = new AtomicInteger();
            AtomicInteger losers = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        try {
                            registry.register("dup", "value");
                            winners.incrementAndGet();
                        } catch (IllegalArgumentException duplicate) {
                            losers.incrementAndGet();
                        }
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
            assertThat(winners.get()).as("exactly one concurrent register wins").isEqualTo(1);
            assertThat(losers.get()).isEqualTo(threads - 1);
            assertThat(registry.require("dup")).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("P0-4 ContextEngine contextPool 与静态处理器注册表并发集合")
    class ContextEnginePool {

        @Test
        @DisplayName("contextPool/PROCESSOR_FACTORY_MAP/PROCESSOR_CLASS_MAP 为 ConcurrentHashMap")
        void fieldsAreConcurrentMaps() throws Exception {
            Field contextPoolField = ContextEngine.class.getDeclaredField("contextPool");
            contextPoolField.setAccessible(true);
            ContextEngine engine = new ContextEngine();
            assertThat(contextPoolField.get(engine)).isInstanceOf(ConcurrentHashMap.class);

            Field factoryMapField = ContextEngine.class.getDeclaredField("PROCESSOR_FACTORY_MAP");
            factoryMapField.setAccessible(true);
            assertThat(factoryMapField.get(null)).isInstanceOf(ConcurrentHashMap.class);

            Field classMapField = ContextEngine.class.getDeclaredField("PROCESSOR_CLASS_MAP");
            classMapField.setAccessible(true);
            assertThat(classMapField.get(null)).isInstanceOf(ConcurrentHashMap.class);
        }

        @Test
        @DisplayName("并发 createContext 同一 contextId 仅创建一个实例（computeIfAbsent 原子化）")
        void concurrentCreateContextReturnsSameInstance() throws Exception {
            ContextEngine engine = new ContextEngine();
            int threads = 16;
            ExecutorService executor = newNamedPool("ctx-engine", threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<com.openjiuwen.core.context.ModelContext>> futures = new ArrayList<>();
            Set<com.openjiuwen.core.context.ModelContext> distinct = new HashSet<>();
            try {
                for (int i = 0; i < threads; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        return engine.createContext("shared-ctx", null, null, null, null);
                    }));
                }
                ready.await();
                start.countDown();
                for (Future<com.openjiuwen.core.context.ModelContext> future : futures) {
                    distinct.add(future.get());
                }
            } finally {
                executor.shutdownNow();
            }
            assertThat(distinct).as("computeIfAbsent must create exactly one context").hasSize(1);
        }
    }

    @Nested
    @DisplayName("P1-3 TaskLoopController/LoopQueues 并发队列与原子轮次计数")
    class TaskLoopConcurrency {

        @Test
        @DisplayName("LoopQueues steering/isFollowUp 为 ConcurrentLinkedQueue、events 为 PriorityBlockingQueue、sequence 为 AtomicLong")
        void loopQueuesFieldsAreConcurrent() throws Exception {
            LoopQueues queues = new LoopQueues();

            Field steeringField = LoopQueues.class.getDeclaredField("steering");
            steeringField.setAccessible(true);
            assertThat(steeringField.get(queues)).isInstanceOf(ConcurrentLinkedQueue.class);

            Field followUpField = LoopQueues.class.getDeclaredField("isFollowUp");
            followUpField.setAccessible(true);
            assertThat(followUpField.get(queues)).isInstanceOf(ConcurrentLinkedQueue.class);

            Field eventsField = LoopQueues.class.getDeclaredField("events");
            eventsField.setAccessible(true);
            assertThat(eventsField.get(queues)).isInstanceOf(PriorityBlockingQueue.class);

            Field sequenceField = LoopQueues.class.getDeclaredField("sequence");
            sequenceField.setAccessible(true);
            assertThat(sequenceField.getType()).isEqualTo(AtomicLong.class);
        }

        @Test
        @DisplayName("并发 pushSteer + drainSteering 不丢失消息")
        void concurrentPushAndDrainSteeringNoLoss() throws Exception {
            LoopQueues queues = new LoopQueues();
            int producers = 8;
            int perProducer = 1000;
            ExecutorService executor = newNamedPool("steer-queue", producers + 1);
            CountDownLatch ready = new CountDownLatch(producers + 1);
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> firstError = new AtomicReference<>();
            List<Future<?>> futures = new ArrayList<>();
            List<String> drained = new ArrayList<>();
            try {
                for (int i = 0; i < producers; i++) {
                    int id = i;
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        for (int j = 0; j < perProducer; j++) {
                            try {
                                queues.pushSteer("steer-" + id + "-" + j);
                            } catch (Throwable throwable) {
                                firstError.compareAndSet(null, throwable);
                            }
                        }
                    }));
                }
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    awaitGate(start);
                    try {
                        for (int i = 0; i < 50; i++) {
                            drained.addAll(queues.drainSteering());
                            Thread.sleep(2);
                        }
                    } catch (Throwable throwable) {
                        firstError.compareAndSet(null, throwable);
                    }
                }));
                ready.await();
                start.countDown();
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                executor.shutdownNow();
            }
            drained.addAll(queues.drainSteering());
            assertThat(firstError.get()).as("concurrent push/drain must not throw").isNull();
            assertThat(drained).as("no steering message lost").hasSize(producers * perProducer);
        }

        @Test
        @DisplayName("SessionState roundCounter 为 AtomicInteger 且字段为 volatile")
        void sessionStateFieldsAreAtomicAndVolatile() throws Exception {
            Class<?> sessionStateClass = null;
            for (Class<?> inner : TaskLoopController.class.getDeclaredClasses()) {
                if ("SessionState".equals(inner.getSimpleName())) {
                    sessionStateClass = inner;
                    break;
                }
            }
            assertThat(sessionStateClass).as("SessionState inner class must exist").isNotNull();

            Field roundCounterField = sessionStateClass.getDeclaredField("roundCounter");
            assertThat(roundCounterField.getType()).isEqualTo(AtomicInteger.class);

            assertThat(Modifier.isVolatile(
                    sessionStateClass.getDeclaredField("isRoundActive").getModifiers())).isTrue();
            assertThat(Modifier.isVolatile(
                    sessionStateClass.getDeclaredField("isLastRoundFollowUp").getModifiers())).isTrue();
            assertThat(Modifier.isVolatile(
                    sessionStateClass.getDeclaredField("lastResult").getModifiers())).isTrue();
        }

        @Test
        @DisplayName("并发 prepareRound 轮次计数不丢失更新")
        void concurrentPrepareRoundNoLostUpdate() throws Exception {
            TaskLoopController controller = new TaskLoopController();
            int threads = 16;
            int perThread = 500;
            int expected = threads * perThread;
            ExecutorService executor = newNamedPool("round-counter", threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger maxRound = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        awaitGate(start);
                        for (int j = 0; j < perThread; j++) {
                            int round = controller.prepareRound("race-session", false);
                            maxRound.accumulateAndGet(round, Math::max);
                        }
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
            assertThat(controller.getRoundCounter("race-session"))
                    .as("roundCounter must equal total prepareRound calls").isEqualTo(expected);
            assertThat(maxRound.get()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("P2-7 LSPServerManager 非并发集合字段")
    class LSPServerManagerFields {

        @Test
        @DisplayName("configs/instances/spawning/extensionMap/docVersions 为 ConcurrentHashMap、diagHandlerInstances 为 ConcurrentHashMap.newKeySet")
        void fieldsAreConcurrentCollections() throws Exception {
            LSPServerManager manager = new LSPServerManager();
            String[] mapFields = {"configs", "instances", "spawning", "extensionMap", "docVersions"};
            for (String name : mapFields) {
                Field field = LSPServerManager.class.getDeclaredField(name);
                field.setAccessible(true);
                assertThat(field.get(manager))
                        .as(name + " must be a ConcurrentHashMap").isInstanceOf(ConcurrentHashMap.class);
            }
            Field diagField = LSPServerManager.class.getDeclaredField("diagHandlerInstances");
            diagField.setAccessible(true);
            assertThat(diagField.get(manager).getClass().getName()).contains("ConcurrentHashMap");
        }
    }
}
