# com.openjiuwen.core.workflow.component.llm.IntentDetectionOutput

## class IntentDetectionOutput

```java
public class IntentDetectionOutput
```

Output model for IntentDetection component.

## Fields

| Signature | Description |
| --- | --- |
| `private int classificationId = -1` | . |
| `private String reason =` | . |
| `private String categoryName =` | . |

## Constructors

| Signature | Description |
| --- | --- |
| `public IntentDetectionOutput()` | Create a new `IntentDetectionOutput` instance. |
| `public IntentDetectionOutput(int classificationId, String reason, String categoryName)` | Create a new `IntentDetectionOutput` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public Map<String, Object> toMap()` | Execute `toMap`. |
| `public int getClassificationId()` | Return the classification id. |
| `public void setClassificationId(int classificationId)` | Update the classification id. |
| `public String getReason()` | Return the reason. |
| `public void setReason(String reason)` | Update the reason. |
| `public String getCategoryName()` | Return the category name. |
| `public void setCategoryName(String categoryName)` | Update the category name. |
