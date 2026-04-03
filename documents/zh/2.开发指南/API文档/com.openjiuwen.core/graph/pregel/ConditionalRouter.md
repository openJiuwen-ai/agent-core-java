# com.openjiuwen.core.graph.pregel.ConditionalRouter

## 类 ConditionalRouter

```java
public class ConditionalRouter implements IRouter
```

通过 selector 动态决定目标节点的条件路由器。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `selector` | `Function<Object, Object>` | `-` | 返回单个目标或目标列表的选择函数。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ConditionalRouter(Function<Object, Object> selector)` | 基于目标选择函数创建 `ConditionalRouter`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<Message> dispatch(String sourceNode)` | 执行 `selector`，将结果规范化为目标列表，并生成对应的 `TriggerMessage`。 |

## 相关测试

- `PregelTest`
