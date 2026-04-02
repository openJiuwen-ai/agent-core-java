# com.openjiuwen.core.graph.pregel.Pregel

## 类 Pregel

```java
public class Pregel
```

实现 BSP 模型的 Pregel 图执行引擎。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `nodes` | `Map<String, PregelNode>` | `-` | 节点名到 `PregelNode` 的映射。 |
| `channels` | `List<Channel>` | `-` | 当前图使用的全部 channel。 |
| `initial` | `String` | `-` | 初始触发节点名。 |
| `store` | `Store` | `-` | 可选的图状态持久化存储。 |
| `afterStep` | `Consumer<PregelLoop>` | `-` | 每个 super-step 结束后执行的可选回调。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public Pregel(Map<String, PregelNode> nodes, List<Channel> channels, Store store, Consumer<PregelLoop> afterStep)` | 使用默认起始节点 `PregelConstants.START` 创建 `Pregel`。 |
| `public Pregel(Map<String, PregelNode> nodes, List<Channel> channels, String initial, Store store, Consumer<PregelLoop> afterStep)` | 基于节点、channel、起始节点、存储与 after-step 回调创建 `Pregel`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, Object> run(PregelConfig config) throws Exception` | 执行 Pregel 图；顶层中断会返回包含 `TASK_STATUS_INTERRUPT` 的结果映射，其余异常继续抛出。 |
| `public Map<String, PregelNode> getNodes()` | 返回当前 `nodes`。 |
| `public List<Channel> getChannels()` | 返回当前 `channels`。 |
| `public String getInitial()` | 返回当前 `initial`。 |
| `public Store getStore()` | 返回当前 `store`。 |
| `public Consumer<PregelLoop> getAfterStep()` | 返回当前 `afterStep`。 |

## 相关测试

- `CompiledGraphTest`
- `PregelTest`
