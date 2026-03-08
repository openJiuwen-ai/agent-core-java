# Operator 模块 API 文档

> 包路径：`com.openjiuwen.core.operator`

Operator 模块提供原子执行单元抽象，以及围绕 LLM、Memory、Tool 三类调用的标准操作符实现，支持参数调优、状态快照与可选流式输出。

---

## 目录

- [1. 核心抽象](#1-核心抽象)
- [2. LLM 调用](#2-llm-调用)
- [3. Memory 调用](#3-memory-调用)
- [4. Tool 调用](#4-tool-调用)
- [5. 向后兼容 API](#5-向后兼容-api)

---

## 1. 核心抽象

### 1.1 Operator

原子执行单元抽象基类，所有具体 Operator 都继承自该类。

**包路径**：`com.openjiuwen.core.operator`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getOperatorId()` | `String` | 返回当前 Operator 的唯一标识 |
| `getTunables()` | `Map<String, TunableSpec>` | 返回可调参数描述 |
| `setParameter(String target, Object value)` | `void` | 更新指定调优参数 |
| `getState()` | `Map<String, Object>` | 导出当前状态快照 |
| `loadState(Map<String, Object> state)` | `void` | 从状态快照恢复 |
| `invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `Object` | 同步执行 |
| `invoke(Map<String, Object> inputs, Session session)` | `Object` | 同步执行的便捷重载 |
| `stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `OperatorStream<?>` | 流式执行，默认抛出 `UnsupportedOperationException` |
| `stream(Map<String, Object> inputs, Session session)` | `OperatorStream<?>` | 流式执行的便捷重载 |

### 1.2 OperatorStream\<T\>

显式支持关闭的流式输出接口。

**包路径**：`com.openjiuwen.core.operator`

| 继承关系 | 说明 |
|----------|------|
| `Iterator<T>` | 逐块读取流式结果 |
| `AutoCloseable` | 支持提前终止流 |

`close()` 默认是空实现，具体 Operator 可以覆写。

### 1.3 TunableSpec

单个可调参数的结构化描述。

**包路径**：`com.openjiuwen.core.operator`

**记录字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 参数名 |
| `kind` | `String` | 参数类型，如 `prompt`、`discrete`、`text` |
| `path` | `String` | 参数在 Operator 内部的路径 |
| `constraint` | `Object` | 约束元数据，可为空 |

**构造方法**：
```java
TunableSpec(String name, String kind, String path, Object constraint)
TunableSpec(String name, String kind, String path)
```

---

## 2. LLM 调用

### 2.1 LLMCallOperator

面向大模型调用的 Operator，实现了 Prompt 可调、历史消息拼装与流式输出封装。

**包路径**：`com.openjiuwen.core.operator.llm_call`

**常量**：

| 常量 | 类型 | 说明 |
|------|------|------|
| `DEFAULT_USER_PROMPT` | `String` | 默认用户 Prompt，值为 `{{query}}` |

**构造方法**：
```java
LLMCallOperator(
    String modelName,
    Model llm,
    Object systemPrompt,
    Object userPrompt,
    boolean freezeSystemPrompt,
    boolean freezeUserPrompt,
    String llmCallId,
    BiConsumer<String, Object> onParameterUpdated
)

LLMCallOperator(String modelName, Model llm, Object systemPrompt, Object userPrompt)
```

**调优参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `system_prompt` | `prompt` | 仅在 `freezeSystemPrompt=false` 时暴露 |
| `user_prompt` | `prompt` | 仅在 `freezeUserPrompt=false` 时暴露 |

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getOperatorId()` | `String` | 返回 Operator ID，默认值为 `llm_call` |
| `getTunables()` | `Map<String, TunableSpec>` | 返回当前可调 Prompt |
| `setParameter(String target, Object value)` | `void` | 更新系统或用户 Prompt |
| `getState()` | `Map<String, Object>` | 返回 `system_prompt` 与 `user_prompt` 快照 |
| `loadState(Map<String, Object> state)` | `void` | 从状态恢复 Prompt |
| `invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `AssistantMessage` | 同步调用模型 |
| `stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `OperatorStream<AssistantMessageChunk>` | 流式调用模型 |
| `getSystemPrompt()` | `PromptTemplate` | 获取系统 Prompt |
| `getUserPrompt()` | `PromptTemplate` | 获取用户 Prompt |
| `updateSystemPrompt(Object value)` | `void` | 直接更新系统 Prompt |
| `updateUserPrompt(Object value)` | `void` | 直接更新用户 Prompt |
| `setFreezeSystemPrompt(boolean freezeSystemPrompt)` | `void` | 设置系统 Prompt 是否冻结 |
| `setFreezeUserPrompt(boolean freezeUserPrompt)` | `void` | 设置用户 Prompt 是否冻结 |
| `getFreezeSystemPrompt()` | `boolean` | 获取系统 Prompt 冻结状态 |
| `getFreezeUserPrompt()` | `boolean` | 获取用户 Prompt 冻结状态 |

**调用约定**：

- `kwargs.history` 可以传入 `List<BaseMessage>` 作为历史消息。
- `kwargs.tools` 会透传给 `Model.invoke()` / `Model.stream()`。
- 如果 `inputs.messages` 已经是 `List<BaseMessage>`，则走 passthrough 模式，仅额外补入 system prompt。

### 2.2 LLMCall

`LLMCallOperator` 的向后兼容别名类。

**包路径**：`com.openjiuwen.core.operator.llm_call`

**构造方法**：
```java
LLMCall(
    String modelName,
    Model llm,
    Object systemPrompt,
    Object userPrompt,
    boolean freezeSystemPrompt,
    boolean freezeUserPrompt,
    String llmCallId,
    BiConsumer<String, Object> onParameterUpdated
)

LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt)
```

---

## 3. Memory 调用

### 3.1 MemoryOperation

Memory Operator 依赖的最小内存操作协议。

**包路径**：`com.openjiuwen.core.operator.memory_call`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Object` | 执行一次内存调用 |
| `supportsStream()` | `boolean` | 是否支持流式输出，默认 `false` |
| `stream(Map<String, Object> inputs, Map<String, Object> kwargs)` | `Iterator<Object>` | 流式输出，默认抛出 `UnsupportedOperationException` |

### 3.2 MemoryInvoker

非标准内存调用流程的函数式回调接口。

**包路径**：`com.openjiuwen.core.operator.memory_call`

```java
Object invoke(Map<String, Object> inputs) throws Exception
```

### 3.3 MemoryCallOperator

面向内存调用的 Operator，支持开关和重试次数调优。

**包路径**：`com.openjiuwen.core.operator.memory_call`

**构造方法**：
```java
MemoryCallOperator(MemoryOperation memory, String memoryCallId, MemoryInvoker memoryInvoker)
MemoryCallOperator(MemoryOperation memory)
MemoryCallOperator(MemoryInvoker memoryInvoker)
MemoryCallOperator()
```

**调优参数**：

| 参数名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| `enabled` | `discrete` | `bool` | 是否启用 Memory Operator |
| `max_retries` | `discrete` | `int, min=0, max=5` | 最大重试次数 |

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getOperatorId()` | `String` | 返回 Operator ID，默认值为 `memory_call` |
| `getTunables()` | `Map<String, TunableSpec>` | 返回 `enabled` 与 `max_retries` |
| `setParameter(String target, Object value)` | `void` | 更新启用状态或重试次数 |
| `getState()` | `Map<String, Object>` | 返回 `enabled` 与 `max_retries` 状态 |
| `loadState(Map<String, Object> state)` | `void` | 恢复状态 |
| `invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `Object` | 调用 `memoryInvoker` 或 `memory.invoke()` |
| `stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `OperatorStream<Object>` | 调用 `memory.stream()`，要求已配置 `memory` |

---

## 4. Tool 调用

### 4.1 ToolExecutionResult

Router 模式下的工具执行结果封装。

**包路径**：`com.openjiuwen.core.operator.tool_call`

**记录字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `result` | `Object` | 工具执行结果 |
| `toolMessage` | `ToolMessage` | 与结果配套的工具响应消息 |

### 4.2 ToolExecutor

Router 模式工具执行器接口。

**包路径**：`com.openjiuwen.core.operator.tool_call`

```java
ToolExecutionResult execute(Object toolCall, Session session) throws Exception
```

### 4.3 ToolRegistry

ToolCallOperator 依赖的最小工具注册表接口。

**包路径**：`com.openjiuwen.core.operator.tool_call`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getToolDefs()` | `List<Map<String, Object>>` | 返回工具定义列表，默认空列表 |
| `getTools()` | `Map<String, Tool>` | 返回工具对象映射，默认空映射 |
| `setToolDescription(String toolName, String description)` | `void` | 更新指定工具的描述 |

### 4.4 ToolCallOperator

面向工具调用的 Operator，支持单工具直接执行和 Router 批量分发两种模式。

**包路径**：`com.openjiuwen.core.operator.tool_call`

**构造方法**：
```java
ToolCallOperator(Tool tool, String toolCallId, ToolExecutor toolExecutor, ToolRegistry toolRegistry)
ToolCallOperator(Tool tool)
ToolCallOperator(ToolExecutor toolExecutor)
ToolCallOperator(Tool tool, ToolRegistry toolRegistry)
ToolCallOperator()
```

**调优参数**：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `tool_description` | `text` | 仅在注入 `toolRegistry` 时暴露，用于批量更新工具描述 |

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getOperatorId()` | `String` | 返回 Operator ID，默认值为 `tool_call` |
| `getTunables()` | `Map<String, TunableSpec>` | 返回工具描述调优项；无注册表时为空 |
| `setParameter(String target, Object value)` | `void` | 批量更新工具描述 |
| `getState()` | `Map<String, Object>` | 返回 `enabled` 与 `max_retries` 状态 |
| `loadState(Map<String, Object> state)` | `void` | 恢复 `enabled` 与 `max_retries` |
| `invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `Object` | 执行工具调用 |
| `stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `OperatorStream<Object>` | 流式调用单个 `Tool` |

**调用模式**：

- 直接模式：配置了 `Tool` 时，调用 `tool.invoke(inputs, kwargs)`。
- Router 模式：当 `inputs.tool_calls` 是列表且配置了 `ToolExecutor` 时，逐个执行并返回 `List<ToolExecutionResult>`。
- 流式模式仅适用于直接模式；未配置 `Tool` 时会抛出 `UnsupportedOperationException`。

---

## 5. 向后兼容 API

### 5.1 legacy.llm_call.LLMCall

旧版 LLMCall 封装，不继承 `Operator`，用于兼容 pre-operator 调用路径。

**包路径**：`com.openjiuwen.core.operator.legacy.llm_call`

**常量**：

| 常量 | 类型 | 说明 |
|------|------|------|
| `DEFAULT_USER_PROMPT` | `String` | 默认用户 Prompt，值为 `{{query}}` |

**构造方法**：
```java
LLMCall(
    String modelName,
    Model llm,
    Object systemPrompt,
    Object userPrompt,
    boolean freezeSystemPrompt,
    boolean freezeUserPrompt,
    String llmCallId
)

LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt)
```

**公共方法**：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Map<String, Object> inputs, Session session, List<BaseMessage> history, Object tools)` | `AssistantMessage` | 同步调用 |
| `invoke(Map<String, Object> inputs, Session session)` | `AssistantMessage` | 便捷重载 |
| `stream(Map<String, Object> inputs, Session session, List<BaseMessage> history, Object tools)` | `OperatorStream<AssistantMessageChunk>` | 流式调用 |
| `stream(Map<String, Object> inputs, Session session)` | `OperatorStream<AssistantMessageChunk>` | 便捷重载 |
| `getOptimizerCallback()` | `LegacyOptimizerCallback` | 获取回调 |
| `setOptimizerCallback(LegacyOptimizerCallback optimizerCallback)` | `void` | 设置回调 |
| `getSystemPrompt()` / `getUserPrompt()` | `PromptTemplate` | 获取 Prompt |
| `updateSystemPrompt(Object systemPrompt)` / `updateUserPrompt(Object userPrompt)` | `void` | 更新 Prompt |
| `setFreezeSystemPrompt(boolean freezeSystemPrompt)` / `setFreezeUserPrompt(boolean freezeUserPrompt)` | `void` | 设置冻结状态 |
| `getFreezeSystemPrompt()` / `getFreezeUserPrompt()` | `boolean` | 获取冻结状态 |

### 5.2 LegacyOptimizerCallback

旧版 LLMCall 的完成回调接口。

**包路径**：`com.openjiuwen.core.operator.legacy.llm_call`

```java
void onComplete(String llmCallId, Map<String, Object> inputs, Object response, Session session) throws Exception
```
