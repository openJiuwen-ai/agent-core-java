# com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.CancelReason

## 枚举 CancelReason

```java
public enum CancelReason
```

CancelReason 表示 ResponseCollector 在被唤醒或终止时对应的原因。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `value` | `String` | `-` | 枚举对应的字符串值，用于写入取消事件或日志。 |

## 枚举值

| 枚举值 | 说明 |
| --- | --- |
| `RUNNER_STOPPED` | Runner 或 Adapter 主动停止。 |
| `TTL_EXPIRE` | 等待响应超过 TTL。 |
| `QUEUE_FULL` | 响应收集队列已满。 |
| `FINISH` | 正常结束，不需要额外唤醒等待者。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前取消原因对应的字符串值。 |
