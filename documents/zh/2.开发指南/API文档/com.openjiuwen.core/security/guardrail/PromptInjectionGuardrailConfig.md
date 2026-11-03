# com.openjiuwen.core.security.guardrail.PromptInjectionGuardrailConfig

## class PromptInjectionGuardrailConfig

```java
public class PromptInjectionGuardrailConfig
```

`PromptInjectionGuardrailConfig` 是 `PromptInjectionGuardrail` 的配置对象，对应 Python 内置护栏配置。

## Fields

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `mode` | `"rules"` | 检测模式：`rules`、`api`、`local`。 |
| `modelType` | `null` | 模型输出类型：`bert` 或 `qwen`。 |
| `apiUrl` | `null` | API 模式的远端检测地址。 |
| `apiKey` | `null` | API 模式的 Bearer token。 |
| `timeout` | `30.0d` | API 请求超时时间，单位秒。 |
| `modelPath` | `null` | local 模式的模型路径。 |
| `device` | `"auto"` | local 模式设备标识。 |
| `customPatterns` | `null` | rules 模式的自定义正则规则。 |
| `riskLevel` | `RiskLevel.HIGH` | rules 模式命中后的风险等级。 |
| `bertThresholds` | `null` | BERT parser 使用的阈值配置。 |
| `attackClassId` | `1` | BERT parser 的攻击类别 ID。 |
| `qwenRiskType` | `"content_risk"` | Qwen parser 输出风险类型。 |
| `parser` | `null` | 自定义模型输出解析器。 |

所有字段都通过标准 getter/setter 访问。

## 约束

- `api` 模式必须设置 `apiUrl`。
- `local` 模式必须设置 `modelPath`。
- `api` / `local` 模式必须设置 `modelType` 或 `parser`。
- `modelType` 只能是 `bert` 或 `qwen`。
