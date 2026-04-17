# com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema

## 类 WorkflowSchema

```java
public class WorkflowSchema
```

旧版工作流声明对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `id` | `String` | `""` | 工作流标识。 |
| `name` | `String` | `""` | 工作流名称。 |
| `description` | `String` | `""` | 工作流描述。 |
| `version` | `String` | `""` | 工作流版本。 |
| `inputs` | `Map<String, Object>` | `new LinkedHashMap<>()` | 工作流输入参数定义。 |

## 说明

- 源码使用 Lombok `@Data` 与 `@Builder` 生成访问器和 builder。
