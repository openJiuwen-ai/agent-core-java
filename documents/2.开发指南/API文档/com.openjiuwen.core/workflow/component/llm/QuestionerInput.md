# com.openjiuwen.core.workflow.component.llm.QuestionerInput

## class QuestionerInput

```java
public class QuestionerInput
```

Questioner 组件输入模型。

`query` 保存当前用户输入，`extraFields` 保存其余附加字段；该对象可在 `Map<String, Object>` 与强类型表示之间转换。

## Fields

| Signature | Description |
| --- | --- |
| `private Object query =` | 当前用户输入内容。 |

## Methods

| Signature | Description |
| --- | --- |
| `public static QuestionerInput fromMap(Map<String, Object> inputs)` | Execute `fromMap`. |
| `public Map<String, Object> toMap()` | Execute `toMap`. |

## Notes

- This type uses Lombok-generated members; the page lists source-defined fields and explicit methods only.
