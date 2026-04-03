# com.openjiuwen.core.operator.legacy.llm_call.LLMCall

## class LLMCall

```java
public class LLMCall
```

`LLMCall` 是旧版兼容路径上的 LLM 调用包装器，负责按 `system + history + user` 顺序格式化消息，并在调用结束后触发旧版 optimizer callback。

## 构造方法

### `public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt, boolean freezeSystemPrompt, boolean freezeUserPrompt, String llmCallId)`

创建兼容层 LLM 调用器。

**说明**

- `userPrompt` 为 `null` 或空字符串时会回退到默认模板 `{{query}}`。
- `llmCallId` 为 `null` 时会回退为 `llm_call`。
- `freezeSystemPrompt` 和 `freezeUserPrompt` 控制后续 `update*Prompt()` 是否生效。

### `public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt)`

使用默认参数创建实例：`freezeSystemPrompt = false`、`freezeUserPrompt = true`、`llmCallId = "llm_call"`。

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `invoke(Map<String, Object> inputs, Session session, List<BaseMessage> history, Object tools)` | `AssistantMessage` | 格式化 prompt 后调用 `Model.invoke(...)`；若已设置 `LegacyOptimizerCallback`，会在收到完整响应后回调。 |
| `invoke(Map<String, Object> inputs, Session session)` | `AssistantMessage` | 不传 `history/tools` 的便捷重载。 |
| `stream(Map<String, Object> inputs, Session session, List<BaseMessage> history, Object tools)` | `OperatorStream<AssistantMessageChunk>` | 调用 `Model.stream(...)`，并在流关闭或自然结束时把全部 chunk 聚合为字符串再触发回调。 |
| `stream(Map<String, Object> inputs, Session session)` | `OperatorStream<AssistantMessageChunk>` | 不传 `history/tools` 的流式便捷重载。 |
| `getOptimizerCallback()` / `setOptimizerCallback(...)` | `LegacyOptimizerCallback` / `void` | 读取或设置旧版完成回调。 |
| `getSystemPrompt()` / `getUserPrompt()` | `PromptTemplate` | 返回当前系统/用户 prompt 模板对象。 |
| `updateSystemPrompt(Object systemPrompt)` / `updateUserPrompt(Object userPrompt)` | `void` | 在对应 freeze 开关关闭时替换 prompt 模板。 |
| `setFreezeSystemPrompt(boolean)` / `setFreezeUserPrompt(boolean)` | `void` | 切换系统/用户 prompt 的冻结状态。 |
| `getFreezeSystemPrompt()` / `getFreezeUserPrompt()` | `boolean` | 读取当前冻结状态。 |

## 说明

- 该兼容类不继承 `Operator`，因此不会像新版 `LLMCallOperator` 那样写入 `Session` 的当前算子 ID。
- `LegacyLLMCallTest` 验证了回调能收到完整 `AssistantMessage`，以及流式场景下拼接后的字符串结果。
- 流关闭时若回调抛出异常，内部会直接忽略，以免影响兼容路径上的消费方。
