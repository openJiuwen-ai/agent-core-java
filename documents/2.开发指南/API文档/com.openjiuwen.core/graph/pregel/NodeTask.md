# com.openjiuwen.core.graph.pregel.NodeTask

## 类 NodeTask

```java
public class NodeTask implements Callable<Object>
```

执行单个 Pregel 节点并产出路由消息的任务单元。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `node` | `PregelNode` | `-` | 当前要执行的 Pregel 节点。 |
| `config` | `PregelConfig` | `-` | 当前执行使用的 Pregel 配置。 |
| `version` | `int` | `-` | 当前节点版本号，用于拼接内部命名空间。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public NodeTask(PregelNode node, PregelConfig config, int version)` | 基于节点、配置与版本号创建 `NodeTask`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object call() throws Exception` | 执行节点函数并派发路由消息；成功时返回 `List<Message>`，中断时返回 `GraphInterrupt` 实例。 |

## 相关测试

- `TaskExecutorPoolTest`
