# DeepAgent Java Examples

这个目录演示如何真实调用 `DeepAgent`，并提供一个**统一入口** `DeepAgentExamples`，通过命令行参数选择运行不同的示例。日志同时输出到控制台和当前工程下的 `logs/deep-agent-examples.log`。

> 版本说明：本示例依赖 `agent-core-java 0.1.17`（或更高版本）。运行前请先在 `agent-core-java` 仓库根目录执行 `mvn install -DskipTests` 把 jar 装入本地 Maven 仓库。

## 目录结构

```
deep_agent/
├── pom.xml                              # Maven 工程定义
├── README.md
├── logs/                                # 运行时自动生成，日志落盘目录
│   └── deep-agent-examples.log
└── src/
    ├── main/
    │   ├── java/examples/deep_agent/
    │   │   ├── DeepAgentExamples.java            # 唯一 main 入口，按参数分发
    │   │   ├── DeepAgentExampleConfigLoader.java # 配置加载器（环境变量 / apiconfig.json）
    │   │   ├── DeepAgentTemplateExample.java     # 通用模板示例
    │   │   ├── DeepAgentRedisExample.java        # Redis 共享示例
    │   │   ├── DeepAgentToolGuardrailExample.java# 工具护栏示例
    │   │   └── ContextOffloadExample.java        # Context Offload 卸载/装载示例
    │   └── resources/
    │       └── logback.xml                       # 日志配置（CONSOLE + FILE）
```

> 本目录下**只有 `DeepAgentExamples` 包含 `main` 方法**，其余示例类均暴露 `public static void run(String[] args)` 方法供其调用。

## 可选示例

| 名称 | 说明 | 需要 LLM | 需要 Redis | 对应类 |
|------|------|---------|-----------|--------|
| `template` | 通用模板：`Runner.runAgent`（阻塞）+ `Runner.runAgentStreaming`（流式）两种调用方式 | 是 | 否 | `DeepAgentTemplateExample` |
| `redis` | Todo 与 Checkpointer 共享同一 Redis 连接，验证两者均写入同一 Redis 实例 | 是 | 是 | `DeepAgentRedisExample` |
| `guardrail` | 工具护栏（ALLOW/ASK/DENY/file_guard）端到端演示，使用确定性 fake LLM | 否 | 否 | `DeepAgentToolGuardrailExample` |
| `offload` | Context Offload 卸载/装载端到端：DeepAgent + `Runner.runAgent`/`runAgentStreaming` 真实触发，大输出工具结果自动卸载为 `[[OFFLOAD: ...]]` 标记，第一轮内 LLM 调用内置 reload 工具装载回注原文；第二轮展示跨调用 catalog 生命周期（`OFFLOAD_MISSING` 为预期行为）（需要 LLM；Redis 可选） | 是 | 可选 | `ContextOffloadExample` |
| `list` | 列出所有可选示例 | — | — | — |

## 配置

配置读取优先级：**环境变量** → classpath 中的 `apiconfig.json`（即 `src/main/resources/apiconfig.json`，Maven 编译时自动复制到 `target/classes`）→ Redis 主机/端口的内置默认值。

仓库内的 `src/main/resources/apiconfig.json` 是**脱敏模板**（LLM 字段为空），可直接编辑该文件填入真实配置（本地使用，勿提交凭据）；也可以用系统属性 `openjiuwen.example.config` 或环境变量 `OPENJIUWEN_API_CONFIG` 指向任意外部配置文件。

| 环境变量 | 说明 | 哪些示例需要 |
|---------|------|-------------|
| `API_BASE` | 大模型 API 地址，例如 `http://your-host:8080/v1` | template / redis / offload |
| `API_KEY` | 大模型 API Key | template / redis / offload |
| `MODEL_PROVIDER` | 客户端类型，例如 `OpenAI` | template / redis / offload |
| `MODEL_NAME` | 模型名，例如 `GLM-5.1` | template / redis / offload |
| `LLM_SSL_VERIFY` | 是否校验 SSL，`False` 表示关闭 | template / redis / offload |
| `REDIS_HOST` | Redis 主机，默认 `127.0.0.1` | redis；offload 中为**可选**（配置后 Offload 后端链为 FILE→REDIS→MEMORY，未配置为 FILE→MEMORY） |
| `REDIS_PORT` | Redis 端口，默认 `6379` | redis；offload 中为可选 |
| `REDIS_PASSWORD` | Redis 密码（offload 示例仅从环境变量读取） | offload 中为可选 |

> `guardrail` 示例使用内置 fake LLM，无需任何外部配置即可运行。`offload` 示例必须配置 LLM（缺配置时会打印提示并优雅退出）；Redis 可选。

PowerShell 设置示例（请把 `<your-api-key>` 换成你自己的 Key）：

```powershell
$env:API_BASE = "http://your-host:8080/v1"
$env:API_KEY = "<your-api-key>"
$env:MODEL_PROVIDER = "OpenAI"
$env:MODEL_NAME = "GLM-5.1"
$env:LLM_SSL_VERIFY = "False"
$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "6379"
```

## 运行方式

### 方式一：Maven exec（推荐开发期使用）

```powershell
# 先确保 agent-core-java 已 install 到本地仓库
cd D:\path\to\agent-core-java
mvn install -DskipTests

# 进入示例工程运行
cd examples\deep_agent
mvn -q -DskipTests exec:java "-Dexec.args=template"
mvn -q -DskipTests exec:java "-Dexec.args=redis"
mvn -q -DskipTests exec:java "-Dexec.args=guardrail"
mvn -q -DskipTests exec:java "-Dexec.args=offload"
mvn -q -DskipTests exec:java "-Dexec.args=list"
```

### 方式二：打包 uber-jar 后用 java -jar

```powershell
mvn -DskipTests package
java -jar target\deep-agent-examples-0.1.0.jar template
java -jar target\deep-agent-examples-0.1.0.jar guardrail
```

> 未传参数或传 `list` / `-h` / `--help` 时打印用法与可选示例清单。

## 日志

日志通过 `src/main/resources/logback.xml` 配置，同时输出到两处：

- **CONSOLE** — 控制台，格式 `HH:mm:ss.SSS [thread] LEVEL logger - msg`
- **FILE** — `logs/deep-agent-examples.log`，格式 `yyyy-MM-dd HH:mm:ss.SSS [thread] LEVEL logger - msg`，每次运行覆盖（`append=false`）

日志级别默认 `INFO`。框架内部日志（ReAct 迭代、LLM 调用、资源管理、TaskScheduler 等）通过 slf4j 走 logback，会同时落到文件；示例代码中的 `System.out.println` 进度输出只在控制台。

## 模板结构（标准 7 步）

`DeepAgentTemplateExample` 展示了 DeepAgent 示例的标准骨架：

1. **准备工作目录** — `Files.createTempDirectory`，不污染示例目录。
2. **设置 Checkpointer + 启动 Runner** — InMemory 默认；Redis 持久化时替换。
3. **构建 `DeepAgentConfig`** — model + backend + 各项能力开关。
4. **创建 `DeepAgent`** — `AgentCard` + `Workspace` + `HarnessFactory.createDeepAgent`。
5. **`ensureInitialized()`** — 触发懒初始化，保证 rails 在依赖注入后完成 wiring。
6. **执行调用** — `Runner.runAgent`（阻塞，返回 Map）或 `Runner.runAgentStreaming`（流式，返回 `Iterator<Object>`，元素为 `OutputSchema`）。
7. **`finally` 统一清理** — 重置 checkpointer → 关闭 agent → `Runner.stop()`。

### 调用方式说明

| 方式 | 签名 | 返回 | 适用场景 |
|------|------|------|---------|
| `Runner.runAgent` | `runAgent(agent, inputs, session, ModelContext)` | `Object`（通常是 `Map`，含 `output` 字段） | 阻塞调用，一次拿到完整结果 |
| `Runner.runAgentStreaming` | `runAgentStreaming(agent, inputs, session, ModelContext, List<StreamMode>)` | `Iterator<Object>`（元素为 `OutputSchema`） | 流式调用，逐 chunk 处理 |

> `ModelContext` 可传 `null`；`List<StreamMode>` 传 `null` 等价于 `List.of(StreamMode.OUTPUT)`。
>
> 流式 chunk 的 `OutputSchema.type` 常见值：`llm_reasoning`（思考过程）、`llm_output`（LLM token）、`answer`（最终答案）、`controller_output`（控制器输出）、`llm_usage`（token 用量）、`tool_output`（工具结果）。提取答案文本时遍历 `answer` / `llm_output` / `tool_output` 的 `payload.content` 即可。

## Context Offload 示例说明（`offload`）

`ContextOffloadExample` 通过真实 DeepAgent + Runner 驱动 Context Offload v2 管道的完整闭环：

1. **配置注入**：`DeepAgentConfig` 不携带 Context Offload 配置；示例在 `ensureInitialized()` 之前通过 `deepAgent.reactAgent()` 拿到内部 `ReActAgent`，对其 `ReActAgentConfig` 原地设置 `contextEngineConfig`（`enable_offload` + `OffloadConfig`）与 `contextProcessors`（`MessageOffloader` spec）。首次 `initContext` 时框架检测到配置变化自动重建引擎并接线 v2 管道。
2. **真实触发卸载**（第一轮 `Runner.runAgent`）：注册 `generate_large_report` 工具（返回约 3500+ 字符），工具结果作为 tool 消息进入上下文时被 `MessageOffloader` 自动卸载到 FILE 后端，inline 内容替换为 `[[OFFLOAD: handle=..., type=filesystem, path=...]]` 标记。
3. **真实触发装载（第一轮内闭环）**：`enable_reload=true` 时框架自动注册内置 `reload_original_context_messages` 工具；第一轮 ReAct 循环内 LLM 从标记提取 `offload_handle`/`offload_type` 主动调用该工具，框架五步装载校验（namespace → 状态门 → 定向后端 → sha256 → touch）后把原文回注上下文（后续轮次 prompt tokens 显著跳升即为装载证据）。reload 非消费式：装载成功后落盘文件保留。
4. **第二轮（流式）展示跨调用 catalog 生命周期**：v2 管道的 catalog 是 per-context 绑定的内存对象（`OffloadCatalogImpl`），每次 Runner 调用新建 context 时重新绑定——上一轮的 handle 在新 catalog 中查不到，reload 返回 `OFFLOAD_MISSING`（落盘文件本身仍在，由 Janitor 按孤儿策略回收）；LLM 随即基于上下文中第一轮已装载的原文作答，验证装载结果的知识留存。
5. **关键旋钮（演示用调小）**：`large_message_threshold=200`（默认 1000）；`tokens_threshold=100`（默认 20000，总量触发门控——每次加消息时先按「上下文总 tokens 是否达标」决定是否执行卸载扫描）；单轮场景 `recent_message_protection_count=0`（默认 5，保护窗口会把整个单轮上下文挡在卸载范围之外）。
6. **可选 Redis**：配置 `REDIS_HOST` 后端链为 FILESYSTEM → REDIS → MEMORY；未配置自动退化为 FILESYSTEM → MEMORY。Redis 不可达时按链降级，save 永不抛错。

## 扩展点

在模板基础上扩展时，常见改造点：

| 需求 | 改造位置 | 参考 |
|------|---------|------|
| 自定义提示词 | `SYSTEM_PROMPT` / `buildUserPrompt()` | 模板 |
| 注册自定义工具 | `ensureInitialized()` 之后调用 `registerHarnessTool` | `DeepAgentToolGuardrailExample` |
| Todo 任务规划 | `enableTaskPlanning(true)` + `todoStorageType("kv")` | `DeepAgentRedisExample` |
| Redis 持久化 | 替换 `InMemoryCheckpointer` 为 `RedisCheckpointer` + 注入 `kvStoreConfig` | `DeepAgentRedisExample` |
| 多租户隔离 | `enableTenantIsolation(true)` + `tenantDataRoot` | `DeepAgentRedisExample` |
| 工具护栏 / 权限 | `permissions(...)` + 可选 `permissionHost(...)` | `DeepAgentToolGuardrailExample` |
| 新增示例 | 新建 `XxxExample.java` 暴露 `run(String[])`，在 `DeepAgentExamples` 的 `switch` 与 `EXAMPLES` map 中注册 | `DeepAgentExamples` |

## 说明

- 所有示例都不缓存 Agent，运行结束在 `finally` 中清理：重置全局 checkpointer、关闭 `DeepAgent`、`Runner.stop()`。Redis 示例额外删除演示 key 并关闭 `JedisPooled`。
- 工作目录使用系统临时目录（`Files.createTempDirectory`），不会污染示例目录。
- 配置加载器 `DeepAgentExampleConfigLoader` 被所有示例共用，支持环境变量优先 + classpath `apiconfig.json` 回退 + 系统属性 `openjiuwen.example.config` 指定路径。
