# com.openjiuwen.extensions.tracer_otel

`tracer_otel` 是 openJiuwen 框架的可选 OpenTelemetry 链路追踪扩展模块。它将框架内置 Tracer 的 Agent 事件与 Workflow 事件转换为符合 OTel 规范的 Span，并通过 OTLP（HTTP / gRPC）或控制台导出器上报到后端（如 Jaeger、Tempo、OTLP Collector）。

本模块独立于 `agent_teams.observability`，不依赖全局 `GlobalOpenTelemetry` 状态，避免与 `init_observability()` 产生冲突。Handler 持有独立的 `Tracer` 引用，可直接工作。

## 模块结构

| 文件 | 说明 |
| --- | --- |
| [`OtelTracerConfig`](#oteltracerconfig-配置类) | 不可变配置类（Builder 模式），承载采样率、导出器、脱敏等参数 |
| [`OtelTracerSetup`](#oteltracersetup-初始化工具类) | 根据 `OtelTracerConfig` 构建 `SdkTracerProvider` 并返回 `Tracer` |
| [`SemConv`](#semconv-语义约定常量) | 所有 OTel 属性键常量（`gen_ai.*` 与 `openjiuwen.*` 命名空间） |
| [`RedactionUtils`](#redactionutils-脱敏工具类) | Prompt / Completion 的 SHA-256 脱敏与截断工具 |
| [`OtelSpanState`](#otelspanstate-span-状态包装) | 包装 OTel `Span`、`Scope`、`invoke_id` 与 `start_time` |
| [`OtelAgentSpanManager`](#otelagentspanmanager-agent-span-管理器) | Agent 维度 `invoke_id → OtelSpanState` 生命周期映射 |
| [`OtelWorkflowSpanManager`](#otelworkflowspanmanager-workflow-span-管理器) | Workflow 维度映射 + 增量数据缓冲（stream / on_invoke_data） |
| [`OtelAgentHandler`](#otelagenthandler-agent-维度-handler) | 将 LLM / Plugin / Chain / Retriever 等 Agent 事件转为 OTel Span |
| [`OtelWorkflowHandler`](#otelworkflowhandler-workflow-维度-handler) | 将 Workflow 的 Call / Invoke / Stream 事件转为层级化 OTel Span |
| [`OtelRail`](#otelrail-agent-生命周期-rail) | 挂载到 Agent 回调的 Rail，负责根 Span 与 LLM 子 Span 的生命周期 |
| [`package-info`](#package-info) | 包级文档说明 |

## 快速入门

OTel handler 通过 `TracerHandlerRegistry` 全局注册。注册后所有新建 `Tracer` 实例（即每次 agent/workflow 调用）都会在 `Tracer.init` 时自动加载。注册名不能与内置 handler 名（`tracer_agent` / `tracer_workflow`）重复。

```java
import com.openjiuwen.core.session.tracer.TracerHandlerRegistry;
import com.openjiuwen.extensions.tracerotel.OtelAgentHandler;
import com.openjiuwen.extensions.tracerotel.OtelTracerConfig;
import com.openjiuwen.extensions.tracerotel.OtelTracerSetup;
import com.openjiuwen.extensions.tracerotel.OtelWorkflowHandler;
import io.opentelemetry.api.trace.Tracer;

// 1. 构建配置
OtelTracerConfig config = OtelTracerConfig.builder()
        .exporterType("otlp")
        .exporterEndpoint("http://localhost:4317")
        .protocol("grpc")
        .serviceName("my-agent-service")
        .sampleRate(1.0)
        .isRedactionEnabled(true)
        .build();

// 2. 初始化 OTel Tracer
Tracer otelTracer = OtelTracerSetup.initOtelTracer(config);

// 3. 注册 Agent / Workflow 扩展 handler（注册名自定义，不可与内置名冲突）
OtelAgentHandler agentHandler = new OtelAgentHandler(otelTracer, config);
OtelWorkflowHandler workflowHandler = new OtelWorkflowHandler(otelTracer, config);
TracerHandlerRegistry.registerHandler("otel_agent", agentHandler);
TracerHandlerRegistry.registerHandler("otel_workflow", workflowHandler);

// 4. 正常创建/运行 Agent 或 Workflow — Tracer.init 会自动注入已注册的 handler
```

> **说明**
>
> - 生产环境通常将 `exporterType` 设为 `"otlp"`，并提供 `exporterEndpoint` 指向 OTLP Collector 或 Jaeger 等后端。
> - 调试时可改用 `.exporterType("console")`。
> - `TracerHandlerRegistry.unregisterHandler(name)` 可移除单个 handler，`clear()` 可清空全部（常用于测试隔离）。

## 依赖要求

`pom.xml` 需引入以下 OpenTelemetry 依赖（版本 1.38.0）：

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.38.0</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
    <version>1.38.0</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <version>1.38.0</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-logging</artifactId>
    <version>1.38.0</version>
</dependency>
<!-- 测试用 -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-testing</artifactId>
    <version>1.38.0</version>
    <scope>test</scope>
</dependency>
```

---

## OtelTracerConfig 配置类

```java
public final class OtelTracerConfig
```

不可变配置类，采用 Builder 模式。对应 Python 的 `@dataclass(frozen=True) OtelTracerConfig`。

### 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `tracerName` | `String` | `"openjiuwen.tracer.otel"` | OTel Tracer 名称 |
| `exporterType` | `String` | `"otlp"` | 导出器类型：`"otlp"` 或 `"console"` |
| `exporterEndpoint` | `String` | `null` | OTLP 上报端点（`console` 模式下不需要） |
| `protocol` | `String` | `"grpc"` | OTLP 传输协议：`"grpc"` 或 `"http"` |
| `headers` | `Map<String,String>` | `{}` | OTLP 请求自定义头（如鉴权 token） |
| `serviceName` | `String` | `"openjiuwen"` | OTel Resource 中的 `service.name` |
| `serviceVersion` | `String` | `null`（→ `"unknown"`） | OTel Resource 中的 `service.version` |
| `sampleRate` | `double` | `1.0` | 采样率，范围 `[0.0, 1.0]` |
| `scheduleDelayMillis` | `int` | `5000` | `BatchSpanProcessor` 导出间隔（毫秒） |
| `exportTimeoutMs` | `int` | `30000` | `BatchSpanProcessor` 导出超时（毫秒） |
| `maxExportBatchSize` | `int` | `512` | `BatchSpanProcessor` 单批最大 Span 数 |
| `redactionEnabled` | `boolean` | `true` | 脱敏总开关，开启后 prompt/completion 以 SHA-256 哈希 |
| `redactPrompts` | `Boolean` | `null` | prompt 脱敏细粒度覆盖；`null` 回退到 `redactionEnabled` |
| `redactCompletions` | `Boolean` | `null` | completion 脱敏细粒度覆盖；`null` 回退到 `redactionEnabled` |
| `maxAttrLength` | `int` | `4096` | 字符串属性值截断上限 |

### Builder 方法

| 签名 | 说明 |
| --- | --- |
| `public static OtelTracerConfig.Builder builder()` | 创建新的 Builder 实例 |
| `public Builder tracerName(String)` | 设置 Tracer 名称 |
| `public Builder exporterType(String)` | 设置导出器类型 |
| `public Builder exporterEndpoint(String)` | 设置 OTLP 端点 |
| `public Builder protocol(String)` | 设置传输协议 |
| `public Builder headers(Map<String,String>)` | 设置自定义请求头 |
| `public Builder serviceName(String)` | 设置 service.name |
| `public Builder serviceVersion(String)` | 设置 service.version |
| `public Builder sampleRate(double)` | 设置采样率（越界抛 `IllegalArgumentException`） |
| `public Builder scheduleDelayMillis(int)` | 设置导出间隔 |
| `public Builder exportTimeoutMs(int)` | 设置导出超时 |
| `public Builder maxExportBatchSize(int)` | 设置单批最大数 |
| `public Builder isRedactionEnabled(boolean)` | 设置脱敏总开关 |
| `public Builder shouldRedactPrompts(Boolean)` | 覆盖 prompt 脱敏 |
| `public Builder shouldRedactCompletions(Boolean)` | 覆盖 completion 脱敏 |
| `public Builder maxAttrLength(int)` | 设置截断上限 |
| `public OtelTracerConfig build()` | 构建不可变配置，校验 `sampleRate ∈ [0.0, 1.0]` |

### 使用示例

```java
OtelTracerConfig config = OtelTracerConfig.builder()
        .exporterType("otlp")
        .exporterEndpoint("http://localhost:4317")
        .protocol("grpc")
        .sampleRate(0.5)
        .isRedactionEnabled(true)
        .shouldRedactPrompts(false)   // prompt 不脱敏
        .shouldRedactCompletions(true) // completion 强制脱敏
        .build();
```

### 相关测试

- `OtelTracerSetupTest.TestSampleRateValidation`
- `OtelTracerSetupTest.TestInitOtelTracer`

---

## OtelTracerSetup 初始化工具类

```java
public final class OtelTracerSetup
```

根据 `OtelTracerConfig` 构建 `SdkTracerProvider` 并返回 OTel `Tracer`。

**重要**：本类**不会**调用 `GlobalOpenTelemetry.setTracerProvider()`，避免与 `agent_teams.observability.init_observability()` 产生全局状态冲突。返回的 `Tracer` 绑定到本模块独立的 Provider。

### 方法

| 签名 | 说明 |
| --- | --- |
| `public static Tracer initOtelTracer(OtelTracerConfig config)` | 初始化 TracerProvider 并返回 `Tracer`。`exporterType="console"` 使用 `LoggingSpanExporter` + `SimpleSpanProcessor`；`"otlp"` 使用 OTLP 导出器 + `BatchSpanProcessor`。非法 `exporterType` 抛 `BaseError` |
| `public static SpanExporter createOtlpExporter(OtelTracerConfig config)` | 创建 OTLP 导出器。`protocol="http"` → `OtlpHttpSpanExporter`（自动补 `/v1/traces` 路径）；`"grpc"` → `OtlpGrpcSpanExporter`。非法协议抛 `BaseError` |

### 采样策略

采用 `Sampler.parentBased(Sampler.traceIdRatioBased(sampleRate))`：

- 有父 Span 时，跟随父 Span 的采样决策
- 无父 Span 时，按 `sampleRate` 概率采样

### OTLP/HTTP 与 OTLP/gRPC 协议支持

| 协议 | 端点示例 | 导出器类 | 路径处理 |
| --- | --- | --- | --- |
| `grpc` | `http://localhost:4317` | `OtlpGrpcSpanExporter` | 端点原样使用 |
| `http` | `http://localhost:4318` | `OtlpHttpSpanExporter` | 自动追加 `/v1/traces`（若未包含） |

自定义 headers 通过 `setHeaders(() -> headers)` 以 `Supplier<Map<String,String>>` 形式注入，支持鉴权场景（如 `Authorization: Bearer <token>`）。

### 使用示例

```java
// gRPC 模式
OtelTracerConfig grpcConfig = OtelTracerConfig.builder()
        .exporterType("otlp")
        .exporterEndpoint("http://localhost:4317")
        .protocol("grpc")
        .headers(Map.of("api-key", "secret"))
        .build();
Tracer grpcTracer = OtelTracerSetup.initOtelTracer(grpcConfig);

// HTTP 模式
OtelTracerConfig httpConfig = OtelTracerConfig.builder()
        .exporterType("otlp")
        .exporterEndpoint("http://localhost:4318")
        .protocol("http")
        .build();
Tracer httpTracer = OtelTracerSetup.initOtelTracer(httpConfig);

// 控制台模式（调试用）
OtelTracerConfig consoleConfig = OtelTracerConfig.builder()
        .exporterType("console")
        .build();
Tracer consoleTracer = OtelTracerSetup.initOtelTracer(consoleConfig);
```

### 相关测试

- `OtelTracerSetupTest.TestInitOtelTracer`（10 个测试：console / grpc / http / 端点路径 / headers / 非法类型 / 批处理 / 资源属性）
- `OtelTracerSetupTest.TestSampleRateIntegration`（4 个测试：采样率 0/1 / 调度延迟）

---

## SemConv 语义约定常量

```java
public final class SemConv
```

集中定义所有 OTel 属性键，避免 Handler 间的拼写漂移。对应 Python 的 `semconv.py`。

### GenAI 标准属性（`gen_ai.*`）

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `GEN_AI_SYSTEM` | `"gen_ai.system"` | LLM 系统标识键 |
| `GEN_AI_SYSTEM_VALUE` | `"openjiuwen"` | 本框架的 `gen_ai.system` 取值 |
| `GEN_AI_REQUEST_MODEL` | `"gen_ai.request.model"` | 请求模型名 |
| `GEN_AI_RESPONSE_MODEL` | `"gen_ai.response.model"` | 响应模型名（从 usage 或响应体回读） |
| `GEN_AI_OPERATION_NAME` | `"gen_ai.operation.name"` | 操作名（`"chat"` / `"execute_tool"`） |
| `GEN_AI_PROVIDER_NAME` | `"gen_ai.provider.name"` | 供应商名 |
| `GEN_AI_PROMPT` | `"gen_ai.prompt"` | 输入 prompt |
| `GEN_AI_COMPLETION` | `"gen_ai.completion"` | 输出 completion |
| `GEN_AI_REQUEST_TEMPERATURE` | `"gen_ai.request.temperature"` | 采样温度 |
| `GEN_AI_REQUEST_TOP_P` | `"gen_ai.request.top_p"` | top_p |
| `GEN_AI_REQUEST_MAX_TOKENS` | `"gen_ai.request.max_tokens"` | 最大输出 token 数 |
| `GEN_AI_REQUEST_MESSAGE_COUNT` | `"gen_ai.request.message_count"` | 输入消息条数 |
| `GEN_AI_RESPONSE_FINISH_REASON` | `"gen_ai.response.finish_reason"` | 结束原因（单值） |
| `GEN_AI_RESPONSE_FINISH_REASONS` | `"gen_ai.response.finish_reasons"` | 结束原因数组（按规范要求以数组上报） |
| `GEN_AI_TOOL_NAME` | `"gen_ai.tool.name"` | 工具名 |
| `GEN_AI_TOOL_INPUT` | `"gen_ai.tool.input"` | 工具入参 |
| `GEN_AI_TOOL_OUTPUT` | `"gen_ai.tool.output"` | 工具出参 |
| `GEN_AI_TOOL_ID` | `"gen_ai.tool.id"` | 工具调用 ID |
| `GEN_AI_TOOL_CALLS` | `"gen_ai.tool_calls"` | 工具调用列表 |
| `GEN_AI_TOOL_DEFINITIONS` | `"gen_ai.tool.definitions"` | 工具定义列表 |

### Token 用量属性（`gen_ai.usage.*`）

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `GEN_AI_USAGE_PROMPT_TOKENS` | `"gen_ai.usage.prompt_tokens"` | 输入 token 数（旧键） |
| `GEN_AI_USAGE_COMPLETION_TOKENS` | `"gen_ai.usage.completion_tokens"` | 输出 token 数（旧键） |
| `GEN_AI_USAGE_TOTAL_TOKENS` | `"gen_ai.usage.total_tokens"` | 总 token 数 |
| `GEN_AI_USAGE_INPUT_TOKENS` | `"gen_ai.usage.input_tokens"` | 输入 token 数（现行 GenAI 语义键） |
| `GEN_AI_USAGE_OUTPUT_TOKENS` | `"gen_ai.usage.output_tokens"` | 输出 token 数（现行 GenAI 语义键） |
| `GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS` | `"gen_ai.usage.cache_read.input_tokens"` | 缓存命中的输入 token 数 |

> **新旧键双写**：`input_tokens`/`output_tokens` 与 `prompt_tokens`/`completion_tokens` 同时上报，兼容仍读旧键的看板。`total_tokens` 优先取模型服务返回值，仅在缺失且输入、输出均存在时按 `input + output` 计算。
>
> `cache_read.input_tokens` 来自 `UsageMetadata.getCacheTokens()`，各客户端从 `prompt_tokens_details.cached_tokens` 填充；仅当 `> 0` 时写入，避免把「不支持缓存」误报成「缓存命中 0」。

### 流式延迟指标（`openjiuwen.gen_ai.*`）

以下四项是流式调用的延迟观测指标，全部由 `Model.CallbackIterator` 在唯一的流式收口点计算，经 `UsageMetadata` 携带，最终由 `OtelAgentHandler.setStreamLatencyAttributes()` 写入 LLM Span。

| 常量 | 属性键 | 单位 | 计算方式 |
| --- | --- | --- | --- |
| `OJ_GEN_AI_RESPONSE_TIME_TO_FIRST_TOKEN_MS` | `"openjiuwen.gen_ai.response.time_to_first_token_ms"` | 毫秒 | 请求下发 → 首个**携带模型输出**的 chunk 的时间差（TTFT） |
| `OJ_GEN_AI_RESPONSE_TPOT_MS` | `"openjiuwen.gen_ai.response.tpot_ms"` | 毫秒 | 首个到末个输出 chunk 的耗时 ÷ `(output_tokens - 1)`（TPOT） |
| `OJ_GEN_AI_RESPONSE_TOTAL_LATENCY_MS` | `"openjiuwen.gen_ai.response.total_latency_ms"` | 毫秒 | 请求下发 → 流结束的整段耗时 |
| `OJ_GEN_AI_REASONING_DURATION_MS` | `"openjiuwen.gen_ai.reasoning.duration_ms"` | 毫秒 | 首个到末个 `reasoning_content` chunk 的耗时 |

**指标语义要点**

1. **TTFT 的起点是请求下发，不是首帧到达**。`startNanos` 在 `client.stream()` 调用之前采集，因此覆盖建连、TLS 握手、首字节等待等全部前置开销。若起点后移到首个 chunk，测量会漏掉整段网络等待。
2. **TTFT 的终点是「携带输出」的 chunk，不是任意首帧**。判定由 `AssistantMessageChunk.carriesOutputToken()` 完成：`content` 非空、或 `reasoning_content` 非空、或存在带 `name`/`arguments` 的 `tool_call` 才算有效输出。role-only 帧、心跳帧、纯 usage 帧、纯 finish 帧一律不计入——推理模型的供应商常在 prefill 完成前数秒就推一个 role-only delta，把它当首 token 会让 TTFT 严重偏小。
3. **TPOT 的分母是 `output_tokens - 1`，不是 chunk 数**。一个 chunk 可能承载多个 token，chunk 数不等于 token 数。仅在 `output_tokens > 1` 且存在两个以上输出 chunk 时计算。
4. **缺失即不写，绝不写 0**。四项指标都是 `Double` 可空类型，`null` 表示「未测量」（非流式调用、出错、取消、供应商没发输出 chunk、非推理模型）。`0` 本身是合法测量值，写成 `0` 会让下游无法区分「真的瞬时」和「没测到」。
5. **总延迟不受首发 token 限制**。即使整条流没有产出任何输出 chunk（例如只带回 usage 的提前结束），`total_latency_ms` 依然可测，因此它不被 `firstTokenNanos` 门控。
6. **回填时机**：usage 通常只在末帧到达，所以四项指标只能在流结束（`hasNext()` 返回 false）或 `close()` 时回填到最后一个带 usage 的 chunk 对象上。借助对象引用传递，`ReActAgent`、tracer handler、observability rail 等既有消费者无需改动即可读到。

**为什么 TTFT 不写标准键**

标准语义约定中的 `gen_ai.response.time_to_first_chunk`（`GEN_AI_RESPONSE_TIME_TO_FIRST_CHUNK`，单位**秒**）衡量的是「任意类型的首个 chunk」，与「首个携带输出的 chunk」在推理模型上可相差数秒。Java 侧目前只能在输出判定之后观测 chunk，若把 TTFT 写到标准键，等于在标准名称下悄悄替换了测量语义。因此：

- TTFT 写入项目键 `openjiuwen.gen_ai.response.time_to_first_token_ms`
- 标准键 `gen_ai.response.time_to_first_chunk` **保持不写**，留给未来能对原始首帧计时的数据源
- 旧键 `GEN_AI_RESPONSE_TTFT_MS`（`"gen_ai.response.time_to_first_token_ms"`）仅为源码兼容保留，handler 不再写入——它借用了标准 `gen_ai.*` 命名空间却用了非标准名字

### 成本与重试属性（`openjiuwen.gen_ai.usage.*` / `openjiuwen.request.*`）

| 常量 | 属性键 | 说明 |
| --- | --- | --- |
| `OJ_GEN_AI_USAGE_INPUT_COST` | `"openjiuwen.gen_ai.usage.input_cost"` | 输入成本，取自供应商价目表 |
| `OJ_GEN_AI_USAGE_OUTPUT_COST` | `"openjiuwen.gen_ai.usage.output_cost"` | 输出成本 |
| `OJ_GEN_AI_USAGE_TOTAL_COST` | `"openjiuwen.gen_ai.usage.total_cost"` | 总成本 |
| `OJ_REQUEST_RETRY_COUNT` | `"openjiuwen.request.retry_count"` | 本次调用的重试次数 |

> **成本一律不重算**：三项成本直接读取 `UsageMetadata`，本模块不做任何价格计算——定价属于供应商价目表的职责。**值为 0 时跳过**，否则「供应商没报价」会被误读成「这次调用免费」。
>
> `retry_count` 由流式路径上的重试监听器累加，`0` 表示「没有重试」，是真实测量值而非缺失，因此只要 `> 0` 才写（与其他指标的「缺失不写 0」一致）。

### Workflow 自定义属性（`openjiuwen.workflow.*`）

| 常量 | 值 |
| --- | --- |
| `OJ_WORKFLOW_ID` | `"openjiuwen.workflow.id"` |
| `OJ_WORKFLOW_NAME` | `"openjiuwen.workflow.name"` |
| `OJ_WORKFLOW_VERSION` | `"openjiuwen.workflow.version"` |
| `OJ_WORKFLOW_COMPONENT_ID` | `"openjiuwen.workflow.component.id"` |
| `OJ_WORKFLOW_COMPONENT_TYPE` | `"openjiuwen.workflow.component.type"` |
| `OJ_WORKFLOW_COMPONENT_NAME` | `"openjiuwen.workflow.component.name"` |
| `OJ_WORKFLOW_EXECUTION_ID` | `"openjiuwen.workflow.execution_id"` |
| `OJ_WORKFLOW_LOOP_NODE_ID` | `"openjiuwen.workflow.loop.node_id"` |
| `OJ_WORKFLOW_LOOP_INDEX` | `"openjiuwen.workflow.loop.index"` |

### Agent 自定义属性（`openjiuwen.agent.*`）

| 常量 | 值 |
| --- | --- |
| `OJ_AGENT_INVOKE_TYPE` | `"openjiuwen.agent.invoke_type"` |
| `OJ_AGENT_NAME` | `"openjiuwen.agent.name"` |
| `OJ_AGENT_INPUTS` | `"openjiuwen.agent.inputs"` |
| `OJ_AGENT_OUTPUTS` | `"openjiuwen.agent.outputs"` |
| `OJ_AGENT_ERROR_MESSAGE` | `"openjiuwen.agent.error_message"` |

### 基础 Span 属性（`openjiuwen.*`，两个 Handler 共用）

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `OJ_TRACE_ID` | `"openjiuwen.trace.id"` | 桥接 OTel trace 与内置 Tracer UUID |
| `OJ_INVOKE_ID` | `"openjiuwen.invoke_id"` | 本次调用 ID |
| `OJ_PARENT_INVOKE_ID` | `"openjiuwen.parent_invoke_id"` | 父调用 ID |
| `OJ_START_TIME` | `"openjiuwen.start_time"` | 开始时间 |
| `OJ_END_TIME` | `"openjiuwen.end_time"` | 结束时间 |
| `OJ_ELAPSED_TIME` | `"openjiuwen.elapsed_time"` | 耗时（`"123ms"` / `"1.23s"`） |
| `OJ_STATUS` | `"openjiuwen.status"` | 节点状态（`finish` / `error` / `interrupted`） |
| `OJ_ERROR` | `"openjiuwen.error"` | 错误信息 JSON（含 `error_code` 与 `message`） |
| `OJ_CHILD_INVOKE_IDS` | `"openjiuwen.child_invoke_ids"` | 子调用 ID 列表 |
| `OJ_META_DATA` | `"openjiuwen.meta_data"` | 元数据 JSON |

### Workflow 专有基础属性

| 常量 | 值 |
| --- | --- |
| `OJ_PARENT_NODE_ID` | `"openjiuwen.parent_node_id"` |
| `OJ_SOURCE_IDS` | `"openjiuwen.source_ids"` |
| `OJ_INNER_ERROR` | `"openjiuwen.inner_error"` |
| `OJ_STREAM_INPUTS` | `"openjiuwen.stream_inputs"` |
| `OJ_STREAM_OUTPUTS` | `"openjiuwen.stream_outputs"` |
| `OJ_INTERACTIVE_INPUTS` | `"openjiuwen.interactive_inputs"` |
| `OJ_WORKFLOW_INPUTS` | `"openjiuwen.workflow.inputs"` |
| `OJ_WORKFLOW_OUTPUTS` | `"openjiuwen.workflow.outputs"` |
| `OJ_WORKFLOW_ERROR_MESSAGE` | `"openjiuwen.workflow.error_message"` |
| `OJ_WORKFLOW_INVOKE_DATA` | `"openjiuwen.workflow.invoke_data"` |

---

## RedactionUtils 脱敏工具类

```java
public final class RedactionUtils
```

Prompt / Completion 脱敏与截断工具。轻量内联实现，不依赖 `observability/redaction.py`，避免跨层耦合。

### 脱敏策略

- **脱敏开启**：值替换为 `"sha256:<16位十六进制>"`（SHA-256 哈希前 16 字符），便于关联去重
- **脱敏关闭**：仅按 `maxAttrLength` 截断，追加 `"...<truncated>"` 后缀
- `null` 值统一视为空字符串 `""`

### 细粒度覆盖逻辑

`shouldRedact(config, field)` 解析优先级：

1. `field="prompts"` 且 `config.redactPrompts != null` → 使用 `redactPrompts`
2. `field="completions"` 且 `config.redactCompletions != null` → 使用 `redactCompletions`
3. 否则回退到 `config.redactionEnabled`

### 方法

| 签名 | 说明 |
| --- | --- |
| `public static String truncate(String value, int maxLength)` | 截断字符串；`maxLength <= 0` 时不截断 |
| `public static String hashValue(String value)` | 返回 `"sha256:<16位hex>"` |
| `public static boolean shouldRedact(OtelTracerConfig config, String field)` | 判断是否需要脱敏；`field` 为 `"prompts"` / `"completions"` / `null` |
| `public static String redact(Object value, OtelTracerConfig config, String field)` | 按策略脱敏或截断 |
| `public static String redact(Object value, OtelTracerConfig config)` | 等价于 `redact(value, config, null)`，使用 `redactionEnabled` |

### 使用示例

```java
// prompt 强制不脱敏，completion 强制脱敏
OtelTracerConfig config = OtelTracerConfig.builder()
        .isRedactionEnabled(true)
        .shouldRedactPrompts(false)
        .shouldRedactCompletions(true)
        .build();

String prompt = RedactionUtils.redact("hello", config, "prompts");
// → "hello"（不脱敏，仅可能截断）

String completion = RedactionUtils.redact("world", config, "completions");
// → "sha256:486ea46224d1..."
```

### 相关测试

- `RedactionUtilsTest`（25 个测试：truncate / hashValue / shouldRedact / redact 各路径）

---

## OtelSpanState Span 状态包装

```java
public final class OtelSpanState
```

包装一个 OTel `Span` 及其关联的 `Scope`、`invoke_id`、`start_time`。对应 Python 的 `OtelSpanState` dataclass。

### 构造函数

```java
public OtelSpanState(Span span, Scope scope, String invokeId, LocalDateTime startTime)
```

| 参数 | 说明 |
| --- | --- |
| `span` | OTel Span 实例 |
| `scope` | `Context.makeCurrent()` 返回的 Scope（Workflow Span 可为 `null`） |
| `invokeId` | 关联的调用 ID |
| `startTime` | 缓存的开始时间（用于耗时计算） |

### 方法

| 签名 | 说明 |
| --- | --- |
| `public Span getSpan()` | 获取 OTel Span |
| `public Scope getScope()` | 获取 Context Scope |
| `public String getInvokeId()` | 获取调用 ID |
| `public LocalDateTime getStartTime()` | 获取开始时间 |

### 相关测试

- `OtelSpanManagerTest`（13 个测试）

---

## OtelAgentSpanManager Agent Span 管理器

```java
public final class OtelAgentSpanManager
```

管理 Agent 维度的 `invoke_id → OtelSpanState` 映射，用于建立父子 Span 关系。基于 `ConcurrentHashMap`，线程安全。

### 方法

| 签名 | 说明 |
| --- | --- |
| `public void push(String invokeId, OtelSpanState state)` | 注册 Span 状态 |
| `public OtelSpanState pop(String invokeId)` | 移除并返回 Span 状态；不存在返回 `null` |
| `public OtelSpanState get(String invokeId)` | 查询但不移除 Span 状态；不存在返回 `null` |

### 父子关系建立流程

1. `onLlmStart` / `onChainStart` 等创建 Span 时调用 `push(invokeId, state)`
2. 创建子 Span 时，通过 `span.getParentInvokeId()` 查询父 Span 的 `OtelSpanState`
3. 用父 Span 的 `Context.current().with(parentSpan)` 作为子 Span 的 parent context
4. Span 结束时调用 `pop(invokeId)` 清理

### 相关测试

- `OtelSpanManagerTest`（push / pop / get 各路径）

---

## OtelWorkflowSpanManager Workflow Span 管理器

```java
public final class OtelWorkflowSpanManager
```

管理 Workflow 维度的 `invoke_id → OtelSpanState` 映射，并缓冲增量数据（`onInvokeData`、`streamInputs`、`streamOutputs`）。在 `onCallDone` 时将缓冲数据一次性刷写为单个 OTel 属性，避免产生大量小属性。

### 方法

| 签名 | 说明 |
| --- | --- |
| `public void push(String invokeId, OtelSpanState state)` | 注册 Span 并初始化三个缓冲列表 |
| `public OtelSpanState pop(String invokeId)` | 移除 Span 并清空缓冲；返回 Span 或 `null` |
| `public OtelSpanState get(String invokeId)` | 查询 Span（不移除） |
| `public void appendOnInvokeData(String invokeId, Map<String,Object> data)` | 追加 on_invoke_data 到缓冲 |
| `public List<Map<String,Object>> getOnInvokeData(String invokeId)` | 返回缓冲的 on_invoke_data（不可变副本） |
| `public void appendStreamInput(String invokeId, Object chunk)` | 追加流式输入 chunk |
| `public List<Object> getStreamInputs(String invokeId)` | 返回缓冲的流式输入 |
| `public void appendStreamOutput(String invokeId, Object chunk)` | 追加流式输出 chunk |
| `public List<Object> getStreamOutputs(String invokeId)` | 返回缓冲的流式输出 |

### 相关测试

- `OtelSpanManagerTest`（含 Workflow 管理器的缓冲行为）

---

## OtelAgentHandler Agent 维度 Handler

```java
public class OtelAgentHandler extends TraceExtAgentHandler
```

将 Tracer Agent 事件（LLM、Plugin、Chain、Retriever、Evaluator、Workflow）转换为 OTel Span。

### Span 类型映射

| 事件类型 | SpanKind | 属性命名空间 | span 名前缀 |
| --- | --- | --- | --- |
| LLM | `CLIENT` | `gen_ai.*` + `openjiuwen.agent.*` | `llm.<class_name>` |
| Plugin (Tool) | `INTERNAL` | `openjiuwen.agent.*` + `gen_ai.tool.name` | `tool.<class_name>` |
| Chain | `INTERNAL` | `openjiuwen.agent.*` | `chain.<class_name>` |
| Retriever | `INTERNAL` | `openjiuwen.agent.*` | `retriever.<class_name>` |
| Evaluator | `INTERNAL` | `openjiuwen.agent.*` | `evaluator.<class_name>` |
| Workflow (agent-level) | `INTERNAL` | `openjiuwen.agent.*` | `workflow.<class_name>` |
| Prompt | `INTERNAL` | `openjiuwen.agent.*` | `prompt.<class_name>` |

### 构造函数

| 签名 | 说明 |
| --- | --- |
| `public OtelAgentHandler(Tracer otelTracer, OtelTracerConfig config, String traceId)` | 完整构造，注入 traceId 桥接 |
| `public OtelAgentHandler(Tracer otelTracer, OtelTracerConfig config)` | 简化构造，traceId 延后注入 |

### LLM 事件方法

| 签名 | 说明 |
| --- | --- |
| `onLlmStart(TraceAgentSpan span, Object inputs, Map instanceInfo)` | 创建 CLIENT Span，设置 `gen_ai.request.model`、`gen_ai.operation.name="chat"`、`gen_ai.prompt`（脱敏后） |
| `onLlmRequest(TraceAgentSpan span, Map kwargs)` | 添加 `llm.request` 事件 |
| `onLlmEnd(TraceAgentSpan span, Object outputs)` | 设置 `gen_ai.completion`（脱敏后），写入 token 用量、成本、延迟指标、`finish_reason` 与 `gen_ai.response.model`，结束 Span，状态置 OK |
| `onLlmError(TraceAgentSpan span, Throwable error)` | 状态置 ERROR，记录异常事件，设置 `openjiuwen.error` 与 `openjiuwen.agent.error_message` |

### 响应属性写入流程

`onLlmEnd` 通过 `recordResponseAttrs(state, rawResponse)` 统一回读响应属性，支持 POJO 与 Map 两种形态：

```
recordResponseAttrs
├── resolveUsage(rawResponse)
│   ├── AssistantMessage.getUsageMetadata()        // 非流式
│   └── List<chunk> 逆序扫描，取首个带 usage 的末帧  // 流式
└── 命中 usage 后依次调用
    ├── setTokenAttributes              → 输入/输出/总 token（新旧键双写）
    ├── setUsageBreakdownAttributes     → cache_read、三项成本、retry_count
    ├── setUsageModelName               → gen_ai.response.model（来自 usage）
    ├── setStreamLatencyAttributes      → TTFT / TPOT / 总延迟 / 推理时长
    ├── setFinishReason                 → finish_reason + finish_reasons 数组
    └── setResponseModel                → gen_ai.response.model（来自响应体，兜底）
```

| 私有方法 | 写入内容 |
| --- | --- |
| `setTokenAttributes` | `gen_ai.usage.{prompt,completion,total,input,output}_tokens` |
| `setUsageBreakdownAttributes` | `gen_ai.usage.cache_read.input_tokens`、三项成本、`openjiuwen.request.retry_count` |
| `setUsageModelName` | `gen_ai.response.model`（读 `UsageMetadata.getModelName()`） |
| `setStreamLatencyAttributes` | `time_to_first_token_ms`、`tpot_ms`、`total_latency_ms`、`reasoning.duration_ms` |
| `setFinishReason` | `gen_ai.response.finish_reason` + `gen_ai.response.finish_reasons` |
| `setResponseModel` | `gen_ai.response.model`（仅 Map 响应，兜底 `setUsageModelName`） |

**流式路径 usage 回读**：`onLlmEnd` 在流式场景收到的是 `List<AssistantMessageChunk>`，而 usage 只挂在末帧，因此 `resolveUsage` 逆序扫描取第一个非空 usage。token 指标受正常路径保护；`total_tokens` 缺失时按 `input + output` 补齐。

### 容错约定

`recordResponseAttrs` 整体包裹 try/catch，捕获 `NullPointerException` / `ClassCastException` / `IllegalArgumentException` / `IllegalStateException` 后以 `debug` 级别记录并跳过——OTel 上报失败绝不传播到业务流。

### 非 LLM 事件方法

每种类型（Plugin / Chain / Retriever / Evaluator / Workflow / Prompt）均有 `onXxxStart` / `onXxxEnd` / `onXxxError` 三个方法：

| 方法模式 | 说明 |
| --- | --- |
| `onXxxStart(span, inputs, instanceInfo)` | 创建 INTERNAL Span，设置 `invoke_type`、`agent.name`、`inputs`（脱敏） |
| `onXxxEnd(span, outputs)` | 设置 `outputs`（脱敏）、结束时间、状态 OK，结束 Span |
| `onXxxError(span, error)` | 状态置 ERROR，记录异常，结束 Span |

### 错误处理

所有方法均包裹 try/catch，OTel 失败不会传播到业务流。错误通过 `Loggers.SESSION.warn` 记录。

### 错误码映射

| 异常类型 | `openjiuwen.error` 中的 `error_code` |
| --- | --- |
| `BaseError` | `baseError.getStatus().getCode()`（框架错误码） |
| 其他 `Throwable` | `StatusCode.WORKFLOW_EXECUTION_ERROR.getCode()` |

### 序列化辅助

| 方法 | 说明 |
| --- | --- |
| `static String serialize(Object value)` | 将 Map / List / 数组序列化为 JSON 字符串，其他类型用 `String.valueOf` |
| `static Object normalizeLlmPayload(Object value)` | 递归将 POJO（如 `BaseMessage`）转为 Map，等价于 Pydantic 的 `model_dump()` |

### 相关测试

- `OtelAgentHandlerTest`（19 个测试，分 6 组）
  - `TestLlmEvents`（4）：CLIENT Span 创建 / completion 设置 / ERROR 标记 / 通用 Throwable
  - `TestRedaction`（4）：prompt 脱敏开关 / 细粒度覆盖 / 非 LLM 输入脱敏
  - `TestNonLlmEvents`（4）：Chain INTERNAL Span / Plugin tool.name / Chain ERROR
  - `TestParentChild`（1）：父子 Span 上下文传递
  - `TestNormalization`（3）：payload 规范化
  - `TestRobustness`（2）：异常容错

---

## OtelWorkflowHandler Workflow 维度 Handler

```java
public class OtelWorkflowHandler extends TraceExtWorkflowHandler
```

将 Tracer Workflow 事件转换为层级化 OTel Span。维护三个内部映射构建 Span 树：

| 映射 | 键 | 值 | 用途 |
| --- | --- | --- | --- |
| `spanManager` | `invoke_id` | `OtelSpanState` | 生命周期管理 |
| `layerRootSpans` | `parent_node_id` | 根 `OtelSpanState` | 每层 Workflow 的根 Span |
| `componentSpans` | `node_id` | 组件 `OtelSpanState` | 子 Workflow 根的父 Span |

### 父子关系四路分支

`resolveParentContext(parentNodeId, metadata)` 逻辑：

| 条件 | 父 Span 来源 |
| --- | --- |
| `parent_node_id=""` + 是 Workflow 根 | 无父（顶层） |
| `parent_node_id=""` + 非 Workflow 根 | `layerRootSpans.get("")`（根 Workflow 根） |
| `parent_node_id!=""` + 是 Workflow 根 | `componentSpans.get(parentNodeId)`（宿主组件 Span） |
| `parent_node_id!=""` + 非 Workflow 根 | `componentSpans.get(parentNodeId)`（宿主组件 Span） |

### SpanKind 判定

根据 `component_type` 字符串匹配：

| component_type 包含 | SpanKind | gen_ai.operation.name |
| --- | --- | --- |
| `LLM` / `IntentDetection` / `Questioner` | `CLIENT` | `"chat"` |
| `Tool` | `INTERNAL` | `"execute_tool"` |
| 其他 | `INTERNAL` | （不设置） |

### 事件方法

| 签名 | 说明 |
| --- | --- |
| `onCallStart(invokeId, metadata, inputs, needSend, sourceIds, parentNodeId)` | 创建 Span，注册到三个映射，设置 workflow / component 属性 |
| `onCallDone(invokeId, outputs)` | 刷写缓冲数据，设置 outputs，状态 OK，结束 Span，清理映射 |
| `onPreInvoke(invokeId, inputs, componentMetadata, needSend)` | 更新 inputs 与组件属性 |
| `onPreStream(invokeId, chunk, needSend)` | 缓冲流式输入（仅 Map 类型） |
| `onInvoke(invokeId, onInvokeData, exception)` | 异常路径：标记错误/中断并结束 Span；正常路径：缓冲 on_invoke_data |
| `onPostInvoke(invokeId, outputs, inputs)` | 更新 outputs |
| `onPostStream(invokeId, chunk)` | 缓冲流式输出（仅 Map 类型） |
| `onInteract(invokeId, inputs, componentMetadata, needSend)` | 设置 `interactive_inputs` |

### 异常分类处理

`onInvoke` 中的 `exception` 三路分支：

| 异常类型 | 处理 |
| --- | --- |
| `GraphInterrupt` | 状态置 `interrupted`，**不**设 OTel ERROR，**不**设 `OJ_ERROR`，仅记录 `workflow.error_message` |
| `BaseError` | 状态置 `error`，OTel ERROR，`OJ_ERROR` 含 `error_code` |
| 其他 `Throwable` | 状态置 `error`，OTel ERROR，`OJ_ERROR` 用 `WORKFLOW_EXECUTION_ERROR` |

### 相关测试

- `OtelWorkflowHandlerTest`（20 个测试，分 6 组）
  - `TestLifecycle`（3）：onCallStart / onCallDone / 属性设置
  - `TestComponentType`（5）：LLM CLIENT / Tool / 其他 INTERNAL / operation.name
  - `TestInvokeException`（4）：GraphInterrupt / BaseError / 通用 Throwable / inner_error
  - `TestDataBuffering`（5）：onInvokeData / streamInputs / streamOutputs / flush
  - `TestSubWorkflow`（2）：子 Workflow 根的父 Span 解析
  - `TestRobustness`（3）：异常容错

---

## OtelRail Agent 生命周期 Rail

```java
public class OtelRail extends AgentRail
```

挂载到 Agent 回调的 Rail，负责管理根 Span（`beforeInvoke` / `afterInvoke`）与 LLM 子 Span（`beforeModelCall` / `afterModelCall` / `onModelException`）的生命周期。

### 优先级

`setPriority(0)`（最低优先级），确保在同事件回调中**最后**执行：Span 创建不阻塞其他 Rail，Span 收尾在其他 Rail 完成后进行。

### 回调方法

| 方法 | 说明 |
| --- | --- |
| `beforeInvoke(ctx)` | 创建根 Agent Span，存入 `ctx.extra["_otel_root_span"]`，触发 `on_chain_start` |
| `afterInvoke(ctx)` | 异常→触发 `on_chain_error`；正常→触发 `on_chain_end`，从 `ctx.extra` 移除根 Span |
| `beforeModelCall(ctx)` | 创建 LLM 子 Span（父为 `ctx.extra` 中的根 Span），触发 `on_llm_start` |
| `afterModelCall(ctx)` | 异常已设置时跳过（由 `onModelException` 处理）；否则触发 `on_llm_end` |
| `onModelException(ctx)` | 触发 `on_llm_error`，清理子 Span |
| `beforeToolCall(ctx)` | 创建 Tool/Plugin 子 Span，触发 `on_plugin_start` |
| `afterToolCall(ctx)` | 异常已设置时跳过（由 `onToolException` 处理）；否则触发 `on_plugin_end` |
| `onToolException(ctx)` | 触发 `on_plugin_error`，清理 Tool 子 Span |

根 Span 存放在 `AgentCallbackContext.getExtra()`（键 `_otel_root_span`），避免实例字段在并发调用间串扰。

### 解析辅助

通过类型检查从 `AgentCallbackContext` 解析信息（非反射）：

| 方法 | 说明 |
| --- | --- |
| `getTracer(ctx)` | `session.tracer()`；若为公开 `AgentSession` facade 则经 `getInner()` 解包 |
| `getAgentName(ctx)` | `agent.getCard().getName()`，回退到类名 |
| `getModelName(ctx)` | 从 `ReActAgentConfig` 读取 model name，回退到 `"LLM"` |

### 使用示例

```java
// 注册 Rail 到 Agent
Agent agent = Agent.builder()
        // ... 其他配置
        .rails(List.of(new OtelRail()))
        .build();
```

---

## 流式延迟指标采集链路

TTFT / TPOT 这类指标无法从 Span 的开始、结束时间推导出来——OTel Span 只知道整段耗时，不知道流里第一个 token 在第几毫秒出现。因此本模块的延迟指标走了一条独立的采集链路：**在模型层收口点计时 → 挂到 `UsageMetadata` 上 → 由 Handler 读出并写入 Span**。

### 完整链路

```
Model.stream(request)
    │
    ├── startNanos = System.nanoTime()          ← 在 client.stream() 之前采集
    │
    ├── client.stream(messages, options)        ← 建连 / TLS / 首字节等待
    │
    └── new CallbackIterator(delegate, framework, ..., startNanos, retryCounter)
            │
            ├── next()  逐帧判定
            │     ├── chunk.carriesOutputToken()        → 记 firstTokenNanos / lastTokenNanos
            │     ├── chunk.getReasoningContent() 非空   → 记 reasoningFirstNanos / reasoningLastNanos
            │     └── chunk.getUsageMetadata() 非空      → 存为 lastUsage
            │
            └── backfillLatency()   在 hasNext()==false 或 close() 时执行一次
                  ├── lastUsage.setRetryCount(...)
                  ├── setTotalLatency(整段耗时)
                  ├── setReasoningDurationMs(reasoningLast - reasoningFirst)
                  ├── setTtftSeconds((firstTokenNanos - startNanos) / 1e9)
                  └── setInterTokenLatencyMs(elapsed / (output_tokens - 1))
                          │
                          ▼
                  UsageMetadata（随响应对象/末帧返回）
                          │
                          ▼
              TraceAgentHandler.onLlmEnd → OtelAgentHandler.recordResponseAttrs
                          │
                          ├── resolveUsage → List<chunk> 逆序取末帧 usage
                          └── setStreamLatencyAttributes → span.setAttribute(...)
```

### 为什么在 `Model.CallbackIterator` 收口

`CallbackIterator` 是所有模型客户端（OpenAI / Qwen / 自研等）流式调用的唯一共同出口：无论具体客户端如何实现，返回的 `Iterator<AssistantMessageChunk>` 都会被这层包装。把计时放在这里，等于**一次实现覆盖全部客户端**，不需要每个 `ModelClient` 各自埋点，也不会因为新增客户端而漏采。

### 时间基准

- 使用 `System.nanoTime()` 而非 `System.currentTimeMillis()`：后者是墙上时钟，会被 NTP 校时、夏令时调整影响，计算出的耗时可能出现负数或跳变；`nanoTime()` 是单调时钟，只用于同一 JVM 内的差值计算，不跨进程传递。
- 所有时刻都在 `next()` 被调用的瞬间采集，因此测量的是**消费者实际拿到 chunk 的时刻**，包含了框架内部排队、上游背压等真实延迟。

### 回调框架装配依赖（重要）

`CallbackIterator` 只在 `framework != null` 时才被构造。若组件上的 `callbackFramework` 为 `null`，`Model.stream()` 会直接返回裸 iterator，**上述全部延迟指标与 `LLM_STREAM_INPUT`/`LLM_STREAM_OUTPUT` 事件都不会产生**。

组件级 `callbackFramework` 是静态字段，仅由测试通过 `setCallbackFramework(...)` 赋值，生产装配路径没有赋值点。因此 `Model`、`Tool`、`Workflow`、`Pregel`、`NodeTask` 统一改为经 `CallbackUtils.resolveCallbackFramework(componentFramework)` 解析：

| 优先级 | 来源 | 说明 |
| --- | --- | --- |
| 1 | 组件上显式设置的 `callbackFramework` | 测试与特殊场景可精确控制 |
| 2 | `CallbackUtils.frameworkSupplier` | 测试可替换的注入点 |
| 3 | `Runner.callbackFramework()` | 进程级全局单例，生产默认落点 |

这与 Python 侧 `_fw = Runner.callback_framework` 的行为对齐。缺了这层回退，埋点会「代码在、数据无」——最典型的现象是 Span 正常上报但延迟属性全部缺失。

> `Runner` 仅在 `resolveCallbackFramework` 方法体内引用，避免 `CallbackUtils` 类加载时触发 `Runner` 的初始化链。

### 延迟指标的自检方法

判断延迟属性是否真的写进了 Span，看两点：

1. **Span 上有无 `openjiuwen.gen_ai.response.time_to_first_token_ms`**。完全没有 → 大概率是回调框架未装配（见上一节），而非指标计算错误。
2. **值是否明显偏小**。若 TTFT 小于供应商的首帧推送延迟（例如推理模型常见 100~300ms 的 prefill 停顿），要怀疑「role-only 帧被当成首 token」——检查 `carriesOutputToken()` 判定是否被绕过。

`ModelStreamLatencyProductionWiringTest` 就是按这个思路写的回归用例：全真实装配（ReActAgent → Model → 真实 `OpenAIModelClient` → 本地 SSE stub → `OtelRail`），stub 先推 role-only 帧再停顿 150ms 才给首个 content 帧，断言 TTFT 必须 `> 120ms`，从而同时守住「属性必须存在」和「首帧语义必须正确」两件事。

---

## package-info

包级文档，说明本模块提供 `OtelTracerConfig`（配置）、`OtelTracerSetup`（初始化）、`OtelAgentHandler` 与 `OtelWorkflowHandler`（Span 创建）、`OtelRail`（Agent 生命周期集成），对应 Python 的 `openjiuwen.extensions.tracer_otel` 包。

---

## 测试总览

本模块相关单元测试（含延迟指标与装配回归）：

| 测试类 | 测试数 | 覆盖范围 |
| --- | --- | --- |
| `OtelTracerSetupTest` | 21 | 初始化 / 采样率验证 / 采样率集成 |
| `OtelAgentHandlerTest` | 29 | LLM 事件 / 脱敏 / 非 LLM 事件 / 父子关系 / 规范化 / 容错 |
| `OtelWorkflowHandlerTest` | 22 | 生命周期 / 组件类型 / 异常处理 / 数据缓冲 / 子 Workflow / 容错 |
| `RedactionUtilsTest` | 25 | 截断 / 哈希 / 脱敏判定 / 脱敏应用 |
| `OtelSpanManagerTest` | 13 | push / pop / get / 缓冲行为 |
| `TracerExtDispatchWhenNestedMissingTest` | 2 | 嵌套场景下扩展 handler 分发 |
| `OtelExitFlushSubprocessTest` | 1 | 进程退出时 Span 刷写 |
| `ModelStreamLatencyProductionWiringTest` | 1 | 生产装配下 TTFT/TPOT/总延迟必须回填（含首帧语义校验） |

配套的模型层指标采集单元测试位于 `core/src/test/java/com/openjiuwen/core/foundation/llm/`：

| 测试类 | 覆盖范围 |
| --- | --- |
| `StreamTimingCollectionTest` | `CallbackIterator` 的 TTFT / TPOT / 总延迟 / 推理时长计算，含 role-only 帧排除、单 token 边界 |
| `StreamLatencyMetricsTest` | `UsageMetadata` 延迟字段的回填与 `null` 语义 |

测试基础设施见 `ConftestOtel`：使用 `InMemorySpanExporter` + `SdkTracerProvider` + `SimpleSpanProcessor`，提供 `clearExporter()` 与 `jaegerIsAvailable()` 辅助方法。

---

## Python → Java 转换说明

| Python 概念 | Java 实现 |
| --- | --- |
| `@dataclass(frozen=True)` | `final class` + Builder 模式 |
| `async/await` | 同步方法 |
| `contextvars.ContextVar` | OTel `Context.current()` + `Scope` |
| Pydantic `model_dump()` | Jackson `convertValue` 递归转换（`normalizeLlmPayload`） |
| 模块级变量 | `static final` 常量 |
| `try/except` 包裹 | `try/catch` + `Loggers.SESSION.warn` |
| `NonRecordingSpan` 判断 | `span.isRecording()` |
| `TracerProvider.getTracer(name)` | `TracerProvider.get(name)`（OTel 1.38.0 API 变更） |
| `setHeaders(Map)` | `setHeaders(() -> map)`（Supplier 形式） |
