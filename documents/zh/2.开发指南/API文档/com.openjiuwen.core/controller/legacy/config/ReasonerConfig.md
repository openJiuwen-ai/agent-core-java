# com.openjiuwen.core.controller.legacy.config.ReasonerConfig

## class ReasonerConfig

```java
public class ReasonerConfig
```

`ReasonerConfig` 是旧版 reasoner 的总配置，组合了意图检测、Planner、主动识别和 Reflector 四个子配置，并附带 logging/metrics 开关。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `intentDetection` | `IntentDetectionConfig` | 新实例 | 意图检测配置。 |
| `planner` | `PlannerConfig` | 新实例 | Planner 配置。 |
| `proactiveIdentifier` | `ProactiveIdentifierConfig` | 新实例 | 主动识别配置。 |
| `reflector` | `ReflectorConfig` | 新实例 | Reflector 配置。 |
| `enableMetrics` | `boolean` | `true` | 是否启用指标。 |
| `enableLogging` | `boolean` | `true` | 是否启用日志。 |
| `metadata` | `Map<String, Object>` | 空映射 | 附加元数据。 |

## 说明

- 该类同样使用 Lombok `@Data` / `@Builder` 生成访问器和 builder。
- `AgentReasoner` 会把它作为 `detect()` 的配置输入传给 `IntentDetector`。
