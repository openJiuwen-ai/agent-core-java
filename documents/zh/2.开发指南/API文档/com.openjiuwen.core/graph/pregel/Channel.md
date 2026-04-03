# com.openjiuwen.core.graph.pregel.Channel

## 抽象类 Channel

```java
public abstract class Channel
```

Pregel 节点之间传递消息的抽象 channel。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | `String` | `-` | channel 名称，也是默认的路由键与节点名来源。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getKey()` | 返回当前 channel 的路由键；默认直接返回 `name`。 |
| `public String getNodeName()` | 返回当前 channel 所属节点名；默认直接返回 `name`。 |
| `public abstract boolean isReady()` | 返回 channel 是否已满足触发节点执行的条件。 |
| `public abstract boolean accept(Message msg)` | 接收一条消息，并在内部状态发生变化时返回 `true`。 |
| `public abstract void consume()` | 消费并清空当前缓冲状态。 |
| `public abstract Object snapshot()` | 生成可持久化的 channel 状态快照。 |
| `public abstract void restore(Object snapshotData)` | 从快照恢复 channel 状态。 |

## 相关测试

- `ChannelTest`
- `PregelTest`
