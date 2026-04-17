# com.openjiuwen.core.common.exception.ExternalServiceError

## 类 ExternalServiceError

```java
public class ExternalServiceError extends ExecutionError
```

`ExternalServiceError` 用于表示外部服务调用异常。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ExternalServiceError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | 接收完整的状态码、自定义消息、细节对象、根因异常与模板参数。 |
| `public ExternalServiceError(StatusCode status, Map<String, Object> params)` | 仅提供 `StatusCode` 与模板参数，消息正文按 `StatusCode.errmsg` 渲染。 |
| `public ExternalServiceError(StatusCode status)` | 仅基于 `StatusCode` 构造异常实例。 |

## 说明

- 该类型只负责表达更细粒度的异常语义，不新增字段或公开方法。
- 继承 `ExecutionError` 的默认语义：`isRecoverable()` 为 `true`，`isFatal()` 为 `false`。
- 相关测试：`ErrorTest`。
