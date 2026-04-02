# com.openjiuwen.core.workflow.component.llm.QuestionerUtils

## class QuestionerUtils

```java
public final class QuestionerUtils
```

Utility methods for the Questioner component.

## Methods

| Signature | Description |
| --- | --- |
| `public static String formatTemplate(String template, Map<String, Object> userFields)` | Format a template string replacing {{key}} placeholders with values from userFields. |
| `public static String formatContinueAskQuestion(List<FieldInfo> nonExtractedKeyFields, String acceptLanguage)` | Build the "continue asking" question text for non-extracted required fields. |
| `public static Map<String, Object> formatQuestionerOutput(OutputCache outputCache)` | Build the questioner output map from an OutputCache. |
| `public static QuestionerInput validateInputs(Object inputs)` | Validate inputs into a QuestionerInput. |
| `public static boolean isValidValue(Object inputValue)` | Check if a value is considered "valid" (non-null, non-empty, not "null"/"none"). |
| `public static Object[] validateAndConvertType(Object value, String expectedType)` | Validate and convert a value to the expected field type. |
