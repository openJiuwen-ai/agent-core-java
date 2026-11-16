/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.store.kv.DbBasedKVStore;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

/**
 * SQLite-backed concurrency cases: concurrent multi-session saves over one
 * shared file-database checkpointer stay isolated - no cross contamination
 * between session blobs, cursors or journal trails - and a single session
 * hammering sequential boundary saves never corrupts the file.
 */
class StepCheckpointSqliteConcurrencyTest {
    private static final String AGENT_DESCRIPTION = "step sqlite concurrency agent";

    private PersistenceCheckpointer sqliteCheckpointer;

    @TempDir
    Path tempDir;

    private DbBasedKVStore sqliteStore;

    @BeforeEach
    void installSqliteCheckpointer() throws Exception {
        sqliteStore = new DbBasedKVStore(sqliteDataSource());
        sqliteCheckpointer = new PersistenceCheckpointer(sqliteStore);
        CheckpointerFactory.setDefaultCheckpointer(sqliteCheckpointer);
    }

    @AfterEach
    void releaseSqliteCheckpointer() {
        CheckpointerFactory.releaseDefaultCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    @Test
    @DisplayName("3.18 concurrent multi-session saves over one SQLite file stay isolated")
    void concurrentSavesStayIsolatedPerSession() throws Exception {
        int sessionCount = 4;
        ExecutorService pool = Executors.newFixedThreadPool(sessionCount);
        try {
            List<String> sessionIds = new ArrayList<>();
            for (int index = 0; index < sessionCount; index++) {
                sessionIds.add("sqlite-session-" + index);
            }
            CountDownLatch ready = new CountDownLatch(sessionCount);
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (String sessionId : sessionIds) {
                futures.add(CompletableFuture.runAsync(() -> {
                    ready.countDown();
                    awaitStart(start);
                    runOneSessionTask(sessionId);
                }, pool));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);

            for (String sessionId : sessionIds) {
                // The verification session carries the same card id as the
                // crashed worker so recovery reads the exact keys the worker
                // wrote - otherwise the agent id would resolve to empty and
                // the lookup would miss the stored blob.
                AgentSession verify = new AgentSession(sessionId, null, probeAgentCard(sessionId));
                verify.preRun(Map.of());
                StepCursor cursor = sqliteCheckpointer.lastCompletedStep(verify.getInner());
                assertThat(cursor)
                        .as("session %s must have a persisted cursor", sessionId)
                        .isNotNull();
                assertThat(cursor.getStepType()).isEqualTo("TOOL_CALL");
                assertThat(cursor.getStatus()).isEqualTo(StepStatus.COMPLETED);

                List<StepRecord> trail = new KvStepJournal(sqliteStore).replay(sessionId);
                assertThat(trail).isNotEmpty();
                assertThat(trail).allMatch(record -> sessionId.equals(record.getSessionId()));
            }

            // No session was left RUNNING and no half-written dump_type-only
            // key survived: every dump_type has its blob pair.
            assertThat(sqliteCheckpointer.scanRunningSteps()).isEmpty();
            Map<String, Object> allKeys = sqliteStore.getByPrefix("").join();
            assertThat(allKeys.keySet().stream().filter(key -> key.endsWith(":agent_state_blobs")))
                    .hasSize(sessionCount);
            for (String key : allKeys.keySet()) {
                if (key.endsWith(":agent_state_blobs_dump_type")) {
                    String blobKey = key.substring(0, key.length() - "_dump_type".length());
                    assertThat(allKeys.keySet())
                            .as("dump_type key %s must have its blob pair", key)
                            .contains(blobKey);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("3.18 high-frequency single-session saves never corrupt the SQLite file")
    void highFrequencySavesKeepFileHealthy() {
        String sessionId = "sqlite-hf-session";
        AgentCard card = probeAgentCard(sessionId);
        AgentSession session = new AgentSession(sessionId, null, card);
        session.preRun(Map.of());

        // One session hammering the shared file with sequential boundary
        // saves; every save is a two-write transaction and the next read
        // must always see the complete pair with the latest cursor.
        for (int seq = 1; seq <= 20; seq++) {
            sqliteCheckpointer.stepCompleted(session.getInner(),
                    new StepCursor(seq, "TOOL_CALL", "probe_a", StepStatus.COMPLETED,
                            System.currentTimeMillis()));
        }

        AgentSession verify = new AgentSession(sessionId, null, card);
        verify.preRun(Map.of());
        StepCursor cursor = sqliteCheckpointer.lastCompletedStep(verify.getInner());
        assertThat(cursor).isNotNull();
        assertThat(cursor.getSeq()).isEqualTo(20L);
        assertThat(cursor.getStatus()).isEqualTo(StepStatus.COMPLETED);
        assertThat(sqliteCheckpointer.scanRunningSteps()).isEmpty();
    }

    private void runOneSessionTask(String sessionId) {
        AgentCard card = probeAgentCard(sessionId);
        ReActAgent taskAgent = new ReActAgent(card);
        taskAgent.configure(ReActAgentConfig.builder()
                .maxIterations(5)
                .shouldFailTaskOnToolError(false)
                .stepCheckpointPolicy(StepCheckpointPolicy.STEP_ALWAYS)
                .build());
        taskAgent.getAbilityManager().addAbility(probeToolCard("probe_a"), probeTool("probe_a"));
        taskAgent.getAbilityManager().addAbility(probeToolCard("probe_b"), probeTool("probe_b"));
        taskAgent.setLlm(scriptedModel(new AtomicInteger()));
        AgentSession session = new AgentSession(sessionId, null, card);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) taskAgent.invoke(
                Map.of("query", "task for " + sessionId, "conversation_id", sessionId), session);
        assertThat(result).containsEntry("result_type", "answer");
    }

    private static AgentCard probeAgentCard(String agentId) {
        return new AgentCard(agentId, agentId, AGENT_DESCRIPTION);
    }

    private static ToolCard probeToolCard(String name) {
        return ToolCard.builder()
                .id(name + "_id")
                .name(name)
                .description("probe tool " + name)
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()))
                .build();
    }

    private static Tool probeTool(String name) {
        return new LocalFunction(probeToolCard(name), inputs -> "ok-" + name);
    }

    /**
     * Scripted model matching the resilience-suite loop: first call requests
     * both probe tools, the second call answers with text.
     *
     * @param callCount receives the number of model invocations
     * @return the model wired with a scripted client
     */
    private static Model scriptedModel(AtomicInteger callCount) {
        return new Model((messages, modelConfig, clientConfig, options) -> {
            int call = callCount.getAndIncrement();
            if (call == 0) {
                return CompletableFuture.completedFuture(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(
                                toolCall("call-a", "probe_a"),
                                toolCall("call-b", "probe_b")))
                        .build());
            }
            return CompletableFuture.completedFuture(new AssistantMessage("sqlite-done"));
        });
    }

    private static ToolCall toolCall(String id, String name) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments("{}")
                .build();
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for the start signal", exception);
        }
    }

    /**
     * Create the sqlite file datasource backing the checkpointer: the file
     * is forced into existence once, WAL journaling is enabled for the
     * concurrent-writer workload, and every later connection raises its own
     * busy timeout so short write-lock contention resolves instead of
     * failing with SQLITE_BUSY.
     *
     * @return datasource opening one fresh connection per operation
     * @throws SQLException when the file cannot be created or WAL mode
     *         cannot be enabled on the initial connection
     */
    private DataSource sqliteDataSource() throws SQLException {
        String url = "jdbc:sqlite:"
                + tempDir.resolve("step_checkpoint.sqlite").toAbsolutePath().toString().replace('\\', '/');
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
        }
        return new SqliteDataSource(url);
    }

    /**
     * Minimal datasource forwarding every getConnection call to the sqlite
     * driver - each operation opens its own connection so concurrent writes
     * go through SQLite's own locking.
     */
    private static final class SqliteDataSource implements DataSource {
        private static final String BUSY_TIMEOUT_PRAGMA = "PRAGMA busy_timeout=5000";

        private final String url;

        SqliteDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                statement.execute(BUSY_TIMEOUT_PRAGMA);
            }
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("DataSource cannot unwrap " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
            // no logging
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // no login timeout
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }
}
