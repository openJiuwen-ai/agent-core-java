# com.openjiuwen.core.singleagent.schema.AgentCard

## 类 AgentCard

```java
public class AgentCard extends BaseCard
```

描述 agent 能力入口的卡片模型。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `inputParams` | `Object` | `-` | 输入参数模式，既可以是 `Map<String, Object>`，也可以是 `Class<?>`。 |
| `outputParams` | `Object` | `-` | 输出参数模式，规则与 `inputParams` 相同。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public Map<String, Object> getInputParamsAsMap()` | 将 `inputParams` 解析为 `Map<String, Object>`；若存储为 `Class<?>`，则返回包含 `$javaClass` 的最小描述。 |
| `public Map<String, Object> getOutputParamsAsMap()` | 将 `outputParams` 解析为 `Map<String, Object>`。 |
| `@Override public Object toolInfo()` | 根据名称、描述和输入参数构造 `ToolInfo`。 |

## 说明

- 相关测试：`AbilityManagerSupplementTest`、`AbilityManagerTest`、`AgentCallbackManagerTest`、`ReActAgentEvolveTest`、`ReActAgentTest`、`BaseAgentTest`、`ControllerAgentTest`、`DataClassCoverageTest`、`SchemaTest`。
- `toolInfo()` 只使用 `name`、`description` 与输入参数生成工具描述；`SchemaTest` 覆盖了 builder 默认值与 `ToolInfo` 构造行为。
