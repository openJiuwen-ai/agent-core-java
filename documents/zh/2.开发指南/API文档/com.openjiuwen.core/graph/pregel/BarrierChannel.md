# com.openjiuwen.core.graph.pregel.BarrierChannel

## 类 BarrierChannel

```java
public class BarrierChannel extends Channel
```

用于 N→1 汇聚同步的 barrier channel。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `nodeName` | `String` | `-` | 当前 barrier 对应的目标节点名。 |
| `expected` | `Set<String>` | `-` | 需要全部到齐的来源节点集合。 |
| `routerKey` | `String` | `-` | 形如 `barrier:senders->node` 的路由键。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BarrierChannel(String nodeName, Set<String> expected)` | 基于目标节点与预期来源集合创建 `BarrierChannel`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getKey()` | 返回当前 barrier channel 的路由键。 |
| `public String getNodeName()` | 返回当前 channel 归属的目标节点名。 |
| `public boolean isReady()` | 当已接收来源集合与 `expected` 完全一致时返回 `true`。 |
| `public boolean accept(Message msg)` | 接收 `BarrierMessage`；首次收到某个 sender 时更新内部状态。 |
| `public void consume()` | 清空已接收 sender 集合，重置 barrier 状态。 |
| `public Object snapshot()` | 将当前已接收 sender 集合快照为列表。 |
| `public void restore(Object snapshotData)` | 从列表快照恢复已接收 sender 集合。 |

## 相关测试

- `ChannelTest`
- `PregelTest`
