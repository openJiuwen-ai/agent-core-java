/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deepagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.IsolatedActions;
import com.openjiuwen.core.foundation.tool.mcp.CountingMcpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.GlobalRegistrySnapshot;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.resourcemanager.ToolMgr;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * DeepAgent facade MCP concurrent registration tests: the caller-visible contract "concurrent
 * registrations of the same server all succeed with one live
 * connection" is carried by the facade — its "lost the placeholder
 * race → re-query → re-tag" branch turns the ToolMgr-level
 * recognizable loser error into a reuse of the winner's entry.
 * <p>
 * Placeholder-window serialization: the cases pins that a
 * pre-query observing a mid-flight placeholder never adopts it — the
 * loser waits for the winner's terminal state and registers fresh
 * after a rollback, instead of succeeding early against a server that
 * never came online.
 * <p>
 * The shared ResourceMgr's ToolMgr is swapped (reflection, the same
 * access path GlobalRegistrySnapshot reads) for one whose
 * createClient hands out CountingMcpClient doubles; the swap is
 * always restored and every registered server removed afterwards,
 * which the registry snapshot delta-zero assertion proves.
 */
@DisplayName("DeepAgent facade MCP concurrent registration")
class DeepAgentMcpConcurrentRegistrationTest {
    private static final String WORKSPACE = "./target/mcp-facade-concurrent-test";

    private static final int CONCURRENT_AGENTS = 4;

    private ResourceMgr resourceMgr;

    private ToolMgr originalToolMgr;

    private CountingToolMgr installedToolMgr;

    private final Set<String> registeredServerIds = ConcurrentHashMap.newKeySet();

    private final List<DeepAgent> createdAgents = new CopyOnWriteArrayList<>();

    /**
     * ToolMgr that creates one counting client per createClient call from
     * the given config-aware factory and records every client for count
     * aggregation. The latch constructor keeps the default gated/ungated
     * behavior of the earlier cases (second-level latch).
     */
    static final class CountingToolMgr extends ToolMgr {
        final List<CountingMcpClient> clients = new CopyOnWriteArrayList<>();

        private final Function<McpServerConfig, ? extends CountingMcpClient> clientFactory;

        CountingToolMgr() {
            this(defaultClientFactory(null));
        }

        CountingToolMgr(CountDownLatch connectGate) {
            this(defaultClientFactory(connectGate));
        }

        CountingToolMgr(Function<McpServerConfig, ? extends CountingMcpClient> clientFactory) {
            this.clientFactory = clientFactory;
        }

        @Override
        protected McpClient createClient(McpServerConfig config) {
            CountingMcpClient client = clientFactory.apply(config);
            clients.add(client);
            return client;
        }
    }

    /** Counting client that returns only once every sibling has connected. */
    static final class GatedConnectClient extends CountingMcpClient {
        private final CountDownLatch connectGate;

        GatedConnectClient(CountDownLatch connectGate) {
            this.connectGate = connectGate;
        }

        @Override
        public boolean connect(int retryTimes, float timeout) throws Exception {
            boolean isConnected = super.connect(retryTimes, timeout);
            connectGate.countDown();
            if (!connectGate.await(30L, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "connect gate did not open - a sibling registration never connected");
            }
            return isConnected;
        }
    }

    /**
     * Client whose first listTools() parks on the gate and then fails, so
     * the placeholder winner's discovery deterministically rolls the entry
     * back once the test releases the gate; later calls answer immediately
     * .
     */
    static final class GatedFailFirstClient extends CountingMcpClient {
        private final CountDownLatch firstDiscoveryGate;
        private final AtomicInteger discoveryCalls = new AtomicInteger();

        GatedFailFirstClient(CountDownLatch firstDiscoveryGate) {
            this.firstDiscoveryGate = firstDiscoveryGate;
        }

        @Override
        public List<Object> listTools(float timeout) throws Exception {
            if (discoveryCalls.getAndIncrement() == 0) {
                if (!firstDiscoveryGate.await(30L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("discovery gate was never released");
                }
                throw new IllegalStateException("boom-retry-discovery");
            }
            return super.listTools(timeout);
        }

        boolean firstDiscoveryStarted() {
            return discoveryCalls.get() > 0;
        }
    }

    @BeforeEach
    void rememberOriginalToolMgr() throws Exception {
        resourceMgr = Runner.resourceMgr();
        Object rawToolMgr = fieldValue(fieldValue(resourceMgr, "resourceRegistry"), "toolMgr");
        if (!(rawToolMgr instanceof ToolMgr toolMgr)) {
            throw new IllegalStateException("resourceRegistry.toolMgr is not a ToolMgr");
        }
        originalToolMgr = toolMgr;
    }

    @AfterEach
    void restoreGlobalRegistries() throws Exception {
        cleanUpRegisteredState();
        if (installedToolMgr != null) {
            setFieldValue(fieldValue(resourceMgr, "resourceRegistry"), "toolMgr", originalToolMgr);
            installedToolMgr = null;
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Pre-registered server re-tagged without a second connection")
    void preRegisteredServerIsReTaggedWithoutSecondConnection() throws Exception {
        installToolMgr(new CountingToolMgr());
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        runPreRegisteredRetag();
        cleanUpRegisteredState();
        GlobalRegistrySnapshot.take().assertDeltaZero(before);
    }

    private void runPreRegisteredRetag() {
        // One shared config instance for the pre-registration and the
        // agent (listed twice in the agent config: the second pass also
        // covers the registeredMcps dedup guard): covers the facade's
        // identity fast path (left == right) in addition to the value
        // comparison.
        McpServerConfig shared = facadeConfig("anchor-lib", "http://anchor");
        Runner.resourceMgr().addMcpServer(shared, null, null);
        registeredServerIds.add("anchor-lib");

        DeepAgent agent = newAgent("mcp-facade-anchor", List.of(shared, shared));
        agent.ensureInitialized();

        assertThat(totalConnects()).as("prequery reuses the existing connection").isEqualTo(1);
        assertThat(totalDisconnects()).as("live connection kept").isEqualTo(0);
        assertThat(Runner.resourceMgr().getMcpServerConfig("anchor-lib")).isNotNull();
        assertThat(agent.getAgent().getAbilityManager().get("anchor-lib")).isNotNull();
        assertThat(Runner.resourceMgr().listMcpServers("mcp-facade-anchor").stream()
                .map(McpServerConfig::getServerId))
                .as("agent tag attached to the reused entry").contains("anchor-lib");
    }

    @Test
    @Timeout(60)
    @DisplayName("Concurrent facade registrations all succeed on one connection")
    void concurrentFacadeRegistrationsAllSucceedWithSingleConnection() throws Exception {
        CountDownLatch connectGate = new CountDownLatch(CONCURRENT_AGENTS);
        installToolMgr(new CountingToolMgr(connectGate));
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        runConcurrentFacadeRegistrations();
        cleanUpRegisteredState();
        GlobalRegistrySnapshot.take().assertDeltaZero(before);
    }

    private void runConcurrentFacadeRegistrations() throws Exception {
        List<Callable<Void>> initializations = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_AGENTS; i++) {
            String agentId = "mcp-facade-agent-" + i;
            McpServerConfig config = facadeConfig("facade-lib", "http://facade");
            initializations.add(() -> {
                newAgent(agentId, config).ensureInitialized();
                return null;
            });
        }
        List<Exception> failures = runAll(initializations, CONCURRENT_AGENTS);

        assertThat(failures).as("every concurrent registration attempt succeeds").isEmpty();
        assertThat(totalConnects()).as("each attempt connected before the race")
                .isEqualTo(CONCURRENT_AGENTS);
        assertThat(totalDisconnects()).as("losers recycled, one live connection")
                .isEqualTo(CONCURRENT_AGENTS - 1);
        assertThat(installedToolMgr.listMcpServerIds()).containsExactly("facade-lib");
        assertThat(Runner.resourceMgr().getMcpToolIds("facade-lib"))
                .as("winner's tool discovered").isNotEmpty();
        for (int i = 0; i < CONCURRENT_AGENTS; i++) {
            String agentId = "mcp-facade-agent-" + i;
            assertThat(Runner.resourceMgr().listMcpServers(agentId).stream()
                    .map(McpServerConfig::getServerId))
                    .as("tag of %s attached via winner or retag", agentId).contains("facade-lib");
        }
        for (DeepAgent agent : createdAgents) {
            assertThat(agent.getAgent().getAbilityManager().get("facade-lib"))
                    .as("ability entry present for every agent").isNotNull();
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Loser registers after the winner rolled its entry back")
    void loserRegistersAfterWinnerRollback() throws Exception {
        CountDownLatch discoveryGate = new CountDownLatch(1);
        AtomicInteger creations = new AtomicInteger();
        installToolMgr(new CountingToolMgr(config -> {
            if (creations.incrementAndGet() == 1) {
                GatedFailFirstClient client = new GatedFailFirstClient(discoveryGate);
                client.listToolsReturning(List.of(facadeToolCard()));
                return client;
            }
            return plainFacadeToolClient();
        }));
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        runLoserAfterWinnerRollback(discoveryGate);
        cleanUpRegisteredState();
        GlobalRegistrySnapshot.take().assertDeltaZero(before);
    }

    // The first agent claims the server slot and parks inside its
    // discovery RPC; the second agent's pre-query runs while the
    // placeholder is still mid-flight and must NOT adopt it:
    // the loser parks on the server slot lock and, after the winner's
    // rollback, comes back and registers fresh, instead of succeeding
    // early against a server that never came online. The bounded wait
    // below burns its budget while the loser holds its pre-query
    // decision, and the winner's discovery failure is released only
    // after that decision settled.
    private void runLoserAfterWinnerRollback(CountDownLatch discoveryGate) throws Exception {
        List<String> agentIds = List.of("mcp-facade-retry-a", "mcp-facade-retry-b");
        ExecutorService pool = newPool(2);
        try {
            Future<Void> winner = pool.submit(() -> {
                newAgent(agentIds.get(0), facadeConfig("retry-lib", "http://retry")).ensureInitialized();
                return null;
            });
            awaitFirstDiscoveryStarted();
            Future<Void> loser = pool.submit(() -> {
                newAgent(agentIds.get(1), facadeConfig("retry-lib", "http://retry")).ensureInitialized();
                return null;
            });
            awaitFutureSettled(loser);
            assertThat(winner.isDone()).as("winner still parked while the loser holds its decision").isFalse();
            discoveryGate.countDown();
            List<Exception> failures = awaitAgentInitializations(List.of(winner, loser));

            // The original placeholder winner fails visibly with its
            // cause preserved; the loser recovered as the fresh winner.
            assertThat(failures).as("only the placeholder winner fails visibly").hasSize(1);
            assertThat(failures.get(0).getCause())
                    .as("failure keeps the discovery cause")
                    .hasMessage("boom-retry-discovery");
            assertThat(installedToolMgr.clients).as("winner client plus the loser's fresh client").hasSize(2);
            assertThat(totalConnects() - totalDisconnects()).as("exactly one live connection").isEqualTo(1);
            assertThat(installedToolMgr.listMcpServerIds()).containsExactly("retry-lib");
            assertThat(Runner.resourceMgr().getMcpToolIds("retry-lib"))
                    .as("loser discovered the tools").isNotEmpty();
            assertSingleRecoveringOwner(agentIds, "retry-lib");
        } finally {
            discoveryGate.countDown();
            pool.shutdownNow();
        }
    }

    private void assertSingleRecoveringOwner(List<String> agentIds, String serverId) {
        long taggedAgents = agentIds.stream()
                .filter(id -> Runner.resourceMgr().listMcpServers(id).stream()
                        .map(McpServerConfig::getServerId).anyMatch(serverId::equals))
                .count();
        assertThat(taggedAgents).as("exactly the recovering agent owns the entry tag").isEqualTo(1);
        for (DeepAgent agent : createdAgents) {
            if (agent.isInitialized()) {
                assertThat(agent.getAgent().getAbilityManager().get(serverId))
                        .as("recovering agent sees the ability entry").isNotNull();
            }
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Conflicting pre-registration fails visibly at the facade")
    void conflictingPreRegistration_failsVisibly() throws Exception {
        installToolMgr(new CountingToolMgr());
        GlobalRegistrySnapshot before = GlobalRegistrySnapshot.take();
        runConflictingPreRegistration();
        cleanUpRegisteredState();
        GlobalRegistrySnapshot.take().assertDeltaZero(before);
    }

    private void runConflictingPreRegistration() {
        Runner.resourceMgr().addMcpServer(facadeConfig("conflict-lib", "http://established"), null, null);
        registeredServerIds.add("conflict-lib");

        // Same server id, different connection semantics: the facade's
        // pre-query re-tag path must report the conflict instead of
        // silently merging the two servers (retag conflict check).
        DeepAgent agent = newAgent("mcp-facade-conflict", facadeConfig("conflict-lib", "http://different"));
        assertThatThrownBy(agent::ensureInitialized)
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("already registered with a different config");

        assertThat(totalConnects()).as("conflict detected on the pre-query, no second connection built")
                .isEqualTo(1);
        assertThat(Runner.resourceMgr().getMcpServerConfig("conflict-lib").getServerPath())
                .as("established entry unaffected").isEqualTo("http://established");
        assertThat(agent.getAgent().getAbilityManager().get("conflict-lib"))
                .as("no ability entry for the rejected config").isNull();
    }

    // ========== Fixtures and helpers ==========

    private static Function<McpServerConfig, ? extends CountingMcpClient> defaultClientFactory(
            CountDownLatch connectGate) {
        if (connectGate == null) {
            return config -> plainFacadeToolClient();
        }
        return config -> {
            GatedConnectClient client = new GatedConnectClient(connectGate);
            client.listToolsReturning(List.of(facadeToolCard()));
            return client;
        };
    }

    private static CountingMcpClient plainFacadeToolClient() {
        CountingMcpClient client = new CountingMcpClient();
        client.listToolsReturning(List.of(facadeToolCard()));
        return client;
    }

    private static McpToolCard facadeToolCard() {
        McpToolCard card = new McpToolCard();
        card.setName("facade-tool");
        card.setDescription("facade tool");
        return card;
    }

    private static List<Exception> awaitAgentInitializations(List<Future<Void>> futures) throws Exception {
        List<Exception> failures = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                future.get(60L, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause() instanceof Exception cause ? cause : e);
            } catch (TimeoutException e) {
                dumpAllThreadStacks();
                throw e;
            }
        }
        return failures;
    }

    private static void awaitFutureSettled(Future<?> future) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (!future.isDone() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
    }

    private void awaitFirstDiscoveryStarted() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30L);
        while (!isFirstDiscoveryStarted() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        if (!isFirstDiscoveryStarted()) {
            throw new IllegalStateException("winner never parked inside its discovery RPC");
        }
    }

    private boolean isFirstDiscoveryStarted() {
        if (installedToolMgr.clients.isEmpty()) {
            return false;
        }
        if (!(installedToolMgr.clients.get(0) instanceof GatedFailFirstClient gatedClient)) {
            throw new IllegalStateException("expected the first client to gate its first discovery");
        }
        return gatedClient.firstDiscoveryStarted();
    }

    private static McpServerConfig facadeConfig(String serverName, String serverPath) {
        return McpServerConfig.builder()
                .serverName(serverName)
                .serverPath(serverPath)
                .build();
    }

    private DeepAgent newAgent(String agentId, McpServerConfig mcpConfig) {
        return newAgent(agentId, List.of(mcpConfig));
    }

    private DeepAgent newAgent(String agentId, List<McpServerConfig> mcpConfigs) {
        for (McpServerConfig config : mcpConfigs) {
            registeredServerIds.add(config.getServerId());
        }
        DeepAgent agent = new DeepAgent(
                AgentCard.builder().id(agentId).name(agentId).description("MCP facade test agent").build(),
                DeepAgentConfig.builder().mcps(mcpConfigs).build(),
                Workspace.builder().rootPath(WORKSPACE).language("en").build());
        createdAgents.add(agent);
        return agent;
    }

    private void installToolMgr(CountingToolMgr toolMgr) throws Exception {
        installedToolMgr = toolMgr;
        setFieldValue(fieldValue(resourceMgr, "resourceRegistry"), "toolMgr", toolMgr);
    }

    private void cleanUpRegisteredState() {
        for (DeepAgent agent : createdAgents) {
            // Best-effort cleanup: destroy is one-shot and tolerant, so a
            // failure here is intentionally contained and dropped.
            IsolatedActions.runIsolated(agent::destroy);
        }
        createdAgents.clear();
        for (String serverId : registeredServerIds) {
            // Best-effort cleanup between tests.
            IsolatedActions.runIsolated(() -> Runner.resourceMgr()
                    .removeMcpServer(serverId, null, null, TagMatchStrategy.ALL, true));
        }
        registeredServerIds.clear();
    }

    private int totalConnects() {
        return installedToolMgr.clients.stream().mapToInt(CountingMcpClient::connectCalls).sum();
    }

    private int totalDisconnects() {
        return installedToolMgr.clients.stream().mapToInt(CountingMcpClient::disconnectCalls).sum();
    }

    private static List<Exception> runAll(List<Callable<Void>> tasks, int threads) throws Exception {
        ExecutorService pool = newPool(threads);
        try {
            List<Exception> failures = new ArrayList<>();
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            for (Future<Void> future : futures) {
                try {
                    future.get(60L, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause() instanceof Exception cause ? cause : e);
                } catch (TimeoutException e) {
                    dumpAllThreadStacks();
                    throw e;
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void dumpAllThreadStacks() {
        StringBuilder dump = new StringBuilder("registration tasks timed out - all thread stacks:\n");
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            dump.append("  \"").append(entry.getKey().getName()).append("\" state=")
                    .append(entry.getKey().getState()).append('\n');
            for (StackTraceElement element : entry.getValue()) {
                dump.append("    at ").append(element).append('\n');
            }
        }
        Loggers.COMMON.error("{}", dump);
    }

    private static Object fieldValue(Object target, String field) throws Exception {
        Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        return declared.get(target);
    }

    private static void setFieldValue(Object target, String field, Object value) throws Exception {
        Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    private static ExecutorService newPool(int threads) {
        AtomicInteger seq = new AtomicInteger();
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(threads * 2), runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("mcp-facade-" + seq.incrementAndGet());
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                        Loggers.COMMON.error("Uncaught exception in {}: {}",
                            failedThread.getName(), error.getMessage()));
                    return thread;
                });
    }
}
