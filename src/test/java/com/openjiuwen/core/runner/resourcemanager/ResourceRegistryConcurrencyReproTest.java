// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.runner.resourcemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression tests for the HashMap concurrency defects in the global resource
 * registry. Each entry point that used to mutate a plain HashMap concurrently
 * must now retain every entry and never throw on iteration or compound remove.
 */
@DisplayName("Resource Registry Concurrency Regression")
class ResourceRegistryConcurrencyReproTest {

    private static ThreadPoolExecutor newBoundedPool(int threads) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            r -> {
                Thread t = new Thread(r, "repro-worker");
                t.setDaemon(true);
                return t;
            });
    }

    private static int mapSize(Object owner, String fieldName) throws Exception {
        Field f = owner.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return ((Map<?, ?>) f.get(owner)).size();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> mapField(Object owner, String fieldName) throws Exception {
        Field f = owner.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return (Map<String, ?>) f.get(owner);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> void putInMap(Object owner, String fieldName, K key, V value) throws Exception {
        Field f = owner.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        ((Map<K, V>) f.get(owner)).put(key, value);
    }

    @Test
    @DisplayName("sysOpResources retains every entry under concurrent addSysOperationTools")
    void sysOpResourcesRetainsAllEntriesUnderConcurrency() throws Exception {
        ToolMgr mgr = new ToolMgr();
        int total = 20_000;
        int threads = 16;
        int per = total / threads;
        ThreadPoolExecutor pool = newBoundedPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            int base = t * per;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < per; i++) {
                        String id = "sysop-" + (base + i);
                        mgr.addSysOperationTools(id, List.of("tool-" + (base + i)));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        boolean finished = done.await(60, TimeUnit.SECONDS);
        if (!finished) {
            pool.shutdownNow();
        } else {
            pool.shutdown();
        }
        pool.awaitTermination(5, TimeUnit.SECONDS);
        int size = mapSize(mgr, "sysOpResources");
        System.out.println("[sysOpResources retain] expected=" + total + " actual=" + size + " finished=" + finished);
        assertEquals(total, size, "no entry may be lost under concurrent registration");
    }

    @Test
    @DisplayName("sysOpResources iteration is thread-safe under concurrent put")
    void sysOpResourcesIterationIsThreadSafeUnderConcurrency() throws Exception {
        ToolMgr mgr = new ToolMgr();
        for (int i = 0; i < 2_000; i++) {
            mgr.addSysOperationTools("seed-" + i, List.of("t"));
        }
        Map<String, ?> map = mapField(mgr, "sysOpResources");
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 500_000; i++) {
                mgr.addSysOperationTools("w-" + i, List.of("t"));
                if (error.get() != null) {
                    return;
                }
            }
        }, "repro-writer");
        Thread iterator = new Thread(() -> {
            try {
                for (int round = 0; round < 200; round++) {
                    for (Map.Entry<String, ?> entry : map.entrySet()) {
                        if (entry.getValue() == null) {
                            return;
                        }
                    }
                }
            } catch (Throwable t) {
                error.set(t);
            }
        }, "repro-iterator");
        writer.start();
        iterator.start();
        writer.join(30_000);
        iterator.join(30_000);
        Throwable t = error.get();
        System.out.println("[sysOpResources CME] " + (t == null ? "none" : t.getClass().getName()));
        assertNull(t, "iteration must not throw under concurrent mutation");
    }

    @Test
    @DisplayName("removeToolServer keeps mcpServerNameToIds consistent under concurrent removal")
    void removeToolServerKeepsRegistriesConsistentUnderConcurrency() throws Exception {
        int rounds = 20;
        int serversPerRound = 100;
        for (int round = 0; round < rounds; round++) {
            ToolMgr mgr = new ToolMgr();
            McpClient client = new NoOpMcpClient();
            String groupName = "grp-" + round;
            List<String> serverIds = new ArrayList<>();
            for (int i = 0; i < serversPerRound; i++) {
                String serverId = groupName + "-srv-" + i;
                serverIds.add(serverId);
                McpServerConfig config = McpServerConfig.builder()
                    .serverName(groupName)
                    .serverId(serverId)
                    .build();
                ToolMgr.McpServerResource resource = new ToolMgr.McpServerResource(config, client,
                    List.of("tool-" + serverId), System.currentTimeMillis(), null);
                putInMap(mgr, "mcpServerResources", serverId, resource);
            }
            putInMap(mgr, "mcpServerNameToIds", groupName, new ArrayList<>(serverIds));

            ThreadPoolExecutor pool = newBoundedPool(serversPerRound);
            CountDownLatch ready = new CountDownLatch(serversPerRound);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(serversPerRound);
            AtomicReference<Throwable> error = new AtomicReference<>();
            for (String sid : serverIds) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        mgr.removeToolServer(sid, true);
                    } catch (Throwable t) {
                        error.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            start.countDown();
            done.await(30, TimeUnit.SECONDS);
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            int resourcesLeft = mapSize(mgr, "mcpServerResources");
            int nameEntries = mapSize(mgr, "mcpServerNameToIds");
            System.out.println("[removeToolServer round " + round + "] resourcesLeft=" + resourcesLeft
                + " nameEntries=" + nameEntries
                + " error=" + (error.get() == null ? "none" : error.get().getClass().getSimpleName()));
            assertNull(error.get(), "removeToolServer must not throw under concurrency");
            assertEquals(0, resourcesLeft, "all server resources must be removed");
            assertEquals(0, nameEntries, "no stale group entry may remain");
        }
    }

    private static final class NoOpMcpClient implements McpClient {
        @Override
        public boolean connect(int retryTimes, float timeout) {
            return true;
        }

        @Override
        public boolean disconnect(float timeout) {
            return true;
        }

        @Override
        public List<Object> listTools(float timeout) {
            return List.of();
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
            return null;
        }

        @Override
        public Optional<Object> getToolInfo(String toolName, float timeout) {
            return Optional.empty();
        }

        @Override
        public String getServerPath() {
            return "";
        }
    }
}
