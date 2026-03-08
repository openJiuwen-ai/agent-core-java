# operator 模块转译缺陷修复清单

## 1. 检查范围

对照 Python 版 `openjiuwen.core.operator` 与 Java 版 `com.openjiuwen.core.operator`，重点检查：

- `base`
- `llm_call`
- `memory_call`
- `tool_call`
- `legacy.llm_call`

---

## 2. 待修复问题

### 2.1 【中】`LLMCallOperator.updateUserPrompt` 对空字符串处理与 Python 不一致

**Python 版**

- 构造函数中 `user_prompt or DEFAULT_USER_PROMPT`
- `update_user_prompt()` 直接保留传入值，即使是空字符串也不会自动回退默认 prompt

**Java 版**

- `updateUserPrompt()` 复用了 `resolveUserPrompt()`
- 当传入空字符串时会被强制改回 `{{query}}`

**影响**

- `setParameter("user_prompt", "")` 和 `loadState()` 无法忠实恢复“显式空 prompt”状态。

---

### 2.2 【中】`MemoryCallOperator.stream` 对流式能力的判定比 Python 更严格

**Python 版**

- 只要 memory 对象具备 `stream` 方法，就允许进入流式执行

**Java 版**

- 除了实现 `stream()` 外，还要求 `supportsStream()` 必须返回 `true`

**影响**

- Java 侧实现如果只覆写了 `stream()` 而忘记同步覆写 `supportsStream()`，会被错误判定为“不支持流式”。

---

### 2.3 【高】`legacy.llm_call.LLMCall` 被错误实现为 operator 别名

**Python 版**

- `legacy.llm_call.LLMCall` 是独立旧接口
- 具备 `optimizer_callback`
- 不继承新 operator 抽象
- `invoke/stream` 行为与新 `LLMCallOperator` 不完全相同

**Java 版**

- 当前直接继承 `com.openjiuwen.core.operator.llm_call.LLMCall`
- 实际变成了新 operator 的路径别名

**影响**

- 旧路径调用方拿到的是错误的行为模型，缺少 `optimizer_callback` 语义，且会引入 operator 上下文副作用。

---

### 2.4 【中】operator 的流式返回缺少显式关闭协议

**Python 版**

- `async for` 结束或取消时更容易触发清理逻辑

**Java 版**

- 当前返回裸 `Iterator`
- 只有“正常读完”才会自动清理 session 中的当前 operator id

**影响**

- 调用方如果提前停止消费 stream，operator context 可能残留在 session 中。

---

## 3. 修复顺序

1. 修正 `LLMCallOperator` 的空 user prompt 语义
2. 放宽 `MemoryCallOperator.stream` 的能力判定
3. 重新实现 `legacy.llm_call.LLMCall`
4. 为 operator stream 增加显式 close 协议，并补单测
