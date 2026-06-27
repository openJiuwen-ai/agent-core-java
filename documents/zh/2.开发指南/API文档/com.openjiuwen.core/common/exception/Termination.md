# com.openjiuwen.core.common.exception.Termination

## 类 Termination

```java
public class Termination extends BaseError
```

`Termination` 表示正常停止、取消或完成等非错误型控制流终止信号。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Termination(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | 接收完整的状态码、自定义消息、细节对象、根因异常与模板参数。 |
| `public Termination(StatusCode status, Map<String, Object> params)` | 仅提供 `StatusCode` 与模板参数，消息正文按 `StatusCode.errmsg` 渲染。 |
| `public Termination(StatusCode status)` | 仅基于 `StatusCode` 构造终止对象。 |

## 说明

- 该类型复用 `BaseError` 的结构化输出能力，但语义上不表示执行失败。
- `defaultRecoverable()` 与 `defaultFatal()` 都返回 `false`。
- `ErrorTest` 覆盖了该类型默认不可恢复、非致命的语义约束。
