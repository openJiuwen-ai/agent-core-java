# com.openjiuwen.core.controller.legacy.reasoner.DefaultIntentDetector

## class DefaultIntentDetector

```java
public class DefaultIntentDetector implements IntentDetector
```

`DefaultIntentDetector` 是旧版默认意图检测实现，负责准备检测输入、解析分类结果，并在缺省情况下把用户消息转换为一个 `TaskType.WORKFLOW` 任务。

## 构造方法

### `public DefaultIntentDetector(IntentDetectionConfig intentConfig, Object agentConfig, ContextEngine contextEngine, Session session)`

绑定意图检测配置、agent 配置、上下文引擎和当前会话。

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `detect(Event event, Session session, ReasonerConfig config)` | `IntentDetectionController.Intent` | 检测意图；若未产生任务，则返回 `DEFAULT_RESPONSE` 意图。 |
| `processMessage(Event event)` | `List<Task>` | 准备输入并生成任务列表；默认实现对非空输入创建一个 workflow 任务。 |
| `prepareDetectionInput(Event event)` | `Map<String, Object>` | 拼装分类列表、提示词、聊天历史和当前输入。 |
| `parseIntentFromOutput(String llmOutput)` | `String` | 从模型输出中解析 `result` 字段，映射到分类名称。 |

## 说明

- `prepareDetectionInput()` 会在 `enableHistory = true` 时从 `ContextEngine` 读取最近 `2 * chatHistoryMaxTurn` 条消息。
- `parseIntentFromOutput()` 会清理 JSON 代码栅栏，再用正则查找 `"result"` 数值；解析失败时回退到 `defaultClass`。
- 当前 `processMessage()` 的公开逻辑没有真正调用模型，而是对非空输入直接生成一个默认 workflow 任务。
