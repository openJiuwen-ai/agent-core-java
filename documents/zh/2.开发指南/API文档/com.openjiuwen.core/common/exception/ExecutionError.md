# com.openjiuwen.core.common.exception.ExecutionError

## 类 ExecutionError

```java
public class ExecutionError extends BaseError
```

`ExecutionError` 表示工作流、Agent、工具等执行期异常，默认可恢复且非致命。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ExecutionError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | 接收完整的状态码、自定义消息、细节对象、根因异常与模板参数。 |
| `public ExecutionError(StatusCode status, Map<String, Object> params)` | 仅提供 `StatusCode` 与模板参数，消息正文按 `StatusCode.errmsg` 渲染。 |
| `public ExecutionError(StatusCode status)` | 仅基于 `StatusCode` 构造异常实例。 |

## 说明

- `defaultRecoverable()` 返回 `true`。
- `defaultFatal()` 返回 `false`。
- `ErrorTest` 明确验证该类型默认可恢复且非致命。
