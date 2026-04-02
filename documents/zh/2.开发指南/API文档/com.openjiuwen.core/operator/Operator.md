# com.openjiuwen.core.operator.Operator

## abstract class Operator

```java
public abstract class Operator
```

`Operator` 是所有原子算子的抽象基类，统一约束可调参数描述、状态快照、单次调用与可选流式调用接口。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getOperatorId()` | `String` | 返回当前算子的唯一标识，通常写入 `Session` 轨迹。 |
| `getTunables()` | `Map<String, TunableSpec>` | 返回可调参数名到参数规格的映射。 |
| `setParameter(String target, Object value)` | `void` | 应用单个参数的新值。 |
| `getState()` | `Map<String, Object>` | 导出当前算子的可序列化状态快照。 |
| `loadState(Map<String, Object> state)` | `void` | 从状态快照恢复运行态。 |
| `invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `Object` | 执行一次完整的算子调用；具体输入/输出由子类定义。 |
| `invoke(Map<String, Object> inputs, Session session)` | `Object` | 省略 `kwargs` 的便捷重载，内部传入空映射。 |
| `stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `OperatorStream<?>` | 可选的流式执行入口；基类默认直接抛出 `UnsupportedOperationException`。 |
| `stream(Map<String, Object> inputs, Session session)` | `OperatorStream<?>` | 省略 `kwargs` 的流式便捷重载。 |

## 说明

- `OperatorBaseTest` 验证了 `Operator` 仍然是抽象类，且基类 `stream()` 的默认异常消息为 `stream not implemented`。
- `LLMCallOperatorTest`、`MemoryCallOperatorTest` 与 `ToolCallOperatorTest` 表明，具体算子会在调用或流式执行结束后清理 `Session` 中的当前算子 ID。
