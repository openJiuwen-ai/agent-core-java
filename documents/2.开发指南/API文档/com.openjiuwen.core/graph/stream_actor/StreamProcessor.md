# com.openjiuwen.core.graph.stream_actor.StreamProcessor

## 类 StreamProcessor

```java
public class StreamProcessor
```

负责消费 `StreamPayload` 队列，把消息分发到 schema 叶子路径对应的阻塞迭代器，并在收到全部结束帧后收敛。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `END_SENTINEL` | `Object` | `new Object()` | 迭代器收到后表示流结束的静态哨兵对象。 |
| `nodeId` | `String` | `-` | 当前 processor 归属的 consumer 节点 ID。 |
| `queue` | `BlockingQueue<StreamPayload>` | `new LinkedBlockingQueue<>()` | 接收来自 `StreamActor` 的原始 payload 队列。 |
| `processorQueues` | `Map<String, List<BlockingQueue<Object>>>` | `new HashMap<>()` | schema 引用路径到下游阻塞队列列表的映射。 |
| `sources` | `Set<String>` | `new HashSet<>(sources)` | 期望收齐结束帧的来源键集合，格式为 `producerId-ABILITY`。 |
| `timeoutSeconds` | `long` | `streamGeneratorTimeoutSeconds > 0 ? streamGeneratorTimeoutSeconds : 0` | 生成器取流超时秒数；`0` 表示无限等待。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public StreamProcessor(String nodeId, List<String> sources, long streamGeneratorTimeoutSeconds)` | 基于节点 ID、上游来源集合和超时配置创建 processor。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void run(ComponentAbility ability)` | 启动主循环，持续读取 payload，并把匹配到的值写入各 schema 路径队列；当全部来源都收到结束帧后退出。 |
| `public void receive(StreamPayload payload)` | 将 payload 放入内部阻塞队列等待处理。 |
| `public Map<String, Object> generator(Map<String, Object> schema, Consumer<Object> streamCallback)` | 将 `schema` 中包含 `$` 的叶子路径替换为阻塞迭代器，静态叶子值保持不变，并在迭代消费时回调 `streamCallback`。 |

## 相关测试

- `StreamProcessorTest`
