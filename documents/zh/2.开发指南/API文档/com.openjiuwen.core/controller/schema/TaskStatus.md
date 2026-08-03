# com.openjiuwen.agent_teams.schema.status.TaskStatus

## enum TaskStatus

```java
public enum TaskStatus
```

`TaskStatus` 定义控制器任务的状态流转枚举。

## 状态值

| 枚举值 | 字符串值 | 说明 |
|---|---|---|
| `SUBMITTED` | `submitted` | 已提交，等待调度执行。 |
| `WORKING` | `working` | 正在执行。 |
| `PAUSED` | `paused` | 已暂停。 |
| `INPUT_REQUIRED` | `input-required` | 任务需要用户补充输入。 |
| `COMPLETED` | `completed` | 已完成。 |
| `CANCELED` | `canceled` | 已取消。 |
| `FAILED` | `failed` | 执行失败。 |
| `WAITING` | `waiting` | 等待依赖或其他外部条件。 |
| `UNKNOWN` | `unknown` | 未知状态或尚未初始化。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getValue()` | `String` | 返回状态对应的字符串值。 |
| `fromValue(String value)` | `TaskStatus` | 按字符串值恢复枚举；未知值时抛出 `IllegalArgumentException`。 |
| `toString()` | `String` | 直接返回字符串值。 |

## 说明

- 源码注释给出的主流程是 `submitted -> working -> (completed | failed | paused | canceled)`，以及 `working -> input-required`。
- `TaskScheduler.areAllTasksCompleted()` 只把 `SUBMITTED` 和 `WORKING` 视为“仍在活跃执行”的状态。
