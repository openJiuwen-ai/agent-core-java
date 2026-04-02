# com.openjiuwen.core.workflow.component.llm.OutputCache

## class OutputCache

```java
public class OutputCache
```

Questioner 处理过程中的临时输出缓存。

它集中保存当前用户回复、待追问问题和字段抽取结果，供 `QuestionerDirectReplyHandler` 在多轮交互之间复用。

## Fields

| Signature | Description |
| --- | --- |
| `private Object userResponse =` | 当前缓存的用户回复内容。 |
| `private String question =` | 当前待返回给用户的追问文本。 |

## Notes

- This type uses Lombok-generated members; the page lists source-defined fields and explicit methods only.
