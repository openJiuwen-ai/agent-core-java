# com.openjiuwen.core.workflow.component.llm.FieldInfo

Questioner 字段抽取与追问流程使用的字段描述对象。

## class FieldInfo

```java
public class FieldInfo
```

## Lombok

- 该类型使用 `@Data`、`@Builder`、`@NoArgsConstructor` 和 `@AllArgsConstructor` 生成访问器、构建器与构造方法。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `fieldName` | `String` | - | 字段名。 |
| `description` | `String` | - | 字段说明。 |
| `type` | `String` | `"string"` | 字段类型。 |
| `cnFieldName` | `String` | `""` | 中文显示名。 |
| `required` | `boolean` | `false` | 是否必填。 |
| `defaultValue` | `Object` | `""` | 默认值。 |

## Notes

- `Builder.Default` 仅在使用 Lombok Builder 构造实例时为 `type`、`cnFieldName`、`required` 与 `defaultValue` 提供默认值。
