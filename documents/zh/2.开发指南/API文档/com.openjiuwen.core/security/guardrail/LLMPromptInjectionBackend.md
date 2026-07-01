# com.openjiuwen.core.security.guardrail.LLMPromptInjectionBackend

## class LLMPromptInjectionBackend

```java
public class LLMPromptInjectionBackend extends GuardrailBackend
```

`LLMPromptInjectionBackend` 表达“用大模型判断提示词注入”的后端形态，对应 Python 文档中的 LLM 检测后端。Java 0.1.14 当前尚未接入具体 LLM runtime，实际分析会走规则后端回退。

## Constructors

| 构造函数 | 说明 |
| --- | --- |
| `LLMPromptInjectionBackend(LLMPromptInjectionBackendConfig config)` | 使用配置中的 system prompt；未配置时使用默认检测 prompt。 |

## Methods

### `RiskAssessment analyze(GuardrailContext ctx)`

- 上下文文本为空时返回安全结果。
- 拼接 system prompt 和待检测文本。
- 当前通过 `RuleBasedPromptInjectionBackend` 执行回退分析。

### `getSystemPrompt()`

返回当前使用的 system prompt。

## 当前边界

该类保留了与 Python 文档一致的 LLM 检测形态，但 Java 当前版本没有真正调用模型；需要应用侧接入具体 LLM 调用能力后才能作为模型检测后端使用。
