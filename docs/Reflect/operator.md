# operator 模块 Python / Java API 映射

## 对照范围

- Python: `agent-core-python/openjiuwen/core/operator/**`
- Java: `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/operator/**`
- 统计口径:
  - Python 统计公开类、包级导出别名、公开属性与不以下划线开头的方法
  - Java 统计 `public` 类 / 接口 / `record` 与 `public` 方法
- 命名约定:
  - `snake_case -> camelCase`
  - `property -> getter`
  - `async invoke/stream -> 同步 invoke + OperatorStream`

## 复核结论

- Python `operator` 主体公开 API 已在 Java 侧形成完整对位，核心类未发现整类缺失。
- 差异主要来自四类适配:
  - Python `property` / `__init__` / `async` 语义在 Java 中落为 getter、构造器、同步调用
  - Python duck typing 在 Java 中落为显式接口或桥接类型
  - Python 包级别名在 Java 中落为直接导入具体类
  - Python `LLMCall = LLMCallOperator` 在 Java 中落为 `LLMCall extends LLMCallOperator`

## 状态说明

- `完全映射`: 类职责与公开 API 基本一一对应
- `适配映射`: 有对位实现，但存在命名、同步模型、类型系统或别名形式差异
- `Java-only`: Java 为承接 Python 动态能力而补出的桥接 API

## 包级映射

| Python 模块 | Java 包 / 类型 | 状态 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.core.operator` | `com.openjiuwen.core.operator` + 子包直接导入 | 适配映射 | Python 通过 `__all__` 暴露 `Operator`、`TunableSpec`、`LLMCallOperator`、`LLMCall`、`ToolCallOperator`、`MemoryCallOperator`；Java 通过包内具体类直接导入。 |
| `openjiuwen.core.operator.llm_call` | `com.openjiuwen.core.operator.llm_call` | 完全映射 | `LLMCallOperator` 与兼容入口 `LLMCall` 均存在。 |
| `openjiuwen.core.operator.memory_call` | `com.openjiuwen.core.operator.memory_call` | 适配映射 | `MemoryCallOperator` 对位存在，Java 额外引入 `MemoryOperation`、`MemoryInvoker`。 |
| `openjiuwen.core.operator.tool_call` | `com.openjiuwen.core.operator.tool_call` | 适配映射 | `ToolCallOperator` 对位存在，Java 额外引入 `ToolRegistry`、`ToolExecutor`、`ToolExecutionResult`。 |
| `openjiuwen.core.operator.legacy.llm_call` | `com.openjiuwen.core.operator.legacy.llm_call` | 适配映射 | 旧版 `LLMCall` 对位存在，Java 将回调签名显式化为 `LegacyOptimizerCallback`。 |

## 1. base

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `TunableSpec` | `TunableSpec` | `__init__(name, kind, path, constraint=None) -> TunableSpec(name, kind, path, constraint)`；属性 `name/kind/path/constraint -> name()/kind()/path()/constraint()` | 完全映射 | Python 用 `__slots__` 固定字段，Java 用 `record` 承载同一语义。 |
| `Operator` | `Operator` | `operator_id -> getOperatorId`；`get_tunables -> getTunables`；`set_parameter -> setParameter`；`get_state -> getState`；`load_state -> loadState`；`invoke(inputs, session, **kwargs) -> invoke(inputs, session, kwargs)`；`stream(inputs, session, **kwargs) -> stream(inputs, session, kwargs)`；`_set_operator_context -> setOperatorContext` | 适配映射 | Java 额外提供无 `kwargs` 的重载 `invoke(inputs, session)`、`stream(inputs, session)`。 |
| `AsyncIterator[Any]`（由 `Operator.stream()` 返回） | `OperatorStream<T>` | `async for chunk in operator.stream(...) -> while (stream.hasNext()) { stream.next(); }`；提前停止时 Python generator close -> Java `close()` | 适配映射 | `OperatorStream` 是 Java 对 Python 异步流接口的显式承接。 |

## 2. llm_call

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `LLMCallOperator` | `LLMCallOperator` | `__init__(model_name, llm, system_prompt, user_prompt, freeze_system_prompt=False, freeze_user_prompt=True, llm_call_id="llm_call", on_parameter_updated=None) -> LLMCallOperator(...)`；`operator_id -> getOperatorId`；`get_tunables -> getTunables`；`set_parameter -> setParameter`；`get_state -> getState`；`load_state -> loadState`；`invoke -> invoke`；`stream -> stream`；`get_system_prompt -> getSystemPrompt`；`get_user_prompt -> getUserPrompt`；`update_system_prompt -> updateSystemPrompt`；`update_user_prompt -> updateUserPrompt`；`set_freeze_system_prompt -> setFreezeSystemPrompt`；`set_freeze_user_prompt -> setFreezeUserPrompt`；`get_freeze_system_prompt -> getFreezeSystemPrompt`；`get_freeze_user_prompt -> getFreezeUserPrompt` | 适配映射 | `DEFAULT_USER_PROMPT = "{{query}}"` 在两侧都保留；Java 额外把流式返回显式封装为 `OperatorStream<AssistantMessageChunk>`。 |
| `LLMCall`（`LLMCallOperator` 的兼容别名） | `LLMCall extends LLMCallOperator` | Python `LLMCall = LLMCallOperator` -> Java `new LLMCall(...)`；其余公开方法全部继承 `LLMCallOperator` | 适配映射 | Python 是“同一类型别名”，Java 是“兼容子类入口”。 |

## 3. legacy.llm_call

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `legacy.llm_call.LLMCall` | `legacy.llm_call.LLMCall` | `__init__(model_name, llm, system_prompt, user_prompt, freeze_system_prompt=False, freeze_user_prompt=True, llm_call_id="llm_call") -> LLMCall(...)`；`invoke(inputs, session, history=None, tools=None) -> invoke(inputs, session, history, tools)`；`stream(inputs, session, history=None, tools=None) -> stream(inputs, session, history, tools)`；`get_optimizer_callback -> getOptimizerCallback`；`set_optimizer_callback -> setOptimizerCallback`；`get_system_prompt -> getSystemPrompt`；`get_user_prompt -> getUserPrompt`；`update_system_prompt -> updateSystemPrompt`；`update_user_prompt -> updateUserPrompt`；`set_freeze_system_prompt -> setFreezeSystemPrompt`；`set_freeze_user_prompt -> setFreezeUserPrompt`；`get_freeze_system_prompt -> getFreezeSystemPrompt`；`get_freeze_user_prompt -> getFreezeUserPrompt` | 适配映射 | 该旧接口在 Java 中已不再被错误实现为新 operator 的简单别名，而是独立兼容实现。 |
| `Callable` 形式的 legacy optimizer callback | `LegacyOptimizerCallback` | `callback(llm_call_id, inputs, response, session) -> onComplete(llmCallId, inputs, response, session)` | 适配映射 | Java 把旧回调签名收敛为显式函数式接口。 |

## 4. memory_call

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `MemoryCallOperator` | `MemoryCallOperator` | `__init__(memory=None, memory_call_id="memory_call", *, memory_invoke=None) -> MemoryCallOperator(MemoryOperation, String, MemoryInvoker)`；便捷构造 `memory only -> MemoryCallOperator(MemoryOperation)`；`memory_invoke only -> MemoryCallOperator(MemoryInvoker)`；`empty -> MemoryCallOperator()`；`operator_id -> getOperatorId`；`get_tunables -> getTunables`；`set_parameter -> setParameter`；`get_state -> getState`；`load_state -> loadState`；`invoke -> invoke`；`stream -> stream` | 适配映射 | Python 中 `memory` 是 duck-typed 任意对象；Java 侧通过 `MemoryOperation`、`MemoryInvoker` 把调用契约显式化。 |
| `memory` duck-typed object | `MemoryOperation` | `memory.invoke(inputs, **kwargs) -> invoke(inputs, kwargs)`；`memory.stream(inputs, **kwargs) -> stream(inputs, kwargs)` | Java-only | Java 为承接 Python 任意 memory 对象而新增的最小能力接口。 |
| `memory_invoke` 回调 | `MemoryInvoker` | `memory_invoke(inputs) -> invoke(inputs)` | Java-only | 对位 Python 的自定义 memory 调用回调。 |

## 5. tool_call

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `ToolCallOperator` | `ToolCallOperator` | `__init__(tool=None, tool_call_id="tool_call", *, tool_executor=None, tool_registry=None) -> ToolCallOperator(Tool, String, ToolExecutor, ToolRegistry)`；便捷构造 `tool only -> ToolCallOperator(Tool)`；`tool_executor only -> ToolCallOperator(ToolExecutor)`；`tool + tool_registry -> ToolCallOperator(Tool, ToolRegistry)`；`empty -> ToolCallOperator()`；`operator_id -> getOperatorId`；`get_tunables -> getTunables`；`set_parameter -> setParameter`；`get_state -> getState`；`load_state -> loadState`；`invoke -> invoke`；`stream -> stream` | 适配映射 | Router 模式在 Python 返回 `(result, tool_message)` 元组列表，在 Java 收敛为 `ToolExecutionResult` 列表。 |
| `tool_registry` duck-typed object | `ToolRegistry` | `get_tool_defs -> getToolDefs`；`get_tools -> getTools`；`set_tool_description -> setToolDescription` | Java-only | Java 为承接 Python 任意 registry 对象而新增的显式注册表契约。 |
| `tool_executor` 回调 | `ToolExecutor` | `tool_executor(tool_call, session) -> execute(toolCall, session)` | Java-only | Java 用函数式接口承接 Python 的 router-mode 执行器。 |
| `(result, tool_message)` tuple | `ToolExecutionResult` | `tuple[0] -> result()`；`tuple[1] -> toolMessage()` | Java-only | Java 用 `record` 表达 Python router-mode 的二元返回值。 |

## 6. Java-only 桥接 API 总览

| Java API | Python 对位 | 角色 | 说明 |
| --- | --- | --- | --- |
| `OperatorStream<T>` | `AsyncIterator[Any]` | 流式桥接 | 对位 Python `async for` 消费模型，并增加显式 `close()`。 |
| `MemoryOperation` | duck-typed `memory` 对象 | memory 契约 | 把 `invoke/stream` 能力显式化。 |
| `MemoryInvoker` | `memory_invoke` 回调 | memory 回调 | 对位 Python 自定义 memory 调用函数。 |
| `ToolRegistry` | duck-typed `tool_registry` 对象 | tool registry 契约 | 承接 `get_tool_defs/get_tools/set_tool_description`。 |
| `ToolExecutor` | `tool_executor` 回调 | router 执行器 | 对位 Python router-mode 回调。 |
| `ToolExecutionResult` | `(result, tool_message)` | tuple 桥接 | 收敛 Python 元组结果。 |
| `LegacyOptimizerCallback` | legacy optimizer callback | legacy 回调契约 | 为旧版 `LLMCall` 的 callback 提供显式签名。 |

## 7. 不计入本表的内部 helper

- Python 内部 helper:
  - `LLMCallOperator._format_messages`
  - `LLMCallOperator._format_llm_input`
  - `LLMCallOperator._format_passthrough`
- Java 内部 helper:
  - `LLMCallOperator.extractHistory`
  - `LLMCallOperator.formatMessages`
  - 各 operator 内部的 `ContextClosingIterator`

这些方法属于实现细节，不作为“跨语言公开 API”统计口径的一部分。

## 8. 总结

- 若按“类职责 + 公开方法”对照，Python `operator` 主链路在 Java 中已经具备完整映射。
- 若按“调用习惯”对照，最主要的迁移点是:
  - `property -> getter`
  - `async -> 同步 + OperatorStream`
  - duck typing -> 显式接口 / 记录类型
  - `LLMCall` 真别名 -> 兼容子类
