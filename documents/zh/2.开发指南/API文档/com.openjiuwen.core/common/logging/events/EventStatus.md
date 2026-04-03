# com.openjiuwen.core.common.logging.events.EventStatus

## 枚举 EventStatus

```java
public enum EventStatus
```

`EventStatus` 表示结构化事件的执行状态。

## 枚举值

| 枚举值 | 序列化值 | 说明 |
| --- | --- | --- |
| `SUCCESS` | `success` | 事件成功完成。 |
| `FAILURE` | `failure` | 事件执行失败。 |
| `PENDING` | `pending` | 事件已创建但尚未结束。 |
| `TIMEOUT` | `timeout` | 事件因超时结束。 |
| `CANCELLED` | `cancelled` | 事件被主动取消。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前枚举对应的稳定小写字符串。 |
