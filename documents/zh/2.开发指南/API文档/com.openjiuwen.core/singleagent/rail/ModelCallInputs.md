# com.openjiuwen.core.singleagent.rail.ModelCallInputs

## 类 ModelCallInputs

```java
public class ModelCallInputs implements EventInputs
```

用于 `BEFORE_MODEL_CALL` / `AFTER_MODEL_CALL` 事件的输入载荷。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `messages` | `List<Object>` | `new ArrayList<>()` | 送入模型调用的消息列表。 |
| `tools` | `List<ToolInfo>` | `-` | 当前可供模型调用的工具描述列表。 |
| `response` | `Object` | `-` | 模型返回结果对象。 |

## 说明

- 相关测试：`ReActAgentTest`、`DataClassCoverageTest`、`RailDataClassesTest`。
- builder 默认会为 `messages` 创建空列表。
