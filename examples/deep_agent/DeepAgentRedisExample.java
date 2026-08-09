/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.deep_agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import redis.clients.jedis.JedisPooled;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates a real DeepAgent invocation whose <em>Checkpointer</em> (session
 * state) and <em>Todo</em> (task plan) both persist to the same Redis instance
 * through one shared connection.
 *
 * <p>It follows the "Todo 与 Checkpointer 共享 Redis" pattern: a single
 * thread-safe {@link JedisPooled} is created at startup and reused by two paths:
 * <ul>
 *   <li><b>Checkpointer path</b> — the shared pool is wrapped once in a
 *       {@link RedisStore} and registered as the global default checkpointer via
 *       {@link CheckpointerFactory#setDefaultCheckpointer}.</li>
 *   <li><b>Todo path</b> — the same pool is injected through
 *       {@code DeepAgentConfig.kvStoreConfig.conf.redis_client}, so the framework
 *       SPI ({@code RedisKVStoreProvider}) reuses it instead of reflecting a new
 *       non-thread-safe Jedis; {@code TaskPlanningRail} then stores todos in the
 *       resulting {@code agent.kvStore} (the same Redis connection).</li>
 * </ul>
 *
 * <p>The example then runs one real {@code DeepAgent.invoke} that asks the LLM to
 * call the {@code todo_create} tool, and verifies that both the checkpoint keys
 * ({@code *:agent_state_blobs*}) and the todo key ({@code *:todo}) land in Redis.
 */
public final class DeepAgentRedisExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AGENT_ID = "deep_agent_redis_example";
    private static final String TENANT_ID = "deep_agent_example";
    private static final String SYSTEM_PROMPT = "你是一个任务规划助手。"
            + "当用户要求规划任务时，必须先调用 todo_create 工具创建任务清单，再用一句话中文总结。";

    private DeepAgentRedisExample() {
    }

    public static void main(String[] args) throws Exception {
        Path workspacePath = Files.createTempDirectory("deep-agent-redis-");
        JedisPooled jedisPooled = null;
        DeepAgent deepAgent = null;

        try {
            String redisHost = DeepAgentExampleConfigLoader.getRedisHost();
            int redisPort = DeepAgentExampleConfigLoader.getRedisPort();
            System.out.println("=== DeepAgent Todo & Checkpointer 共享 Redis 示例 ===");
            System.out.println("[1] Redis 地址: " + redisHost + ":" + redisPort);
            System.out.println("[1] 工作目录: " + workspacePath);
            System.out.println("[1] 模型: " + DeepAgentExampleConfigLoader.getModelName()
                    + " (" + DeepAgentExampleConfigLoader.getModelProvider() + ")");

            // 2. Create one shared, thread-safe JedisPooled + wrap it in a RedisStore.
            jedisPooled = new JedisPooled(redisHost, redisPort);
            String pong = jedisPooled.ping();
            RedisStore sharedRedisStore = new RedisStore(jedisPooled);
            System.out.println("[2] 共享 JedisPooled 已创建, PING -> " + pong);

            // 3. Register a RedisCheckpointer backed by the shared RedisStore as the
            //    global default; DeepAgent's session preRun/postRun will use it.
            RedisCheckpointer checkpointer = new RedisCheckpointer(sharedRedisStore, Map.of());
            CheckpointerFactory.setDefaultCheckpointer(checkpointer);
            System.out.println("[3] RedisCheckpointer 已注册为默认 checkpointer (与 Todo 共享同一 RedisStore)");

            // Clean any leftover keys from previous runs.
            sharedRedisStore.deleteByPrefix(TENANT_ID + ":", null);

            // 4. Build the DeepAgent. The kvStoreConfig passes the SAME jedisPooled
            //    through the `redis_client` field so RedisKVStoreProvider reuses it
            //    (no reflective single-Jedis) and the Todo path shares the connection.
            Map<String, Object> kvStoreConfig = new LinkedHashMap<>();
            kvStoreConfig.put("type", "redis");
            Map<String, Object> kvConf = new LinkedHashMap<>();
            kvConf.put("host", redisHost);
            kvConf.put("port", redisPort);
            kvConf.put("redis_client", jedisPooled);
            kvStoreConfig.put("conf", kvConf);

            DeepAgentConfig config = DeepAgentConfig.builder()
                    .enableTaskLoop(true)
                    .enableTaskPlanning(true)
                    .todoStorageType("kv")
                    .kvStoreConfig(kvStoreConfig)
                    .enableTenantIsolation(true)
                    .tenantDataRoot(workspacePath.toString())
                    .workspacePath(workspacePath.toString())
                    .systemPrompt(SYSTEM_PROMPT)
                    .maxIterations(8)
                    .completionTimeout(180.0)
                    .language("cn")
                    .model(buildModelConfig())
                    .backend(buildBackendConfig())
                    .build();

            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID).name(AGENT_ID).description("DeepAgent Redis 共享示例").build();
            Workspace ws = Workspace.builder().rootPath(workspacePath.toString()).language("cn").build();
            deepAgent = HarnessFactory.createDeepAgent(card, config, ws);

            // 5. Bridge the framework timing: trigger lazy init now so
            //    TaskPlanningRail.init reads the already-injected kvStore and wires
            //    the todo storage onto the shared RedisStore.
            deepAgent.ensureInitialized();
            String kvStoreClass = deepAgent.getKvStore() == null
                    ? "null" : deepAgent.getKvStore().getClass().getSimpleName();
            System.out.println("[5] DeepAgent.ensureInitialized() 已触发, kvStore=" + kvStoreClass);

            // 6. Run one real invoke that guides the LLM to call todo_create.
            String sessionId = "deep_agent_redis_" + System.currentTimeMillis();
            TenantContext tenantCtx = TenantContext.builder().tenantId(TENANT_ID).build();
            AgentSessionApi session = AgentSession.createAgentSession(sessionId, null, deepAgent.getCard())
                    .withTenantContext(tenantCtx);
            String prompt = buildTodoPrompt(sessionId);
            System.out.println("[6] 调用 DeepAgent, sessionId=" + sessionId);
            System.out.println("[6] 提示词: " + prompt);

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("query", prompt);
            inputs.put("conversation_id", sessionId);
            Map<String, Object> result = deepAgent.invoke(inputs, session);
            System.out.println("[6] Agent 输出: " + extractOutput(result));

            // 7. Verify both Checkpoint and Todo were persisted to the shared Redis.
            verifyRedis(sharedRedisStore, sessionId);
        } finally {
            // 8. Cleanup: reset global checkpointer, delete demo keys, release resources.
            try {
                CheckpointerFactory.setDefaultCheckpointer(null);
            } catch (Exception ignored) {
                // best-effort
            }
            if (jedisPooled != null) {
                try {
                    RedisStore cleanupStore = new RedisStore(jedisPooled);
                    cleanupStore.deleteByPrefix(TENANT_ID + ":", null);
                } catch (Exception ignored) {
                    // best-effort
                }
            }
            if (deepAgent != null) {
                try {
                    deepAgent.close();
                } catch (Exception ignored) {
                    // best-effort
                }
            }
            if (jedisPooled != null) {
                try {
                    jedisPooled.close();
                } catch (Exception ignored) {
                    // best-effort
                }
            }
            try {
                Runner.stop();
            } catch (Exception ignored) {
                // best-effort
            }
            System.out.println("=== 示例结束 ===");
        }
    }

    private static Map<String, Object> buildModelConfig() {
        Map<String, Object> modelConfig = new LinkedHashMap<>();
        modelConfig.put("model", DeepAgentExampleConfigLoader.getModelName());
        modelConfig.put("temperature", 0.6);
        modelConfig.put("max_tokens", 512);
        return modelConfig;
    }

    private static Map<String, Object> buildBackendConfig() {
        Map<String, Object> backendConfig = new LinkedHashMap<>();
        backendConfig.put("client_provider", DeepAgentExampleConfigLoader.getModelProvider());
        backendConfig.put("api_key", DeepAgentExampleConfigLoader.getApiKey());
        backendConfig.put("api_base", DeepAgentExampleConfigLoader.getApiBase());
        backendConfig.put("verify_ssl", DeepAgentExampleConfigLoader.getSslVerify());
        return backendConfig;
    }

    /**
     * Builds a prompt that deterministically makes the LLM call {@code todo_create}
     * with the given session id, so the example reliably writes a todo to Redis.
     */
    private static String buildTodoPrompt(String sessionId) {
        return "请帮我规划一个多步任务并调用 todo_create 工具创建任务清单。"
                + "调用 todo_create 时必须传 session_id=\"" + sessionId + "\" 和 tasks="
                + "[{\"content\":\"分析用户需求\",\"activeForm\":\"分析需求\",\"description\":\"理解本次示例要演示的共享 Redis 能力\"},"
                + "{\"content\":\"调用 DeepAgent 执行\",\"activeForm\":\"执行 Agent\",\"description\":\"通过真实 DeepAgent 调用触发 Checkpoint 与 Todo 写入\"},"
                + "{\"content\":\"验证 Redis 持久化\",\"activeForm\":\"验证持久化\",\"description\":\"检查 Redis 中 Checkpoint 与 Todo 的 key\"}]. "
                + "创建任务清单后，用一句中文总结你做了什么。";
    }

    private static void verifyRedis(RedisStore redisStore, String sessionId) {
        System.out.println("[7] 验证 Redis 持久化 (" + TENANT_ID + ": 前缀):");
        Map<String, Object> allKeys;
        try {
            allKeys = redisStore.getByPrefix(TENANT_ID + ":");
        } catch (Exception e) {
            System.out.println("[7] 读取 Redis 失败: " + e.getMessage());
            return;
        }

        List<String> checkpointKeys = new ArrayList<>();
        List<String> todoKeys = new ArrayList<>();
        for (String key : allKeys.keySet()) {
            if (key.contains("agent_state_blobs")) {
                checkpointKeys.add(key);
            } else if (key.contains(":todo")) {
                todoKeys.add(key);
            }
        }

        if (checkpointKeys.isEmpty()) {
            System.out.println("[7] !! 未发现 Checkpoint key (agent_state_blobs). "
                    + "请确认 RedisCheckpointer 已注册且 DeepAgent 完成了一轮执行。");
        } else {
            System.out.println("[7] Checkpointer 已写入 (" + checkpointKeys.size() + " 个 key):");
            for (String key : checkpointKeys) {
                System.out.println("      - " + key);
            }
        }

        if (todoKeys.isEmpty()) {
            System.out.println("[7] !! 未发现 Todo key (*:todo). "
                    + "请确认 LLM 调用了 todo_create, 且 todoStorageType=kv 与 ensureInitialized() 已生效。");
        } else {
            System.out.println("[7] Todo 已写入 (" + todoKeys.size() + " 个 key):");
            for (String key : todoKeys) {
                Object value = allKeys.get(key);
                System.out.println("      - " + key);
                System.out.println("        内容: " + prettyJson(value));
            }
        }

        boolean shared = !checkpointKeys.isEmpty() && !todoKeys.isEmpty();
        System.out.println("[7] 共享结论: " + (shared
                ? "Checkpointer 与 Todo 均写入同一 Redis 实例 (key 前缀相同, 共享成功)"
                : "仅部分数据写入, 请检查日志."));
    }

    private static String extractOutput(Map<String, Object> result) {
        if (result == null) {
            return "null";
        }
        Object output = result.get("output");
        if (output != null) {
            return output.toString();
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }

    private static String prettyJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            try {
                Object parsed = MAPPER.readValue(text, Object.class);
                return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
            } catch (Exception e) {
                return text;
            }
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
