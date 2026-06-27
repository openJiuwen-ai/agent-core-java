# com.openjiuwen.core.graph.stream_actor.ActorManager

## 类 ActorManager

```java
public class ActorManager
```

根据图中的流式边和组件能力创建 `StreamActor`，统一负责 producer 发送、consumer 取流和收尾清理。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `streamEdges` | `Map<String, List<String>>` | 入参值或 `new HashMap<>()` | producer 节点到下游 consumer 节点列表的映射。 |
| `streams` | `Map<String, StreamActor>` | `new LinkedHashMap<>()` | 已注册 consumer 的 `StreamActor` 映射。 |
| `streamsTransform` | `StreamTransform` | `new StreamTransform()` | 提供 schema/自定义函数两种流式输入转换能力。 |
| `subGraph` | `boolean` | 入参值 | 标记当前 manager 是否服务于子图。 |
| `subWorkflowStreamQueue` | `BlockingQueue<Object>` | `subGraph ? new LinkedBlockingQueue<>(10240) : null` | 子图模式下暴露给子工作流的共享输出队列。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ActorManager(Map<String, List<String>> streamEdges, StreamGraph graph, boolean subGraph, BaseSession session, java.util.function.Function<String, List<ComponentAbility>> compAbilitiesProvider)` | 反向展开 `streamEdges`，从 `session.config()` 读取 `STREAM_INPUT_GEN_TIMEOUT_KEY` 超时配置，并只为具备 `COLLECT` / `TRANSFORM` 能力的 consumer 创建 `StreamActor`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public BlockingQueue<Object> subWorkflowStream()` | 返回子图输出队列；当 `subGraph` 为 `false` 时抛出 `GRAPH_STREAM_ACTOR_EXECUTION_ERROR`。 |
| `public StreamTransform getStreamTransform()` | 返回内部复用的 `StreamTransform` 实例。 |
| `public void produce(String producerId, Object messageContent, ComponentAbility ability, boolean firstFrame)` | 将 `messageContent` 包装成 `{producerId: value}` 并分发到全部下游 consumer；没有 consumer 时只记录 warning 日志。 |
| `public void endMessage(String producerId, ComponentAbility ability)` | 发送 `"END_" + producerId` 形式的结束帧，通知下游处理器收敛。 |
| `public Map<String, Object> consume(String consumerId, ComponentAbility ability, Object schema, Consumer<Object> streamCallback)` | 为指定 consumer 生成与 `schema` 结构一致的迭代器映射；找不到 `StreamActor` 时返回空映射。 |
| `public void awaitCompletion()` | 等待全部 `StreamActor` 完成当前 stream 调用与处理器线程。 |
| `public void shutdown()` | 逐个关闭全部 `StreamActor`，用于图执行结束或异常中断后的清理。 |
