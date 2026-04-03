# com.openjiuwen.core.graph.pregel.StaticRouter

## 类 StaticRouter

```java
public class StaticRouter implements IRouter
```

向固定目标发送 `TriggerMessage` 的静态路由器（1→N）。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `targets` | `List<String>` | `-` | 当前路由器固定发送的目标节点列表。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public StaticRouter(List<String> targets)` | 基于目标节点列表创建 `StaticRouter`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<Message> dispatch(String sourceNode)` | 针对每个目标节点生成一条以 `sourceNode` 为 sender 的 `TriggerMessage`。 |

## 相关测试

- `PregelTest`
- `TaskExecutorPoolTest`
