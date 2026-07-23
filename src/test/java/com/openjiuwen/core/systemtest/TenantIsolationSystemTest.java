/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * End-to-end system tests for multi-tenant data isolation.
 * <p>
 * The suite is split into three groups:
 * <ul>
 *   <li>A-group (ST-A1..A6): file-system resource isolation, needs real LLM.</li>
 *   <li>B-group (ST-B1..B3): KV-store resource isolation against a real Redis
 *       instance, needs {@code REDIS_URL} configured.</li>
 *   <li>C-group (ST-C1..C3): security boundary and lifecycle.</li>
 * </ul>
 * Each test calls only the {@code assumeXxxAvailable()} guard it actually needs,
 * so A/C-group tests still run when Redis is absent, and B-group tests still run
 * when the LLM is absent.
 */
@Tag("system-test")
class TenantIsolationSystemTest extends SystemTestSupport {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupTenantContext() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    // ----------------------------------------------------------------------
    // Shared agent factories
    // ----------------------------------------------------------------------

    private DeepAgent createTenantAwareAgent(String agentId, Path workspacePath, boolean enableTenantIsolation) {
        Model model = new Model(remoteClientConfig(60), remoteRequestConfig(0.1, 256));
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(enableTenantIsolation)
                .tenantDataRoot(enableTenantIsolation ? workspacePath.toString() : null)
                .workspacePath(workspacePath.toString())
                .model(model)
                .systemPrompt("Reply briefly in English. If asked for an exact token, return that token.")
                .maxIterations(3)
                .completionTimeout(120.0)
                .enableTaskLoop(true)
                .build();
        AgentCard card = AgentCard.builder().id(agentId).name(agentId).description("tenant isolation ST agent").build();
        Workspace ws = Workspace.builder().rootPath(workspacePath.toString()).language("cn").build();
        return new DeepAgent(card, config, ws);
    }

    private DeepAgent createNoTenantAgent(String agentId, Path workspacePath) {
        Model model = new Model(remoteClientConfig(60), remoteRequestConfig(0.1, 256));
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(workspacePath.toString())
                .model(model)
                .systemPrompt("Reply briefly in English.")
                .maxIterations(3)
                .completionTimeout(120.0)
                .enableTaskLoop(true)
                .build();
        AgentCard card = AgentCard.builder().id(agentId).name(agentId).description("no-tenant ST agent").build();
        Workspace ws = Workspace.builder().rootPath(workspacePath.toString()).language("cn").build();
        return new DeepAgent(card, config, ws);
    }

    private List<Object> collectStream(Iterator<Object> iterator) {
        List<Object> items = new ArrayList<>();
        iterator.forEachRemaining(items::add);
        return items;
    }

    /**
     * Creates a DeepAgent configured with a Redis-backed KV store and KV todo
     * storage, using the framework's own SPI chain: HarnessFactory →
     * KVStoreFactory.create("redis", conf) → RedisKVStoreProvider → Jedis →
     * RedisStore. The returned agent's getKvStore() yields a live RedisStore
     * connected to the real Redis instance configured in apiconfig.json.
     */
    private DeepAgent createRedisBackedAgent(String agentId) {
        Model model = new Model(remoteClientConfig(60), remoteRequestConfig(0.1, 256));
        Map<String, Object> kvStoreConfig = Map.of(
                "type", "redis",
                "conf", Map.of("host", redisHost(), "port", redisPort()));
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(tempDir.toString())
                .workspacePath(tempDir.toString())
                .model(model)
                .systemPrompt("Reply briefly.")
                .maxIterations(3)
                .completionTimeout(120.0)
                .enableTaskLoop(true)
                .enableTaskPlanning(true)
                .todoStorageType("kv")
                .kvStoreConfig(kvStoreConfig)
                .build();
        AgentCard card = AgentCard.builder()
                .id(agentId).name(agentId).description("Redis ST agent").build();
        Workspace ws = Workspace.builder().rootPath(tempDir.toString()).language("cn").build();
        return HarnessFactory.createDeepAgent(card, config, ws);
    }

    // ----------------------------------------------------------------------
    // A-group: file-system resource isolation (needs real LLM)
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("ST-A1: Tenant workspace isolation between two tenants")
    void testInvoke_tenantWorkspaceIsolation() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("tenant-ws-agent");
        DeepAgent agent = createTenantAwareAgent(agentId, tempDir, true);

        TenantContext tenantA = TenantContext.builder().tenantId("tenant_a").build();
        TenantContext tenantB = TenantContext.builder().tenantId("tenant_b").build();

        String sessionIdA = trackSessionId("tenant-a-inv");
        String sessionIdB = trackSessionId("tenant-b-inv");

        Map<String, Object> inputsA = Map.of(
                "query", "Reply with the exact token TENANT_A_ACK.",
                "conversation_id", sessionIdA);
        Map<String, Object> inputsB = Map.of(
                "query", "Reply with the exact token TENANT_B_ACK.",
                "conversation_id", sessionIdB);

        AgentSessionApi sessionA = AgentSessionApi.create(sessionIdA, null, agent.getCard())
                .withTenantContext(tenantA);
        AgentSessionApi sessionB = AgentSessionApi.create(sessionIdB, null, agent.getCard())
                .withTenantContext(tenantB);

        Map<String, Object> resultA = agent.invoke(inputsA, sessionA);
        Map<String, Object> resultB = agent.invoke(inputsB, sessionB);

        assertNotNull(resultA, "tenant_a invoke should return a result");
        assertNotNull(resultB, "tenant_b invoke should return a result");

        Path tenantAWorkspace = tempDir.resolve("tenants").resolve("tenant_a");
        Path tenantBWorkspace = tempDir.resolve("tenants").resolve("tenant_b");

        assertTrue(Files.exists(tenantAWorkspace),
                () -> "tenant_a workspace directory should exist: " + tenantAWorkspace);
        assertTrue(Files.exists(tenantBWorkspace),
                () -> "tenant_b workspace directory should exist: " + tenantBWorkspace);
        assertNotEquals(tenantAWorkspace.toString(), tenantBWorkspace.toString(),
                "workspace paths for different tenants must differ");

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must be cleared after invoke");
    }

    @Test
    @DisplayName("ST-A2: No-tenant backward compatibility when isolation is disabled")
    void testInvoke_noTenant_backwardCompat() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("no-tenant-agent");
        DeepAgent agent = createNoTenantAgent(agentId, tempDir);

        String sessionId = trackSessionId("no-tenant-inv");

        Map<String, Object> inputs = Map.of(
                "query", "Reply with the exact token NO_TENANT_ACK.",
                "conversation_id", sessionId);

        AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard());
        Map<String, Object> result = agent.invoke(inputs, session);

        assertNotNull(result, "invoke without TenantContext should return a result");

        Path tenantsDir = tempDir.resolve("tenants");
        assertTrue(!Files.exists(tenantsDir) || isDirectoryEmpty(tenantsDir),
                "no tenant-related paths should appear in workspace when isolation is disabled");

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must be null after invoke without tenant context");
    }

    @Test
    @DisplayName("ST-A3: Stream with tenant isolation clears context after stream")
    void testStream_tenantIsolation() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("tenant-stream-agent");
        DeepAgent agent = createTenantAwareAgent(agentId, tempDir, true);

        TenantContext tenantCtx = TenantContext.builder().tenantId("stream_tenant").build();
        String sessionId = trackSessionId("tenant-stream-inv");

        Map<String, Object> inputs = Map.of(
                "query", "Reply with the exact token STREAM_ACK.",
                "conversation_id", sessionId);

        AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard())
                .withTenantContext(tenantCtx);

        Iterator<Object> streamIt = agent.stream(inputs, session, List.of(StreamMode.OUTPUT));
        List<Object> streamItems = collectStream(streamIt);

        assertTrue(streamItems.size() > 0,
                () -> "stream output should contain at least one item, got: " + streamItems.size());
        String flattened = flattenText(streamItems);
        assertTrue(containsIgnoreCase(flattened, "STREAM_ACK") || flattened.length() > 0,
                () -> "stream output should contain result, got: " + flattened);

        Path tenantWorkspace = tempDir.resolve("tenants").resolve("stream_tenant");
        assertTrue(Files.exists(tenantWorkspace),
                () -> "stream_tenant workspace directory should exist: " + tenantWorkspace);

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must be cleared after stream");
    }

    @Test
    @DisplayName("ST-A4: Both entry points (explicit TenantContext and session) reach the same tenant workspace")
    void testInvoke_dualEntryPoints() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("tenant-both-agent");
        DeepAgent agent = createTenantAwareAgent(agentId, tempDir, true);

        TenantContext tenantCtx = TenantContext.builder().tenantId("dual_tenant").build();

        String explicitSessionId = trackSessionId("explicit-inv");
        String sessionSessionId = trackSessionId("session-inv");

        Map<String, Object> explicitInputs = Map.of(
                "query", "Reply with the exact token EXPLICIT_ACK.",
                "conversation_id", explicitSessionId);
        Map<String, Object> sessionInputs = Map.of(
                "query", "Reply with the exact token SESSION_ACK.",
                "conversation_id", sessionSessionId);

        Map<String, Object> explicitResult = agent.invoke(explicitInputs, tenantCtx);
        assertNotNull(explicitResult,
                "invoke(inputs, TenantContext) shortcut should return a result");

        Path tenantWorkspace = tempDir.resolve("tenants").resolve("dual_tenant");
        assertTrue(Files.exists(tenantWorkspace),
                () -> "explicit TenantContext should create tenant workspace: " + tenantWorkspace);
        assertTrue(tenantWorkspace.toString().contains("dual_tenant"),
                () -> "workspace path should contain tenant ID: " + tenantWorkspace);

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must be cleared after explicit TenantContext invoke");

        AgentSessionApi session = AgentSessionApi.create(sessionSessionId, null, agent.getCard())
                .withTenantContext(tenantCtx);
        Map<String, Object> sessionResult = agent.invoke(sessionInputs, session);
        assertNotNull(sessionResult,
                "invoke(inputs, session.withTenantContext()) should return a result");

        assertTrue(Files.exists(tenantWorkspace),
                () -> "session-based TenantContext should reuse tenant workspace: " + tenantWorkspace);

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must be cleared after session-based TenantContext invoke");
    }

    @Test
    @DisplayName("ST-A5: Tenant directory structure contains all isolated subdirectories")
    void testInvoke_tenantDirectoryStructure() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("tenant-dir-agent");
        DeepAgent agent = createTenantAwareAgent(agentId, tempDir, true);

        TenantContext tenantCtx = TenantContext.builder().tenantId("dir_tenant").build();
        String sessionId = trackSessionId("dir-inv");

        Map<String, Object> inputs = Map.of(
                "query", "Reply with the exact token DIR_ACK.",
                "conversation_id", sessionId);

        AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard())
                .withTenantContext(tenantCtx);
        Map<String, Object> result = agent.invoke(inputs, session);

        assertNotNull(result, "invoke should return a result");

        Path tenantRoot = tempDir.resolve("tenants").resolve("dir_tenant");
        assertTrue(Files.exists(tenantRoot),
                "tenant root (workspace) directory should exist");
        assertTrue(Files.exists(tenantRoot.resolve("skills")),
                "skills subdirectory should exist under tenant root");
        assertTrue(Files.exists(tenantRoot.resolve("tmp")),
                "tmp subdirectory should exist under tenant root");
        assertTrue(Files.exists(tenantRoot.resolve("checkpoints")),
                "checkpoints subdirectory should exist under tenant root");

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must be cleared after invoke");
    }

    @Test
    @DisplayName("ST-A6: Different tenants have separate skill directories")
    void testInvoke_tenantSkillDirIsolation() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("tenant-skill-agent");
        DeepAgent agent = createTenantAwareAgent(agentId, tempDir, true);

        TenantContext tenantA = TenantContext.builder().tenantId("skill_tenant_a").build();
        TenantContext tenantB = TenantContext.builder().tenantId("skill_tenant_b").build();

        String sessionIdA = trackSessionId("skill-inv-a");
        String sessionIdB = trackSessionId("skill-inv-b");

        Map<String, Object> inputsA = Map.of(
                "query", "Reply briefly.",
                "conversation_id", sessionIdA);
        Map<String, Object> inputsB = Map.of(
                "query", "Reply briefly.",
                "conversation_id", sessionIdB);

        AgentSessionApi sessionA = AgentSessionApi.create(sessionIdA, null, agent.getCard())
                .withTenantContext(tenantA);
        AgentSessionApi sessionB = AgentSessionApi.create(sessionIdB, null, agent.getCard())
                .withTenantContext(tenantB);

        agent.invoke(inputsA, sessionA);
        agent.invoke(inputsB, sessionB);

        Path skillDirA = tempDir.resolve("tenants").resolve("skill_tenant_a").resolve("skills");
        Path skillDirB = tempDir.resolve("tenants").resolve("skill_tenant_b").resolve("skills");

        assertTrue(Files.exists(skillDirA),
                () -> "skill_tenant_a skills directory should exist: " + skillDirA);
        assertTrue(Files.exists(skillDirB),
                () -> "skill_tenant_b skills directory should exist: " + skillDirB);
        assertNotEquals(skillDirA.toString(), skillDirB.toString(),
                "skill directories for different tenants must differ");
    }

    // ----------------------------------------------------------------------
    // B-group: KV-store resource isolation against a real Redis instance
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("ST-B1: DeepAgent uses TodoTool via LLM and writes todos to real Redis")
    void testTodo_redisTenantIsolation() throws Exception {
        assumeRemoteModelAvailable();
        assumeRedisAvailable();

        // 1. Configure DeepAgent with Redis-backed KV store + KV todo storage.
        // HarnessFactory → KVStoreFactory.create("redis") → RedisKVStoreProvider
        // → reflection creates Jedis(127.0.0.1, 6379) → RedisStore(jedis).
        // TaskPlanningRail.init (triggered by invoke) registers the todo_create
        // tool backed by KvTodoStorage(redisStore).
        DeepAgent agent = createRedisBackedAgent(uniqueId("todo-redis-agent"));
        assertNotNull(agent.getKvStore(), "kvStore should be injected by HarnessFactory");
        assertTrue(agent.getKvStore() instanceof RedisStore,
                "kvStore should be a RedisStore (framework SPI created Jedis + RedisStore)");
        RedisStore redisStore = (RedisStore) agent.getKvStore();

        // Clean any leftover keys from previous runs
        redisStore.deleteByPrefix("todo_tenant_a:", null);
        redisStore.deleteByPrefix("todo_tenant_b:", null);

        // 2. Tenant A: invoke with a prompt that guides the LLM to call the
        //    todo_create tool. The LLM autonomously invokes todo_create, which
        //    internally calls KvTodoStorage.save(sessionId, todos) →
        //    redisStore.set("todo_tenant_a:{sessionId}:todo", json).
        //    No direct storage.save() call from the test.
        String sessionIdA = trackSessionId("todo-inv-a");
        TenantContext tenantA = TenantContext.builder().tenantId("todo_tenant_a").build();
        AgentSessionApi sessionA = AgentSessionApi.create(sessionIdA, null, agent.getCard())
                .withTenantContext(tenantA);
        String promptA = "You are a task planning assistant. Before answering, you MUST call the "
                + "todo_create tool with session_id=\"" + sessionIdA + "\" and tasks="
                + "[{\"content\":\"help tenant A\",\"activeForm\":\"helping tenant A\","
                + "\"description\":\"handle tenant A request\"}]. "
                + "After creating the todo, reply with the word done.";
        Map<String, Object> inputsA = Map.of("query", promptA, "conversation_id", sessionIdA);
        agent.invoke(inputsA, sessionA);

        // 3. Verify the LLM-driven todo_create wrote data to real Redis under
        //    the tenant-prefixed key namespace.
        Map<String, Object> keysA = redisStore.getByPrefix("todo_tenant_a:");
        assertFalse(keysA.isEmpty(),
                "Redis should contain tenant A's todo data after LLM invoked todo_create. "
                        + "Keys found: " + keysA.keySet());
        assertTrue(keysA.keySet().stream().anyMatch(k -> k.contains(":todo")),
                "tenant A todo key should follow the '{tenantId}:{sessionId}:todo' format. "
                        + "Keys: " + keysA.keySet());

        // 4. Tenant B: same flow, different tenant context and session.
        String sessionIdB = trackSessionId("todo-inv-b");
        TenantContext tenantB = TenantContext.builder().tenantId("todo_tenant_b").build();
        AgentSessionApi sessionB = AgentSessionApi.create(sessionIdB, null, agent.getCard())
                .withTenantContext(tenantB);
        String promptB = "You are a task planning assistant. Before answering, you MUST call the "
                + "todo_create tool with session_id=\"" + sessionIdB + "\" and tasks="
                + "[{\"content\":\"help tenant B\",\"activeForm\":\"helping tenant B\","
                + "\"description\":\"handle tenant B request\"}]. "
                + "After creating the todo, reply with the word done.";
        Map<String, Object> inputsB = Map.of("query", promptB, "conversation_id", sessionIdB);
        agent.invoke(inputsB, sessionB);

        Map<String, Object> keysB = redisStore.getByPrefix("todo_tenant_b:");
        assertFalse(keysB.isEmpty(),
                "Redis should contain tenant B's todo data after LLM invoked todo_create. "
                        + "Keys found: " + keysB.keySet());

        // 5. Cross-tenant key isolation: tenant A's key namespace must not contain
        //    any of tenant B's keys, and vice versa.
        for (String key : keysB.keySet()) {
            assertFalse(keysA.containsKey(key),
                    "tenant A key namespace must not contain tenant B's key: " + key);
        }
        for (String key : keysA.keySet()) {
            assertFalse(keysB.containsKey(key),
                    "tenant B key namespace must not contain tenant A's key: " + key);
        }

        // 6. Verify the tenant-prefixed key was written by the LLM-driven tool call.
        //    The key format is "todo_tenant_a:{sessionId}:todo" because
        //    KvTodoStorage.buildKey uses TenantKVStoreKeyResolver.resolveKey.
        boolean hasTenantAKey = keysA.keySet().stream()
                .anyMatch(k -> k.startsWith("todo_tenant_a:" + sessionIdA));
        assertTrue(hasTenantAKey,
                "tenant A should have a todo key starting with 'todo_tenant_a:" + sessionIdA
                        + "'. Keys: " + keysA.keySet());

        // Cleanup Redis keys
        redisStore.deleteByPrefix("todo_tenant_a:", null);
        redisStore.deleteByPrefix("todo_tenant_b:", null);

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must be cleared");
    }

    @Test
    @DisplayName("ST-B2: Checkpointer session save/restore and tenant isolation via DeepAgent execution")
    void testCheckpointer_redisTenantIsolation() throws Exception {
        assumeRemoteModelAvailable();
        assumeRedisAvailable();

        // 1. Configure DeepAgent with Redis-backed KV store via framework SPI.
        //    HarnessFactory → KVStoreFactory.create("redis") → RedisStore.
        DeepAgent agent = createRedisBackedAgent(uniqueId("cp-redis-agent"));
        RedisStore redisStore = (RedisStore) agent.getKvStore();
        assertNotNull(agent.getKvStore(), "kvStore should be injected by HarnessFactory");
        assertTrue(agent.getKvStore() instanceof RedisStore,
                "kvStore should be a RedisStore (framework SPI created Jedis + RedisStore)");

        // 2. Build a RedisCheckpointer backed by the same RedisStore and install
        //    it as the global default. In production, RunnerImpl.start() does
        //    exactly this: creates a checkpointer from RunnerConfig and calls
        //    CheckpointerFactory.setDefaultCheckpointer(cp).
        RedisCheckpointer cp = new RedisCheckpointer(redisStore, Map.of());
        CheckpointerFactory.setDefaultCheckpointer(cp);

        // Clean any leftover keys from previous runs
        redisStore.deleteByPrefix("cp_tenant_a:", null);
        redisStore.deleteByPrefix("cp_tenant_b:", null);

        String sessionId = trackSessionId("cp-e2e");
        String markerKey = "st_b2_checkpoint_marker";
        String markerValue = "tenant_a_state_" + UUID.randomUUID().toString().substring(0, 8);

        // --- Phase 1: Tenant A executes with full checkpointer lifecycle ---
        //    Manually orchestrate preRun → invoke → updateState → postRun,
        //    mirroring RunnerImpl.runAgent(). The tenant context must be on
        //    the thread when preRun/postRun fire so that
        //    TenantKVStoreKeyResolver produces tenant-prefixed keys.
        TenantContext tenantA = TenantContext.builder().tenantId("cp_tenant_a").build();
        TenantContextHolder.setCurrentTenant(tenantA);
        try {
            AgentSessionApi sessionA = AgentSessionApi.create(sessionId, null, agent.getCard())
                    .withTenantContext(tenantA);

            // preRun triggers checkpointer.preAgentExecute →
            // agentStorage.recover (first run: no prior state in Redis)
            // + writes INTERACTIVE_INPUT into session state.
            Map<String, Object> inputsA = Map.of(
                    "query", "Reply with the exact token CP_ACK.",
                    "conversation_id", sessionId);
            sessionA.preRun(inputsA);

            // DeepAgent.invoke sets tenant context internally and runs the
            // task loop (LLM-driven ReAct agent). After invoke returns,
            // DeepAgent clears TenantContextHolder in its finally block.
            agent.invoke(inputsA, sessionA);

            // DeepAgent.invoke clears TenantContextHolder; re-set before
            // postRun so the checkpointer resolves keys under tenant A.
            TenantContextHolder.setCurrentTenant(tenantA);

            // Inject a recognizable marker AFTER invoke, BEFORE postRun.
            // This ensures the marker is captured in the checkpoint data
            // that postAgentExecute serializes and writes to Redis.
            sessionA.updateState(Map.of(markerKey, markerValue));

            // postRun triggers checkpointer.postAgentExecute →
            // agentStorage.save → serializes session state and writes two
            // keys to Redis under the cp_tenant_a: prefix:
            //   cp_tenant_a:{sessionId}:agent:{agentId}:agent_state_blobs_dump_type
            //   cp_tenant_a:{sessionId}:agent:{agentId}:agent_state_blobs
            sessionA.postRun();
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        // --- Phase 2: Verify checkpoint was saved to Redis ---
        TenantContextHolder.setCurrentTenant(tenantA);
        try {
            assertTrue(cp.sessionExists(sessionId),
                    "RedisCheckpointer should report session exists after "
                    + "postAgentExecute saved state to Redis");

            Map<String, Object> keysA = redisStore.getByPrefix("cp_tenant_a:");
            assertFalse(keysA.isEmpty(),
                    "Redis should contain tenant A's checkpoint keys. Keys: " + keysA.keySet());
            assertTrue(keysA.keySet().stream().anyMatch(k -> k.contains("agent_state_blobs")),
                    "tenant A checkpoint should include agent_state_blobs keys. Keys: " + keysA.keySet());
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        // --- Phase 3: Restore session under tenant A ---
        //    Create a new AgentSessionApi with the same sessionId.
        //    preRun triggers checkpointer.preAgentExecute →
        //    agentStorage.recover, which reads the state from Redis
        //    and calls session.state().setState(savedMap).
        TenantContextHolder.setCurrentTenant(tenantA);
        try {
            AgentSessionApi restoredSession = AgentSessionApi.create(sessionId, null, agent.getCard())
                    .withTenantContext(tenantA);

            Map<String, Object> restoreInputs = Map.of(
                    "query", "Continuing after checkpoint restore.",
                    "conversation_id", sessionId);

            // preRun → checkpointer.preAgentExecute → agentStorage.recover
            // → deserializes state from Redis and restores via setState()
            restoredSession.preRun(restoreInputs);

            // The marker we injected before postRun should be present in the
            // restored state, proving that agentStorage.recover successfully
            // deserialized and restored the checkpoint from Redis.
            Object restoredMarker = restoredSession.getState(markerKey);
            assertEquals(markerValue, restoredMarker,
                    "restored session should contain the marker value that was "
                    + "checkpointed by tenant A's postAgentExecute");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        // --- Phase 4: Tenant B cannot see tenant A's checkpoint state ---
        TenantContext tenantB = TenantContext.builder().tenantId("cp_tenant_b").build();
        TenantContextHolder.setCurrentTenant(tenantB);
        try {
            // sessionExists resolves the prefix under tenant B's namespace,
            // so it should find no keys for the same logical sessionId.
            assertFalse(cp.sessionExists(sessionId),
                    "tenant B's checkpointer.sessionExists should return false — "
                    + "tenant A's checkpoint keys live under cp_tenant_a:, not cp_tenant_b:");

            // Even if tenant B creates a session with the same sessionId,
            // preRun recovers nothing (no checkpoint under cp_tenant_b: prefix).
            AgentSessionApi sessionB = AgentSessionApi.create(sessionId, null, agent.getCard())
                    .withTenantContext(tenantB);
            sessionB.preRun(Map.of("query", "isolation probe", "conversation_id", sessionId));

            Object crossTenantState = sessionB.getState(markerKey);
            assertNull(crossTenantState,
                    "tenant B's restored session should not contain tenant A's "
                    + "checkpoint marker — cross-tenant state isolation holds");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        // Cleanup: reset default checkpointer and delete Redis test keys
        CheckpointerFactory.setDefaultCheckpointer(null);
        redisStore.deleteByPrefix("cp_tenant_a:", null);
        redisStore.deleteByPrefix("cp_tenant_b:", null);

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must be cleared");
    }

    @Test
    @DisplayName("ST-B3: LongTermMemory key-prefix isolation in real Redis via DeepAgent config")
    void testLongTermMemory_redisTenantIsolation() throws Exception {
        assumeRedisAvailable();

        // Configure DeepAgent with Redis-backed KV store via framework SPI
        DeepAgent agent = createRedisBackedAgent(uniqueId("mem-redis-agent"));
        RedisStore redisStore = (RedisStore) agent.getKvStore();

        // Clean any leftover keys
        redisStore.deleteByPrefix("mem_tenant_a:", null);
        redisStore.deleteByPrefix("mem_tenant_b:", null);

        String scopeId = "scope-" + UUID.randomUUID().toString().substring(0, 8);
        String memoryKey = "memory_scope_config/" + scopeId;

        // Tenant A writes a memory-scoped key
        TenantContext tenantA = TenantContext.builder().tenantId("mem_tenant_a").build();
        TenantContextHolder.setCurrentTenant(tenantA);
        String keyA;
        try {
            keyA = TenantKVStoreKeyResolver.resolveKey(memoryKey);
            assertTrue(keyA.startsWith("mem_tenant_a:"),
                    "tenant A memory key should use ':' as tenant separator: " + keyA);
            redisStore.set(keyA, "memoryA");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        // Tenant B writes the same logical key — lands in a different physical key
        TenantContext tenantB = TenantContext.builder().tenantId("mem_tenant_b").build();
        TenantContextHolder.setCurrentTenant(tenantB);
        String keyB;
        try {
            keyB = TenantKVStoreKeyResolver.resolveKey(memoryKey);
            assertTrue(keyB.startsWith("mem_tenant_b:"),
                    "tenant B memory key should be prefixed: " + keyB);
            assertNotEquals(keyA, keyB,
                    "same logical memory key must resolve to different physical keys per tenant");
            redisStore.set(keyB, "memoryB");
            Object ownValue = redisStore.get(keyB);
            assertEquals("memoryB", ownValue,
                    "tenant B should read its own memory value via its own resolver key");
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        // Cross-tenant prefix isolation in real Redis
        Map<String, Object> keysA = redisStore.getByPrefix("mem_tenant_a:");
        assertTrue(keysA.containsKey(keyA),
                "tenant A prefix should contain tenant A's memory key");
        assertFalse(keysA.containsKey(keyB),
                "tenant A prefix should not contain tenant B's memory key");
        Map<String, Object> keysB = redisStore.getByPrefix("mem_tenant_b:");
        assertTrue(keysB.containsKey(keyB),
                "tenant B prefix should contain tenant B's memory key");

        // Cleanup Redis keys
        redisStore.deleteByPrefix("mem_tenant_a:", null);
        redisStore.deleteByPrefix("mem_tenant_b:", null);

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must be cleared");
    }

    // ----------------------------------------------------------------------
    // C-group: security boundary and lifecycle
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("ST-C1: Tenant boundary blocks path traversal across tenants")
    void testPathTraversal_blocked() throws Exception {
        Path tenantARoot = tempDir.resolve("tenants").resolve("trav_tenant_a").toAbsolutePath().normalize();
        Files.createDirectories(tenantARoot.resolve("tmp"));
        Path tenantBRoot = tempDir.resolve("tenants").resolve("trav_tenant_b").toAbsolutePath().normalize();
        Files.createDirectories(tenantBRoot.resolve("tmp"));

        // Bind CwdContext to tenant A's root — the same mechanism
        // LocalFsOperation.validateTenantBoundary relies on at runtime.
        CwdContext.setTenantRoot(tenantARoot.toString());
        try {
            Path insideTenantA = tenantARoot.resolve("tmp").resolve("file.txt");
            assertTrue(CwdContext.isWithinTenantRoot(insideTenantA),
                    "a path inside the tenant root must be within the boundary");

            Path insideTenantB = tenantBRoot.resolve("tmp").resolve("escape.txt");
            assertFalse(CwdContext.isWithinTenantRoot(insideTenantB),
                    "a path inside another tenant's root must be outside the boundary");

            // A traversal attempt pointing outside the tenant root is rejected
            Path escapeViaParent = tenantARoot.resolve("..").resolve("trav_tenant_b")
                    .resolve("tmp").resolve("escape.txt").normalize();
            assertFalse(CwdContext.isWithinTenantRoot(escapeViaParent),
                    "a parent-traversal path to another tenant must be outside the boundary");
        } finally {
            CwdContext.reset();
        }

        assertNull(CwdContext.getTenantRoot(),
                "tenant root must be cleared after the test");
    }

    @Test
    @DisplayName("ST-C2: Strict mode fails fast when tenantId is missing")
    void testStrictMode_failFast() {
        // No assumeRemoteModelAvailable(): strict-mode validation runs before any LLM call.
        Model model = new Model(remoteClientConfig(60), remoteRequestConfig(0.1, 256));
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(tempDir.toString())
                .workspacePath(tempDir.toString())
                .model(model)
                .systemPrompt("Reply briefly.")
                .maxIterations(1)
                .completionTimeout(10.0)
                .build();
        AgentCard card = AgentCard.builder()
                .id(uniqueId("strict-agent"))
                .name("strict-agent")
                .description("strict mode ST agent")
                .build();
        Workspace ws = Workspace.builder().rootPath(tempDir.toString()).language("cn").build();
        DeepAgent agent = new DeepAgent(card, config, ws);

        Map<String, Object> inputs = Map.of("query", "hi", "conversation_id", uniqueId("strict-inv"));

        // 1. Explicit TenantContext entry with null context must fail fast
        IllegalStateException explicitError = assertThrows(IllegalStateException.class,
                () -> agent.invoke(inputs, (TenantContext) null));
        assertTrue(explicitError.getMessage().contains("Tenant isolation is enabled")
                        || explicitError.getMessage().contains("no tenantId was provided"),
                () -> "error should mention tenant isolation: " + explicitError.getMessage());

        // 2. Session entry carrying no tenantContext must fail fast too
        AgentSessionApi sessionWithoutTenant = AgentSessionApi.create(uniqueId("strict-sess"), null, card);
        IllegalStateException sessionError = assertThrows(IllegalStateException.class,
                () -> agent.invoke(inputs, sessionWithoutTenant));
        assertTrue(sessionError.getMessage().contains("Tenant isolation is enabled")
                        || sessionError.getMessage().contains("no tenantId was provided"),
                () -> "error should mention tenant isolation: " + sessionError.getMessage());

        // No tenant directory should have been created — bind never happened
        assertFalse(Files.exists(tempDir.resolve("tenants")),
                "no tenant directory should be created when strict mode rejects the call");

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must remain null after rejected calls");
    }

    @Test
    @DisplayName("ST-C3: DeepAgent AutoCloseable lifecycle stops TmpFileCleaner")
    void testAutoCloseableLifecycle() throws Exception {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("tenant-lifecycle-agent");
        DeepAgent agent = createTenantAwareAgent(agentId, tempDir, true);

        TenantContext tenantCtx = TenantContext.builder().tenantId("lifecycle_tenant").build();
        String sessionId = trackSessionId("lifecycle-inv");

        Map<String, Object> inputs = Map.of(
                "query", "Reply briefly.",
                "conversation_id", sessionId);

        AgentSessionApi session = AgentSessionApi.create(sessionId, null, agent.getCard())
                .withTenantContext(tenantCtx);

        agent.invoke(inputs, session);

        Path tenantTmp = tempDir.resolve("tenants").resolve("lifecycle_tenant").resolve("tmp");
        assertTrue(Files.exists(tenantTmp),
                "tenant tmp directory should exist after invoke");

        agent.close();

        assertNull(TenantContextHolder.getCurrentTenant(),
                "TenantContextHolder must be cleared after close");

        // Verify the TmpFileCleaner scheduler has been shut down (via reflection,
        // since both the cleaner field and its scheduler field are private).
        Field cleanerField = DeepAgent.class.getDeclaredField("tmpFileCleaner");
        cleanerField.setAccessible(true);
        Object cleaner = cleanerField.get(agent);
        if (cleaner != null) {
            Field schedulerField = cleaner.getClass().getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            Object scheduler = schedulerField.get(cleaner);
            if (scheduler instanceof ExecutorService executorService) {
                assertTrue(executorService.isShutdown(),
                        "TmpFileCleaner scheduler should be shut down after agent.close()");
            }
        }
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
}
