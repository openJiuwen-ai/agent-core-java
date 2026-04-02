# com.openjiuwen.core.controller.schema.Intent

## class Intent

```java
public class Intent
```

`Intent` 表示意图识别阶段输出的结构化任务操作对象，记录意图类型、关联输入事件、目标任务和补充信息，是 `IntentRecognizer` 与 `EventHandlerWithIntentRecognition` 之间的核心契约。

## 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `intentType` | `IntentType` | 意图类型。 |
| `event` | `Event` | 触发该意图的原始事件。 |
| `targetTaskId` | `String` | 目标任务 ID。 |
| `targetTaskDescription` | `String` | 创建、继续或切换任务时使用的任务描述。 |
| `dependTaskId` | `List<String>` | 继续任务时依赖的任务 ID 列表。 |
| `supplementaryInfo` | `String` | 补充任务信息。 |
| `modificationDetails` | `String` | 修改任务时的额外说明。 |
| `confidence` | `double` | 置信度，必须位于 `0.0` 到 `1.0` 之间。 |
| `metadata` | `Map<String, Object>` | 附加元数据，默认空映射。 |
| `clarificationPrompt` | `String` | 未识别意图时返回给用户的澄清提示。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `Intent(IntentType intentType, Event event, String targetTaskId)` | 用最小参数构造意图，默认 `confidence = 1.0`。 |
| `Intent(IntentType intentType, Event event, String targetTaskId, String targetTaskDescription, List<String> dependTaskId, String supplementaryInfo, String modificationDetails, double confidence, String clarificationPrompt)` | 构造完整意图并立即执行校验。 |

## 校验规则

| 意图类型 | 必要条件 |
|---|---|
| `CREATE_TASK` | `targetTaskDescription` 必填。 |
| `CONTINUE_TASK` | `dependTaskId` 不能为空。 |
| `SUPPLEMENT_TASK` | `targetTaskId` 与 `supplementaryInfo` 必填。 |
| `MODIFY_TASK` | `targetTaskId` 与 `modificationDetails` 必填。 |
| `PAUSE_TASK` / `RESUME_TASK` / `CANCEL_TASK` | `targetTaskId` 必填。 |
| `SWITCH_TASK` | `targetTaskDescription` 必填。 |
| `UNKNOWN_TASK` | `clarificationPrompt` 必填。 |

## 说明

- 任一构造函数都会调用内部 `validate()`；若条件不满足，会抛出 `AGENT_CONTROLLER_RUNTIME_ERROR`。
- `setMetadata()` 在传入 `null` 时会回退为空映射。
