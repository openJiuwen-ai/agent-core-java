# com.openjiuwen.core.graph.pregel.ChannelManager

## 类 ChannelManager

```java
public class ChannelManager
```

管理所有 channel，并负责消息缓冲、刷新与 ready 节点判定。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ChannelManager(List<Channel> channels)` | 基于给定 channel 列表创建 `ChannelManager`，并恢复初始 ready 节点集合。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void bufferMessage(Message msg)` | 将消息加入下一轮 `flush` 使用的缓冲区。 |
| `public boolean isEmpty()` | 返回当前消息缓冲区是否为空。 |
| `public void flush()` | 将缓冲消息分发到对应 channel，并更新 ready 节点集合。 |
| `public List<String> getReadyNodes()` | 返回当前所有 ready 节点名。 |
| `public void consume(String nodeName)` | 消费指定节点下所有 ready channel，并移除该节点的 ready 标记。 |
| `public Map<String, Object> snapshot()` | 生成全部 channel 的持久化快照，跳过 `__end__` 节点。 |
| `public void restore(Map<String, Object> snapshotMap)` | 从快照恢复 channel 状态，并重新计算 ready 节点集合。 |
| `public List<Message> getBuffer()` | 返回原始缓冲消息列表，用于错误态持久化。 |

## 相关测试

- `ChannelTest`
