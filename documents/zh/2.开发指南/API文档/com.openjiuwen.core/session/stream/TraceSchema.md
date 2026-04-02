# com.openjiuwen.core.session.stream.TraceSchema

## 类 TraceSchema

```java
public class TraceSchema implements WorkflowChunk
```

trace 流 schema。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TraceSchema()` | 创建一个空的 trace schema。 |
| `public TraceSchema(String type, Object payload)` | 使用类型和载荷创建 trace schema。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getType()` | 返回 trace 类型。 |
| `public void setType(String type)` | 设置 trace 类型。 |
| `public Object getPayload()` | 返回 trace 载荷。 |
| `public void setPayload(Object payload)` | 设置 trace 载荷。 |
| `public static TraceSchema fromMap(Map<String, Object> data)` | 从 `Map` 构建并校验一个 `TraceSchema`。 |

## 说明

- 相关测试：`StreamOutputFullTest`、`StreamOutputTest`。
