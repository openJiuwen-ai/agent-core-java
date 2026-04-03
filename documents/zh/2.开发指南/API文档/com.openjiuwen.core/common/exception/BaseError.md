# com.openjiuwen.core.common.exception.BaseError

## 类 BaseError

```java
public class BaseError extends RuntimeException
```

`BaseError` 是框架统一异常基类，负责把 `StatusCode`、模板渲染结果、结构化细节以及恢复语义封装到同一个运行时异常对象中。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `status` | `StatusCode` | 当前异常绑定的语义状态码。 |
| `code` | `int` | 从 `status.getCode()` 缓存得到的整数编码。 |
| `params` | `Map<String, Object>` | 模板参数的不可变副本。 |
| `details` | `Object` | 额外的结构化细节对象。 |
| `templateMessage` | `String` | 根据 `StatusCode.errmsg` 渲染出的模板消息。 |
| `message` | `String` | 最终对外暴露的消息；传入 `msg` 时会覆盖模板文案。 |
| `recoverable` | `boolean` | 是否建议重试或重新规划。 |
| `fatal` | `boolean` | 是否应立即终止当前执行流程。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BaseError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params)` | 接收完整的状态码、自定义消息、细节对象、根因异常与模板参数。 |
| `public BaseError(StatusCode status, String msg, Object details, Throwable cause)` | 省略模板参数，使用空参数表构造异常。 |
| `public BaseError(StatusCode status, Map<String, Object> params)` | 仅提供 `StatusCode` 与模板参数，消息正文按模板渲染。 |
| `public BaseError(StatusCode status)` | 仅基于 `StatusCode` 构造异常实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, Object> toMap()` | 输出包含 `code`、`status`、`message`、`params`、`raw_message` 与 `details` 的结构化映射。 |
| `public String toJson()` | 将 `toMap()` 序列化为 JSON；序列化失败时退回 `toString()`。 |
| `public StatusCode getStatus()` | 返回当前状态码对象。 |
| `public int getCode()` | 返回整数状态码。 |
| `public Map<String, Object> getParams()` | 返回模板参数映射。 |
| `public Object getDetails()` | 返回结构化细节对象。 |
| `public String getTemplateMessage()` | 返回模板渲染后的标准消息。 |
| `public String getMessage()` | 返回最终对外消息。 |
| `public boolean isRecoverable()` | 返回是否可恢复。 |
| `public boolean isFatal()` | 返回是否致命。 |
| `public String toString()` | 以 `[code] message` 形式输出异常摘要。 |

## 说明

- `params` 在构造时会复制为不可变映射，避免外部继续修改。
- 模板占位符使用 `{key}` 语法；缺失参数时会替换为 `<missing:key>`，`ErrorTest` 对该行为有覆盖。
- `toMap()` 中的 `message` 保存标准模板消息，`raw_message` 保存最终显示消息。
- `defaultRecoverable()` 与 `defaultFatal()` 是受保护扩展点，供子类覆写默认语义。
