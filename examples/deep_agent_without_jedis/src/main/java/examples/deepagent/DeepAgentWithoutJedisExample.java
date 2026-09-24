/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.deepagent;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class DeepAgentWithoutJedisExample {
    private static final String AGENT_ID = "deep_agent_without_jedis";
    private static final String TENANT_ID = "deep_agent_without_jedis_tenant";

    private DeepAgentWithoutJedisExample() {
    }

    public static void main(String[] args) throws Exception {
        assertJedisIsAbsent();
        Path workspacePath = Files.createTempDirectory("deep-agent-without-jedis-");
        DeepAgent agent = null;
        try {
            System.out.println("=== DeepAgent without Jedis ===");
            System.out.println("[1] model=" + DeepAgentWithoutJedisConfigLoader.modelName()
                    + " (" + DeepAgentWithoutJedisConfigLoader.modelProvider() + ")");
            System.out.println("[1] workspace=" + workspacePath);

            DeepAgentConfig config = DeepAgentConfig.builder()
                    .enableTaskLoop(true)
                    .enableTaskPlanning(true)
                    .addGeneralPurposeAgent(true)
                    .todoStorageType("file")
                    .enableTenantIsolation(true)
                    .tenantDataRoot(workspacePath.toString())
                    .workspacePath(workspacePath.toString())
                    .systemPrompt("你是一个任务执行助手。请先使用 todo_create 创建多步任务，再完成任务并用中文简要总结。")
                    .maxIterations(8)
                    .completionTimeout(180.0)
                    .language("cn")
                    .model(modelConfig())
                    .backend(backendConfig())
                    .build();

            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID)
                    .name(AGENT_ID)
                    .description("DeepAgent without Jedis")
                    .build();
            Workspace workspace = Workspace.builder()
                    .rootPath(workspacePath.toString())
                    .language("cn")
                    .build();

            agent = HarnessFactory.createDeepAgent(card, config, workspace);
            agent.ensureInitialized();
            System.out.println("[2] DeepAgent initialized with a general-purpose subagent, file todos, "
                    + "and in-memory checkpoint");

            String sessionId = "session-" + System.currentTimeMillis();
            AgentSessionApi session = AgentSession.createAgentSession(sessionId, null, agent.getCard())
                    .withTenantContext(TenantContext.builder().tenantId(TENANT_ID).build());
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("query", "请先调用 todo_create 创建两个任务：整理当前请求、总结为什么普通 DeepAgent 不需要 Redis。"
                    + "调用时使用 session_id=\"" + sessionId + "\"，完成后给出两句话总结。");
            inputs.put("conversation_id", sessionId);

            Map<String, Object> result = agent.invoke(inputs, session);
            if (result == null || result.containsKey("error") || Boolean.TRUE.equals(result.get("rejected"))
                    || "interrupt".equals(result.get("result_type"))
                    || result.get("final_result") instanceof Map<?, ?> finalResult
                    && finalResult.containsKey("error")) {
                throw new IllegalStateException("DeepAgent did not complete successfully: " + result);
            }
            System.out.println("[3] Agent output: " + result.getOrDefault("output", result));
            try (Stream<Path> files = Files.walk(workspacePath)) {
                Path todoFile = files.filter(path -> path.getFileName().toString().equals("todo.json"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "The model did not create a file-backed todo; inspect the agent output"));
                System.out.println("[4] File-backed todo: " + todoFile);
            }
            System.out.println("=== Example completed ===");
        } finally {
            if (agent != null) {
                agent.close();
            }
            CheckpointerFactory.setDefaultCheckpointer(null);
            Runner.stop();
        }
    }

    private static Map<String, Object> modelConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("model", DeepAgentWithoutJedisConfigLoader.modelName());
        config.put("temperature", 0.2);
        config.put("max_tokens", 512);
        return config;
    }

    private static Map<String, Object> backendConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("client_provider", DeepAgentWithoutJedisConfigLoader.modelProvider());
        config.put("api_key", DeepAgentWithoutJedisConfigLoader.apiKey());
        config.put("api_base", DeepAgentWithoutJedisConfigLoader.apiBase());
        config.put("verify_ssl", DeepAgentWithoutJedisConfigLoader.sslVerify());
        return config;
    }

    private static void assertJedisIsAbsent() throws ClassNotFoundException {
        try {
            Class.forName("redis.clients.jedis.Jedis", false,
                    DeepAgentWithoutJedisExample.class.getClassLoader());
        } catch (ClassNotFoundException expected) {
            return;
        }
        throw new IllegalStateException(
                "Jedis is present on the consumer classpath; check the Maven exclusion before running this example");
    }
}
