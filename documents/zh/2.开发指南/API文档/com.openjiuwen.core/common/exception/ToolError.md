# com.openjiuwen.core.common.exception.ToolError

## 类 ToolError

```java
public class ToolError extends ExecutionError
```

`ToolError` 是工具执行异常的专用类型，可选地保存 `BaseCard` 副本，并把传入的 `card` 信息合并到 `details` 中。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `card` | `BaseCard` | 保存在异常对象中的工具 card 副本；未提供时为 `null`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ToolError(StatusCode status, String msg, Object details, Throwable cause, BaseCard card, Map<String, Object> params)` | 接收完整上下文；当 `card` 非空时会把传入 `card` 合并进 `details`，并在字段 `card` 中保存其副本。 |
| `public ToolError(StatusCode status, Map<String, Object> params)` | 仅提供 `StatusCode` 与模板参数，不携带 `BaseCard`。 |
| `public ToolError(StatusCode status)` | 仅基于 `StatusCode` 构造异常实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public BaseCard getCard()` | 返回构造时保存到异常对象中的 `BaseCard` 副本；未提供时返回 `null`。 |

## 说明

- 当 `details` 本身是 `Map` 时，内部逻辑会先复制该映射，再写入 `card` 键。
- 当 `details` 不是 `Map` 且非空时，会新建映射，并把原值放到 `original_details`。
- `ErrorTest` 覆盖了 `card` 被写入 `details` 的行为。
