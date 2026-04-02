# com.openjiuwen.core.security.guardrail.RiskLevel

## enum RiskLevel

```java
public enum RiskLevel
```

`RiskLevel` 定义 guardrail 评估使用的标准风险等级。

## 枚举值

| 枚举值 | `getValue()` | 说明 |
|---|---|---|
| `SAFE` | `"safe"` | 未发现风险。 |
| `LOW` | `"low"` | 低风险。 |
| `MEDIUM` | `"medium"` | 中风险。 |
| `HIGH` | `"high"` | 高风险。 |
| `CRITICAL` | `"critical"` | 严重风险。 |

## 主要方法

### `public String getValue()`

返回与当前枚举值对应的小写字符串，适合写入日志、序列化字段或后端协议。
