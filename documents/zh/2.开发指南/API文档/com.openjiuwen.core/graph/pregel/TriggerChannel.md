# com.openjiuwen.core.graph.pregel.TriggerChannel

## 类 TriggerChannel

```java
public class TriggerChannel extends Channel
```

收到任意触发消息即变为 ready 的 channel。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TriggerChannel(String name)` | 基于名称创建 `TriggerChannel`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public boolean isReady()` | 只要内部消息列表非空即返回 `true`。 |
| `public boolean accept(Message msg)` | 接收 `TriggerMessage` 并写入内部缓冲。 |
| `public void consume()` | 清空内部 `TriggerMessage` 缓冲。 |
| `public Object snapshot()` | 将当前消息列表快照为新列表。 |
| `public void restore(Object snapshotData)` | 从消息列表快照恢复内部状态。 |

## 相关测试

- `ChannelTest`
- `PregelTest`
