// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.runner.resourcemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.Tag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency tests for the global resource registries.
 *
 * <p>Before the fix, the plain-HashMap registries lost entries under
 * concurrent writes (HashMap resize race) and threw
 * {@code ConcurrentModificationException} when write raced iteration; these
 * tests pin the ConcurrentHashMap / CopyOnWriteArrayList behavior.
 */
@DisplayName("ResourceMgr/ToolMgr concurrency")
class ResourceMgrConcurrencyTest {
    private static final int THREADS = 16;

    private static final int PER_THREAD = 2000;

    private ResourceMgr resourceMgr;

    private ToolMgr toolMgr;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        resourceMgr = new ResourceMgr();
        toolMgr = new ToolMgr();
    }

    static Tool makeTool(String toolId) {
        ToolCard card =
            ToolCard.builder().id(toolId).name(toolId).description("tool " + toolId).build();
        return new ResourceMgrTest.SimpleTool(card);
    }

    private static ExecutorService newPool(int threads) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
    }

    /**
     * Regression: concurrent addTool from many threads must not
     * lose registry entries (plain HashMap lost ~65 of 50000 under resize
     * races).
     */
    @Test
    void concurrentAddTool_noEntryLost() throws Exception {
        ExecutorService pool = newPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int threadIdx = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < PER_THREAD; i++) {
                        String id = "tool-" + threadIdx + "-" + i;
                        Result<ToolCard> result = resourceMgr.addTool(makeTool(id), null);
                        assertTrue(result.isOk(), "addTool must succeed for " + id);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60L, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(THREADS * PER_THREAD, countRegisteredTools(),
                "concurrent registration must lose no entries");
        // Review note: the count above goes through getTool, which
        // reads ToolMgr.tools + TagMgr but never idToCard. Count again
        // through getResourceByTag, whose lookup path is idToCard.get per
        // tagged id, so a lost-entry regression in the card registry
        // itself is also detected.
        assertEquals(THREADS * PER_THREAD, resourceMgr.getResourceByTag(Tag.GLOBAL).size(),
                "concurrent registration must lose no card-registry entries");
    }

    /**
     * Iteration must be weakly consistent: concurrent writes while another
     * thread iterates the card registry must never throw CME.
     */
    @Test
    void concurrentAddAndIterate_noCme() throws Exception {
        ExecutorService pool = newPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch stop = new CountDownLatch(1);
            AtomicInteger scans = new AtomicInteger();
            Future<?> writer = pool.submit(() -> {
                start.await();
                for (int i = 0; i < PER_THREAD; i++) {
                    Result<ToolCard> result = resourceMgr.addTool(makeTool("iter-" + i), null);
                    assertTrue(result.isOk(), "addTool must succeed for iter-" + i);
                }
                stop.countDown();
                return null;
            });
            Future<?> iterator = pool.submit(() -> {
                start.await();
                // getWorkflow on an unregistered id falls through to
                // findWorkflowByAlternateId, a full scan over the live
                // idToCard.entrySet(); racing that scan against concurrent
                // addTool is the CME scenario the fix must survive. The
                // do-while guarantees at least one real scan even if the
                // writer finishes first.
                int scan = 0;
                do {
                    assertNull(resourceMgr.getWorkflow("miss-" + scan++));
                } while (stop.getCount() > 0);
                scans.set(scan);
                return null;
            });
            start.countDown();
            writer.get(60L, TimeUnit.SECONDS);
            iterator.get(60L, TimeUnit.SECONDS);
            assertTrue(scans.get() > 0, "iterator must perform at least one registry scan");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * sysOpResources under concurrent register/remove: no lost entry, no CME,
     * and the returned id list is a snapshot that never aliases internal
     * state.
     */
    @Test
    void concurrentSysOperationTools_consistentSnapshot() throws Exception {
        ExecutorService pool = newPool(THREADS);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int threadIdx = t;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        String sysOpId = "sysop-" + threadIdx + "-" + i;
                        List<String> ids = List.of(sysOpId + "-a", sysOpId + "-b");
                        toolMgr.addSysOperationTools(sysOpId, ids);
                        List<String> read = toolMgr.getSysOperationToolIds(sysOpId);
                        read.set(0, "mutated"); // snapshot: must not affect internals
                        assertEquals(ids, toolMgr.getSysOperationToolIds(sysOpId));
                        assertEquals(ids, toolMgr.removeSysOperationTools(sysOpId));
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(60L, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertTrue(toolMgr.getSysOperationToolIds("sysop-0-0").isEmpty(),
                "all sysOps removed, registry must be empty");
    }

    /**
     * mcpServerNameToIds index consistency: concurrent registration of many
     * server ids under one server name, then concurrent removal of every
     * other id, must leave the index exactly matching the survivors.
     */
    @Test
    void concurrentMcpIndex_registerAndRemove_stayConsistent() throws Exception {
        toolMgr = toolMgrWithMockClient(Collections.emptyList());
        int servers = 64;
        String serverName = "shared-name";
        for (int i = 0; i < servers; i++) {
            toolMgr.addToolServer(serverConfig("srv-" + i, serverName), null);
        }
        assertEquals(servers, toolMgr.getMcpServerIds(serverName).size(),
                "all server ids registered under the name");

        // Concurrently remove the even-indexed servers from the index.
        ExecutorService pool = newPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < servers; i += 2) {
                final String serverId = "srv-" + i;
                futures.add(pool.submit(() -> {
                    toolMgr.removeToolServer(serverId, true);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(60L, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<String> survivors = toolMgr.getMcpServerIds(serverName);
        assertEquals(servers / 2, survivors.size(), "half the servers remain indexed");
        for (int i = 1; i < servers; i += 2) {
            assertTrue(survivors.contains("srv-" + i), "odd server must remain");
        }
        for (int i = 0; i < servers; i += 2) {
            assertFalse(survivors.contains("srv-" + i), "even server must be removed");
        }
    }

    /**
     * The compound removeToolServer index update (drop id, remove key when
     * drained) must be atomic: two servers sharing one name are removed
     * concurrently and the key disappears exactly once, without leaving an
     * empty list behind.
     */
    @Test
    void concurrentRemoval_sameServerName_keyRemovedExactlyWhenDrained() throws Exception {
        toolMgr = toolMgrWithMockClient(Collections.emptyList());
        String serverName = "pair-name";
        toolMgr.addToolServer(serverConfig("pa", serverName), null);
        toolMgr.addToolServer(serverConfig("pb", serverName), null);
        assertEquals(2, toolMgr.getMcpServerIds(serverName).size());

        ExecutorService pool = newPool(2);
        try {
            Future<?> fa = pool.submit(() -> toolMgr.removeToolServer("pa", true));
            Future<?> fb = pool.submit(() -> toolMgr.removeToolServer("pb", true));
            fa.get(30L, TimeUnit.SECONDS);
            fb.get(30L, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertTrue(toolMgr.getMcpServerIds(serverName).isEmpty(),
                "drained name must not stay in the index");
    }

    /**
     * Builds a ToolMgr whose MCP client factory returns a mocked client that
     * always connects successfully and lists the given tool cards. A single
     * instance serves all servers registered through it, so index assertions
     * observe one shared registry.
     */
    /**
     * Null server_name must keep the former HashMap tolerance: registration
     * succeeds, the server stays addressable by server_id, and the name index
     * simply does not track it (ConcurrentHashMap rejects null keys).
     */
    @Test
    void mcpServer_nullServerName_registersAndRemovesWithoutNpe() throws Exception {
        toolMgr = toolMgrWithMockClient(Collections.emptyList());
        McpServerConfig config = McpServerConfig.builder()
                .serverId("null-name-server").serverName(null).build();
        toolMgr.addToolServer(config, null);
        assertTrue(toolMgr.getMcpServerIds(null).isEmpty(),
                "null name lookup stays empty instead of throwing");
        toolMgr.removeToolServer("null-name-server", true);
        assertNull(toolMgr.getMcpServerConfig("null-name-server"),
                "server removed by id even though it had no name");
    }

    /**
     * Null id lookups keep the old "not found" semantics instead of a
     * ConcurrentHashMap NPE.
     */
    @Test
    void nullIdLookups_returnNotFoundSemantics() {
        assertNull(toolMgr.getMcpServerConfig(null));
        assertTrue(toolMgr.getMcpServerIds(null).isEmpty());
        assertTrue(toolMgr.getSysOperationToolIds(null).isEmpty());
        assertTrue(toolMgr.removeSysOperationTools(null).isEmpty());
        assertNull(toolMgr.getMcpTool("any", null));
    }

    /**
     * Review finding: addTool's former containsKey + put
     * compound let two concurrent registrations of the same toolId both pass
     * the guard and silently overwrite each other. With putIfAbsent exactly
     * one registration wins, the loser gets the duplicate exception, and the
     * map holds the winner's instance.
     */
    @Test
    void concurrentAddTool_sameToolId_exactlyOneWinner() throws Exception {
        ExecutorService pool = newPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger duplicates = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        toolMgr.addTool("shared-tool", makeTool("shared-tool"));
                        successes.incrementAndGet();
                    } catch (IllegalArgumentException e) {
                        duplicates.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30L, TimeUnit.SECONDS);
            }
            assertEquals(1, successes.get());
            assertEquals(7, duplicates.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private ToolMgr toolMgrWithMockClient(List<McpToolCard> cards) throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.connect(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyFloat())).thenReturn(true);
        when(client.listTools()).thenReturn(new ArrayList<>(cards));
        return new ToolMgr() {
            @Override
            protected McpClient createClient(McpServerConfig cfg) {
                return client;
            }
        };
    }

    private static McpServerConfig serverConfig(String serverId, String serverName) {
        return McpServerConfig.builder().serverId(serverId).serverName(serverName).build();
    }

    private int countRegisteredTools() {
        int count = 0;
        for (String id : registeredToolIds()) {
            if (resourceMgr.getTool(id) != null) {
                count++;
            }
        }
        return count;
    }

    private Iterable<String> registeredToolIds() {
        return () -> new Iterator<String>() {
            private int next = 0;

            @Override
            public boolean hasNext() {
                return next < THREADS * PER_THREAD;
            }

            @Override
            public String next() {
                int i = next++;
                return "tool-" + (i / PER_THREAD) + "-" + (i % PER_THREAD);
            }
        };
    }
}
