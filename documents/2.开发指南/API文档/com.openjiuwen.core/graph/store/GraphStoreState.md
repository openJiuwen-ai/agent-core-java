# com.openjiuwen.core.graph.store.GraphStoreState

## 类 GraphStoreState

```java
public class GraphStoreState
```

保存 Pregel 图恢复/续跑所需的状态快照；类名使用 `GraphStoreState` 以避免与图节点状态类型重名。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `ns` | `String` | `-` | 当前状态所属的命名空间。 |
| `step` | `int` | `-` | 当前已持久化的 super-step 编号。 |
| `channelValues` | `Map<String, Object>` | `channelValues != null ? channelValues : new HashMap<>()` | 已落盘的 channel 快照值。 |
| `pendingBuffer` | `List<Message>` | `pendingBuffer != null ? pendingBuffer : Collections.emptyList()` | 尚未完全消费的消息缓冲区。 |
| `pendingNode` | `Map<String, PendingNode>` | `pendingNode != null ? pendingNode : new HashMap<>()` | 待恢复节点信息映射。 |
| `nodeVersion` | `Map<String, Integer>` | `nodeVersion != null ? nodeVersion : new HashMap<>()` | 节点版本号映射。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public GraphStoreState(String ns, int step, Map<String, Object> channelValues, List<Message> pendingBuffer, Map<String, PendingNode> pendingNode, Map<String, Integer> nodeVersion)` | 创建状态快照，并为 `null` 的集合字段填充默认空集合。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getNs()` | 返回命名空间。 |
| `public int getStep()` | 返回当前 super-step。 |
| `public Map<String, Object> getChannelValues()` | 返回 channel 快照映射。 |
| `public List<Message> getPendingBuffer()` | 返回待消费消息缓冲区。 |
| `public Map<String, PendingNode> getPendingNode()` | 返回待恢复节点映射。 |
| `public Map<String, Integer> getNodeVersion()` | 返回节点版本映射。 |
| `public static GraphStoreState create(String ns, int step, Map<String, Object> channelSnapshot, List<Message> pendingBuffer, Map<String, PendingNode> pendingNode, Map<String, Integer> nodeVersion)` | 语义化工厂方法，内部直接委托构造器创建实例。 |

## 相关测试

- `GraphStoreTest`
