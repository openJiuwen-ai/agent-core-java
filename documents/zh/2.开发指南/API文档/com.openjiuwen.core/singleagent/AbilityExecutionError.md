# com.openjiuwen.core.single_agent.AbilityExecutionError

## 类 AbilityExecutionError

```java
public class AbilityExecutionError extends AgentError
```

统一封装能力执行失败的异常类型，并保留可返回给模型的 `ToolMessage`。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public AbilityExecutionError(StatusCode status, String msg, ToolMessage toolMessage)` | 使用 `status`、`msg` 和 `toolMessage` 构造执行异常。 |
| `public AbilityExecutionError(StatusCode status, String msg, Throwable cause, ToolMessage toolMessage)` | 在保留底层 `cause` 的同时构造执行异常。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public ToolMessage getToolMessage()` | 返回与异常关联的 `ToolMessage`。 |

## 说明

- 相关测试：`AbilityExecutionErrorTest`、`AbilityManagerSupplementTest`、`DataClassCoverageTest`。
- 异常继承 `AgentError`，构造时会把 `error_msg` 写入异常详情，并保存调用方提供的 `ToolMessage`。
