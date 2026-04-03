# com.openjiuwen.core.workflow.component.llm.QuestionerOutput

## class QuestionerOutput

```java
public class QuestionerOutput
```

Questioner 组件输出模型。

它将已抽取字段保存在内部映射中，并按需附带 `user_response` 与 `question`，用于返回当前轮的追问或最终收集结果。

## Fields

| Signature | Description |
| --- | --- |
| `private Object userResponse =` | 当前轮记录的用户回复。 |
| `private String question =` | 当前待展示或最近一次展示的问题。 |

## Methods

| Signature | Description |
| --- | --- |
| `public Object getUserResponse()` | Return the user response. |
| `public void setUserResponse(Object userResponse)` | Update the user response. |
| `public String getQuestion()` | Return the question. |
| `public void setQuestion(String question)` | Update the question. |
| `public void putField(String key, Object value)` | Execute `putField`. |
| `public Map<String, Object> toMap()` | Execute `toMap`. |
| `public static QuestionerOutput fromFields(Map<String, Object> fields)` | Execute `fromFields`. |
