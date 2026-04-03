# com.openjiuwen.core.graph.pregel.PregelNode

## 类 PregelNode

```java
public class PregelNode
```

Pregel 执行图中的节点定义。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | `String` | `-` | 节点名称。 |
| `func` | `Object` | `-` | 节点实际执行的 callable 对象。 |
| `routers` | `List<IRouter>` | `-` | 节点执行完成后用于派发消息的路由器列表。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PregelNode(String name, Object func, List<IRouter> routers)` | 基于名称、执行函数与路由器列表创建 `PregelNode`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getName()` | 返回当前 `name`。 |
| `public Object getFunc()` | 返回当前 `func`。 |
| `public List<IRouter> getRouters()` | 返回当前 `routers`。 |

## 相关测试

- `PregelTest`
- `TaskExecutorPoolTest`
