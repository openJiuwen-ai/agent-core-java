# Foundation 模块 API 文档

> 包路径：`com.openjiuwen.core.foundation`

Foundation 模块提供框架的基础能力层，包括大语言模型（LLM）调用、Prompt 模板引擎和工具（Tool）执行框架。

---

## 目录

- [1. 大语言模型（llm）](#1-大语言模型llm)
- [2. Prompt 模板（prompt）](#2-prompt-模板prompt)
- [3. 工具框架（tool）](#3-工具框架tool)

---

## 1. 大语言模型（llm）

### 1.1 Model

统一的大模型调用入口，基于 SPI 工厂模式支持多提供商注册。

**包路径**：`com.openjiuwen.core.foundation.llm`

**内部接口 ModelClientFactory**：
```java
public interface ModelClientFactory {
    String providerName();       // 提供商名称
    BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig);
}
```

**静态方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `registerFactory(ModelClientFactory factory)` | `void` | 注册模型客户端工厂 |

**构造方法**：
```java
Model(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs)` | `AssistantMessage` | 同步调用大模型 |
| `stream(Object messages, Object tools, Float temperature, Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs)` | `Iterator<AssistantMessageChunk>` | 流式调用大模型 |
| `generateImage(List<UserMessage> messages, String model, String size, String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs)` | `ImageGenerationResponse` | 生成图片 |
| `generateSpeech(List<UserMessage> messages, String model, String voice, String languageType, Map<String, Object> kwargs)` | `AudioGenerationResponse` | 生成语音 |
| `generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark, String negativePrompt, Integer seed, Map<String, Object> kwargs)` | `VideoGenerationResponse` | 生成视频 |

### 1.2 BaseModelClient

所有 LLM 提供商客户端的抽象基类。

**包路径**：`com.openjiuwen.core.foundation.llm.model_clients`

**构造方法**：
```java
protected BaseModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig)
```

**受保护字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `modelConfig` | `ModelRequestConfig` | 模型请求配置 |
| `modelClientConfig` | `ModelClientConfig` | 模型客户端配置 |

**抽象方法**（与 Model 类相同的方法签名）：
- `invoke(...)` → `AssistantMessage`
- `stream(...)` → `Iterator<AssistantMessageChunk>`
- `generateImage(...)` → `ImageGenerationResponse`
- `generateSpeech(...)` → `AudioGenerationResponse`
- `generateVideo(...)` → `VideoGenerationResponse`

**受保护方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getClientName()` | `String` | 获取客户端名称 |
| `validateConfig()` | `void` | 验证配置 |
| `convertMessagesToDict(Object messages)` | `List<Map<String, Object>>` | 将消息转换为字典列表 |
| `convertToolsToDict(Object tools)` | `List<Map<String, Object>>` | 将工具转换为字典列表 |
| `buildRequestParams(...)` | `Map<String, Object>` | 构建请求参数 |

### 1.3 消息类型

#### BaseMessage

所有消息类型的基类。

**包路径**：`com.openjiuwen.core.foundation.llm.schema`

**核心字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `role` | `String` | 消息角色 |
| `content` | `String` | 消息内容 |

#### 具体消息类

| 类名 | 角色 | 说明 |
|------|------|------|
| `UserMessage` | `"user"` | 用户消息 |
| `AssistantMessage` | `"assistant"` | 助手消息，可包含 `toolCalls` 和 `usage` |
| `SystemMessage` | `"system"` | 系统消息 |
| `ToolMessage` | `"tool"` | 工具消息，包含 `toolCallId` |

#### 流式消息块

| 类名 | 说明 |
|------|------|
| `BaseMessageChunk` | 消息块基类 |
| `AssistantMessageChunk` | 助手消息块，支持 `merge()` 合并 |

#### ToolCall

工具调用信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 工具调用 ID |
| `type` | `String` | 调用类型，默认值为 `"function"` |
| `name` | `String` | 工具名称 |
| `arguments` | `String` | 工具参数，JSON 字符串形式 |
| `index` | `Integer` | 同一响应中多次工具调用的顺序索引 |

#### UsageMetadata

模型响应使用量元数据。

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `int` | 响应状态码 |
| `errMsg` | `String` | 错误消息 |
| `prompt` | `String` | 实际请求 Prompt |
| `taskId` | `String` | 任务 ID |
| `modelName` | `String` | 实际使用的模型名 |
| `totalLatency` | `double` | 总耗时 |
| `firstTokenTime` | `String` | 首 Token 时间 |
| `requestStartTime` | `String` | 请求开始时间 |
| `inputTokens` | `int` | 输入 Token 数 |
| `outputTokens` | `int` | 输出 Token 数 |
| `totalTokens` | `int` | 总 Token 数 |
| `cacheTokens` | `int` | 命中缓存的 Token 数 |

### 1.4 配置类

#### ModelClientConfig

模型客户端连接级配置（提供 `builder()` 构建器，支持额外字段透传）。

**包路径**：`com.openjiuwen.core.foundation.llm.schema`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `clientId` | `String` | 自动生成 UUID | 客户端唯一标识 |
| `clientProvider` | `String` | - | 模型客户端提供商 |
| `apiKey` | `String` | - | API 密钥 |
| `apiBase` | `String` | - | API 基础地址 |
| `timeout` | `double` | `60.0` | 请求超时时间（秒） |
| `maxRetries` | `int` | `3` | 最大重试次数 |
| `verifySsl` | `boolean` | `true` | 是否校验证书 |
| `sslCert` | `String` | `null` | 自定义证书路径 |
| `extraFields` | `Map<String, Object>` | `{}` | 非标准扩展字段 |

#### ModelRequestConfig

模型请求级配置（Lombok `@Data`、`@Builder`，支持额外字段透传）。

**包路径**：`com.openjiuwen.core.foundation.llm.schema`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `modelName` | `String` | `""` | 模型名称，序列化字段名为 `model` |
| `temperature` | `Double` | `0.95` | 温度参数 |
| `topP` | `Double` | `0.1` | Top-P 参数 |
| `maxTokens` | `Integer` | `null` | 最大生成 Token 数 |
| `stop` | `String` | `null` | 停止序列 |
| `extraFields` | `Map<String, Object>` | `{}` | 非标准扩展字段 |

### 1.5 响应类型

#### GenerationResponse

通用生成响应。

| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | `Object` | 生成内容 |
| `usage` | `UsageMetadata` | Token 使用量 |

#### ImageGenerationResponse

图片生成响应。

#### AudioGenerationResponse

语音生成响应。

#### VideoGenerationResponse

视频生成响应。

### 1.6 输出解析器

#### BaseOutputParser

输出解析器抽象基类。

**包路径**：`com.openjiuwen.core.foundation.llm.output_parsers`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `parse(String text)` | `Object` | 解析文本输出（抽象） |
| `getFormatInstructions()` | `String` | 获取格式指引（抽象） |

#### JsonOutputParser

从 LLM 文本输出中提取 JSON。

**继承**：`BaseOutputParser`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `parse(String text)` | `Object` | 从文本中提取并解析 JSON |
| `getFormatInstructions()` | `String` | 获取 JSON 格式指引 |

### 1.7 ProviderType

模型提供商类型枚举。

| 枚举值 | 说明 |
|--------|------|
| `OPENAI` | OpenAI |
| `SILICONFLOW` | SiliconFlow |
| `DASHSCOPE` | 灵积（DashScope） |

### 1.8 MergeUtils

流式消息块合并工具。

**包路径**：`com.openjiuwen.core.foundation.llm.schema`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `mergeChunks(Iterator<AssistantMessageChunk> chunks)` | `AssistantMessage` | 合并所有消息块为完整消息 |

---

## 2. Prompt 模板（prompt）

### 2.1 PromptTemplate

支持占位符替换的 Prompt 模板类（Lombok `@Data`、`@Builder`）。

**包路径**：`com.openjiuwen.core.foundation.prompt`

**字段**：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | `String` | `""` | 模板名称 |
| `content` | `Object` | `""` | 模板内容（支持 String 或 List<BaseMessage>） |
| `placeholderPrefix` | `String` | `"{{"` | 占位符前缀 |
| `placeholderSuffix` | `String` | `"}}"` | 占位符后缀 |

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `toMessages()` | `List<BaseMessage>` | 将模板内容转换为消息列表 |
| `format(Map<String, Object> keywords)` | `PromptTemplate` | 使用关键字替换占位符，返回新模板 |

**使用示例**：
```java
PromptTemplate template = PromptTemplate.builder()
    .content("你好 {{name}}，请回答关于 {{topic}} 的问题")
    .build();
PromptTemplate filled = template.format(Map.of("name", "用户", "topic", "Java"));
List<BaseMessage> messages = filled.toMessages();
```

### 2.2 PromptAssembler

占位符替换引擎，支持 String 和 List<BaseMessage> 两种内容类型。

**包路径**：`com.openjiuwen.core.foundation.prompt.assemble`

### 2.3 变量处理

#### Variable（抽象类）

模板变量处理基类。

**包路径**：`com.openjiuwen.core.foundation.prompt.assemble.variables`

#### TextableVariable

字符串占位符处理器。

#### DictableVariable

递归字典/列表占位符处理器。

---

## 3. 工具框架（tool）

### 3.1 Tool

所有工具实现的抽象基类。

**包路径**：`com.openjiuwen.core.foundation.tool`

**构造方法**：
```java
protected Tool(ToolCard card)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getCard()` | `ToolCard` | 获取工具卡片 |
| `invoke(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Object` | 调用工具（抽象）|
| `invoke(Map<String, Object> inputs)` | `Object` | 调用工具（便捷方法）|
| `stream(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Iterator<Object>` | 流式调用工具（抽象）|
| `stream(Map<String, Object> inputs)` | `Iterator<Object>` | 流式调用工具（便捷方法）|

### 3.2 ToolCard

工具卡片，描述工具的元数据和输入参数。

**包路径**：`com.openjiuwen.core.foundation.tool`  
**继承**：`BaseCard`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `inputParams` | `Map<String, Object>` | 空 Map | 输入参数 Schema |
| `properties` | `Map<String, Object>` | 空 Map | 附加属性 |

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `toolInfo()` | `ToolInfo` | 获取工具信息（名称、描述、参数） |

### 3.3 ToolInfo

工具信息定义，用于传递给 LLM。

**包路径**：`com.openjiuwen.core.foundation.tool.schema`

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 工具名称 |
| `description` | `String` | 工具描述 |
| `parameters` | `Map<String, Object>` | 参数 Schema |

### 3.4 LocalFunction

本地函数工具，将 Java 函数包装为标准工具。

**包路径**：`com.openjiuwen.core.foundation.tool.function`  
**继承**：`Tool`

**构造方法**：
```java
LocalFunction(ToolCard card, Function<Map<String, Object>, Object> func)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Object` | 执行本地函数 |
| `stream(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Iterator<Object>` | 流式执行 |
| `getFunc()` | `Function<Map<String, Object>, Object>` | 获取底层函数 |

**使用示例**：
```java
ToolCard card = ToolCard.builder()
    .name("calculator")
    .description("简单计算器")
    .build();
Tool tool = new LocalFunction(card, inputs -> {
    int a = (int) inputs.get("a");
    int b = (int) inputs.get("b");
    return a + b;
});
Object result = tool.invoke(Map.of("a", 1, "b", 2));
```

### 3.5 MCP 工具

#### McpClient（接口）

MCP 客户端接口，定义与 MCP 服务器的通信契约。

**包路径**：`com.openjiuwen.core.foundation.tool.mcp`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `connect(int retryTimes, float timeout)` | `boolean` | 连接到 MCP 服务器 |
| `connect()` | `boolean` | 使用默认参数连接 |
| `disconnect(float timeout)` | `boolean` | 断开连接 |
| `disconnect()` | `boolean` | 使用默认参数断开 |
| `listTools(float timeout)` | `List<Object>` | 列出可用工具 |
| `listTools()` | `List<Object>` | 使用默认超时列出工具 |
| `callTool(String toolName, Map<String, Object> arguments, float timeout)` | `Object` | 调用工具 |
| `callTool(String toolName, Map<String, Object> arguments)` | `Object` | 使用默认超时调用工具 |
| `getToolInfo(String toolName, float timeout)` | `Optional<Object>` | 获取工具信息 |
| `getToolInfo(String toolName)` | `Optional<Object>` | 使用默认超时获取工具信息 |
| `getServerPath()` | `String` | 获取服务器路径 |

#### McpTool

MCP 工具实现。

**包路径**：`com.openjiuwen.core.foundation.tool.mcp`  
**继承**：`Tool`

**构造方法**：
```java
McpTool(McpClient mcpClient, McpToolCard card)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Object` | 通过 MCP 客户端调用工具 |
| `stream(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Iterator<Object>` | 流式调用 |

#### McpToolCard

MCP 工具卡片（继承 `ToolCard`）。

#### McpServerConfig

MCP 服务器配置（Lombok `@Data`、`@Builder`）。

#### McpToolInfo

MCP 工具信息定义。

**包路径**：`com.openjiuwen.core.foundation.tool.schema`

### 3.6 RESTful API 工具

#### RestfulApi

RESTful API 工具，将 HTTP API 包装为标准工具。

**包路径**：`com.openjiuwen.core.foundation.tool.service_api`  
**继承**：`Tool`

**构造方法**：
```java
RestfulApi(RestfulApiCard card)
```

**方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Object` | 执行 HTTP 请求并返回结果 |
| `stream(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Iterator<Object>` | 流式执行 |

**关键默认值**：
- API 超时：60 秒
- 最大响应大小：10 MB

#### RestfulApiCard

RESTful API 卡片，描述 API 端点信息。

**包路径**：`com.openjiuwen.core.foundation.tool.service_api`  
**继承**：`ToolCard`

| 字段 | 类型 | 说明 |
|------|------|------|
| `url` | `String` | API 地址 |
| `method` | `String` | HTTP 方法 |
| `timeout` | `double` | 超时时间 |
| `headers` | `Map<String, String>` | 请求头 |

#### ApiParamLocation

API 参数位置枚举。

| 枚举值 | 说明 |
|--------|------|
| `QUERY` | 查询参数 |
| `BODY` | 请求体 |
| `HEADER` | 请求头 |
| `PATH` | 路径参数 |

#### ApiParamMapper

API 参数映射器，将输入参数映射到不同的 HTTP 位置。

### 3.7 响应解析器

#### BaseResponseParser

响应解析器抽象基类。

**包路径**：`com.openjiuwen.core.foundation.tool.service_api.parser`

#### JsonResponseParser

JSON 响应解析器。

#### TextResponseParser

文本响应解析器。

#### ParserRegistry

解析器注册表（单例）。

### 3.8 响应解压器

#### BaseResponseDecompressor

响应解压器抽象基类。

**包路径**：`com.openjiuwen.core.foundation.tool.service_api.parser`

#### GzipDecompressor

Gzip 解压器。

#### DeflateDecompressor

Deflate 解压器。
