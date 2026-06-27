# com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.CancelEvent

## 类 CancelEvent

```java
public final class CancelEvent
```

`CancelEvent` 会被放入收集器队列中，用于唤醒阻塞等待中的调用方。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `reason` | `CancelReason` | `-` | 当前取消事件对应的原因。 |
| `info` | `String` | `-` | 取消事件附带的补充说明。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public CancelEvent(CancelReason reason)` | 仅使用取消原因创建事件。 |
| `public CancelEvent(CancelReason reason, String info)` | 使用取消原因和补充信息创建事件。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public CancelReason getReason()` | 返回取消原因。 |
| `public String getInfo()` | 返回附加说明。 |
| `public String toString()` | 返回包含原因和说明的调试字符串。 |
