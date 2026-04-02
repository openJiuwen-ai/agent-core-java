# com.openjiuwen.core.common.exception.ValidationError

## 类 ValidationError

```java
public class ValidationError extends BaseError
```

`ValidationError` 表示约束、校验或不支持能力导致的异常，默认不可恢复且非致命。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ValidationError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | 接收完整的状态码、自定义消息、细节对象、根因异常与模板参数。 |
| `public ValidationError(StatusCode status, Map<String, Object> params)` | 仅提供 `StatusCode` 与模板参数，消息正文按 `StatusCode.errmsg` 渲染。 |
| `public ValidationError(StatusCode status)` | 仅基于 `StatusCode` 构造异常实例。 |

## 说明

- `defaultRecoverable()` 返回 `false`。
- `defaultFatal()` 返回 `false`。
- `ErrorTest` 明确验证该类型默认不可恢复且非致命。
