# com.openjiuwen.core.session.tracer.InvokeType

## 枚举 InvokeType

```java
public enum InvokeType
```

InvokeType 定义 agent trace 中使用的调用类型字符串。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回该枚举对应的字符串值。 |

## 枚举值

| 枚举值 | 说明 |
| --- | --- |
| `PROMPT` | prompt 调用。 |
| `LLM` | LLM 调用。 |
| `PLUGIN` | plugin / tool 调用。 |
| `WORKFLOW` | workflow 调用。 |
| `CHAIN` | chain 调用。 |
| `RETRIEVER` | retrieval 调用。 |
| `EVALUATOR` | evaluator 调用。 |

## 说明

- 相关测试：`TracerTest`。
- `getValue()` 返回每个枚举常量在源码中定义的字符串字面量。
