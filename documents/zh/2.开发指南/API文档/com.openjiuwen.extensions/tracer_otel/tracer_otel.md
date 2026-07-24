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

```java
// 1. 构建配置
OtelTracerConfig config = OtelTracerConfig.builder()
        .exporterType("otlp")
        .exporterEndpoint("http://localhost:4317")
        .protocol("grpc")
        .serviceName("my-agent-service")
        .sampleRate(1.0)
        .redactionEnabled(true)
        .build();

// 2. 初始化 Tracer
Tracer otelTracer = OtelTracerSetup.initOtelTracer(config);

// 3. 注册到框架内置 Tracer（通过 TraceExtAgentHandler / TraceExtWorkflowHandler）
OtelAgentHandler agentHandler = new OtelAgentHandler(otelTracer, config);
OtelWorkflowHandler workflowHandler = new OtelWorkflowHandler(otelTracer, config);
```

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
| `public Builder redactionEnabled(boolean)` | 设置脱敏总开关 |
| `public Builder redactPrompts(Boolean)` | 覆盖 prompt 脱敏 |
| `public Builder redactCompletions(Boolean)` | 覆盖 completion 脱敏 |
| `public Builder maxAttrLength(int)` | 设置截断上限 |
| `public OtelTracerConfig build()` | 构建不可变配置，校验 `sampleRate ∈ [0.0, 1.0]` |

### 使用示例

```java
OtelTracerConfig config = OtelTracerConfig.builder()
        .exporterType("otlp")
        .exporterEndpoint("http://localhost:4317")
        .protocol("grpc")
        .sampleRate(0.5)
        .redactionEnabled(true)
        .redactPrompts(false)   // prompt 不脱敏
        .redactCompletions(true) // completion 强制脱敏
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
| `GEN_AI_OPERATION_NAME` | `"gen_ai.operation.name"` | 操作名（`"chat"` / `"execute_tool"`） |
| `GEN_AI_PROMPT` | `"gen_ai.prompt"` | 输入 prompt |
| `GEN_AI_COMPLETION` | `"gen_ai.completion"` | 输出 completion |
| `GEN_AI_USAGE_PROMPT_TOKENS` | `"gen_ai.usage.prompt_tokens"` | 输入 token 数 |
| `GEN_AI_USAGE_COMPLETION_TOKENS` | `"gen_ai.usage.completion_tokens"` | 输出 token 数 |
| `GEN_AI_TOOL_NAME` | `"gen_ai.tool.name"` | 工具名 |

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
        .redactionEnabled(true)
        .redactPrompts(false)
        .redactCompletions(true)
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
| `onLlmEnd(TraceAgentSpan span, Object outputs)` | 设置 `gen_ai.completion`（脱敏后），结束 Span，状态置 OK |
| `onLlmError(TraceAgentSpan span, Throwable error)` | 状态置 ERROR，记录异常事件，设置 `openjiuwen.error` 与 `openjiuwen.agent.error_message` |

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
| `beforeInvoke(ctx)` | 创建根 Agent Span，触发 `on_chain_start` |
| `afterInvoke(ctx)` | 异常→触发 `on_chain_error`；正常→触发 `on_chain_end`，清理 rootSpan |
| `beforeModelCall(ctx)` | 创建 LLM 子 Span（父为 rootSpan），触发 `on_llm_start` |
| `afterModelCall(ctx)` | 异常已设置时跳过（由 `onModelException` 处理）；否则触发 `on_llm_end` |
| `onModelException(ctx)` | 触发 `on_llm_error`，清理子 Span |

### 反射辅助

通过反射从 `AgentCallbackContext` 解析信息，避免与具体 Agent 实现耦合：

| 方法 | 说明 |
| --- | --- |
| `getTracer(ctx)` | 反射调用 `session.tracer()` 获取内置 Tracer |
| `getAgentName(ctx)` | 反射 `agent.getCard().getName()`，回退到类名 |
| `getModelName(ctx)` | 反射 `agent.getConfig().getModelConfig().getModelName()`，回退到 `"LLM"` |

### 使用示例

```java
// 注册 Rail 到 Agent
Agent agent = Agent.builder()
        // ... 其他配置
        .rails(List.of(new OtelRail()))
        .build();
```

---

## package-info

包级文档，说明本模块提供 `OtelTracerConfig`（配置）、`OtelTracerSetup`（初始化）、`OtelAgentHandler` 与 `OtelWorkflowHandler`（Span 创建）、`OtelRail`（Agent 生命周期集成），对应 Python 的 `openjiuwen.extensions.tracer_otel` 包。

---

## 测试总览

本模块共 97 个单元测试，全部通过：

| 测试类 | 测试数 | 覆盖范围 |
| --- | --- | --- |
| `OtelTracerSetupTest` | 20 | 初始化 / 采样率验证 / 采样率集成 |
| `OtelAgentHandlerTest` | 19 | LLM 事件 / 脱敏 / 非 LLM 事件 / 父子关系 / 规范化 / 容错 |
| `OtelWorkflowHandlerTest` | 20 | 生命周期 / 组件类型 / 异常处理 / 数据缓冲 / 子 Workflow / 容错 |
| `RedactionUtilsTest` | 25 | 截断 / 哈希 / 脱敏判定 / 脱敏应用 |
| `OtelSpanManagerTest` | 13 | push / pop / get / 缓冲行为 |

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
