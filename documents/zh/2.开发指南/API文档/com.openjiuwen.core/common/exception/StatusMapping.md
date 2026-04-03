# com.openjiuwen.core.common.exception.StatusMapping

## 类 StatusMapping

```java
public final class StatusMapping
```

`StatusMapping` 负责把 `StatusCode` 解析成具体异常类型。源码中的解析顺序依次是：手工覆盖、关键字规则、编码区间回退，最后才退回 `ExecutionError`。

## 手工覆盖候选

| StatusCode | Exception |
| --- | --- |
| `CONTROLLER_INVOKE_LLM_FAILED` | `FrameworkError` |
| `TOOL_EXECUTION_ERROR` | `ToolError` |
| `TOOL_NOT_FOUND_ERROR` | `ValidationError` |
| `AGENT_GROUP_EXECUTION_ERROR` | `AgentError` |

## 关键字规则

| 关键字 | Exception |
| --- | --- |
| `INVALID`、`VALIDATE`、`NOT_SUPPORTED`、`PARAM`、`MISSING`、`DUPLICATED`、`CONFIG`、`SCHEMA`、`FORMAT`、`TEMPLATE` | `ValidationError` |
| `INIT`、`CONNECT`、`SERVICE`、`QUEUE`、`PROVIDER`、`CALL`、`INVOKE_LLM`、`MODEL`、`REMOTE` | `FrameworkError` |
| `TIMEOUT`、`EXECUTE`、`EXECUTION`、`RUNTIME`、`PROCESS`、`STREAM`、`RESPONSE` | `ExecutionError` |

## 号段规则

| 区间 | Exception |
| --- | --- |
| `100000–119999` | `WorkflowError` |
| `120000–129999` | `AgentError` |
| `130000–139999` | `RunnerError` |
| `140000–149999` | `GraphError` |
| `150000–159999` | `ContextError` |
| `160000–179999` | `ToolchainError` |
| `180000–189999` | `FrameworkError` |
| `190000–198999` | `SessionError` |
| `199000–199999` | `SysOperationError` |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static Function<StatusCode, BaseError> resolveExceptionFactory(StatusCode status)` | 返回当前状态码对应的异常工厂。 |
| `public static BaseError resolveException(StatusCode status)` | 直接实例化当前状态码对应的异常对象。 |
| `public static Map<StatusCode, Function<StatusCode, BaseError>> buildStatusExceptionMap()` | 为当前全部 `StatusCode` 生成不可变映射表。 |

## 说明

- 内部的 `putIfExists(...)` 会先检查枚举成员是否存在；若当前构建里没有该成员，则跳过对应手工覆盖项。
- `ErrorTest` 覆盖了关键字规则、区间规则以及 `buildStatusExceptionMap()` 的完整性。
