# com.openjiuwen.core.session.interaction.InteractionOutput

## 类 InteractionOutput

```java
public class InteractionOutput
```

交互事件输出载荷，保存交互目标 ID 与对应值。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public InteractionOutput()` | 创建一个空载荷对象。 |
| `public InteractionOutput(String id, Object value)` | 使用给定 `id` 和 `value` 创建交互输出载荷。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getId()` | 返回交互目标 ID。 |
| `public void setId(String id)` | 设置交互目标 ID。 |
| `public Object getValue()` | 返回交互值。 |
| `public void setValue(Object value)` | 设置交互值。 |
| `public boolean equals(Object o)` | 基于字段值比较两个 `InteractionOutput` 是否相等。 |
| `public int hashCode()` | 返回与 `equals(...)` 对应的哈希值。 |

## 说明

- 相关测试：`WorkflowInteractionTest`。
