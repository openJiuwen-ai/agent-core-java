/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.deep_agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepAgent 通用示例模板。
 *
 * <p>本类是一个最小可运行的 DeepAgent 骨架，展示「真实 LLM 调用」的标准结构：
 * <ol>
 *   <li>从环境变量 / classpath {@code apiconfig.json}（{@code src/main/resources}）读取 LLM 配置；</li>
 *   <li>构建 {@link DeepAgentConfig}（model + backend）；</li>
 *   <li>通过 {@link HarnessFactory#createDeepAgent} 创建 {@link DeepAgent}；</li>
 *   <li>{@code ensureInitialized()} 触发懒初始化；</li>
 *   <li>通过 {@link Runner#runAgent} 阻塞调用 + {@link Runner#runAgentStreaming} 流式调用
 *       两种方式执行一轮真实调用并打印结果；</li>
 *   <li>{@code finally} 统一释放资源。</li>
 * </ol>
 *
 * <p><b>如何扩展本模板</b>：
 * <ul>
 *   <li>替换 {@link #SYSTEM_PROMPT} 与 {@link #buildUserPrompt()} 为你的业务提示词；</li>
 *   <li>在 {@code buildConfig} 中按需开启 {@code enableTaskPlanning} /
 *       {@code enableTenantIsolation} / {@code permissions} 等能力；</li>
 *   <li>需要 Redis Checkpointer / Todo 持久化时，参考
 *       {@code DeepAgentRedisExample} 替换 {@link InMemoryCheckpointer}；</li>
 *   <li>需要注册自定义工具时，在 {@code ensureInitialized()} 之后调用
 *       {@code deepAgent.registerHarnessTool(...)}；</li>
 *   <li>只需阻塞调用时，删除 step 6b 的 {@code runAgentStreaming} 段落；
 *       只需流式调用时，删除 step 6 的 {@code runAgent} 段落。</li>
 * </ul>
 *
 * <p>运行方式见同目录 {@code README.md}。
 */
public final class DeepAgentTemplateExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Agent 标识，同时用于 AgentCard.id / name 与日志。按需修改。 */
    private static final String AGENT_ID = "deep_agent_template";
    /** 系统提示词。按业务场景替换。 */
    private static final String SYSTEM_PROMPT = "你是一个通用助手，请用中文回答用户的问题。";

    private DeepAgentTemplateExample() {
    }

    /**
     * 入口。
     *
     * @param args 未使用
     * @throws Exception 示例执行失败时抛出
     */
    public static void run(String[] args) throws Exception {
        System.out.println("=== DeepAgent 通用示例模板 ===");

        // 1. 准备工作目录（系统临时目录，不污染示例目录）。
        Path workspacePath = Files.createTempDirectory("deep-agent-template-");
        System.out.println("[1] 工作目录: " + workspacePath);
        System.out.println("[1] 模型: " + DeepAgentExampleConfigLoader.getModelName()
                + " (" + DeepAgentExampleConfigLoader.getModelProvider() + ")");

        // 2. 设置默认 checkpointer（InMemory 即可满足单进程示例；
        //    需要 Redis 持久化时参考 DeepAgentRedisExample）。
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);
        Runner.start();
        System.out.println("[2] InMemoryCheckpointer 已注册, Runner 已启动");

        DeepAgent deepAgent = null;
        try {
            // 3. 构建 DeepAgentConfig。
            DeepAgentConfig config = buildConfig(workspacePath);

            // 4. 创建 DeepAgent。
            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID).name(AGENT_ID)
                    .description("DeepAgent 通用示例模板").build();
            Workspace ws = Workspace.builder()
                    .rootPath(workspacePath.toString())
                    .language("cn").build();
            deepAgent = HarnessFactory.createDeepAgent(card, config, ws);

            // 5. 触发懒初始化（保证 rails 在 kvStore / permissions 注入后完成 wiring）。
            deepAgent.ensureInitialized();
            System.out.println("[3] DeepAgent.ensureInitialized() 已触发");

            // —— 扩展点：在此处注册自定义工具 ——
            // deepAgent.registerHarnessTool(yourTool);

            // 6. 通过 Runner.runAgent 执行一轮真实调用（阻塞，返回 Map 结果）。
            //    对比 deepAgent.invoke：Runner.runAgent 是框架统一入口，内部会处理
            //    Runner 启停、session 绑定、ModelContext 注入等，推荐作为标准调用方式。
            String sessionId = "template_" + System.currentTimeMillis();
            AgentSessionApi session = AgentSession.createAgentSession(sessionId, null, deepAgent.getCard());
            String prompt = buildUserPrompt();
            System.out.println("[4] 调用 Runner.runAgent, sessionId=" + sessionId);
            System.out.println("[4] 提示词: " + prompt);

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("query", prompt);
            inputs.put("conversation_id", sessionId);
            Object runResult = Runner.runAgent(deepAgent, inputs, session, null);
            System.out.println("[5] Runner.runAgent 输出: " + extractOutput(asMap(runResult)));

            // 6b. 通过 Runner.runAgentStreaming 执行流式调用（返回 Iterator<Object>，
            //     元素为 OutputSchema）。streamModes 传 null 等价于 List.of(StreamMode.OUTPUT)。
            String sessionId2 = "template_stream_" + System.currentTimeMillis();
            AgentSessionApi session2 = AgentSession.createAgentSession(sessionId2, null, deepAgent.getCard());
            System.out.println("[6] 调用 Runner.runAgentStreaming, sessionId=" + sessionId2);
            Map<String, Object> inputs2 = new LinkedHashMap<>();
            inputs2.put("query", prompt);
            inputs2.put("conversation_id", sessionId2);
            Iterator<Object> stream = Runner.runAgentStreaming(
                    deepAgent, inputs2, session2, null, null);
            List<Object> chunks = new ArrayList<>();
            StringBuilder streamText = new StringBuilder();
            java.util.Map<String, Integer> typeCounts = new java.util.LinkedHashMap<>();
            while (stream.hasNext()) {
                Object chunk = stream.next();
                chunks.add(chunk);
                // 统计 chunk 类型分布（OutputSchema.type 或 class 名）
                String typeKey = chunk instanceof OutputSchema os
                        ? "OutputSchema:" + os.getType() : chunk.getClass().getName();
                typeCounts.merge(typeKey, 1, Integer::sum);
                // 提取 answer / llm_output / tool_output 的 content 文本
                if (chunk instanceof OutputSchema os) {
                    String t = os.getType();
                    if ("answer".equals(t) || "llm_output".equals(t) || "tool_output".equals(t)) {
                        Object payload = os.getPayload();
                        if (payload instanceof Map<?, ?> pm) {
                            Object content = pm.get("content");
                            if (content == null) {
                                content = pm.get("text");
                            }
                            if (content != null) {
                                streamText.append(content);
                            }
                        } else if (payload != null) {
                            streamText.append(payload);
                        }
                    }
                }
            }
            System.out.println("[7] Runner.runAgentStreaming 收到 " + chunks.size() + " 个 chunk");
            System.out.println("[7] chunk type 分布: " + typeCounts);
            System.out.println("[7] 流式文本: " + truncate(streamText.toString(), 300));
        } finally {
            // 7. 统一清理：重置全局 checkpointer → 关闭 agent → 停止 Runner。
            try {
                CheckpointerFactory.setDefaultCheckpointer(null);
            } catch (Exception ignored) {
                // best-effort
            }
            if (deepAgent != null) {
                try {
                    deepAgent.close();
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

    // ===================== Config builders =====================

    /**
     * 构建 DeepAgentConfig。按需调整各项开关。
     *
     * @param workspacePath 工作目录
     * @return DeepAgentConfig
     */
    private static DeepAgentConfig buildConfig(Path workspacePath) {
        return DeepAgentConfig.builder()
                .enableTaskLoop(true)
                .enableTaskPlanning(false)        // 需要 Todo 时改为 true + todoStorageType("kv")
                .enableTenantIsolation(false)     // 需要多租户隔离时改为 true
                .restrictToWorkDir(false)
                .workspacePath(workspacePath.toString())
                .systemPrompt(SYSTEM_PROMPT)
                .maxIterations(8)
                .completionTimeout(180.0)
                .language("cn")
                .model(buildModelConfig())
                .backend(buildBackendConfig())
                .build();

        // —— 扩展点：按需追加 ——
        // .permissions(permissionsMap)                 // 工具护栏，参考 DeepAgentToolGuardrailExample
        // .todoStorageType("kv")                        // Todo 存到 kvStore（需 enableTaskPlanning(true)）
        // .kvStoreConfig(kvStoreConfigMap)             // 注入 Redis 连接，参考 DeepAgentRedisExample
        // .tenantDataRoot(workspacePath.toString())     // 多租户数据根目录（需 enableTenantIsolation(true)）
    }

    /**
     * 模型参数。temperature / max_tokens 按业务调整。
     */
    private static Map<String, Object> buildModelConfig() {
        Map<String, Object> modelConfig = new LinkedHashMap<>();
        modelConfig.put("model", DeepAgentExampleConfigLoader.getModelName());
        modelConfig.put("temperature", 0.6);
        modelConfig.put("max_tokens", 512);
        return modelConfig;
    }

    /**
     * 后端（LLM 客户端）配置。从 ConfigLoader 读取，无需硬编码。
     */
    private static Map<String, Object> buildBackendConfig() {
        Map<String, Object> backendConfig = new LinkedHashMap<>();
        backendConfig.put("client_provider", DeepAgentExampleConfigLoader.getModelProvider());
        backendConfig.put("api_key", DeepAgentExampleConfigLoader.getApiKey());
        backendConfig.put("api_base", DeepAgentExampleConfigLoader.getApiBase());
        backendConfig.put("verify_ssl", DeepAgentExampleConfigLoader.getSslVerify());
        return backendConfig;
    }

    // ===================== Prompt & output =====================

    /**
     * 用户提示词。按业务场景替换。
     */
    private static String buildUserPrompt() {
        return "你好，请用一句话介绍你自己。";
    }

    /**
     * 从 runAgent / invoke 返回的结果中提取可读输出。
     * 结果通常是 Map，优先取 "output" 字段；否则序列化整个对象。
     */
    @SuppressWarnings("unchecked")
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

    /**
     * 把 runAgent 返回的 Object 安全转成 Map（实际返回类型通常是 LinkedHashMap）。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object result) {
        if (result instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of("__raw__", String.valueOf(result));
    }

    /**
     * 截断文本到指定长度，单行化。
     */
    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ');
        if (oneLine.length() <= maxLen) {
            return oneLine;
        }
        return oneLine.substring(0, maxLen) + "...";
    }
}
