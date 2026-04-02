# com.openjiuwen.core.operator.memory_call.MemoryCallOperator

## class MemoryCallOperator

```java
public class MemoryCallOperator extends Operator
```

`MemoryCallOperator` 是记忆调用算子，支持启用开关、重试次数控制，以及在标准 `MemoryOperation` 与自定义 `MemoryInvoker` 之间切换执行路径。

## 构造方法

### `public MemoryCallOperator(MemoryOperation memory, String memoryCallId, MemoryInvoker memoryInvoker)`

创建完整形态的记忆调用算子。

**说明**

- `memoryCallId` 为 `null` 时会回退到 `memory_call`。
- `memoryInvoker` 非空时优先于 `memory` 执行，适合桥接不满足统一契约的记忆实现。

### `public MemoryCallOperator(MemoryOperation memory)`

使用默认 `memory_call` ID、无自定义回调创建实例。

### `public MemoryCallOperator(MemoryInvoker memoryInvoker)`

只配置自定义记忆回调，由该回调独立完成调用。

### `public MemoryCallOperator()`

创建空配置实例；若在未注入 `memory` / `memoryInvoker` 的情况下调用 `invoke()`，会抛出异常。

## 可调参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `enabled` | `discrete` | 布尔开关，`false` 时任何调用都会直接失败。 |
| `max_retries` | `discrete` | 最大重试次数，约束为整数且范围限制在 `[0, 5]`。 |

## 状态快照

| 字段 | 类型 | 说明 |
|---|---|---|
| `enabled` | `boolean` | 当前是否启用。 |
| `max_retries` | `int` | 当前重试次数上限。 |

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `getOperatorId()` | `String` | 返回当前算子 ID。 |
| `setParameter(String target, Object value)` | `void` | 更新 `enabled` 或 `max_retries`；未知参数会被忽略。 |
| `loadState(Map<String, Object> state)` | `void` | 从状态映射恢复启用状态和重试次数。 |
| `invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `Object` | 设置 operator context 后执行记忆调用；`memoryInvoker` 优先，其次是 `memory.invoke(inputs, kwargs)`。 |
| `stream(Map<String, Object> inputs, Session session, Map<String, Object> kwargs)` | `OperatorStream<Object>` | 在配置了 `memory` 时委托给 `memory.stream(...)`，并在流结束时清理 operator context。 |

## 说明

- 当 `enabled = false` 时，`invoke()` 会抛出 `IllegalStateException("MemoryCallOperator disabled: ...")`。
- `max_retries` 的解析结果会被限制到 `[0, 5]`，测试覆盖了负数与大于 5 的输入。
- `MemoryInvoker` 路径只接收 `inputs`，不会收到 `kwargs`；`MemoryOperation` 路径则会完整接收 `kwargs`。
- 若未配置 `memory`，`stream()` 会直接抛出 `UnsupportedOperationException("memory stream not implemented")`。
