# com.openjiuwen.core.common.exception.StatusCodeTemplate

## 记录 StatusCodeTemplate

```java
public record StatusCodeTemplate(String name, String codeSuggestion, String messageTemplate, String exceptionSemantic)
```

`StatusCodeTemplate` 根据结构化输入生成建议的枚举名、编码区间、消息模板与异常语义，主要用于工具或代码生成流程。

## 组件

| 组件 | 类型 | 说明 |
| --- | --- | --- |
| `name` | `String` | 建议的枚举成员名。 |
| `codeSuggestion` | `String` | 建议使用的编码区间。 |
| `messageTemplate` | `String` | 生成得到的消息模板。 |
| `exceptionSemantic` | `String` | 建议映射到的异常语义类型名。 |

## 常量

| 常量 | 类型 | 说明 |
| --- | --- | --- |
| `ALLOWED_SCOPES` | `Set<String>` | `generate(...)` 接受的 scope 集合，包含 `WORKFLOW`、`COMPONENT`、`AGENT`、`TOOL`、`MODEL`、`SESSION`、`GRAPH`、`CONTROLLER`、`RUNNER`、`PROMPT`、`COMMON`、`CONTEXT`、`TOOLCHAIN`、`MEMORY`、`RETRIEVAL` 与 `SYS_OPERATION`。 |
| `ALLOWED_FAILURE_TYPES` | `Set<String>` | `generate(...)` 接受的 failure type 集合。 |

## 作用域编码建议

| Scope | 编码区间 |
| --- | --- |
| `WORKFLOW` | `100000–100999` |
| `COMPONENT` | `101000–119999` |
| `AGENT` | `120000–129999` |
| `CONTROLLER` | `custom` |
| `RUNNER` | `130000–139999` |
| `GRAPH` | `140000–149999` |
| `CONTEXT` | `150000–154999` |
| `RETRIEVAL` | `155000–157999` |
| `MEMORY` | `158000–159999` |
| `TOOLCHAIN` | `160000–179999` |
| `PROMPT` | `180000–180999` |
| `MODEL` | `181000–181999` |
| `TOOL` | `182000–182999` |
| `COMMON` | `188000–188999` |
| `SESSION` | `190000–198999` |
| `SYS_OPERATION` | `199000–199999` |

## 允许的失败类型

| Failure Type | 建议异常语义 |
| --- | --- |
| `INVALID` | `ValidationError` |
| `NOT_FOUND` | `ValidationError` |
| `NOT_SUPPORTED` | `ValidationError` |
| `CONFIG_ERROR` | `ValidationError` |
| `PARAM_ERROR` | `ValidationError` |
| `TYPE_ERROR` | `ExecutionError` |
| `INIT_FAILED` | `FrameworkError` |
| `CALL_FAILED` | `FrameworkError` |
| `EXECUTION_ERROR` | `ExecutionError` |
| `RUNTIME_ERROR` | `ExecutionError` |
| `PROCESS_ERROR` | `ExecutionError` |
| `TIMEOUT` | `ExecutionError` |
| `INTERRUPTED` | `ExecutionError` |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static StatusCodeTemplate generate(String scope, String subject, String failureType, String detail)` | 校验输入后生成完整模板；当 `detail` 非空时，会插入到枚举名的 `scope` 与 `subject` 之间。 |
| `public static StatusCodeTemplate generate(String scope, String subject, String failureType)` | 省略 `detail` 的便捷重载。 |

## 说明

- `generate(...)` 内部会调用 `ErrorMessageTemplate.generate(...)` 产出消息模板。
- `CONTROLLER` 虽然被列入 `ALLOWED_SCOPES`，但 `codeRangeByScope(...)` 当前没有为它定义专用号段，因此 `codeSuggestion` 会退回 `custom`。
- `TYPE_ERROR` 虽然被列入 `ALLOWED_FAILURE_TYPES`，但当前 `ErrorMessageTemplate.generate(...)` 并不支持它；`StatusCodeTest` 覆盖了这处不一致。
