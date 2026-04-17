# com.openjiuwen.core.controller.schema.Task

## class Task

```java
public class Task
```

`Task` 是控制器内部的任务模型，记录任务标识、优先级、输入输出、状态、父子关系和序列化信息。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `sessionId` | `String` | `null` | 所属会话 ID。 |
| `taskId` | `String` | `null` | 任务 ID。 |
| `taskType` | `String` | `null` | 任务类型，用于选择执行器。 |
| `description` | `String` | `null` | 任务描述。 |
| `priority` | `int` | `1` | 优先级，数值越大越优先。 |
| `inputs` | `List<Object>` | `null` | 输入事件或其他上游输入对象。 |
| `outputs` | `List<ControllerOutputChunk>` | 空列表 | 任务输出块列表。 |
| `status` | `TaskStatus` | `UNKNOWN` | 当前状态。 |
| `parentTaskId` | `String` | `null` | 父任务 ID。 |
| `contextId` | `String` | `null` | 关联上下文 ID。 |
| `inputRequiredFields` | `Object` | `null` | 输入补充时所需字段定义。 |
| `errorMessage` | `String` | `null` | 失败时的错误信息。 |
| `metadata` | `Map<String, Object>` | `null` | 附加元数据。 |
| `extensions` | `Map<String, Object>` | `null` | 扩展字段。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `Task()` | 初始化默认优先级、空输出列表和 `UNKNOWN` 状态。 |
| `Task(String sessionId, String taskId, String taskType)` | 要求三个核心字符串都非空，并在构造时自动去除首尾空白。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `copy()` | `Task` | 深拷贝任务对象及其主要集合字段。 |
| `validate()` | `void` | 校验任务是否合法。 |
| `toMap()` | `Map<String, Object>` | 序列化为普通映射，用于持久化。 |
| `fromMap(Map<String, Object> map)` | `Task` | 从映射恢复任务对象。 |

## 校验规则

- `sessionId`、`taskId` 和 `taskType` 不能为空白字符串。
- `priority` 不能小于 `0`。
- `parentTaskId` 不能是空字符串，也不能与 `taskId` 相同。
- 当状态为 `FAILED` 时，`errorMessage` 必填。
- 当状态为 `INPUT_REQUIRED` 时，`inputRequiredFields` 必填。

## 说明

- `setParentTaskId()` 会自动把空白字符串归一化为 `null`。
- `TaskManager` 对外返回的几乎都是 `copy()` 结果，因此调用方修改任务后需要显式调用 `updateTask()` 才会写回管理器。
