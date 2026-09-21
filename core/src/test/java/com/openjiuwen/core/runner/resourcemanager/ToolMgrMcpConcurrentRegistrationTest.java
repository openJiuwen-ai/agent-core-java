/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.mcp.CountingMcpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerAlreadyRegisteredError;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfigConflictError;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * ToolMgr MCP concurrent registration tests: the placeholder claim makes concurrent same-server
 * registrations resolve to exactly one winner whose connection stays
 * live, while every loser and every failed winner recycles its own
 * connection; the discovery RPC carries a bounded timeout; the read
 * path refresh interleaves with registration without losing entries.
 * <p>
 * Layer note: these cases drive
 * {@link ToolMgr#addToolServer} / {@link ToolMgr#refreshToolServer}
 * directly with {@link CountingMcpClient} doubles — the same entry
 * points the ResourceMgr read path reaches; the caller-visible "all
 * attempts succeed" contract of the DeepAgent facade is covered
 * separately by DeepAgentMcpConcurrentRegistrationTest.
 * <p>
 * Placeholder-window serialization: the cases pin that a
 * removal or a refresh arriving inside a mid-flight placeholder window
 * waits for the registration's terminal state — no orphan tools, no
 * dangling name index, no wedged re-registration — and that a forced
 * refresh of a committed slot re-discovers idempotently instead of
 * colliding with its own earlier registration.
 */
@DisplayName("ToolMgr MCP concurrent registration")
class ToolMgrMcpConcurrentRegistrationTest {
    private static final int CONCURRENT_REGISTRANTS = 4;

    /**
     * Client whose connect() returns only after every sibling client has
     * connected (second-level latch inside the mock, so all
     * N registrants deterministically reach the placeholder race with a
     * live connection each). The wait is bounded so a derailed sibling
     * fails fast instead of parking the others forever.
     */
    static class GatedConnectClient extends CountingMcpClient {
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
     * Gated client whose disconnect always fails after being counted: the
     * loser's connection recycle itself misbehaves, and the registration
     * error must still surface.
     */
    static final class FailingDisconnectGatedClient extends GatedConnectClient {
        FailingDisconnectGatedClient(CountDownLatch connectGate) {
            super(connectGate);
        }

        @Override
        public boolean disconnect(float timeout) throws Exception {
            super.disconnect(timeout);
            throw new IllegalStateException("boom-disconnect");
        }
    }

    /**
     * Client whose first listTools() blocks until released; later calls
     * answer immediately. Pins the placeholder discovery window open so a
     * concurrent refresh can observe the placeholder entry.
     */
    static final class FirstCallGatedClient extends CountingMcpClient {
        private final CountDownLatch firstCallGate;
        private final AtomicInteger discoveryCalls = new AtomicInteger();

        FirstCallGatedClient(CountDownLatch firstCallGate) {
            this.firstCallGate = firstCallGate;
        }

        @Override
        public List<Object> listTools(float timeout) throws Exception {
            if (discoveryCalls.getAndIncrement() == 0 && !firstCallGate.await(30L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("discovery gate was never released");
            }
            return super.listTools(timeout);
        }

        boolean firstDiscoveryStarted() {
            return discoveryCalls.get() > 0;
        }
    }

    /**
     * Discovery client whose first listTools() parks on the gate and whose
     * later calls record whether the first call had already returned. The
     * record makes "the refresh's re-discovery observed a committed entry,
     * not a mid-flight placeholder" assertable without timing:
     * it only reads true when the winner's own discovery fully completed
     * first.
     */
    static final class RefreshOrderProbeClient extends CountingMcpClient {
        private final CountDownLatch firstDiscoveryGate;
        private final AtomicInteger discoveryCalls = new AtomicInteger();
        private volatile boolean hasFirstDiscoveryReturned;
        private final AtomicBoolean laterDiscoverySawFirstReturn = new AtomicBoolean(true);

        RefreshOrderProbeClient(CountDownLatch firstDiscoveryGate) {
            this.firstDiscoveryGate = firstDiscoveryGate;
        }

        @Override
        public List<Object> listTools(float timeout) throws Exception {
            if (discoveryCalls.getAndIncrement() == 0) {
                if (!firstDiscoveryGate.await(30L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("discovery gate was never released");
                }
                List<Object> cards = super.listTools(timeout);
                hasFirstDiscoveryReturned = true;
                return cards;
            }
            laterDiscoverySawFirstReturn.set(hasFirstDiscoveryReturned);
            return super.listTools(timeout);
        }

        boolean firstDiscoveryStarted() {
            return discoveryCalls.get() > 0;
        }

        boolean laterDiscoverySawFirstReturn() {
            return laterDiscoverySawFirstReturn.get();
        }
    }

    /**
     * ToolMgr that creates one counting client per createClient call from
     * the given config-aware factory and records every client for count
     * aggregation.
     */
    static final class RecordingToolMgr extends ToolMgr {
        final List<CountingMcpClient> clients = new CopyOnWriteArrayList<>();

        private final Function<McpServerConfig, ? extends CountingMcpClient> clientFactory;

        RecordingToolMgr(Function<McpServerConfig, ? extends CountingMcpClient> clientFactory) {
            this.clientFactory = clientFactory;
        }

        @Override
        protected McpClient createClient(McpServerConfig config) {
            CountingMcpClient client = clientFactory.apply(config);
            clients.add(client);
            return client;
        }
    }

    /**
     * Outcome of concurrent registration attempts, classified by the two
     * recognizable loser shapes. Any other failure propagates out of the
     * worker future and fails the test directly.
     */
    private static final class RegistrationOutcome {
        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger alreadyRegistered = new AtomicInteger();
        final AtomicInteger conflicts = new AtomicInteger();
    }

    @Test
    @Timeout(60)
    @DisplayName("Concurrent same-server registration keeps one live connection")
    void concurrentSameServerRegistration_exactlyOneWinnerLosersRecycled() throws Exception {
        CountDownLatch connectGate = new CountDownLatch(CONCURRENT_REGISTRANTS);
        RecordingToolMgr mgr = new RecordingToolMgr(
                config -> gatedClientListing(connectGate, toolCard("echo")));
        McpServerConfig config = pathConfig("race-srv", "race-name", "http://race");

        runConcurrentRegistrations(mgr, List.of(config), CONCURRENT_REGISTRANTS, outcome -> {
            assertThat(outcome.successes.get()).as("winner registrations").isEqualTo(1);
            assertThat(outcome.alreadyRegistered.get()).as("recognizable loser errors")
                    .isEqualTo(CONCURRENT_REGISTRANTS - 1);
        });

        assertThat(mgr.listMcpServerIds()).as("registration entries").containsExactly("race-srv");
        assertThat(mgr.getMcpTool("echo", "race-srv")).as("winner tool registered").isNotNull();
        assertThat(totalConnects(mgr)).as("every registrant connected before the race")
                .isEqualTo(CONCURRENT_REGISTRANTS);
        assertThat(totalDisconnects(mgr)).as("every loser recycled its connection")
                .isEqualTo(CONCURRENT_REGISTRANTS - 1);

        // Supplementary registration after the winner finished: same
        // recognizable error — carrying the occupied server id — via the
        // fast path, and no client is built.
        assertThatThrownBy(() -> mgr.addToolServer(config, null))
                .isInstanceOfSatisfying(McpServerAlreadyRegisteredError.class,
                        error -> assertThat(error.getServerId()).isEqualTo("race-srv"));
        assertThat(totalConnects(mgr)).as("fast path never builds a connection")
                .isEqualTo(CONCURRENT_REGISTRANTS);
    }

    @Test
    @Timeout(60)
    @DisplayName("Removal during the placeholder window leaves no orphan state")
    void removalDuringPlaceholderWindow_leavesNoOrphanState() throws Exception {
        CountDownLatch firstDiscoveryGate = new CountDownLatch(1);
        AtomicInteger creations = new AtomicInteger();
        RecordingToolMgr mgr = new RecordingToolMgr(config -> {
            CountingMcpClient client;
            if (creations.incrementAndGet() == 1) {
                client = gatedDiscoveryClient(firstDiscoveryGate, toolCard("echo"));
            } else {
                client = clientListing(toolCard("echo"));
            }
            return client;
        });
        McpServerConfig config = pathConfig("race-srv", "race-name", "http://race");

        ExecutorService pool = newPool(2);
        try {
            Future<String> winner = pool.submit(() -> {
                mgr.addToolServer(config, null);
                return "ok";
            });
            // Park the winner inside its discovery RPC, then remove the
            // server from under the mid-flight placeholder.
            awaitFirstDiscoveryStarted(mgr);
            Future<List<String>> remover = pool.submit(() -> mgr.removeToolServer("race-srv", true));
            firstDiscoveryGate.countDown();
            assertThat(winner.get(30L, TimeUnit.SECONDS)).as("winner outcome").isEqualTo("ok");
            List<String> removedTools = remover.get(30L, TimeUnit.SECONDS);

            // Debt #7 R1: without serialization the winner's late commit
            // registers orphan tools and a dangling name index after the
            // removal already passed, and every re-registration then fails
            // with "already exist tool" forever.
            assertThat(removedTools).as("removal cleans the committed tool ids")
                    .isEqualTo(List.of(ToolMgr.generateMcpToolId("race-srv", "race-name", "echo")));
            assertThat(mgr.listMcpServerIds()).as("no entry left").isEmpty();
            assertThat(mgr.getTool(ToolMgr.generateMcpToolId("race-srv", "race-name", "echo")))
                    .as("no orphan tool survives the removal").isNull();
            assertThat(mgr.getMcpServerIds("race-name")).as("no dangling name index").isEmpty();
            assertThatCode(() -> mgr.addToolServer(config, null))
                    .as("re-registration after the window is not wedged")
                    .doesNotThrowAnyException();
            assertThat(mgr.listMcpServerIds()).containsExactly("race-srv");
            assertThat(mgr.getMcpTool("echo", "race-srv")).isNotNull();
            assertThat(totalConnects(mgr)).isEqualTo(2);
            assertThat(totalDisconnects(mgr)).isEqualTo(1);
        } finally {
            firstDiscoveryGate.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Conflicting config fails visibly, winner unaffected")
    void conflictingConfigRegistration_failsVisiblyAndKeepsWinner() throws Exception {
        RecordingToolMgr mgr = new RecordingToolMgr(config -> clientListing(toolCard("shared")));
        McpServerConfig first = pathConfig("conflict-srv", "conflict-name", "http://first");
        McpServerConfig second = pathConfig("conflict-srv", "conflict-name", "http://second");

        // Sequential phase: the established entry reports the conflict by
        // the recognizable type, not a generic "already exist" message.
        mgr.addToolServer(first, null);
        assertThatThrownBy(() -> mgr.addToolServer(second, null))
                .isInstanceOf(McpServerConfigConflictError.class)
                .hasFieldOrPropertyWithValue("serverId", "conflict-srv");
        assertThat(mgr.listMcpServerIds()).containsExactly("conflict-srv");
        assertThat(mgr.getMcpServerConfig("conflict-srv").getServerPath()).isEqualTo("http://first");
        assertThat(totalConnects(mgr)).as("fast path conflict builds no connection").isEqualTo(1);

        // Concurrent phase: both connect, the loser recycles its connection.
        CountDownLatch connectGate = new CountDownLatch(2);
        RecordingToolMgr raceMgr = new RecordingToolMgr(
                config -> gatedClientListing(connectGate, toolCard("shared")));
        runConcurrentRegistrations(raceMgr, List.of(first, second), 2, outcome -> {
            assertThat(outcome.successes.get()).isEqualTo(1);
            assertThat(outcome.conflicts.get()).as("conflicting loser error").isEqualTo(1);
        });
        assertThat(totalConnects(raceMgr)).isEqualTo(2);
        assertThat(totalDisconnects(raceMgr)).as("conflicting loser recycled its connection").isEqualTo(1);
        assertThat(raceMgr.listMcpServerIds()).containsExactly("conflict-srv");
        assertThat(raceMgr.getMcpTool("shared", "conflict-srv")).isNotNull();
    }

    @Test
    @Timeout(60)
    @DisplayName("Winner discovery failure rolls back placeholder and connection")
    void winnerDiscoveryFailure_rollsBackPlaceholderAndConnection() throws Exception {
        AtomicBoolean failDiscovery = new AtomicBoolean(true);
        IllegalStateException discoveryFailure = new IllegalStateException("boom-list-tools");
        RecordingToolMgr mgr = new RecordingToolMgr(config -> {
            CountingMcpClient client = new CountingMcpClient();
            if (failDiscovery.get()) {
                client.failListToolsWith(discoveryFailure);
            } else {
                client.listToolsReturning(List.of(toolCard("retry")));
            }
            return client;
        });
        McpServerConfig config = pathConfig("rollback-srv", "rollback-name", "http://rollback");

        assertThatThrownBy(() -> mgr.addToolServer(config, null))
                .hasCause(discoveryFailure);
        assertThat(mgr.listMcpServerIds()).as("placeholder released, no dead tag").isEmpty();
        assertThat(mgr.clients.get(0).disconnectCalls()).as("failed winner recycled its connection").isEqualTo(1);

        // Retry with a working client succeeds on the same server id.
        failDiscovery.set(false);
        mgr.addToolServer(config, null);
        assertThat(mgr.listMcpServerIds()).containsExactly("rollback-srv");
        assertThat(mgr.getMcpTool("retry", "rollback-srv")).isNotNull();
    }

    @Test
    @Timeout(60)
    @DisplayName("Winner connect failure leaves no residue")
    void winnerConnectFailure_leavesNoResidue() throws Exception {
        AtomicBoolean failConnect = new AtomicBoolean(true);
        IllegalStateException connectFailure = new IllegalStateException("boom-connect");
        RecordingToolMgr mgr = new RecordingToolMgr(config -> {
            CountingMcpClient client = new CountingMcpClient();
            if (failConnect.get()) {
                client.failConnectWith(connectFailure);
            } else {
                client.listToolsReturning(List.of(toolCard("after")));
            }
            return client;
        });
        McpServerConfig config = pathConfig("connect-fail-srv", "connect-fail-name", "http://connect-fail");

        assertThatThrownBy(() -> mgr.addToolServer(config, null))
                .hasCause(connectFailure);
        assertThat(mgr.listMcpServerIds()).as("no entry after failed connect").isEmpty();
        assertThat(mgr.clients.get(0).disconnectCalls()).as("partially-connected client recycled").isEqualTo(1);

        failConnect.set(false);
        mgr.addToolServer(config, null);
        assertThat(mgr.listMcpServerIds()).containsExactly("connect-fail-srv");
        assertThat(mgr.clients.get(1).successfulConnects()).isEqualTo(1);
    }

    @Test
    @Timeout(60)
    @DisplayName("Connect failure shapes recycle the client and report by status")
    void connectFailureShapes_recycleClientAndReportByStatus() throws Exception {
        // A refused connect surfaces the connection status code and the
        // partially-connected client is recycled.
        RecordingToolMgr refusing = new RecordingToolMgr(config -> {
            CountingMcpClient client = new CountingMcpClient();
            client.refuseConnect();
            return client;
        });
        assertThatThrownBy(() -> refusing.addToolServer(pathConfig("refuse-srv", "refuse-name", "http://refuse"), null))
                .isInstanceOf(BaseError.class)
                .extracting(e -> {
                    if (!(e instanceof BaseError error)) {
                        throw new IllegalStateException("expected a BaseError from a refused connect");
                    }
                    return error.getStatus();
                })
                .isEqualTo(StatusCode.RESOURCE_MCP_SERVER_CONNECTION_ERROR);
        assertThat(refusing.listMcpServerIds()).isEmpty();
        assertThat(refusing.clients.get(0).disconnectCalls()).isEqualTo(1);

        // A throwing connect without message keeps the cause and falls
        // back to a non-empty reason.
        AtomicBoolean failConnect = new AtomicBoolean(true);
        RecordingToolMgr nullMessage = new RecordingToolMgr(config -> {
            CountingMcpClient client = new CountingMcpClient();
            if (failConnect.get()) {
                client.failConnectWith(new NullPointerException());
            } else {
                client.listToolsReturning(List.of(toolCard("after-refuse")));
            }
            return client;
        });
        assertThatThrownBy(() -> nullMessage
                .addToolServer(pathConfig("null-msg-srv", "null-msg-name", "http://nullmsg"), null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("connect failed")
                .hasCauseInstanceOf(NullPointerException.class);
        assertThat(nullMessage.clients.get(0).disconnectCalls()).isEqualTo(1);
        assertThat(nullMessage.listMcpServerIds()).isEmpty();

        // A positive connect timeout is passed through to the client.
        failConnect.set(false);
        McpServerConfig timeoutConfig = pathConfig("ctimeout-srv", "ctimeout-name", "http://ctimeout");
        timeoutConfig.setConnectTimeoutSeconds(5.0);
        nullMessage.addToolServer(timeoutConfig, null);
        assertThat(nullMessage.clients.get(1).connectTimeouts()).containsExactly(5.0f);
        assertThat(nullMessage.listMcpServerIds()).containsExactly("ctimeout-srv");
    }

    @Test
    @Timeout(60)
    @DisplayName("Failing loser disconnect does not mask the registration error")
    void failingLoserDisconnect_doesNotMaskRegistrationError() throws Exception {
        CountDownLatch connectGate = new CountDownLatch(2);
        RecordingToolMgr mgr = new RecordingToolMgr(config -> {
            FailingDisconnectGatedClient client = new FailingDisconnectGatedClient(connectGate);
            client.listToolsReturning(List.of(toolCard("masked")));
            return client;
        });
        McpServerConfig config = pathConfig("mask-srv", "mask-name", "http://mask");
        runConcurrentRegistrations(mgr, List.of(config), 2, outcome -> {
            assertThat(outcome.successes.get()).isEqualTo(1);
            assertThat(outcome.alreadyRegistered.get())
                    .as("primary loser error surfaces despite disconnect failure").isEqualTo(1);
        });
        assertThat(totalDisconnects(mgr))
                .as("loser cleanup was attempted once and its failure swallowed").isEqualTo(1);
        assertThat(mgr.listMcpServerIds()).containsExactly("mask-srv");
        assertThat(mgr.getMcpTool("masked", "mask-srv")).isNotNull();
    }

    @Test
    @Timeout(60)
    @DisplayName("Zero-tool server registers cleanly")
    void zeroToolServer_registersCleanly() throws Exception {
        RecordingToolMgr mgr = new RecordingToolMgr(config -> new CountingMcpClient());
        McpServerConfig config = pathConfig("empty-srv", "empty-name", "http://empty");

        List<McpToolCard> cards = mgr.addToolServer(config, null);

        assertThat(cards).isEmpty();
        assertThat(mgr.listMcpServerIds()).containsExactly("empty-srv");
        assertThat(mgr.getMcpToolId("empty-srv", null)).as("empty tool id list, entry not polluted")
                .isEqualTo(List.of());
        assertThat(mgr.getMcpServerIds("empty-name")).containsExactly("empty-srv");
        assertThat(totalDisconnects(mgr)).as("live connection kept").isEqualTo(0);
    }

    @Test
    @Timeout(60)
    @DisplayName("Registration interleaved with read-path refresh keeps every entry")
    void registrationVersusRefreshInterleave_noLeakNoLostEntries() throws Exception {
        List<String> refreshTargets = List.of("mix-z0", "mix-z1", "mix-z2");
        List<String> toolServers = List.of("mix-t3", "mix-t4", "mix-t5");
        List<String> allServers = new ArrayList<>(refreshTargets);
        allServers.addAll(toolServers);
        List<String> duplicated = List.of("mix-z0", "mix-z1", "mix-t3", "mix-t5");
        // Zero-tool servers stay refreshable (re-discovery re-registers
        // nothing); tool-bearing servers register one tool each.
        RecordingToolMgr mgr = new RecordingToolMgr(config -> config.getServerId().startsWith("mix-t")
                ? clientListing(toolCard(config.getServerId() + "-tool"))
                : new CountingMcpClient());

        ExecutorService pool = newPool(16);
        try {
            List<Future<String>> futures = new ArrayList<>();
            submitRegistrations(pool, futures, mgr, allServers, duplicated);
            submitReaders(pool, futures, mgr, refreshTargets, toolServers);
            int successes = 0;
            int losers = 0;
            int readers = 0;
            for (Future<String> future : futures) {
                String result = future.get(60L, TimeUnit.SECONDS);
                switch (result) {
                    case "ok" -> successes++;
                    case "loser" -> losers++;
                    case "reader-done" -> readers++;
                    default -> throw new IllegalStateException("unexpected task result: " + result);
                }
            }
            assertThat(successes).as("registrations succeeded").isEqualTo(allServers.size());
            assertThat(losers).as("duplicate registrations lost the race").isEqualTo(duplicated.size());
            assertThat(readers).as("read-path tasks completed")
                    .isEqualTo(futures.size() - allServers.size() - duplicated.size());
        } finally {
            pool.shutdownNow();
        }

        assertThat(mgr.listMcpServerIds()).as("no entry lost")
                .containsExactlyInAnyOrderElementsOf(allServers);
        for (String toolServer : toolServers) {
            assertThat(mgr.getMcpTool(toolServer + "-tool", toolServer))
                    .as("tool of %s registered", toolServer).isNotNull();
        }
        // Robust invariants: the
        // fast path may fail a late duplicate without ever building a
        // connection, so connect counts are only bounded below; what must
        // hold in every interleaving is "no leaked connection" — the live
        // count equals the distinct server count.
        assertThat(totalConnects(mgr)).as("at least one connection per server")
                .isGreaterThanOrEqualTo(allServers.size());
        assertThat(totalConnects(mgr) - totalDisconnects(mgr)).as("live connections equal distinct servers")
                .isEqualTo(allServers.size());
    }

    @Test
    @Timeout(60)
    @DisplayName("Refresh during a pending registration waits for the committed entry")
    void refreshDuringPendingRegistration_waitsForCommittedEntry() throws Exception {
        CountDownLatch firstDiscoveryGate = new CountDownLatch(1);
        RefreshOrderProbeClient probe = new RefreshOrderProbeClient(firstDiscoveryGate);
        probe.listToolsReturning(List.of(toolCard("stolen")));
        RecordingToolMgr mgr = new RecordingToolMgr(config -> probe);
        McpServerConfig config = pathConfig("window-srv", "window-name", "http://window");

        ExecutorService pool = newPool(2);
        try {
            Future<String> winner = pool.submit(() -> {
                mgr.addToolServer(config, null);
                return "ok";
            });
            // Park the winner inside its first discovery RPC, then issue a
            // forced refresh into the open placeholder window.
            while (!probe.firstDiscoveryStarted()) {
                Thread.sleep(10L);
            }
            Future<List<McpToolCard>> refresh = pool.submit(() -> mgr.refreshToolServer("window-srv", true, true));
            firstDiscoveryGate.countDown();
            assertThat(winner.get(30L, TimeUnit.SECONDS)).as("winner commits its own registration")
                    .isEqualTo("ok");
            List<McpToolCard> refreshed = refresh.get(30L, TimeUnit.SECONDS);

            // Debt #7 R2: the placeholder window is invisible to a
            // refresh — it must serialize behind the in-flight
            // registration instead of adopting its client, so the winner
            // commits and the refresh re-discovers the committed entry
            // idempotently.
            assertThat(probe.laterDiscoverySawFirstReturn())
                    .as("refresh re-discovered after the winner committed").isTrue();
            assertThat(refreshed).as("refresh re-registered the committed tool").hasSize(1);
            assertThat(mgr.listMcpServerIds()).containsExactly("window-srv");
            assertThat(mgr.getMcpTool("stolen", "window-srv")).isNotNull();
            assertThat(totalDisconnects(mgr)).as("entry keeps its client alive").isEqualTo(0);
        } finally {
            firstDiscoveryGate.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Discovery RPC receives the configured or fallback timeout")
    void discoveryRpcReceivesBoundedTimeout() throws Exception {
        RecordingToolMgr mgr = new RecordingToolMgr(config -> new CountingMcpClient());
        McpServerConfig positive = pathConfig("timeout-pos", "timeout-name", "http://pos");
        positive.setCallTimeoutSeconds(5.0);
        McpServerConfig sentinel = pathConfig("timeout-neg", "timeout-name", "http://neg");
        sentinel.setCallTimeoutSeconds((double) McpServerConfig.NO_TIMEOUT);
        McpServerConfig unset = pathConfig("timeout-unset", "timeout-name", "http://unset");

        mgr.addToolServer(positive, null);
        mgr.addToolServer(sentinel, null);
        mgr.addToolServer(unset, null);

        assertThat(mgr.clients.get(0).listToolsTimeouts()).containsExactly(5.0f);
        assertThat(mgr.clients.get(1).listToolsTimeouts()).as("NO_TIMEOUT sentinel never reaches the client")
                .containsExactly(30.0f);
        assertThat(mgr.clients.get(2).listToolsTimeouts()).containsExactly(30.0f);

        // The read-path refresh carries the same bounded timeout.
        positive.setCallTimeoutSeconds(7.0);
        mgr.refreshToolServer("timeout-pos", true, true);
        assertThat(mgr.clients.get(0).listToolsTimeouts()).containsExactly(5.0f, 7.0f);
    }

    // ========== Fixtures and helpers ==========

    private static CountingMcpClient clientListing(McpToolCard card) {
        CountingMcpClient client = new CountingMcpClient();
        client.listToolsReturning(List.of(card));
        return client;
    }

    private static GatedConnectClient gatedClientListing(CountDownLatch connectGate, McpToolCard card) {
        GatedConnectClient client = new GatedConnectClient(connectGate);
        client.listToolsReturning(List.of(card));
        return client;
    }

    private static FirstCallGatedClient gatedDiscoveryClient(CountDownLatch firstDiscoveryGate, McpToolCard card) {
        FirstCallGatedClient client = new FirstCallGatedClient(firstDiscoveryGate);
        client.listToolsReturning(List.of(card));
        return client;
    }

    private static McpToolCard toolCard(String name) {
        McpToolCard card = new McpToolCard();
        card.setName(name);
        card.setDescription("card " + name);
        return card;
    }

    /**
     * Waits until the first recorded client (a gated discovery double in
     * these tests) has entered its first listTools RPC, so the caller can
     * act while the winner is parked inside the placeholder window.
     *
     * @param mgr the recording manager whose first client gates its first discovery
     * @throws InterruptedException when the wait polling is interrupted
     */
    private static void awaitFirstDiscoveryStarted(RecordingToolMgr mgr) throws InterruptedException {
        while (!isFirstDiscoveryStarted(mgr)) {
            Thread.sleep(10L);
        }
    }

    private static boolean isFirstDiscoveryStarted(RecordingToolMgr mgr) {
        if (mgr.clients.isEmpty()) {
            return false;
        }
        if (!(mgr.clients.get(0) instanceof FirstCallGatedClient gatedClient)) {
            throw new IllegalStateException("expected the first client to gate its first discovery");
        }
        return gatedClient.firstDiscoveryStarted();
    }

    private static McpServerConfig pathConfig(String serverId, String serverName, String serverPath) {
        return McpServerConfig.builder()
                .serverId(serverId)
                .serverName(serverName)
                .serverPath(serverPath)
                .build();
    }

    private void runConcurrentRegistrations(ToolMgr mgr, List<McpServerConfig> configs, int threads,
            Consumer<RegistrationOutcome> assertions) throws Exception {
        ExecutorService pool = newPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            RegistrationOutcome outcome = new RegistrationOutcome();
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                McpServerConfig config = configs.get(t % configs.size());
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        mgr.addToolServer(config, null);
                        outcome.successes.incrementAndGet();
                    } catch (McpServerAlreadyRegisteredError e) {
                        outcome.alreadyRegistered.incrementAndGet();
                    } catch (McpServerConfigConflictError e) {
                        outcome.conflicts.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60L, TimeUnit.SECONDS);
            }
            assertions.accept(outcome);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void submitRegistrations(ExecutorService pool, List<Future<String>> futures, ToolMgr mgr,
            List<String> allServers, List<String> duplicated) {
        for (String serverId : allServers) {
            int copies = duplicated.contains(serverId) ? 2 : 1;
            for (int copy = 0; copy < copies; copy++) {
                futures.add(pool.submit(() -> {
                    try {
                        mgr.addToolServer(pathConfig(serverId, serverId, "http://" + serverId), null);
                        return "ok";
                    } catch (McpServerAlreadyRegisteredError e) {
                        return "loser";
                    }
                }));
            }
        }
    }

    private static void submitReaders(ExecutorService pool, List<Future<String>> futures, ToolMgr mgr,
            List<String> refreshTargets, List<String> toolServers) {
        for (int reader = 0; reader < 6; reader++) {
            final int readerIndex = reader;
            futures.add(pool.submit(() -> {
                for (int round = 0; round < 50; round++) {
                    String zeroServer = refreshTargets.get((readerIndex + round) % refreshTargets.size());
                    // The same refresh the ResourceMgr read path issues.
                    mgr.refreshToolServer(zeroServer, true, true);
                    String toolServer = toolServers.get((readerIndex + round) % toolServers.size());
                    mgr.getMcpToolId(toolServer, null);
                    mgr.getMcpTools(zeroServer);
                }
                return "reader-done";
            }));
        }
    }

    private static int totalConnects(RecordingToolMgr mgr) {
        return mgr.clients.stream().mapToInt(CountingMcpClient::connectCalls).sum();
    }

    private static int totalDisconnects(RecordingToolMgr mgr) {
        return mgr.clients.stream().mapToInt(CountingMcpClient::disconnectCalls).sum();
    }

    private static ExecutorService newPool(int threads) {
        AtomicInteger seq = new AtomicInteger();
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(threads * 2), runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("mcp-concurrent-" + seq.incrementAndGet());
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                        Loggers.COMMON.error("Uncaught exception in {}: {}",
                            failedThread.getName(), error.getMessage()));
                    return thread;
                });
    }
}
