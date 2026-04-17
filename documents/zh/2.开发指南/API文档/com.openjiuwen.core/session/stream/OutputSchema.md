# com.openjiuwen.core.session.stream.OutputSchema

## 类 OutputSchema

```java
public class OutputSchema implements WorkflowChunk
```

标准输出流 schema。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public OutputSchema()` | 创建一个空的输出 schema。 |
| `public OutputSchema(String type, int index, Object payload)` | 使用类型、索引和载荷创建输出 schema。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getType()` | 返回输出类型。 |
| `public void setType(String type)` | 设置输出类型。 |
| `public int getIndex()` | 返回输出序号。 |
| `public void setIndex(int index)` | 设置输出序号。 |
| `public Object getPayload()` | 返回输出载荷。 |
| `public void setPayload(Object payload)` | 设置输出载荷。 |
| `public static OutputSchema fromMap(Map<String, Object> data)` | 从 `Map` 构建并校验一个 `OutputSchema`。 |
| `public boolean equals(Object o)` | 基于字段值比较两个 `OutputSchema` 是否相等。 |
| `public int hashCode()` | 返回与 `equals(...)` 对应的哈希值。 |

## 说明

- 相关测试：`StreamOutputFullTest`、`StreamOutputTest`、`WorkflowInteractionTest`。
