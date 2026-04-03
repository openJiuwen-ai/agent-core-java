# com.openjiuwen.core.graph.CompiledGraph

## 类 CompiledGraph

```java
public class CompiledGraph extends ExecutableGraph<Object, Map<String, Object>>
```

封装 `Pregel` 引擎与 `Checkpointer` 的可执行图实现。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `pregel` | `Pregel` | `-` | 负责实际图执行的 Pregel 引擎。 |
| `checkpointer` | `Checkpointer` | `-` | 主工作流执行前后使用的 checkpoint 组件。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public CompiledGraph(Pregel pregel, Checkpointer checkpointer)` | 基于给定的 `Pregel` 与 `Checkpointer` 创建可执行图。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Iterator<Map<String, Object>> stream(Object inputs, BaseSession session)` | 流式执行尚未实现，当前返回 `null`。 |
| `public void interrupt(Map<String, Object> message)` | 当前未实现中断处理，方法为 no-op。 |

## 相关测试

- `CompiledGraphTest`
