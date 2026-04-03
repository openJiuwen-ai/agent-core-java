# com.openjiuwen.core.workflow.component.llm.IntentDetectionInput

## class IntentDetectionInput

```java
public class IntentDetectionInput
```

意图识别组件的输入模型。

`query` 表示当前待分类的用户输入，`extraFields` 用于保留除 `query` 之外的其他原始字段；`fromMap(...)` 会把这些附加字段完整拷贝出来。

## Fields

| Signature | Description |
| --- | --- |
| `private String query =` | . |

## Constructors

| Signature | Description |
| --- | --- |
| `public IntentDetectionInput()` | Create a new `IntentDetectionInput` instance. |
| `public IntentDetectionInput(String query)` | Create a new `IntentDetectionInput` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static IntentDetectionInput fromMap(Map<String, Object> map)` | Execute `fromMap`. |
| `public String getQuery()` | Return the query. |
| `public void setQuery(String query)` | Update the query. |
| `public Map<String, Object> getExtraFields()` | Return the extra fields. |
