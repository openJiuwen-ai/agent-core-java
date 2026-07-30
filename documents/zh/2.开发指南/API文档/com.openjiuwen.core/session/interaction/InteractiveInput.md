# com.openjiuwen.core.session.interaction.InteractiveInput

## 类 InteractiveInput

```java
public class InteractiveInput implements Serializable
```

用于承载用户交互输入的数据对象，既支持原始输入，也支持按节点 ID 组织的输入映射。

## 字段

| 签名 | 说明 |
| --- | --- |
| `private Map<String, Object> userInputs` | `nodeId -> 用户输入值` 的映射。 |
| `private Object rawInputs` | 不绑定节点 ID 的原始输入，通常用于首个交互。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public InteractiveInput()` | 创建一个没有预置输入的对象。 |
| `public InteractiveInput(Object rawInputs)` | 使用原始输入创建对象；`rawInputs = null` 时会抛出错误。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, Object> getUserInputs()` | 返回节点输入映射。 |
| `public void setUserInputs(Map<String, Object> userInputs)` | 覆盖节点输入映射。 |
| `public Object getRawInputs()` | 返回原始输入。 |
| `public void setRawInputs(Object rawInputs)` | 覆盖原始输入。 |
| `public void update(String nodeId, Object value)` | 为指定节点写入一条用户输入；存在 `rawInputs` 时禁止调用。 |

## 说明

- 相关测试：`InteractiveInputFullTest`、`InteractiveInputTest`。
- `update(...)` 会在 `nodeId` 或 `value` 为空时抛出 `INTERACTION_INPUT_INVALID`。
- 对象可由持久化 checkpointer 使用 Java 原生序列化保存；此时 `userInputs` 中的键和值以及 `rawInputs` 也必须可序列化，否则保存会明确失败。
