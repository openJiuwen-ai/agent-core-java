# com.openjiuwen.core.operator.llm_call.LLMCall

## class LLMCall

```java
public class LLMCall extends LLMCallOperator
```

`LLMCall` 是 `LLMCallOperator` 的向后兼容别名类，用旧类名暴露同一套新版算子行为。

## 构造方法

### `public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt, boolean freezeSystemPrompt, boolean freezeUserPrompt, String llmCallId, BiConsumer<String, Object> onParameterUpdated)`

完整构造器，直接透传到父类 `LLMCallOperator`。

### `public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt)`

简化构造器，使用与父类相同的默认值创建算子实例。

## 继承行为

- 继承 `LLMCallOperator` 的全部公开 API，包括 `getTunables()`、`getState()/loadState()`、`invoke()/stream()` 以及 prompt 冻结控制方法。
- 适合已经使用 `LLMCall` 类名、但希望切换到新版 operator 语义的调用方。
