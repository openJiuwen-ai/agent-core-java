# 流式输出规格

> 适用范围：`agent-core-java` 的 stream 路径（`DeepAgent.stream` / `ReActAgent.invokeForStream`）。本文档定义 stream 中所有 `OutputSchema` chunk 的 type、payload 结构与字段语义，供下游消费者（前端 / SSE 网关 / 测试）统一识别。

---

## 1. 顶层结构

每个流式 chunk 都是一个 `OutputSchema` 对象，三个固定字段：

```json
{
  "type": "<事件类型>",
  "index": <int>,
  "payload": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `string` | 事件类型，决定 `payload` 的结构（见第 2 节） |
| `index` | `int` | 同类型事件的序号，从 0 起；下游按 `(type, index)` 保序重组 |
| `payload` | `object` | 类型专属载荷 |

---

## 2. 事件类型总览

### 2.1 任务生命周期

#### `task_output`（任务启动）

**来源**：`TaskScheduler.executeTask`（任务真正调用前）

```json
{
  "type": "task_output",
  "index": 0,
  "payload": {
    "task_id": "deep_agent_task_<sessionId>_<handlerRound>",
    "task_type": "deep_agent_task",
    "description": "搜索:Topic-A"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `task_id` | `string` | 任务唯一标识，命名规则 `deep_agent_task_<sessionId>_<handlerRound>`，源于 `DeepAgent.executeCoreLoopRound` |
| `task_type` | `string` | 任务类型标签（如 `deep_agent_task`） |
| `description` | `string` | 任务描述（截断 120 字符 + `...`） |

> 每个任务只发一次，出现在该任务所有 `tool_output` / `llm_output` 之前。同一会话连续派生多个任务时，`task_id` 的 `<handlerRound>` 部分递增。

---

### 2.2 LLM 输出

#### `llm_output`（LLM 内容 / 工具调用决策）

**来源**：`ReActAgent.writeAssistantStreamChunk`

```json
// 形态 A：文本内容
{
  "type": "llm_output",
  "index": 0,
  "payload": {
    "task_id": "deep_agent_task_<sessionId>_<handlerRound>",
    "content": "根据搜索结果，",
    "result_type": "answer"
  }
}

// 形态 B：工具调用决策
{
  "type": "llm_output",
  "index": 0,
  "payload": {
    "task_id": "deep_agent_task_<sessionId>_<handlerRound>",
    "tool_calls": [
      {
        "id": "call_1_703f5cfd",
        "type": "function",
        "name": "web_search",
        "arguments": "{\"query\":\"Topic-A\"}",
        "index": 0
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `task_id` | `string` | 从 `session.state.task_id` 读取；standalone ReActAgent 调用时为空字符串 |
| `content` | `string` | LLM 文本片段（形态 A） |
| `tool_calls` | `array` | LLM 决策的工具调用列表（形态 B） |
| `result_type` | `string` | 仅形态 A 携带，固定 `"answer"` |

#### `llm_reasoning`（LLM 推理过程 / 思维链）

```json
{
  "type": "llm_reasoning",
  "index": 0,
  "payload": {
    "task_id": "...",
    "content": "<思维链内容>",
    "result_type": "answer"
  }
}
```

#### `llm_usage`（LLM token 用量统计）

```json
{
  "type": "llm_usage",
  "index": 0,
  "payload": {
    "task_id": "...",
    "usage_metadata": { ... },
    "result_type": "answer"
  }
}
```

---

### 2.3 工具输出

#### `tool_output`（工具流式片段 / 非流式结果 / 工具错误）

**来源**：`AbilityManager.buildToolOutputChunk`，由 `executeStreamingTool` / `streamSingleToolCall` / `executePreparedToolCallWithStreaming` 发出。**三种场景共用同一 type**，下游统一消费 `payload.content`。

```json
{
  "type": "tool_output",
  "index": 0,
  "payload": {
    "task_id": "deep_agent_task_<sessionId>_<handlerRound>",
    "tool_name": "web_search",
    "tool_call_id": "call_1_703f5cfd",
    "content": "正在搜索 'Topic-A'...\n"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `task_id` | `string` | 从 `session.state.task_id` 读取；无 task 概念时为空字符串 |
| `tool_name` | `string` | 工具名（来自 `ToolCall.name`） |
| `tool_call_id` | `string` | 工具调用 id（来自 `ToolCall.id`），与 `llm_output.tool_calls[].id` 对应 |
| `content` | `any` | 工具返回的片段内容；流式工具逐 chunk 产出，非流式工具一次性产出完整结果，错误场景产出 `"tool error: <msg>"` |

| 场景 | `index` 语义 | `content` 语义 |
|------|--------------|----------------|
| 流式工具 chunk | per-tool chunkIndex，从 0 递增 | 单个片段 |
| 流式工具回退 invoke | 0 | 完整结果 |
| 非流式工具 invoke | 0 | 完整结果 |
| 工具异常 | 0 | `"tool error: <msg>"` |



---

### 2.4 Agent 终结输出

#### `answer`（Agent 最终答案）

**来源**：`ReActAgent.writeStreamAnswer` / `DeepAgent.collectStreamToResult` / `ReActAgentEvolve` / `LlmEventHandler`

ReAct 循环或 Deep 任务循环正常结束后发出，payload 含最终答案。该事件是会话/任务级的终结帧，下游收到后即可结束本轮渲染。

```json
{
  "type": "answer",
  "index": 0,
  "payload": {
    "output": "<最终答案对象>",
    "result_type": "answer"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `output` | `any` | 最终答案内容（通常为 string，也可能是结构化对象） |
| `result_type` | `string` | 固定 `"answer"`，与 `error` 的 `"error"` 区分 |

> DeepAgent 多轮场景下，`index` 对应 round 序号；ReActAgent 单轮场景下 `index=0`。

#### `error`（Agent 执行错误）

**来源**：`ReActAgent.writeStreamError` / `DeepAgent.writeStreamError` / `DeepAgent.collectStreamToResult`（轮次错误）/ `ReActAgentEvolve`

ReAct 循环或 Deep 任务循环异常终止时发出，payload 含错误描述。该事件是会话/任务级的终结帧，下游收到后应停止本轮渲染并展示错误。

```json
{
  "type": "error",
  "index": 0,
  "payload": {
    "output": "<错误消息>",
    "result_type": "error"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `output` | `string` | 错误描述；`Throwable.getMessage()` 为 null 时回退到异常类名 |
| `result_type` | `string` | 固定 `"error"` |

> DeepAgent 轮次错误场景下，`index` 对应出错轮次序号（`rounds.size()`）。

> **answer 与 error 的对称性**：两者 payload 结构相同（`output` + `result_type`），`result_type` 区分正常终结与异常终结。下游可用同一渲染逻辑处理 `output`，仅在 `result_type=error` 时切换错误样式。

---

## 3. task_id 的传播链路

```
DeepAgent.executeCoreLoopRound L1643
  生成 "deep_agent_task_<sessionId>_<handlerRound>"
  ↓ 写入 InputEvent.metadata.task_id
TaskLoopEventHandler.handleInput L132
  读取 metadata.task_id（fallback UUID）
  ↓ new Task(sessionId, taskId, ...) → taskManager.addTask
TaskScheduler.executeTask L246
  task.getTaskId()
  ↓ 透传给 executor.executeAbility(taskId, session)
CoreTaskLoopEventExecutor.buildEffectiveInputs L236
  effective.put("task_id", taskId)
  ↓ 透传给 DeepAgent.invokeInnerRoundStreaming(effectiveInputs, ...)
DeepAgent.invokeInnerRoundStreaming L1756
  从 effectiveInputs.get("task_id") 读取（不重新生成）
  ↓ innerSession.updateState(Map.of("task_id", taskId))
AbilityManager.resolveTaskId / ReActAgent.writeAssistantStreamChunk
  从 session.getState("task_id") 读取
  ↓ 写入 tool_output / llm_output payload.task_id
```

**关键约束**：
- `invokeInnerRoundStreaming` **不重新生成** task_id，复用上游注入的值
- `copySessionState` 前后各注入一次，确保 inner session state 不被外层覆盖
- standalone ReActAgent 调用（不经 DeepAgent）时 task_id 为空字符串，字段始终存在

---

## 5. 下游消费建议

### 5.1 按 type 分发

```python
for chunk in stream:
    t = chunk["type"]
    if t == "task_output":
        on_task_started(chunk["payload"])
    elif t == "llm_output":
        p = chunk["payload"]
        if "tool_calls" in p:
            on_llm_tool_calls(p["tool_calls"])
        else:
            on_llm_content(p["content"])
    elif t == "tool_output":
        on_tool_output(chunk["payload"])
    elif t == "llm_reasoning":
        on_llm_reasoning(chunk["payload"]["content"])
    elif t == "llm_usage":
        on_llm_usage(chunk["payload"]["usage_metadata"])
```

### 5.2 按 task_id 聚合

同一 `task_id` 下的所有 `tool_output` / `llm_output` 属于同一任务。会话级聚合用 `task_id` 的 `<sessionId>` 部分提取会话标识。

### 5.3 工具结果重组

`tool_output` 的 `index` 是 per-tool chunkIndex。重组完整工具结果时，按 `(tool_call_id, index)` 排序拼接 `content`：

```python
from collections import defaultdict
tool_chunks = defaultdict(list)
for chunk in stream:
    if chunk["type"] == "tool_output":
        p = chunk["payload"]
        tool_chunks[p["tool_call_id"]].append((chunk["index"], p["content"]))

for call_id, chunks in tool_chunks.items():
    chunks.sort(key=lambda x: x[0])
    full_result = "".join(str(c) for _, c in chunks)
```

---

## 6. 变更历史

| 日期 | 变更 |
|------|------|
| 2026-08-12 | 初始版本：定义 `task_output` / `llm_output` / `llm_reasoning` / `llm_usage` / `tool_output` 五种 type；移除 `session_id` 字段；移除 Hermes 风格 replay cursor 字段 |
| 2026-08-13 | 补充 `answer` / `error` 两种 Agent 终结输出 type 规格（payload 含 `output` + `result_type`，结构对称） |
