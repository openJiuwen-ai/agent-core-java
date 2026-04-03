# com.openjiuwen.core.operator.legacy.llm_call.LegacyOptimizerCallback

## interface LegacyOptimizerCallback

```java
public interface LegacyOptimizerCallback
```

`LegacyOptimizerCallback` 是旧版 `LLMCall` 完成回调接口，用于在同步或流式调用结束后观察最终响应。

## 核心方法

### `void onComplete(String llmCallId, Map<String, Object> inputs, Object response, Session session) throws Exception`

在一次旧版 LLM 调用完成后执行回调。

**参数**

- `llmCallId`: 当前兼容调用器的标识，默认值为 `llm_call`。
- `inputs`: 本次调用的原始输入映射。
- `response`: 同步场景下通常是 `AssistantMessage`，流式场景下是拼接后的响应字符串。
- `session`: 调用时传入的 `Session`。
