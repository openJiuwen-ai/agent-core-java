# com.openjiuwen.core.single_agent.legacy.schema.WorkflowSchema

## class WorkflowSchema

```java
public class WorkflowSchema
```

`WorkflowSchema` 描述应用层配置中的单个工作流引用。

## 字段

| 字段 | 类型 | 默认值 | JSON 别名 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `""` | - | 工作流 ID。 |
| `name` | `String` | `""` | - | 工作流名称。 |
| `version` | `String` | `"1.0"` | - | 工作流版本。 |
| `description` | `String` | `""` | - | 工作流描述。 |
| `inputParams` | `Map<String, Object>` | `new LinkedHashMap<>()` | `inputs` / `inputParams` | 工作流输入参数 Schema。 |

## 显式方法

### `public Map<String, Object> getInputs()`

返回 `inputParams`，作为工作流输入参数的访问器。

### `public void setInputs(Map<String, Object> inputs)`

将 `inputs` 赋值到 `inputParams`。

## 说明

- JSON 序列化时对外字段名为 `inputs`，内部属性名为 `inputParams`。
- `getInputs()` 与 `setInputs()` 提供和 `inputParams` 等价的访问入口。
