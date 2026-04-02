# com.openjiuwen.core.graph.pregel.PregelBuilder

## 类 PregelBuilder

```java
public class PregelBuilder
```

用于构建 `Pregel` 引擎的 builder。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PregelBuilder()` | 创建 `PregelBuilder`，并自动注册 `__start__` 与 `__end__` 两个虚拟节点。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public PregelBuilder addNode(String name, Object fn, List<IRouter> routers)` | 注册节点，并为该节点附加一个默认 `TriggerChannel`。 |
| `public PregelBuilder addNode(String name, Object fn)` | 注册不带初始路由器的节点。 |
| `public PregelBuilder addEdge(Object start, Object end)` | 根据 `start/end` 的形态创建 barrier、1→N 或 1→1 边。 |
| `public PregelBuilder addBranch(String src, java.util.function.Function<Object, Object> selector)` | 为来源节点注册条件分支路由。 |
| `public Pregel build(Store store, Consumer<PregelLoop> afterStepCallback)` | 构建带可选状态存储与 after-step 回调的 `Pregel` 引擎。 |
| `public Pregel build()` | 构建不带状态存储和回调的 `Pregel` 引擎。 |

## 相关测试

- `PregelTest`
