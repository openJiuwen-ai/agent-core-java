# operator 模块 Python / Java API 映射

## 对照范围

- Python: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\operator`
- Java: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\operator`

## 统计口径

- Python 侧统计公开类、包级导出、属性以及非 `_` 开头的方法。
- Java 侧统计 `public` 类、接口、`record` 以及 `public` 方法。
- 内部 helper 方法只在需要解释语义差异时提及，不作为“公开 API 缺漏”统计对象。

## 映射约定

- `snake_case -> camelCase`
- `property -> getter`
- `async invoke/stream -> 同步 invoke + OperatorStream`
- Python duck typing -> Java 显式接口 / record / 函数式接口
- Python 包级别名 / `__all__` -> Java 直接导入具体类型

## 总体结论

- Python `operator` 主链路公开类在 Java 侧已经全部有对应实现，未发现新的“整类缺失”。
- 核心 operator 的主方法均已对齐：`Operator`、`LLMCallOperator`、`MemoryCallOperator`、`ToolCallOperator`、`legacy.llm_call.LLMCall` 均存在。
- Java 为承接 Python 的动态调用方式，额外拆出了若干桥接类型：`OperatorStream`、`MemoryOperation`、`MemoryInvoker`、`ToolRegistry`、`ToolExecutor`、`ToolExecutionResult`、`LegacyOptimizerCallback`。
- 当前残余差异主要集中在语义层，而不是“类或方法没写出来”：流式提前中止的回收时机、`LLMCall` 真别名语义、duck typing 迁移成本、包级导出门面。

## 包级映射

| Python 模块 | Java 包 / 类型 | 状态 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.core.operator` | `com.openjiuwen.core.operator` | 适配映射 | Python 通过 `__all__` 暴露 `Operator`、`TunableSpec`、`LLMCallOperator`、`LLMCall`、`ToolCallOperator`、`MemoryCallOperator`；Java 通过直接导入具体类使用。 |
| `openjiuwen.core.operator.llm_call` | `com.openjiuwen.core.operator.llm_call` | 完整映射 | `LLMCallOperator` 和兼容入口 `LLMCall` 均存在。 |
| `openjiuwen.core.operator.memory_call` | `com.openjiuwen.core.operator.memory_call` | 适配映射 | `MemoryCallOperator` 对齐；Java 额外引入 `MemoryOperation`、`MemoryInvoker` 承接动态调用能力。 |
| `openjiuwen.core.operator.tool_call` | `com.openjiuwen.core.operator.tool_call` | 适配映射 | `ToolCallOperator` 对齐；Java 额外引入 `ToolRegistry`、`ToolExecutor`、`ToolExecutionResult`。 |
| `openjiuwen.core.operator.legacy.llm_call` | `com.openjiuwen.core.operator.legacy.llm_call` | 适配映射 | 旧版 `LLMCall` 已有独立兼容实现，且提供 `LegacyOptimizerCallback`。 |

## 1. base

### 1.1 `TunableSpec`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `TunableSpec.__init__(name, kind, path, constraint=None)` | `new TunableSpec(name, kind, path, constraint)` / `new TunableSpec(name, kind, path)` | 构造参数 1:1 对齐 | 完整映射 | Python 用普通类 + `__slots__`，Java 用 `record` 承载同一语义。 |
| `name` | `name()` | 属性 -> record accessor | 完整映射 | - |
| `kind` | `kind()` | 属性 -> record accessor | 完整映射 | - |
| `path` | `path()` | 属性 -> record accessor | 完整映射 | - |
| `constraint` | `constraint()` | 属性 -> record accessor | 完整映射 | - |

补充说明:

- Python 中 `TunableKind = str` 只是类型别名，Java 没有单独定义对应类型，直接继续使用 `String`。

### 1.2 `Operator`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `operator_id` | `getOperatorId()` | property -> getter | 适配映射 | 命名风格差异。 |
| `get_tunables()` | `getTunables()` | `snake_case -> camelCase` | 完整映射 | - |
| `set_parameter(target, value)` | `setParameter(target, value)` | `snake_case -> camelCase` | 完整映射 | - |
| `get_state()` | `getState()` | `snake_case -> camelCase` | 完整映射 | - |
| `load_state(state)` | `loadState(state)` | `snake_case -> camelCase` | 完整映射 | - |
| `async invoke(inputs, session, **kwargs)` | `invoke(inputs, session, kwargs)` | 异步 -> 同步 | 适配映射 | Java 另有无 `kwargs` 的便捷重载。 |
| `async stream(inputs, session, **kwargs)` | `stream(inputs, session, kwargs)` | 异步流 -> `OperatorStream` | 适配映射 | Java 另有无 `kwargs` 的便捷重载。 |
| `_set_operator_context(session, context_id=None)` | `setOperatorContext(session, operatorId)` | helper 命名适配 | 适配映射 | Python 用 `hasattr` 容忍 Session 能力缺失；Java 通过 `Session#setCurrentOperatorId(...)` 直接承接。 |

### 1.3 `OperatorStream`（Java bridge）

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `AsyncIterator[Any]` | `OperatorStream<T>` | `async for` -> `hasNext()/next()/close()` | Java-only | 这是 Java 为显式承接流式消费与提前关闭语义而新增的桥接接口。 |

## 2. llm_call

### 2.1 `LLMCallOperator`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `LLMCallOperator.__init__(model_name, llm, system_prompt, user_prompt, freeze_system_prompt=False, freeze_user_prompt=True, llm_call_id="llm_call", on_parameter_updated=None)` | `new LLMCallOperator(modelName, llm, systemPrompt, userPrompt, freezeSystemPrompt, freezeUserPrompt, llmCallId, onParameterUpdated)` | 参数语义 1:1 | 完整映射 | Java 另外提供 4 参便捷构造器，对应 Python 默认参数。 |
| `operator_id` | `getOperatorId()` | property -> getter | 适配映射 | - |
| `get_tunables()` | `getTunables()` | `snake_case -> camelCase` | 完整映射 | `system_prompt` 与 `user_prompt` 的 freeze 逻辑一致。 |
| `set_parameter()` | `setParameter()` | `snake_case -> camelCase` | 完整映射 | 两侧都只更新未冻结的 prompt。 |
| `get_state()` | `getState()` | `snake_case -> camelCase` | 完整映射 | 两侧都保存 `system_prompt` / `user_prompt`。 |
| `load_state()` | `loadState()` | `snake_case -> camelCase` | 完整映射 | 两侧都通过 `updateSystemPrompt` / `updateUserPrompt` 恢复。 |
| `invoke(inputs, session, **kwargs)` | `invoke(inputs, session, kwargs)` | 异步 -> 同步 | 适配映射 | `history`、`tools` 都已映射；Java 还支持将其余 kwargs 继续透传给 `Model`。 |
| `stream(inputs, session, **kwargs)` | `stream(inputs, session, kwargs)` | 异步流 -> `OperatorStream<AssistantMessageChunk>` | 适配映射 | 两侧都支持 `history`、`tools`、`messages` passthrough。 |
| `get_system_prompt()` | `getSystemPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `get_user_prompt()` | `getUserPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `update_system_prompt()` | `updateSystemPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `update_user_prompt()` | `updateUserPrompt()` | `snake_case -> camelCase` | 完整映射 | 空字符串只在构造阶段回退默认 prompt，更新阶段两侧都保持原值。 |
| `set_freeze_system_prompt()` | `setFreezeSystemPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `set_freeze_user_prompt()` | `setFreezeUserPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `get_freeze_system_prompt()` | `getFreezeSystemPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `get_freeze_user_prompt()` | `getFreezeUserPrompt()` | `snake_case -> camelCase` | 完整映射 | - |

补充说明:

- Python `DEFAULT_USER_PROMPT = "{{query}}"` 与 Java `DEFAULT_USER_PROMPT` 已完全对齐。
- Python `stream()` 的类型注解写成 `AsyncIterator[AssistantMessage]`，但实际透传底层 `llm.stream(...)` 返回的 chunk；Java 直接显式成 `OperatorStream<AssistantMessageChunk>`。
- Java `invoke()/stream()` 会把 `history/tools` 之外的其余 kwargs 继续传给 `Model`，这是对 Python 当前实现的增强，不属于“缺漏”，但属于语义差异。

### 2.2 `LLMCall`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `LLMCall = LLMCallOperator` | `class LLMCall extends LLMCallOperator` | 别名 -> 兼容子类 | 适配映射 | 方法面已经对齐，但严格类型身份不再完全相同。 |

## 3. legacy.llm_call

### 3.1 `legacy.llm_call.LLMCall`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `LLMCall.__init__(model_name, llm, system_prompt, user_prompt, freeze_system_prompt=False, freeze_user_prompt=True, llm_call_id="llm_call")` | `new legacy.llm_call.LLMCall(modelName, llm, systemPrompt, userPrompt, freezeSystemPrompt, freezeUserPrompt, llmCallId)` | 参数语义 1:1 | 完整映射 | Java 同样保留 4 参便捷构造器。 |
| `invoke(inputs, session, history=None, tools=None)` | `invoke(inputs, session, history, tools)` | 异步 -> 同步 | 适配映射 | 两侧都保留旧接口风格，不复用新 operator 抽象。 |
| `stream(inputs, session, history=None, tools=None)` | `stream(inputs, session, history, tools)` | 异步流 -> `OperatorStream<AssistantMessageChunk>` | 适配映射 | 两侧都在流结束后触发 optimizer callback。 |
| `get_optimizer_callback()` | `getOptimizerCallback()` | `snake_case -> camelCase` | 完整映射 | - |
| `set_optimizer_callback()` | `setOptimizerCallback()` | `snake_case -> camelCase` | 完整映射 | Java 允许传入 `null`，语义与 Python `Optional[Callable]` 对齐。 |
| `get_system_prompt()` | `getSystemPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `get_user_prompt()` | `getUserPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `update_system_prompt()` | `updateSystemPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `update_user_prompt()` | `updateUserPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `set_freeze_system_prompt()` | `setFreezeSystemPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `set_freeze_user_prompt()` | `setFreezeUserPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `get_freeze_system_prompt()` | `getFreezeSystemPrompt()` | `snake_case -> camelCase` | 完整映射 | - |
| `get_freeze_user_prompt()` | `getFreezeUserPrompt()` | `snake_case -> camelCase` | 完整映射 | - |

### 3.2 `LegacyOptimizerCallback`（Java bridge）

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `Callable(llm_call_id, inputs, response, session)` | `LegacyOptimizerCallback.onComplete(llmCallId, inputs, response, session)` | 动态 callable -> 函数式接口 | Java-only | Java 将旧 callback 签名显式化，便于类型约束与调用。 |

## 4. memory_call

### 4.1 `MemoryCallOperator`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `MemoryCallOperator.__init__(memory=None, memory_call_id="memory_call", *, memory_invoke=None)` | `new MemoryCallOperator(memory, memoryCallId, memoryInvoker)` | 参数语义 1:1 | 适配映射 | Java 另外提供 `memory only`、`memoryInvoker only`、空构造器等便捷入口。 |
| `operator_id` | `getOperatorId()` | property -> getter | 适配映射 | - |
| `get_tunables()` | `getTunables()` | `snake_case -> camelCase` | 完整映射 | `enabled` 与 `max_retries` 均已对齐。 |
| `set_parameter()` | `setParameter()` | `snake_case -> camelCase` | 完整映射 | 两侧都做 `max_retries` 的 `0..5` clamp。 |
| `get_state()` | `getState()` | `snake_case -> camelCase` | 完整映射 | - |
| `load_state()` | `loadState()` | `snake_case -> camelCase` | 完整映射 | - |
| `invoke(inputs, session, **kwargs)` | `invoke(inputs, session, kwargs)` | 异步 -> 同步 | 适配映射 | `memory_invoke` 优先于 `memory.invoke(...)` 的逻辑已对齐。 |
| `stream(inputs, session, **kwargs)` | `stream(inputs, session, kwargs)` | 异步流 -> `OperatorStream<Object>` | 适配映射 | 两侧都只在底层 memory 支持流式时可用。 |

### 4.2 Java bridge types

| Python 语义 | Java API | 状态 | 说明 |
| --- | --- | --- | --- |
| duck-typed `memory` 对象 | `MemoryOperation` | Java-only | 显式承接 `invoke(inputs, kwargs)` 与可选 `stream(inputs, kwargs)`。 |
| `memory_invoke(inputs)` 回调 | `MemoryInvoker` | Java-only | 用函数式接口承接 Python 的自定义 memory callback。 |

## 5. tool_call

### 5.1 `ToolCallOperator`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `ToolCallOperator.__init__(tool=None, tool_call_id="tool_call", *, tool_executor=None, tool_registry=None)` | `new ToolCallOperator(tool, toolCallId, toolExecutor, toolRegistry)` | 参数语义 1:1 | 适配映射 | Java 另外提供 `tool only`、`toolExecutor only`、`tool + registry`、空构造器等便捷入口。 |
| `operator_id` | `getOperatorId()` | property -> getter | 适配映射 | - |
| `get_tunables()` | `getTunables()` | `snake_case -> camelCase` | 完整映射 | 两侧都只在存在 registry 时暴露 `tool_description`。 |
| `set_parameter()` | `setParameter()` | `snake_case -> camelCase` | 完整映射 | 两侧都只处理 `tool_description`。 |
| `get_state()` | `getState()` | `snake_case -> camelCase` | 完整映射 | - |
| `load_state()` | `loadState()` | `snake_case -> camelCase` | 完整映射 | - |
| `invoke(inputs, session, **kwargs)` | `invoke(inputs, session, kwargs)` | 异步 -> 同步 | 适配映射 | router mode 与 direct mode 均已映射。 |
| `stream(inputs, session, **kwargs)` | `stream(inputs, session, kwargs)` | 异步流 -> `OperatorStream<Object>` | 适配映射 | 与 Python 一样依赖底层 tool 的 stream 能力。 |

### 5.2 Java bridge types

| Python 语义 | Java API | 状态 | 说明 |
| --- | --- | --- | --- |
| duck-typed `tool_registry` | `ToolRegistry` | Java-only | 承接 `getToolDefs()`、`getTools()`、`setToolDescription()`。 |
| `tool_executor(tool_call, session)` | `ToolExecutor.execute(toolCall, session)` | Java-only | 承接 router mode 回调。 |
| `(result, tool_message)` tuple | `ToolExecutionResult(result, toolMessage)` | Java-only | 用 `record` 承接 Python 的二元返回值。 |

## 6. 缺漏与残余差异检查

### 6.1 已确认对齐

- 未发现新的整类缺失。
- 未发现核心公开方法缺失。
- 第一轮历史问题中最关键的 4 项已经在当前代码中关闭:
  - `LLMCallOperator.updateUserPrompt("")` 不再错误回退默认 prompt
  - `MemoryCallOperator.stream` 不再依赖额外的 `supportsStream()` 判定
  - `legacy.llm_call.LLMCall` 不再误写成新 operator 的简单别名
  - Java 已引入 `OperatorStream.close()` 与各 operator 的 `ContextClosingIterator`

### 6.2 当前仍需关注的差异

| 优先级 | 位置 | Python 行为 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | 流式提前中止清理 | `async for` 提前退出时更容易触发 generator 关闭与上下文回收 | Java 只有“读完全部 chunk”或“显式调用 `close()`”时才会触发 `ContextClosingIterator` 清理 | 若调用方中途停止遍历且忘记 `close()`，`Session.currentOperatorId` 可能短暂残留。 |
| `P1` | duck typing 迁移能力 | `memory`、`tool`、`tool_registry`、callback 可以直接接收“只要有同名方法”的对象或 callable | Java 必须实现 `MemoryOperation`、`MemoryInvoker`、`ToolRegistry`、`ToolExecutor`、`LegacyOptimizerCallback` 等显式类型 | 从 Python 风格对象迁移到 Java 时，需要额外写 adapter。 |
| `P2` | `LLMCall` 别名语义 | `LLMCall is LLMCallOperator`，两者是同一类型对象 | `LLMCall extends LLMCallOperator`，是兼容子类 | 若上层依赖严格类型身份、反射类名或 `getClass()`，语义不完全等价。 |
| `P3` | 包级导出门面 | Python 可通过 `openjiuwen.core.operator` 及子包 `__all__` 进行包级导入 | Java 无对应 package facade，只能导入具体类 | 迁移文档和调用示例不能机械照搬 Python 的导入方式。 |

### 6.3 差异但不计入“缺漏”

- Java `LLMCallOperator.invoke()/stream()` 会把 `history/tools` 之外的 kwargs 继续透传给 `Model`；Python 当前实现没有这一层透传。这是 Java 的增强，不是缺漏。
- Java 将异步接口统一收敛为同步调用 + `OperatorStream`，属于语言运行时差异，不单列为缺漏。
- Java 通过 `record`、函数式接口和最小契约接口承接 Python 动态能力，属于实现方式差异。

## 7. 结论

- 若按“类职责 + 公开方法”对照，Java `operator` 模块已经完成对 Python 版的主 API 映射。
- 当前剩余问题更多是兼容层细节，而不是主链路 API 缺失。
- 后续若还要继续追平 Python 运行语义，建议优先处理两项:
  1. 为 `OperatorStream` 的提前放弃消费场景补足更强的自动清理约束。
  2. 视迁移成本决定是否增加更轻量的 facade / adapter，降低 Python 风格对象接入 Java operator 的门槛。
