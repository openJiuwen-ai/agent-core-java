# com.openjiuwen.core.security.guardrail.GuardrailResult

## class GuardrailResult

```java
public class GuardrailResult
```

`GuardrailResult` 是 guardrail 对单次事件做出的最终判定结果。该类型使用 Lombok `@Value` 和 `@Builder` 声明，实例创建后不可变。

## 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `isSafe` | `boolean` | 是否通过安全检查。 |
| `riskLevel` | `RiskLevel` | 风险级别；安全结果通常使用 `RiskLevel.SAFE`。 |
| `riskType` | `String` | 风险类型标识；未命中风险时可为 `null`。 |
| `details` | `Map<String, Object>` | 额外上下文或命中详情。 |
| `modifiedData` | `Map<String, Object>` | 后端产出的替代数据或脱敏结果；默认实现可不提供。 |

## 静态工厂

| 方法 | 返回 | 说明 |
|---|---|---|
| `pass(Map<String, Object> details)` | `GuardrailResult` | 构造通过结果，固定 `isSafe = true`、`riskLevel = RiskLevel.SAFE`。 |
| `pass()` | `GuardrailResult` | `pass(null)` 的便捷重载。 |
| `block(RiskLevel riskLevel, String riskType, Map<String, Object> details, Map<String, Object> modifiedData)` | `GuardrailResult` | 构造阻断结果，固定 `isSafe = false` 并保留风险元数据。 |

## 说明

- Lombok 会生成只读访问器和 `builder()`；文档只列出源码中显式声明的字段和工厂方法。
- `BaseGuardrail.detect()` 的默认实现只会在阻断结果中写入 `riskLevel`、`riskType`、`details`，不会填充 `modifiedData`。
