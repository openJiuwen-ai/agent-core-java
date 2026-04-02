# com.openjiuwen.core.operator.llm_call.LLMCallOperator

## class LLMCallOperator

```java
public class LLMCallOperator extends Operator
```

`LLMCallOperator` 是新版 LLM 调用算子实现，负责格式化系统/用户 prompt、拼装历史消息、转发工具定义，并在调用期间维护当前 operator context。

## 构造方法

### `public LLMCallOperator(String modelName, Model llm, Object systemPrompt, Object userPrompt, boolean freezeSystemPrompt, boolean freezeUserPrompt, String llmCallId, BiConsumer<String, Object> onParameterUpdated)`

创建可调 prompt 的 LLM 调用算子。

**说明**

- `systemPrompt` 和 `userPrompt` 都会包装成 `PromptTemplate`。
- `userPrompt` 为 `null` 或空字符串时会回退到默认模板 `{{query}}`。
- `llmCallId` 为 `null` 时会回退到 `llm_call`。
- `onParameterUpdated` 会在 `setParameter()` 成功更新 prompt 时收到 `(target, value)` 回调。

### `public LLMCallOperator(String modelName, Model llm, Object systemPrompt, Object userPrompt)`

使用默认参数创建实例：`freezeSystemPrompt = false`、`freezeUserPrompt = true`、`llmCallId = "llm_call"`、`onParameterUpdated = null`。

## 可调参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `system_prompt` | `prompt` | 仅在 `freezeSystemPrompt = false` 时暴露，对应系统提示模板。 |
| `user_prompt` | `prompt` | 仅在 `freezeUserPrompt = false` 时暴露，对应用户提示模板。 |

## 状态快照

| 字段 | 类型 | 说明 |
|---|---|---|
| `system_prompt` | `Object` | 当前系统 prompt 的原始内容。 |
| `user_prompt` | `Object` | 当前用户 prompt 的原始内容。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getOperatorId()` | `String` | 返回当前算子 ID。 |
| `setParameter(String target, Object value)` | `void` | 规范化 prompt 内容，并在未冻结时更新对应 prompt；未知参数会被忽略。 |
| `loadState(Map<String, Object> state)` | `void` | 从状态快照恢复 `system_prompt` / `user_prompt`。 |
| `invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `AssistantMessage` | 按 `system + history + user` 或 `system + messages` 规则组装消息，透传 `tools` 与其他 `kwargs` 到 `Model.invoke(...)`。 |
| `stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `OperatorStream<AssistantMessageChunk>` | 与 `invoke()` 使用同一套消息格式化规则，并把底层迭代器包装成自动清理上下文的 `OperatorStream`。 |
| `getSystemPrompt()` / `getUserPrompt()` | `PromptTemplate` | 读取当前 prompt 模板。 |
| `updateSystemPrompt(Object value)` / `updateUserPrompt(Object value)` | `void` | 在未冻结时直接替换 prompt 模板。 |
| `setFreezeSystemPrompt(boolean)` / `setFreezeUserPrompt(boolean)` | `void` | 动态切换 prompt 冻结状态。 |
| `getFreezeSystemPrompt()` / `getFreezeUserPrompt()` | `boolean` | 返回当前冻结状态。 |

## 说明

- 当 `inputs` 中存在 `messages` 且其值为 `List<?>` 时，算子会直接透传该消息列表，只在前面补上格式化后的系统消息。
- `kwargs` 中的 `history` 需要是 `List<BaseMessage>`，`tools` 则会作为独立参数传给底层 `Model`；其余键值会原样透传。
- `LLMCallOperatorTest` 覆盖了 history 注入、passthrough messages、prompt 回调和流提前关闭后的上下文清理。
- 构造阶段会把空 `userPrompt` 归一化为 `{{query}}`，但后续 `updateUserPrompt("")` 允许显式写入空字符串。
