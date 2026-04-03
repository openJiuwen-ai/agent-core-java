# com.openjiuwen.core.graph.stream_actor.StreamGraph

## 类 StreamGraph

```java
public class StreamGraph
```

保存图节点到 `StreamConsumer` 的注册关系，供 `ActorManager` 构建 actor 时查询。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `streamNodes` | `Map<String, StreamConsumer>` | `new LinkedHashMap<>()` | 节点 ID 到流式 consumer 的映射。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addStreamConsumer(StreamConsumer consumer, String nodeId)` | 为指定节点注册 consumer；同一节点只在首次注册时写入。 |
| `public StreamConsumer getNode(String nodeId)` | 返回节点对应的 consumer，不存在时返回 `null`。 |
