# operator模块转译报告

## 1. 任务背景

Python 版 `openjiuwen.core.operator` 是完整模块，包含：

- `base`
- `llm_call`
- `memory_call`
- `tool_call`
- `legacy.llm_call`

Java 版在本轮开始前没有对应的 `com.openjiuwen.core.operator` 包，既缺核心抽象，也缺三类 operator 的实现与测试承载。

## 2. 本次完成的转译范围

本次已在 Java 版新增并补齐以下内容：

- `com.openjiuwen.core.operator.TunableSpec`
- `com.openjiuwen.core.operator.Operator`
- `com.openjiuwen.core.operator.llm_call.LLMCallOperator`
- `com.openjiuwen.core.operator.llm_call.LLMCall`
- `com.openjiuwen.core.operator.memory_call.MemoryOperation`
- `com.openjiuwen.core.operator.memory_call.MemoryInvoker`
- `com.openjiuwen.core.operator.memory_call.MemoryCallOperator`
- `com.openjiuwen.core.operator.tool_call.ToolRegistry`
- `com.openjiuwen.core.operator.tool_call.ToolExecutor`
- `com.openjiuwen.core.operator.tool_call.ToolExecutionResult`
- `com.openjiuwen.core.operator.tool_call.ToolCallOperator`
- `com.openjiuwen.core.operator.legacy.llm_call.LLMCall`

同时补齐了 operator 运行所需的 session 能力：

- `Session#setCurrentOperatorId`
- `Session#getCurrentOperatorId`
- `BaseSession` 中的 `currentOperatorId` 实际存储

## 3. 对照Python后的核心转译结果

### 3.1 base.py

已转译为 Java 抽象基类 `Operator` 与不可变记录 `TunableSpec`，保留了以下语义：

- operator 唯一标识
- tunable 描述
- 参数更新
- state 快照 / 恢复
- `invoke`
- 默认 `stream not implemented`
- session 上的当前 operator 上下文设置

其中 `TunableSpec` 使用 Java `record` 实现，等价承载 Python `__slots__` 的“固定字段”语义。

### 3.2 llm_call/base.py

已转译并打通：

- `system_prompt` / `user_prompt` tunable
- freeze 开关
- `get_state` / `load_state`
- `invoke`
- `stream`
- `messages` passthrough 模式
- `history` 注入
- `tools` 透传
- 参数更新回调
- `LLMCall` 兼容别名

同时补了 `legacy.llm_call.LLMCall` 的旧路径别名，避免模块路径级别缺口。

### 3.3 memory_call/base.py

已转译并打通：

- `enabled` / `max_retries` state 与 tunable
- callback 优先于 memory 实例调用
- `invoke` 重试
- `stream`
- session operator 上下文清理

由于 Java 没有 Python 那种对任意对象直接 `await obj.invoke(...)` 的鸭子类型能力，本次新增了最小依赖接口：

- `MemoryOperation`
- `MemoryInvoker`

这不是占位实现，而是 operator 模块对 memory 依赖的最小真实契约。

### 3.4 tool_call/base.py

已转译并打通：

- `tool_description` tunable
- registry 描述更新
- router mode
- direct mode
- direct mode 异常重试
- `stream`
- session operator 上下文清理

同样补了 Java 所需的最小真实依赖接口：

- `ToolRegistry`
- `ToolExecutor`
- `ToolExecutionResult`

## 4. 本次补齐的依赖与设计取舍

### 4.1 Session缺少operator上下文

Python 版 `_set_operator_context` 依赖 `session.set_current_operator_id(...)`。

Java 版原有 `Session` 只有最小状态接口，没有 operator 上下文能力。若不补这层，operator 模块只能退化成无上下文执行，无法和 Python 语义对齐。

本次处理：

- 在 `Session` 中新增默认方法
- 在 `BaseSession` 中增加真实字段保存当前 operator id

这样既兼容现有实现，又让 operator 的 tracing attribution 有实际落点。

### 4.2 Python异步接口到Java同步接口

Python operator 使用 `async/await` 和 `AsyncIterator`。

Java 工程当前整体是同步风格，因此本次统一转成：

- `invoke` 返回同步结果
- `stream` 返回 `Iterator<?>`

转译时保留了最重要的语义：

- 调用前设置 operator context
- 调用结束后清理 operator context
- `stream` 完整消费后自动清理上下文

### 4.3 Python鸭子类型到Java显式契约

Python 可以接受任意具有 `invoke/stream` 能力的对象。

Java 不能直接复制这种能力，因此本次没有用反射假装“支持任意对象”，而是明确抽出最小契约接口，使模块可编译、可测试、可复用。

## 5. 本次实际修复的问题

### 5.1 Java侧operator模块整体缺失

这是本次最核心的问题，现已补齐核心主链路与兼容别名。

### 5.2 Session缺少current operator id承载

若不补这一层，LLM/Memory/Tool operator 的上下文设置逻辑无法真正落地。现已修复。

### 5.3 legacy导入路径缺口

Python 侧存在 `operator.legacy.llm_call.LLMCall`。本次新增 Java 兼容别名，避免旧路径不可用。

## 6. 当前与Python相比仍有差异的部分

### 6.1 stream的“提前中断清理”

Python `async for` 在生成器正常结束或被取消时更容易回收上下文。

Java 这里采用 `Iterator`，因此当前实现保证：

- 正常消费完全部 chunk 后会清理上下文
- 调用异常时会清理上下文

但如果调用方拿到 `Iterator` 后提前放弃遍历，`Iterator` 本身没有统一 close 协议，这一类“半路中断”的清理无法像 Python 一样天然保证。当前 UT 未覆盖该场景。

### 6.2 ToolCallOperator对enabled/max_retries的外部调参入口

与 Python 一样，`ToolCallOperator` 只把 `tool_description` 暴露为 tunable；`enabled/max_retries` 保留在 state 中，不作为 tunable 暴露。这是按 Python 行为保持一致，不是漏转。

## 7. 结论

本次已经把 Java 版 operator 从“模块不存在”推进到“核心结构齐全、三类 operator 可执行、兼容入口可用、UT 可回归”的状态。

当前 Java 版已具备：

- operator 抽象层
- LLM operator
- memory operator
- tool operator
- legacy LLMCall 兼容别名
- 与 session 的 operator context 对接

后续如继续追平 Python，建议优先补：

1. 更强的 stream 关闭协议
2. 与上层 agent / optimizer 模块的实际接线
3. 若未来出现真实 memory 模块，再把 `MemoryOperation` 收敛到公共基础层
