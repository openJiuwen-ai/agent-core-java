# com.openjiuwen.core.controller.legacy.reasoner.AgentReasoner

## class AgentReasoner

```java
public class AgentReasoner
```

`AgentReasoner` 是旧版 reasoner 的轻量组合器，把 `IntentDetector` 和 `Planner` 串在一起使用。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `intentDetector` | `IntentDetector` | `null` | 意图检测器。 |
| `planner` | `Planner` | `null` | Planner。 |
| `config` | `ReasonerConfig` | 新实例 | reasoner 总配置。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `detect(Event event, Session session)` | `IntentDetectionController.Intent` | 如果已注入 `intentDetector`，则调用其 `detect()`。 |
| `plan(IntentDetectionController.Intent intent, Session session)` | `Task` | 如果已注入 `planner`，则调用其 `plan()`。 |

## 说明

- 当 `intentDetector` 或 `planner` 为空时，相关方法会直接返回 `null`。
- 该类主要提供旧版控制器中“先识别意图，再规划任务”的组合入口。
