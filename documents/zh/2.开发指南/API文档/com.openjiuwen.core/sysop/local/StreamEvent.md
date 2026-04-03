# com.openjiuwen.core.sysop.local.StreamEvent

## 类 StreamEvent

```java
public class StreamEvent
```

`StreamEvent` 表示本地子进程流式输出中的单个事件，既可承载文本片段，也可承载退出码或错误说明。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | `StreamEventType` | - | 当前事件类型。 |
| `data` | `Object` | - | 事件载荷：`stdout`/`stderr` 为文本，`exit` 为退出码，`error` 为错误消息。 |
| `timestamp` | `Instant` | `Instant.now()` | 事件创建时间戳。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getDataAsString()` | 将当前载荷按字符串形式返回。 |
| `public Integer getDataAsInt()` | 尝试把当前载荷解析为整数；主要用于 `EXIT` 事件。 |

## Lombok 说明

- 该类型使用 `Data`、`Builder`、`NoArgsConstructor`、`AllArgsConstructor` 生成访问器、构建器和构造辅助方法。
- `timestamp` 通过 `@Builder.Default` 在未显式赋值时自动取 `Instant.now()`。

## 相关测试

- `LocalUtilsTest`
