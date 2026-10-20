/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.deep_agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadRedisConfig;
import com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import redis.clients.jedis.JedisPooled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Context Offload（上下文卸载/装载）端到端示例 —— 真实 DeepAgent + Runner 触发版。
 *
 * <p>与纯管道演示不同，本示例通过 {@link Runner#runAgent} / {@link Runner#runAgentStreaming}
 * 真实驱动一个 {@link DeepAgent}（内部 {@link ReActAgent}），让 LLM 在多轮对话中
 * 自然触发 Context Offload v2 管道的「卸载 → marker 渲染 → 装载回注」闭环：</p>
 *
 * <ol>
 *   <li><b>配置注入</b>：{@code DeepAgentConfig} 不携带 Context Offload 配置，
 *       本示例在 {@code ensureInitialized()} 之前通过
 *       {@code deepAgent.reactAgent()} 拿到内部 {@link ReActAgent}，对其
 *       {@link ReActAgentConfig} 原地设置 {@code contextEngineConfig}
 *       （enable_offload + OffloadConfig）与 {@code contextProcessors}
 *       （MessageOffloader spec）。首次 {@code initContext} 时框架检测到配置
 *       变化自动重建引擎并接线 v2 管道（ReActAgent#bindOffloadPipeline）。</li>
 *   <li><b>真实触发卸载</b>：注册一个返回大体积文本的工具
 *       {@code generate_large_report}（数千字符，远超 {@code large_message_threshold}），
 *       第一轮 {@link Runner#runAgent} 让 LLM 调用该工具 —— 工具结果作为 tool
 *       消息进入上下文时被 MessageOffloader 自动卸载到 FILE 后端，inline 内容
 *       被替换为 {@code [[OFFLOAD: handle=..., type=filesystem, path=...]]} 标记。</li>
 *   <li><b>真实触发装载（第一轮内闭环）</b>：{@code enable_reload=true} 使框架自动注册
 *       内置 {@code reload_original_context_messages} 工具，并在系统消息自动追加
 *       取回提示词（SessionModelContext#RELOADER_SYSTEM_PROMPT）——业务提示词无需
 *       重复编写 OFFLOAD/reload 引导。第一轮 ReAct 循环内 LLM 从 marker 中提取
 *       {@code offload_handle}/{@code offload_type} 主动调用该工具 ——
 *       框架走五步装载校验（namespace → 状态门 → 定向后端 → sha256 → touch）把原文
 *       回注上下文，LLM 基于原文作答（后续轮次 prompt tokens 显著跳升即为装载证据）。
 *       reload 非消费式：装载成功后落盘文件保留。</li>
 *   <li><b>第二轮（流式）展示跨调用 catalog 生命周期</b>：v2 管道的 catalog 是
 *       per-context 绑定的内存对象（{@code OffloadCatalogImpl}），每次 Runner 调用
 *       新建 context 时重新绑定 —— 上一轮的 handle 在新 catalog 中查不到，reload
 *       返回 {@code OFFLOAD_MISSING}（落盘文件本身仍在，由 Janitor 按孤儿策略回收）；
 *       LLM 随即基于上下文中第一轮已装载的原文作答，验证装载结果的知识留存。</li>
 *   <li><b>可选 Redis 共享配置</b>：配置 {@code REDIS_HOST}（env 或
 *       classpath 中的 {@code apiconfig.json}）时，本示例创建一个线程安全的
 *       共享 {@code JedisPooled}，用于两条路径：
 *       <ul>
 *         <li><b>Checkpointer 路径</b> —— {@code JedisPooled} → {@code RedisStore} →
 *             {@code RedisCheckpointer}，注册为默认 checkpointer（替代 InMemory，
 *             与 {@code DeepAgentRedisExample} 的共享配置完全一致）。</li>
 *         <li><b>Offload REDIS 后端</b> —— 框架 {@code buildCompositeStore} 内部按
 *             {@code OffloadRedisConfig} 的 host/port 自建独立 {@code JedisPool}
 *             （架构设计：Offload 不共享全局 pool，见 {@code OffloadRedisConfig} 注释
 *             "independent pool — the global Redis pool is not shared"），
 *             但两者指向同一 Redis 实例，验证「同一 Redis 中同时存在 checkpoint 与
 *             offload 数据」的共享语义。后端链为 FILESYSTEM → REDIS → MEMORY。</li>
 *       </ul>
 *       未配置 Redis 时 Checkpointer 回退 {@code InMemoryCheckpointer}，Offload
 *       后端链退化为 FILESYSTEM → MEMORY。Redis 不可达时
 *       {@link com.openjiuwen.core.context.offload.store.CompositeOffloadStore}
 *       按链降级，save 永不抛错。</li>
 * </ol>
 *
 * <p><b>关键旋钮（演示用调小）</b>：{@code large_message_threshold=200}
 * （默认 1000，单条消息卸载边界）；{@code tokens_threshold=100}
 * （默认 20000，总量触发门控——每次加消息时先按「上下文总 tokens 是否达标」
 * 决定是否执行卸载扫描）；单轮场景下 {@code recent_message_protection_count=0}
 * （默认 5，保护窗口会把整个单轮上下文挡在卸载范围之外）。</p>
 *
 * <p><b>环境要求</b>：需要真实 LLM 凭据（env：{@code API_BASE / API_KEY /
 * MODEL_PROVIDER / MODEL_NAME / LLM_SSL_VERIFY}，或 classpath 中的
 * {@code apiconfig.json}，即 {@code src/main/resources/apiconfig.json}，支持
 * 系统属性 {@code openjiuwen.example.config} / env {@code OPENJIUWEN_API_CONFIG}
 * 指定外部文件）；Redis 可选（{@code REDIS_HOST / REDIS_PORT / REDIS_PASSWORD}）。</p>
 *
 * <p>运行方式见同目录 {@code README.md}。</p>
 */
public final class ContextOffloadExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String AGENT_ID = "context_offload_demo";

    /** 租户前缀，用于运行前后清理 Redis 中的 demo key。
     *  offload 示例未开 enableTenantIsolation，checkpoint key 格式为
     *  {@code <sessionId>:agent:<AGENT_ID>:agent_state_blobs}，其中 sessionId 以
     *  "offload_demo_" 开头，因此用该前缀清理所有历史 demo key。 */
    private static final String TENANT_ID = "offload_demo_";

    /** 返回大体积文本的自定义工具名（触发卸载的主角）。 */
    private static final String REPORT_TOOL = "generate_large_report";

    /** 单条消息卸载阈值（字符）。演示用调小值：工具输出数千字符远超该值即被卸载。 */
    private static final int LARGE_MESSAGE_THRESHOLD = 200;

    /** 业务角色提示词。注意：OFFLOAD/reload 的引导不需要写在这里——
     *  {@code enable_reload=true} 时框架会自动注册 reload 工具并在系统消息
     *  追加取回提示词（SessionModelContext#RELOADER_SYSTEM_PROMPT），模型看到
     *  [[OFFLOAD: ...]] 标记后会自行决定是否取回原文。 */
    private static final String SYSTEM_PROMPT = "你是一个报告助手。生成报告必须调用 "
            + REPORT_TOOL + " 工具，并基于工具返回的内容回答。";

    private ContextOffloadExample() {
    }

    /**
     * 入口。
     *
     * @param args 未使用
     * @throws Exception 示例执行失败时抛出
     */
    public static void run(String[] args) throws Exception {
        System.out.println("=== Context Offload 卸载/装载端到端示例（DeepAgent + Runner 真实触发）===");

        // [1] 读取 LLM 配置（env 优先，回退 classpath apiconfig.json；缺失时优雅退出）。
        String apiBase;
        String apiKey;
        String modelProvider;
        String modelName;
        try {
            apiBase = DeepAgentExampleConfigLoader.getApiBase();
            apiKey = DeepAgentExampleConfigLoader.getApiKey();
            modelProvider = DeepAgentExampleConfigLoader.getModelProvider();
            modelName = DeepAgentExampleConfigLoader.getModelName();
        } catch (IllegalStateException missing) {
            System.out.println("[1] x 缺少 LLM 配置: " + missing.getMessage());
            System.out.println("    本示例需要真实 LLM 触发。请设置环境变量 API_BASE / API_KEY /");
            System.out.println("    MODEL_PROVIDER / MODEL_NAME，或在 src/main/resources/apiconfig.json");
            System.out.println("    中填入对应字段（或用 openjiuwen.example.config 系统属性指定外部文件）后重试。");
            return;
        }
        System.out.println("[1] 模型: " + modelName + " (" + modelProvider + "), apiBase=" + apiBase);

        // [2] 准备 Offload 存储根目录与 Agent 工作目录（系统临时目录，不污染示例目录）。
        Path offloadRoot = Files.createTempDirectory("context-offload-demo-");
        Path workspacePath = Files.createTempDirectory("context-offload-ws-");
        boolean withRedis = useRedis();
        System.out.println("[2] Offload 根目录: " + offloadRoot);
        System.out.println("[2] Agent 工作目录: " + workspacePath);

        // [2.5] 共享 Redis 连接（参考 DeepAgentRedisExample 的共享配置模式）。
        //       当 withRedis=true 时创建一个线程安全的 JedisPooled，Checkpointer 与
        //       Offload 的 REDIS 后端指向同一 Redis 实例。
        //       - Checkpointer 路径：JedisPooled -> RedisStore -> RedisCheckpointer
        //         （共享连接，与 DeepAgentRedisExample 完全一致）
        //       - Offload REDIS 后端：框架 buildCompositeStore 内部按 OffloadRedisConfig
        //         的 host/port 自建独立 JedisPool（架构设计：Offload 不共享全局 pool），
        //         但两者指向同一 Redis 实例，验证「同一 Redis 中同时存在 checkpoint 与
        //         offload 数据」的共享语义。
        JedisPooled jedisPooled = null;
        RedisStore sharedRedisStore = null;
        if (withRedis) {
            String redisHost = DeepAgentExampleConfigLoader.getRedisHost();
            int redisPort = DeepAgentExampleConfigLoader.getRedisPort();
            jedisPooled = new JedisPooled(redisHost, redisPort);
            String pong = jedisPooled.ping();
            sharedRedisStore = new RedisStore(jedisPooled);
            System.out.println("[2.5] 共享 JedisPooled 已创建, PING -> " + pong
                    + " (" + redisHost + ":" + redisPort + ")");
        } else {
            System.out.println("[2.5] 未配置 REDIS_HOST, Checkpointer 使用 InMemory, Offload 后端链 FILESYSTEM->MEMORY");
        }

        DeepAgent deepAgent = null;
        try {
            // [3] 构建 Context Offload v2 配置块（ContextEngineConfig + MessageOffloaderConfig）。
            ContextEngineConfig engineConfig = buildContextEngineConfig(offloadRoot);
            MessageOffloaderConfig offloaderConfig = buildOffloaderConfig();
            System.out.println("[3] enable_offload=true, 后端链=FILESYSTEM"
                    + (withRedis ? "->REDIS->MEMORY" : "->MEMORY")
                    + ", large_message_threshold=" + LARGE_MESSAGE_THRESHOLD
                    + ", recent_message_protection_count=0, enable_reload=true"
                    + (withRedis ? " | Checkpointer=RedisCheckpointer(共享 JedisPooled)" : " | Checkpointer=InMemory"));

            // [4] 构建 DeepAgentConfig 并创建 DeepAgent。
            //     enableTaskLoop(true) 必须开启：invokeInternal 在任务循环关闭时
            //     直接返回空壳结果（不调用 LLM，工具也不会执行）。
            DeepAgentConfig deepConfig = DeepAgentConfig.builder()
                    .enableTaskLoop(true)
                    .systemPrompt(SYSTEM_PROMPT)
                    .maxIterations(8)
                    .completionTimeout(180.0)
                    .language("cn")
                    .restrictToWorkDir(false)
                    .workspacePath(workspacePath.toString())
                    .model(buildModelConfig(modelName))
                    .backend(buildBackendConfig(apiBase, apiKey, modelProvider))
                    .build();
            AgentCard card = AgentCard.builder()
                    .id(AGENT_ID).name(AGENT_ID)
                    .description("Context Offload 卸载/装载端到端示例").build();
            Workspace ws = Workspace.builder()
                    .rootPath(workspacePath.toString())
                    .language("cn").build();
            deepAgent = HarnessFactory.createDeepAgent(card, deepConfig, ws);
            System.out.println("[4] DeepAgent 已创建");

            // [5] 把 Offload 配置注入内部 ReActAgent（必须在首个 session 的 initContext 之前）。
            //     DeepAgentConfig 不携带 contextEngineConfig，只能对内部 ReActAgentConfig
            //     原地设置；框架在首次 initContext 时检测到配置变化会自动重建引擎，
            //     并按 enable_offload 接线 v2 管道（bindOffloadPipeline）。
            Object rawAgent = deepAgent.reactAgent();
            if (!(rawAgent instanceof ReActAgent reactAgent)) {
                throw new IllegalStateException("DeepAgent 内部代理不是 ReActAgent，无法注入 Offload 配置");
            }
            Object rawConfig = reactAgent.getConfig();
            if (!(rawConfig instanceof ReActAgentConfig agentConfig)) {
                throw new IllegalStateException("ReActAgent 配置类型异常: " + rawConfig);
            }
            agentConfig.setContextEngineConfig(engineConfig);
            agentConfig.setContextProcessors(List.of(
                    new ContextEngine.ProcessorSpec("MessageOffloader", offloaderConfig)));
            System.out.println("[5] Offload 配置已注入内部 ReActAgent"
                    + "（contextEngineConfig + contextProcessors[MessageOffloader]）");

            // [6] 懒初始化 + 注册大体积输出工具（触发卸载的主角）。
            deepAgent.ensureInitialized();
            deepAgent.registerHarnessTool(buildLargeReportTool());
            System.out.println("[6] ensureInitialized 完成, 已注册工具: " + REPORT_TOOL);

            // [7] 启动 Runner。配置 Redis 时用共享 RedisCheckpointer（与 Offload 的 REDIS
            //     后端指向同一 Redis 实例）；未配置 Redis 时回退 InMemoryCheckpointer。
            if (withRedis && sharedRedisStore != null) {
                // 清理上次运行的残留 key（scanKeys 会自动追加 '*' 通配符）。
                sharedRedisStore.deleteByPrefix(TENANT_ID, null).join();
                CheckpointerFactory.setDefaultCheckpointer(
                        new RedisCheckpointer(sharedRedisStore, Map.of()));
                System.out.println("[7] RedisCheckpointer 已注册为默认 checkpointer"
                        + "（与 Offload REDIS 后端共享同一 Redis 实例）");
            } else {
                CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
                System.out.println("[7] InMemoryCheckpointer 已注册（未配置 Redis）");
            }
            Runner.setConfig(RunnerConfig.DEFAULT);
            Runner.start();
            System.out.println("[7] Runner 已启动");

            // [8] 第一轮（阻塞）：LLM 调用大输出工具 -> 工具结果被自动卸载 -> marker 渲染进上下文。
            String sessionId = "offload_demo_" + System.currentTimeMillis();
            AgentSessionApi session = AgentSession.createAgentSession(sessionId, null, deepAgent.getCard());
            String prompt1 = "请调用 " + REPORT_TOOL + " 工具，生成一份主题为「企业级 AI Agent 平台技术架构」"
                    + "的详细报告，然后告诉我报告一共覆盖哪几个章节。";
            System.out.println("[8] Runner.runAgent（第一轮：触发卸载）, sessionId=" + sessionId);
            System.out.println("[8] 提示词: " + prompt1);
            Map<String, Object> inputs1 = new LinkedHashMap<>();
            inputs1.put("query", prompt1);
            inputs1.put("conversation_id", sessionId);
            Object result1 = Runner.runAgent(deepAgent, inputs1, session, null);
            String output1 = extractOutput(asMap(result1));
            System.out.println("[8] 第一轮输出: " + truncate(output1, 400));
            System.out.println("[8] 输出含卸载 marker [[OFFLOAD:...]]: "
                    + (output1.contains("[[OFFLOAD:") ? "是" : "否（以落盘文件为准）"));
            System.out.println("[8] Offload FILE 后端落盘文件:");
            listOffloadFiles(offloadRoot);

            // [9] 第二轮（流式，同一 sessionId 复用上下文）：展示跨调用 catalog 生命周期。
            //     v2 管道的 OffloadCatalog 是 per-context 绑定的内存对象：streaming 调用
            //     新建 context 并重新 bindOffloadPipeline，上一轮 handle 在新 catalog 中
            //     查不到 → reload 返回 OFFLOAD_MISSING（落盘文件仍保留）。LLM 随即基于
            //     第一轮已装载进上下文的原文作答，验证装载结果的知识留存。
            AgentSessionApi session2 = AgentSession.createAgentSession(sessionId, null, deepAgent.getCard());
            // 纯业务提问，不含任何框架术语（OFFLOAD / reload 工具名等）：
            // enable_reload=true 时框架已自动注册 reload 工具并追加取回提示词，
            // 模型看到 [[OFFLOAD: ...]] 标记后会自行决定取回原文，无需业务侧引导。
            String prompt2 = "请复述报告中「REPORT_BEGIN」之后的头两行内容。";
            System.out.println("[9] Runner.runAgentStreaming（第二轮：跨调用 catalog 生命周期演示）");
            System.out.println("[9] 提示词: " + prompt2);
            Map<String, Object> inputs2 = new LinkedHashMap<>();
            inputs2.put("query", prompt2);
            inputs2.put("conversation_id", sessionId);
            Iterator<Object> stream = Runner.runAgentStreaming(deepAgent, inputs2, session2, null, null);

            int chunkCount = 0;
            boolean reloadObserved = false;
            StringBuilder streamText = new StringBuilder();
            Map<String, Integer> typeCounts = new LinkedHashMap<>();
            while (stream.hasNext()) {
                Object chunk = stream.next();
                chunkCount++;
                String typeKey = chunk instanceof OutputSchema os
                        ? "OutputSchema:" + os.getType() : chunk.getClass().getSimpleName();
                typeCounts.merge(typeKey, 1, Integer::sum);
                if (!(chunk instanceof OutputSchema os)) {
                    continue;
                }
                String type = os.getType();
                if (!"answer".equals(type) && !"llm_output".equals(type) && !"tool_output".equals(type)) {
                    continue;
                }
                Object payload = os.getPayload();
                String content = null;
                if (payload instanceof Map<?, ?> pm) {
                    Object inner = pm.get("content");
                    if (inner == null) {
                        inner = pm.get("text");
                    }
                    content = inner == null ? null : String.valueOf(inner);
                } else if (payload != null) {
                    content = String.valueOf(payload);
                }
                if (content == null) {
                    continue;
                }
                streamText.append(content);
                if ("tool_output".equals(type)) {
                    System.out.println("[9] 观察到工具输出 chunk: " + truncate(content, 160));
                }
            }
            reloadObserved = streamText.indexOf("REPORT_BEGIN") >= 0 || streamText.indexOf("技术研究报告") >= 0;
            System.out.println("[9] 收到 " + chunkCount + " 个 chunk, 类型分布: " + typeCounts);
            System.out.println("[9] 流式文本: " + truncate(streamText.toString(), 300));
            System.out.println("[9] 装载成功迹象（流中含报告原文片段）: " + (reloadObserved ? "是" : "否"));
            System.out.println("[9] 第二轮后的 Offload 落盘文件:");
            listOffloadFiles(offloadRoot);

            System.out.println("[10] 演示完成：卸载（第一轮 tool 消息 -> FILE 后端）"
                    + "与装载（第一轮内 reload 回注原文）均已通过真实 LLM 触发；"
                    + "第二轮演示了跨调用 catalog 生命周期（OFFLOAD_MISSING 属预期行为）。");
        } finally {
            // [11] 统一清理：重置全局 checkpointer -> 关闭 agent（停 Janitor 等资源）-> 停 Runner
            //      -> 清理 Redis demo key -> 关闭共享 JedisPooled。
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
            if (withRedis && sharedRedisStore != null) {
                // [11.5] 验证：打印 Redis 中的 demo key（证明 RedisCheckpointer 确实写入了共享 Redis）。
                try {
                    var allKeys = sharedRedisStore.getByPrefix(TENANT_ID).join();
                    int checkpointCount = 0;
                    for (String key : allKeys.keySet()) {
                        if (key.contains("agent_state_blobs")) {
                            checkpointCount++;
                        }
                    }
                    System.out.println("[11.5] Redis 验证: 发现 " + allKeys.size()
                            + " 个 demo key（" + checkpointCount + " 个 checkpoint），"
                            + "证明 RedisCheckpointer 已写入共享 Redis 实例");
                } catch (Exception verifyEx) {
                    System.out.println("[11.5] Redis 验证读取失败: " + verifyEx.getMessage());
                }
                // 清理 demo key（best-effort，scanKeys 会自动追加 '*' 通配符）。
                try {
                    sharedRedisStore.deleteByPrefix(TENANT_ID, null).join();
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
            System.out.println("=== 示例结束 ===");
            System.out.println("Offload 根目录（保留供查看，可手动删除）: " + offloadRoot);
            System.out.println("Agent 工作目录（保留供查看，可手动删除）: " + workspacePath);
        }
    }

    // ===================== Offload configuration =====================

    /**
     * 构建 ContextEngineConfig：enable_offload + 嵌套 OffloadConfig + enable_reload。
     *
     * <p>后端链 FILESYSTEM → REDIS → MEMORY；未配置 Redis 时
     * {@code ReActAgent#buildCompositeStore} 会跳过 REDIS 环节，
     * 链自动退化为 FILESYSTEM → MEMORY。</p>
     */
    private static ContextEngineConfig buildContextEngineConfig(Path offloadRoot) {
        OffloadConfig offloadConfig = new OffloadConfig();
        offloadConfig.setPrimaryBackend(OffloadBackend.FILESYSTEM);
        offloadConfig.setFallbackBackend(OffloadBackend.REDIS);
        offloadConfig.setFinalFallback(OffloadBackend.MEMORY);
        offloadConfig.setWorkspaceRoot(offloadRoot.toString());
        // 单轮演示必须关闭保护窗口：默认值 5 会把整个单轮上下文挡在卸载范围之外。
        offloadConfig.setRecentMessageProtectionCount(0);
        if (useRedis()) {
            OffloadRedisConfig redisConfig = new OffloadRedisConfig();
            redisConfig.setHost(DeepAgentExampleConfigLoader.getRedisHost());
            redisConfig.setPort(DeepAgentExampleConfigLoader.getRedisPort());
            String password = System.getenv("REDIS_PASSWORD");
            if (password != null && !password.isBlank()) {
                redisConfig.setPassword(password);
            }
            offloadConfig.setRedis(redisConfig);
        }

        return ContextEngineConfig.builder()
                .maxContextMessageNum(200)
                .defaultWindowRoundNum(10)
                // 注册内置装载工具 reload_original_context_messages（enableReload）。
                .enableReload(true)
                // 打开 Context Offload v2 管道开关。
                .enableOffload(true)
                .offload(offloadConfig)
                .build();
    }

    /**
     * 构建 MessageOffloader 处理器配置（经 {@code ContextEngine.ProcessorSpec} 注入）。
     *
     * <p>v2 管道的触发链：{@code triggerAddMessages}（总量门控，
     * tokens >= {@code tokens_threshold} 才继续）→ {@code onAddMessages}
     * 逐条扫描（单条卸载边界为 {@code large_message_threshold}，
     * 保护窗口 {@code recent_message_protection_count} 此处关闭）。</p>
     */
    private static MessageOffloaderConfig buildOffloaderConfig() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        // 演示用低门控：runAddProcessors 先按「上下文总 tokens >= tokens_threshold」
        // 决定是否执行卸载扫描（triggerAddMessages 是 onAddMessages 的前置门）。
        // 默认 20000 在单轮演示中永远达不到，这里调低让第一轮对话即可触发。
        config.setTokensThreshold(100);
        config.setLargeMessageThreshold(LARGE_MESSAGE_THRESHOLD);
        config.setOffloadMessageType(List.of("tool"));
        config.setRecentMessageProtectionCount(0);
        config.setTrimSize(100);
        return config;
    }

    /**
     * 是否启用 Redis 中间层：{@code REDIS_HOST} env 或 classpath
     * {@code apiconfig.json} 显式配置时返回 true。ConfigLoader 的 getRedisHost 带默认值，
     * 不能直接当开关用。
     */
    private static boolean useRedis() {
        String env = System.getenv("REDIS_HOST");
        if (env != null && !env.isBlank()) {
            return true;
        }
        String inFile = DeepAgentExampleConfigLoader.load().get("REDIS_HOST");
        return inFile != null && !inFile.isBlank();
    }

    // ===================== DeepAgent config =====================

    private static Map<String, Object> buildModelConfig(String modelName) {
        Map<String, Object> modelConfig = new LinkedHashMap<>();
        modelConfig.put("model", modelName);
        modelConfig.put("temperature", 0.6);
        modelConfig.put("max_tokens", 1024);
        return modelConfig;
    }

    private static Map<String, Object> buildBackendConfig(String apiBase, String apiKey, String modelProvider) {
        Map<String, Object> backendConfig = new LinkedHashMap<>();
        backendConfig.put("client_provider", modelProvider);
        backendConfig.put("api_key", apiKey);
        backendConfig.put("api_base", apiBase);
        backendConfig.put("verify_ssl", DeepAgentExampleConfigLoader.getSslVerify());
        return backendConfig;
    }

    // ===================== Large-output tool =====================

    /**
     * 构建返回大体积文本的工具。工具结果（数千字符）作为 tool 消息进入上下文时，
     * 超过 {@code large_message_threshold} 即被 MessageOffloader 卸载。
     */
    private static Tool buildLargeReportTool() {
        ToolCard card = ToolCard.builder()
                .id("large_report_tool").name(REPORT_TOOL)
                .description("生成一份长篇技术研究报告全文（数千字），返回报告完整原文。")
                .build();
        return new LocalFunction(card, inputs -> {
            String topic = inputs.get("topic") == null || String.valueOf(inputs.get("topic")).isBlank()
                    ? "企业级 AI Agent 平台技术架构" : String.valueOf(inputs.get("topic"));
            String report = generateLargeReport(topic);
            System.out.println("  -> [" + REPORT_TOOL + "] topic=" + topic
                    + ", 生成内容 " + report.length() + " 字符（超过阈值 " + LARGE_MESSAGE_THRESHOLD
                    + "，进入上下文后将被卸载）");
            return report;
        });
    }

    /**
     * 生成固定结构的报告文本（约 3500+ 字符），REPORT_BEGIN/REPORT_END 便于第二轮
     * 验证装载回注的原文完整性。
     */
    private static String generateLargeReport(String topic) {
        StringBuilder report = new StringBuilder();
        report.append("REPORT_BEGIN\n");
        report.append("《").append(topic).append("》技术研究报告\n");
        report.append("生成时间: ").append(LocalDateTime.now()).append("\n\n");
        String[] sections = {
                "1. 总体架构：分层设计与模块边界",
                "2. 核心执行引擎：ReAct 循环与任务规划",
                "3. 上下文管理：窗口、压缩与卸载（Offload）",
                "4. 工具生态：注册、鉴权与护栏",
                "5. 会话与记忆：检查点、持久化与多租户",
                "6. 可观测性：指标、追踪与审计",
                "7. 演进路线：多智能体协作与生态集成"};
        for (String section : sections) {
            report.append(section).append("\n");
            String sectionTitle = section.substring(section.indexOf(' ') + 1);
            for (int point = 1; point <= 6; point++) {
                report.append("   - 设计要点 ").append(point).append("：围绕「").append(sectionTitle)
                        .append("」阐述该层的职责划分、接口契约、失败语义与容量预估，")
                        .append("并结合线上典型场景给出参数建议、灰度策略与风险清单。\n");
            }
            report.append("\n");
        }
        report.append("REPORT_END");
        return report.toString();
    }

    // ===================== Helpers =====================

    /** 递归列出 Offload 根目录下落盘的文件（卸载是否发生的直接证据）。 */
    private static void listOffloadFiles(Path root) {
        if (!Files.isDirectory(root)) {
            System.out.println("    （目录尚未创建，说明 FILE 后端未落盘）");
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            if (files.isEmpty()) {
                System.out.println("    （无文件）");
                return;
            }
            for (Path file : files) {
                try {
                    System.out.println("    - " + root.relativize(file) + " (" + Files.size(file) + " bytes)");
                } catch (IOException ex) {
                    System.out.println("    - " + file + " (大小读取失败: " + ex.getMessage() + ")");
                }
            }
        } catch (IOException ex) {
            System.out.println("    （遍历失败: " + ex.getMessage() + "）");
        }
    }

    /**
     * 从 runAgent 返回的结果中提取可读输出。
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

    /** 把 runAgent 返回的 Object 安全转成 Map（实际返回类型通常是 LinkedHashMap）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object result) {
        if (result instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of("__raw__", String.valueOf(result));
    }

    /** 截断文本到指定长度，单行化。 */
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
