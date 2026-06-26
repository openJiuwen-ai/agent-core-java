# com.openjiuwen.core.security.guardrail.RiskAssessment

## class RiskAssessment

```java
public class RiskAssessment
```

`RiskAssessment` 表示 `GuardrailBackend` 的原始分析输出。该类型同样基于 Lombok `@Value` 和 `@Builder` 生成不可变实例。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `hasRisk` | `boolean` | - | 是否检测到风险。 |
| `riskLevel` | `RiskLevel` | `RiskLevel.SAFE` | 风险严重级别；builder 未显式设置时使用 `SAFE`。 |
| `riskType` | `String` | `null` | 风险类型标识。 |
| `confidence` | `double` | `0.0` | 置信度；源码未限制取值范围。 |
| `details` | `Map<String, Object>` | `null` | 后端返回的补充细节。 |

## 说明

- `BaseGuardrail.detect()` 仅读取 `hasRisk`、`riskLevel`、`riskType` 和 `details`；`confidence` 主要供上层记录或自行解释。
- 若 `analyze(...)` 返回 `null`，`BaseGuardrail` 会按“无风险”处理，而不是自动创建 `RiskAssessment` 实例。
