# com.openjiuwen.core.session.stream.CustomSchema

## 类 CustomSchema

```java
public class CustomSchema implements WorkflowChunk
```

允许携带任意属性的自定义流 schema。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public CustomSchema()` | 创建一个空的自定义 schema。 |
| `public CustomSchema(Map<String, Object> properties)` | 使用给定属性映射创建自定义 schema。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object get(String key)` | 按键读取自定义属性。 |
| `public void put(String key, Object value)` | 写入一条自定义属性。 |
| `public Map<String, Object> getProperties()` | 返回全部属性映射。 |
| `public static CustomSchema fromMap(Map<String, Object> data)` | 从 `Map` 构建并校验一个 `CustomSchema`。 |

## 说明

- 相关测试：`StreamOutputFullTest`、`StreamOutputTest`。
