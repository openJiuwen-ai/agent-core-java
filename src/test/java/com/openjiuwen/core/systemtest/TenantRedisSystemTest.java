/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.tools.KvTodoStorage;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.workspace.Workspace;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * End-to-end system tests for Redis-backed DeepAgent behavior under
 * multi-tenant and adverse conditions.
 * <p>
 * Every DeepAgent is built from environment variables — never from the
 * classpath {@code apiconfig.json} file (see {@link #createAgentFromEnv}).
 * Required env vars: {@code API_BASE}, {@code API_KEY},
 * {@code MODEL_PROVIDER}, {@code MODEL_NAME}, {@code LLM_SSL_VERIFY};
 * Redis-backed tests additionally need {@code REDIS_HOST} and
 * {@code REDIS_PORT}.
 * <ul>
 *   <li>A-group (ST-A1..A2): tenant isolation on the file system, needs LLM.</li>
 *   <li>B-group (ST-B1..B3): DeepAgent resilience against Redis failures,
 *       needs LLM (and Redis for ST-B2/B3).</li>
 * </ul>
 */
@Tag("system-test")
class TenantRedisSystemTest extends SystemTestSupport {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupTenantContext() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    // ------------------------------------------------------------------
    // Environment-variable based configuration (req 1)
    // ------------------------------------------------------------------

    /**
     * Builds a DeepAgent whose configuration (LLM endpoint, model, Redis
     * connection) is sourced entirely from environment variables — never from
     * the classpath apiconfig.json file.
     *
     * @param agentId the agent id
     * @param workspacePath the workspace root
     * @param enableTenantIsolation whether tenant isolation is enabled
     * @param enableTaskPlanning whether the KV-backed todo rail is wired
     * @param kvStoreConfig optional KV store config (type+conf), null for none
     * @return a fully wired DeepAgent
     */
    private DeepAgent createAgentFromEnv(String agentId, Path workspacePath,
            boolean enableTenantIsolation, boolean enableTaskPlanning,
            Map<String, Object> kvStoreConfig) {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(enableTenantIsolation)
                .tenantDataRoot(enableTenantIsolation ? workspacePath.toString() : null)
                .workspacePath(workspacePath.toString())
                .model(new Model(remoteClientConfig(60), remoteRequestConfig(0.1, 256)))
                .systemPrompt("Reply briefly in English. If asked for an exact token, return that token.")
                .maxIterations(3)
                .completionTimeout(120.0)
                .enableTaskLoop(true)
                .enableTaskPlanning(enableTaskPlanning)
                .todoStorageType(enableTaskPlanning ? "kv" : "file")
                .kvStoreConfig(kvStoreConfig)
                .build();
        AgentCard card = AgentCard.builder().id(agentId).name(agentId)
                .description("redis env ST agent").build();
        Workspace ws = Workspace.builder().rootPath(workspacePath.toString()).language("cn").build();
        return HarnessFactory.createDeepAgent(card, config, ws);
    }

    private DeepAgent createRedisBackedAgent(String agentId) {
        Map<String, Object> kvStoreConfig = Map.of(
                "type", "redis",
                "conf", Map.of("host", redisHost(), "port", redisPort()));
        return createAgentFromEnv(agentId, tempDir, false, true, kvStoreConfig);
    }

    private DeepAgent createBadRedisAgent(String agentId) {
        Map<String, Object> kvStoreConfig = Map.of(
                "type", "redis",
                "conf", Map.of("host", "127.0.0.1", "port", 1));
        return createAgentFromEnv(agentId, tempDir, false, true, kvStoreConfig);
    }

    private void closeQuietly(DeepAgent agent) {
        if (agent == null) {
            return;
        }
        try {
            agent.close();
        } catch (Exception ignored) {
            // best-effort cleanup for system tests
        }
    }

    private String plantTodo(RedisStore redisStore, String sessionId, String value) {
        String key = TenantKVStoreKeyResolver.resolveKey(sessionId + ":todo");
        redisStore.set(key, value);
        return key;
    }

    private void cleanupKey(RedisStore redisStore, String key) {
        try {
            redisStore.delete(key);
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private static boolean hasLogContaining(ListAppender<ILoggingEvent> appender, String token) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains(token));
    }

    private static List<String> formattedLogMessages(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private boolean isDirectoryEmpty(Path dir) {
        if (!Files.exists(dir)) {
            return true;
        }
        try {
            return Files.list(dir).findFirst().isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // A-group: tenant isolation on the file system (needs LLM)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ST-A1: With tenant isolation disabled, passing a tenantId still runs normally and creates no tenant directory")
    void testInvoke_isolationDisabledButTenantIdPassed() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("no-isolation-agent");
        DeepAgent agent = createAgentFromEnv(agentId, tempDir, false, false, null);
        try {
            TenantContext tenantCtx = TenantContext.builder().tenantId("disabled_tenant").build();
            String sessionId = trackSessionId("no-isolation-inv");

            Map<String, Object> inputs = Map.of(
                    "query", "Reply with the exact token NO_ISOLATION_ACK.",
                    "conversation_id", sessionId);

            AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard())
                    .withTenantContext(tenantCtx);

            Map<String, Object> result = assertDoesNotThrow(() -> agent.invoke(inputs, session),
                    "DeepAgent should run normally when isolation is disabled even if a tenantId is passed");
            assertNotNull(result, "DeepAgent should return a result map");

            Path tenantDir = tempDir.resolve("tenants").resolve("disabled_tenant");
            assertFalse(Files.exists(tenantDir),
                    () -> "no tenant directory should be created when isolation is disabled: " + tenantDir);
            assertTrue(!Files.exists(tempDir.resolve("tenants"))
                            || isDirectoryEmpty(tempDir.resolve("tenants")),
                    "no tenant-related paths should appear when isolation is disabled");

            assertNull(TenantContextHolder.getCurrentTenant(),
                    "TenantContextHolder must be cleared after invoke");
        } finally {
            closeQuietly(agent);
        }
    }

    @Test
    @DisplayName("ST-A2: No-tenant backward compatibility when isolation is disabled")
    void testInvoke_noTenant_backwardCompat() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("no-tenant-agent");
        DeepAgent agent = createAgentFromEnv(agentId, tempDir, false, false, null);
        try {
            String sessionId = trackSessionId("no-tenant-inv");

            Map<String, Object> inputs = Map.of(
                    "query", "Reply with the exact token NO_TENANT_ACK.",
                    "conversation_id", sessionId);

            AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard());
            Map<String, Object> result = assertDoesNotThrow(() -> agent.invoke(inputs, session));
            assertNotNull(result, "invoke without TenantContext should return a result");

            Path tenantsDir = tempDir.resolve("tenants");
            assertTrue(!Files.exists(tenantsDir) || isDirectoryEmpty(tenantsDir),
                    "no tenant-related paths should appear in workspace when isolation is disabled");

            assertNull(TenantContextHolder.getCurrentTenant(),
                    "TenantContextHolder must be null after invoke without tenant context");
        } finally {
            closeQuietly(agent);
        }
    }

    // ------------------------------------------------------------------
    // B-group: DeepAgent resilience against Redis failures (needs LLM + Redis)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ST-B1: Redis connection failure is handled gracefully — logged, no crash, returns result")
    void testRedis_connectionFailureHandled() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("redis-fail-agent");
        DeepAgent agent = createBadRedisAgent(agentId);
        assertNotNull(agent.getKvStore(), "kvStore should be injected even for a bad Redis config");
        assertTrue(agent.getKvStore() instanceof RedisStore,
                "kvStore should be a RedisStore");
        RedisStore badStore = (RedisStore) agent.getKvStore();

        Logger redisLogger = (Logger) LoggerFactory.getLogger(RedisStore.class);
        Level previousLevel = redisLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        redisLogger.setLevel(Level.DEBUG);
        redisLogger.addAppender(appender);
        try {
            // 1. Deterministic: a direct Redis op against the closed port is
            //    surfaced as a RuntimeException with a descriptive message and
            //    an ERROR log line — the framework never silently swallows it.
            RuntimeException setEx = assertThrows(RuntimeException.class,
                    () -> badStore.set("st-b1-fail-key", "v"),
                    "RedisStore should surface the connection failure as a RuntimeException");
            assertTrue(setEx.getMessage().contains("Failed to set key"),
                    () -> "exception should describe the Redis failure: " + setEx.getMessage());
            assertTrue(hasLogContaining(appender, "Failed"),
                    () -> "RedisStore should log the connection failure. Logs: " + formattedLogMessages(appender));

            // 2. DeepAgent-level: invoke must not crash. The todo_create tool
            //    catches the Redis failure (TodoTool wraps RuntimeException |
            //    IOException into ToolOutput{success=false}) and feeds the
            //    error back to the LLM; DeepAgent returns a result map.
            String sessionId = trackSessionId("redis-fail-inv");
            String prompt = "You are a task planning assistant. You MUST call the todo_create tool with "
                    + "session_id=\"" + sessionId + "\" and tasks=[{\"content\":\"x\"}]. "
                    + "After the tool returns, if it succeeded reply with the single word done; "
                    + "if it failed or returned an error reply with the single word error.";
            Map<String, Object> inputs = Map.of("query", prompt, "conversation_id", sessionId);
            AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard());

            Map<String, Object> result = assertDoesNotThrow(() -> agent.invoke(inputs, session),
                    "DeepAgent.invoke should handle the Redis failure gracefully");
            assertNotNull(result, "DeepAgent should return a result map even when Redis is unreachable");
            String text = flattenText(result);
            assertFalse(text.isBlank(),
                    "DeepAgent should return information in the result, got: " + text);

            assertTrue(hasLogContaining(appender, "Failed"),
                    "Redis failure should be logged during invoke. Logs: " + formattedLogMessages(appender));
        } finally {
            closeQuietly(agent);
            redisLogger.detachAppender(appender);
            appender.stop();
            redisLogger.setLevel(previousLevel);
        }
    }

    @Test
    @DisplayName("ST-B2: TodoItem with a non-serializable field is handled — returns default, no exception")
    void testTodo_nonSerializableFieldHandled() {
        assumeRemoteModelAvailable();
        assumeRedisAvailable();

        String agentId = uniqueId("todo-badfield-agent");
        DeepAgent agent = createRedisBackedAgent(agentId);
        RedisStore redisStore = (RedisStore) agent.getKvStore();
        try {
            String sessionId = trackSessionId("badfield-inv");

            // Plant a todo whose "content" field is a JSON object where a String
            // is expected — a field that cannot be (de)serialized into
            // TodoItem.content, exercising the safeJsonLoads fallback path.
            String corruptJson = "[{\"content\":{\"nested\":\"object\"},\"status\":\"TODO\"}]";
            String key = plantTodo(redisStore, sessionId, corruptJson);

            // 1. Precise: loading the corrupt todo returns the empty default
            //    list (safeJsonLoads(..., new TodoItem[0])) without throwing.
            KvTodoStorage storage = new KvTodoStorage(redisStore);
            List<TodoItem> loaded = assertDoesNotThrow(() -> storage.load(sessionId),
                    "loading a todo with a non-serializable field should not throw");
            assertNotNull(loaded, "loaded todo list should not be null");
            assertTrue(loaded.isEmpty(),
                    "corrupt todo (non-serializable field) should load as the empty default, got: " + loaded);

            // 2. DeepAgent-level: invoke with a todo_list prompt does not crash
            //    and returns a result (the tool returns the empty default).
            String prompt = "You are a task planning assistant. Call the todo_list tool with session_id=\""
                    + sessionId + "\". Then reply with the single word done.";
            Map<String, Object> inputs = Map.of("query", prompt, "conversation_id", sessionId);
            AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard());

            Map<String, Object> result = assertDoesNotThrow(() -> agent.invoke(inputs, session),
                    "DeepAgent.invoke should handle the corrupt todo gracefully");
            assertNotNull(result, "DeepAgent should return a result map");
            String text = flattenText(result);
            assertFalse(text.isBlank(), "DeepAgent should return information in the result, got: " + text);

            cleanupKey(redisStore, key);
        } finally {
            closeQuietly(agent);
        }
    }

    @Test
    @DisplayName("ST-B3: Malformed TodoItem format is handled — returns default, no exception")
    void testTodo_malformedFormatHandled() {
        assumeRemoteModelAvailable();
        assumeRedisAvailable();

        String agentId = uniqueId("todo-malformed-agent");
        DeepAgent agent = createRedisBackedAgent(agentId);
        RedisStore redisStore = (RedisStore) agent.getKvStore();
        try {
            String sessionId = trackSessionId("malformed-inv");

            // Plant a value at the todo key that is not valid JSON at all.
            String corruptJson = "<<<not-valid-json>>>";
            String key = plantTodo(redisStore, sessionId, corruptJson);

            // 1. Precise: loading the malformed todo returns the empty default
            //    list without throwing.
            KvTodoStorage storage = new KvTodoStorage(redisStore);
            List<TodoItem> loaded = assertDoesNotThrow(() -> storage.load(sessionId),
                    "loading malformed todo JSON should not throw");
            assertNotNull(loaded, "loaded todo list should not be null");
            assertTrue(loaded.isEmpty(),
                    "malformed todo JSON should load as the empty default, got: " + loaded);

            // 2. DeepAgent-level: invoke with a todo_list prompt does not crash
            //    and returns a result.
            String prompt = "You are a task planning assistant. Call the todo_list tool with session_id=\""
                    + sessionId + "\". Then reply with the single word done.";
            Map<String, Object> inputs = Map.of("query", prompt, "conversation_id", sessionId);
            AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard());

            Map<String, Object> result = assertDoesNotThrow(() -> agent.invoke(inputs, session),
                    "DeepAgent.invoke should handle the malformed todo gracefully");
            assertNotNull(result, "DeepAgent should return a result map");
            String text = flattenText(result);
            assertFalse(text.isBlank(), "DeepAgent should return information in the result, got: " + text);

            cleanupKey(redisStore, key);
        } finally {
            closeQuietly(agent);
        }
    }
}
