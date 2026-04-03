# com.openjiuwen.core.controller.modules.IntentToolkits

## class IntentToolkits

```java
public class IntentToolkits
```

`IntentToolkits` 为意图识别阶段提供两类能力：一类是向模型暴露 OpenAI 风格的 function tool schema，另一类是把工具参数转换成结构化的 `Intent`。

## 构造方法

| 构造方法 | 说明 |
|---|---|
| `IntentToolkits(Event event, double confidenceThreshold)` | 绑定当前事件对象，记录置信度阈值，并立即构建工具 schema 索引。 |

## 结果类型

### `public record IntentResult(Intent intent, String message)`

`IntentResult` 封装一次工具分发的结果，包含生成的 `Intent` 和一条可写回上下文的说明消息。

## 支持的工具

| 工具名 | 生成的意图 | 说明 |
|---|---|---|
| `create_task` | `CREATE_TASK` | 创建新的任务并生成随机任务 ID。 |
| `pause_task` | `PAUSE_TASK` | 暂停指定任务。 |
| `cancel_task` | `CANCEL_TASK` | 取消指定任务。 |
| `resume_task` | `RESUME_TASK` | 恢复已暂停的任务。 |
| `unknown_task` | `UNKNOWN_TASK` | 记录无法确定的意图，并附带澄清问题。 |
| `create_dependent_task` | `CONTINUE_TASK` | 创建依赖既有任务的新任务。 |
| `modify_task` | `MODIFY_TASK` | 基于既有任务生成新的修改任务。 |
| `supplement_task` | `SUPPLEMENT_TASK` | 为既有任务补充额外信息。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `createTask(double confidence, String taskDescription)` | `IntentResult` | 生成 `CREATE_TASK` 意图；若置信度不足则回退为 `UNKNOWN_TASK`。 |
| `pauseTask(double confidence, String taskId)` | `IntentResult` | 生成 `PAUSE_TASK` 意图。 |
| `cancelTask(double confidence, String taskId)` | `IntentResult` | 生成 `CANCEL_TASK` 意图。 |
| `resumeTask(double confidence, String taskId)` | `IntentResult` | 生成 `RESUME_TASK` 意图。 |
| `unknownTask(double confidence, String questionForUser)` | `IntentResult` | 生成 `UNKNOWN_TASK` 意图，并保留澄清问题。 |
| `createDependentTask(double confidence, String taskDescription, List<String> dependentTaskIds)` | `IntentResult` | 生成 `CONTINUE_TASK` 意图，并保留依赖任务 ID 列表。 |
| `modifyTask(double confidence, String taskId, String newTaskDescription)` | `IntentResult` | 生成 `MODIFY_TASK` 意图；新的 `Intent` 会带有新的 `targetTaskId`。 |
| `supplementTask(double confidence, String taskId, String supplementInfo)` | `IntentResult` | 生成 `SUPPLEMENT_TASK` 意图。 |
| `getOpenaiToolSchemas(List<String> choices)` | `List<Map<String, Object>>` | 返回全部工具 schema 或按工具名过滤后的子集。 |
| `dispatch(String toolName, Map<String, Object> arguments)` | `IntentResult` | 按工具名选择对应创建方法，并把参数转换成意图结果。 |

## 说明

- 所有公开的意图创建方法都会先检查 `confidence`；低于阈值时统一回退到 `lowConfidenceIntent(...)`。
- `buildToolSchemaChoices()` 生成的工具参数都带有 `additionalProperties = false`，因此 schema 不接受额外字段。
- `createDependentTask(...)` 生成的 `Intent` 类型是 `CONTINUE_TASK`，不是单独的 `CREATE_DEPENDENT_TASK` 枚举项。
- `modifyTask(...)` 会创建新的随机 `targetTaskId`，并把原任务 ID 放入依赖列表中。
