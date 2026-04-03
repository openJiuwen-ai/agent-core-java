# com.openjiuwen.core.workflow.component.tool.ToolComponentInput

## 类 ToolComponentInput

```java
public class ToolComponentInput
```

Tool 组件输入模型，保存传给工具的动态键值对。

## 字段

| 签名 | 说明 |
| --- | --- |
| `private final Map<String, Object> fields` | 工具输入键值集合。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ToolComponentInput()` | 创建 `ToolComponentInput` 实例。 |
| `public ToolComponentInput(Map<String, Object> fields)` | 创建 `ToolComponentInput` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, Object> getFields()` | 返回工具输入键值集合。 |
| `public Object get(String key)` | 读取指定键对应的输入值。 |
| `public void put(String key, Object value)` | 写入工具输入字段。 |
| `public Map<String, Object> toMap()` | 转换为 `Map` 表示。 |
| `public static ToolComponentInput fromMap(Object inputs)` | 根据 `Map` 构造 `ToolComponentInput` 实例。 |
