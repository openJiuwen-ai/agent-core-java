# com.openjiuwen.core.common.exception.GuardrailError

## 类 GuardrailError

```java
public class GuardrailError extends ValidationError
```

`GuardrailError` 用于表示 guardrail 拦截导致的校验异常。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public GuardrailError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | 接收完整的状态码、自定义消息、细节对象、根因异常与模板参数。 |
| `public GuardrailError(StatusCode status, Map<String, Object> params)` | 仅提供 `StatusCode` 与模板参数，消息正文按 `StatusCode.errmsg` 渲染。 |
| `public GuardrailError(StatusCode status)` | 仅基于 `StatusCode` 构造异常实例。 |

## 说明

- 该类型只负责表达更细粒度的异常语义，不新增字段或公开方法。
- 继承 `ValidationError` 的默认语义：`isRecoverable()` 为 `false`，`isFatal()` 为 `false`。
- 相关测试：`ErrorTest`。
