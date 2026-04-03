# com.openjiuwen.core.common.exception.FrameworkError

## 类 FrameworkError

```java
public class FrameworkError extends BaseError
```

`FrameworkError` 表示基础设施、运行环境或依赖异常，默认不可恢复且致命。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public FrameworkError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | 接收完整的状态码、自定义消息、细节对象、根因异常与模板参数。 |
| `public FrameworkError(StatusCode status, Map<String, Object> params)` | 仅提供 `StatusCode` 与模板参数，消息正文按 `StatusCode.errmsg` 渲染。 |
| `public FrameworkError(StatusCode status)` | 仅基于 `StatusCode` 构造异常实例。 |

## 说明

- `defaultRecoverable()` 返回 `false`。
- `defaultFatal()` 返回 `true`。
- `ErrorTest` 明确验证该类型默认不可恢复且致命。
