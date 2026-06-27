# com.openjiuwen.core.workflow.component.llm.QuestionerState

## class QuestionerState

```java
public class QuestionerState
```

Questioner 组件状态机基类。

该类型负责维护交互轮次、最近一次用户回复、当前问题文本、已抽取字段集合以及执行状态，并提供序列化、session 持久化和事件驱动迁移能力。

## Fields

| Signature | Description |
| --- | --- |
| `private static final String QUESTIONER_STATE_KEY =` | 写入 session 时使用的固定键名。 |
| `private int responseNum` | 已发生的用户交互轮次计数。 |
| `private Object userResponse =` | 最近一次用户回复内容。 |
| `private String question =` | 当前待追问或最近一次追问文本。 |
| `private ExecutionStatus status = ExecutionStatus.START` | 当前状态机阶段。 |

## Constructors

| Signature | Description |
| --- | --- |
| `public QuestionerState()` | 创建新的空白状态对象。 |
| `public QuestionerState(int responseNum, Object userResponse, String question, Map<String, Object> extractedKeyFields, ExecutionStatus status)` | 使用给定状态值创建状态对象。 |

## Methods

| Signature | Description |
| --- | --- |
| `public static QuestionerState deserialize(Map<String, Object> rawState)` | 从原始状态映射恢复状态对象，并根据 `status` 转换为对应子状态。 |
| `public Map<String, Object> serialize()` | 将当前状态序列化为可写入 session 的映射。 |
| `public QuestionerState handleEvent(QuestionerEvent event)` | 根据事件切换到起始态、交互态或结束态。 |
| `public static QuestionerState loadFromSession(Object sessionState)` | 从节点 state、扁平 state 或嵌套 `comp_state` 结构中恢复 Questioner 状态。 |
| `public static void storeToSession(QuestionerState state, com.openjiuwen.core.session.NodeSessionApi session)` | 以 `questioner_state` 键把当前状态写回 session。 |
| `public boolean isUndergoingInteraction()` | 判断当前是否仍处于等待用户回复的交互阶段。 |
| `public boolean isFreshState()` | 判断是否仍处于未交互的初始状态。 |
| `public int getResponseNum()` | 返回当前交互轮次。 |
| `public void setResponseNum(int responseNum)` | 更新交互轮次。 |
| `public void incrementResponseNum()` | 将交互轮次加一。 |
| `public Object getUserResponse()` | 返回最近一次用户回复。 |
| `public void setUserResponse(Object userResponse)` | 更新最近一次用户回复。 |
| `public String getQuestion()` | 返回当前问题文本。 |
| `public void setQuestion(String question)` | 更新当前问题文本。 |
| `public Map<String, Object> getExtractedKeyFields()` | 返回已抽取字段集合。 |
| `public void setExtractedKeyFields(Map<String, Object> extractedKeyFields)` | 更新已抽取字段集合。 |
| `public ExecutionStatus getStatus()` | 返回当前执行状态。 |
| `public void setStatus(ExecutionStatus status)` | 更新当前执行状态。 |
