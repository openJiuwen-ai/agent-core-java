# com.openjiuwen.core.security.guardrail.PromptInjectionGuardrail

## class PromptInjectionGuardrail

```java
public class PromptInjectionGuardrail extends BaseGuardrail
```

`PromptInjectionGuardrail` 是 Java 0.1.14 当前已经落地的内置提示词注入护栏，对应 Python 的 `PromptInjectionGuardrail`。它默认监听 LLM 输入和工具输出事件，并把待检测文本交给配置生成的后端分析。

## 默认事件

```java
public static final List<Object> DEFAULT_EVENTS = List.of(
        LLMCallEvents.LLM_INVOKE_INPUT,
        ToolCallEvents.TOOL_INVOKE_OUTPUT
);
```

- LLM 输入事件会从 `messages` 中提取最后一条消息的 `content`。
- 工具输出事件会从 `result` 字段提取文本。
- 其他自定义事件会作为 `RAW` 上下文传给后端。

## Constructors

| 构造函数 | 说明 |
| --- | --- |
| `PromptInjectionGuardrail()` | 使用默认配置和规则后端。 |
| `PromptInjectionGuardrail(PromptInjectionGuardrailConfig config)` | 按配置创建后端。 |
| `PromptInjectionGuardrail(PromptInjectionGuardrailConfig config, boolean enableLogging)` | 按配置创建后端，并指定日志开关。 |
| `PromptInjectionGuardrail(GuardrailBackend backend, boolean enableLogging)` | 直接使用自定义后端。 |
| `PromptInjectionGuardrail(List<?> events, GuardrailBackend backend, boolean enableLogging)` | 指定事件列表和自定义后端。 |

## Static Methods

### `buildBackendFromConfig(PromptInjectionGuardrailConfig config)`

根据配置构造后端：

- `mode = "rules"`: 返回 `RuleBasedPromptInjectionBackend`。
- `mode = "api"`: 返回 `APIModelBackend`，要求 `apiUrl`，并要求 `modelType` 或 `parser`。
- `mode = "local"`: 返回 `LocalModelBackend`，要求 `modelPath`，并要求 `modelType` 或 `parser`。

`modelType` 只接受 `bert` 或 `qwen`。未指定 parser 时，`bert` 使用 `BertBinaryParser`，`qwen` 使用 `QwenGuardParser`。

## 使用示例

```java
PromptInjectionGuardrail guardrail = new PromptInjectionGuardrail();
guardrail.register(Runner.getCallbackFramework());
```

使用自定义规则：

```java
PromptInjectionGuardrailConfig config = new PromptInjectionGuardrailConfig();
config.setMode("rules");
config.setCustomPatterns(List.of("ignore.*previous.*instructions"));
config.setRiskLevel(RiskLevel.HIGH);

PromptInjectionGuardrail guardrail = new PromptInjectionGuardrail(config);
```
