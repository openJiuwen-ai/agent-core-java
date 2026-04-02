# com.openjiuwen.core.workflow.component.llm.QuestionerState

## class QuestionerState

```java
public class QuestionerState
```

Questioner component state machine.

## Fields

| Signature | Description |
| --- | --- |
| `private static final String QUESTIONER_STATE_KEY =` | . |
| `private int responseNum` | Response num. |
| `private Object userResponse =` | . |
| `private String question =` | . |
| `private ExecutionStatus status = ExecutionStatus.START` | . |

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerState()` | Create a new `QuestionerState` instance. |
| `public QuestionerState(int responseNum, Object userResponse, String question, Map<String, Object> extractedKeyFields, ExecutionStatus status)` | Create a new `QuestionerState` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static QuestionerState deserialize(Map<String, Object> rawState)` | Execute `deserialize`. |
| `public Map<String, Object> serialize()` | Execute `serialize`. |
| `public QuestionerState handleEvent(QuestionerEvent event)` | Execute `handleEvent`. |
| `public static QuestionerState loadFromSession(Object sessionState)` | Execute `loadFromSession`. |
| `public static void storeToSession(QuestionerState state, com.openjiuwen.core.session.NodeSessionApi session)` | Execute `storeToSession`. |
| `public boolean isUndergoingInteraction()` | Report whether undergoing interaction. |
| `public boolean isFreshState()` | Report whether fresh state. |
| `public int getResponseNum()` | Return the response num. |
| `public void setResponseNum(int responseNum)` | Update the response num. |
| `public void incrementResponseNum()` | Execute `incrementResponseNum`. |
| `public Object getUserResponse()` | Return the user response. |
| `public void setUserResponse(Object userResponse)` | Update the user response. |
| `public String getQuestion()` | Return the question. |
| `public void setQuestion(String question)` | Update the question. |
| `public Map<String, Object> getExtractedKeyFields()` | Return the extracted key fields. |
| `public void setExtractedKeyFields(Map<String, Object> extractedKeyFields)` | Update the extracted key fields. |
| `public ExecutionStatus getStatus()` | Return the status. |
| `public void setStatus(ExecutionStatus status)` | Update the status. |
