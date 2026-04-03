# com.openjiuwen.core.application.llm.LlmController

## class LlmController

```java
public class LlmController
```

`LlmController` 是围绕 `LlmEventHandler` 的轻量控制器封装。它负责绑定配置和上下文引擎、归一化输入格式，并暴露常用的控制方法。

## 构造方法

### `public LlmController()`

创建一个未绑定配置的空控制器。

### `public LlmController(LlmAgentConfig config, ContextEngine contextEngine)`

立即使用给定配置和上下文引擎完成初始化。

## 主要方法

| 方法 | 说明 |
|---|---|
| `setupFromAgent(LlmAgent agent)` | 从现有 `LlmAgent` 复制配置、上下文引擎与能力管理器。 |
| `handleEvent(Event event, AgentSessionApi session)` | 委托给 `LlmEventHandler.handleInput(...)`。 |
| `createMessage(Map<String, Object> inputs)` | 将 `content` 归一化为 `query`，并返回 `InputEvent`。 |
| `setLlmControllerPromptTemplate(List<Map<String, String>> promptTemplate)` | 更新事件处理器上的提示模板。 |
| `setPromptTemplate(List<Map<String, String>> promptTemplate)` | `setLlmControllerPromptTemplate(...)` 的别名。 |
| `getAgentConfig()` | 返回当前绑定的配置。 |
| `getContextEngine()` | 返回当前绑定的 `ContextEngine`。 |
| `getEventHandler()` | 返回当前 `LlmEventHandler`；未配置时抛出 `IllegalStateException`。 |
| `static convertTimestamp(String utcTimestamp)` | 将 `yyyy-MM-dd HH:mm:ss` 形式的 UTC 时间转换到系统时区；解析失败时原样返回。 |

## 说明

- `createMessage()` 会保留结构化输入中的已有字段，并确保传入 `InputEvent` 前至少存在 `query` 键。
- `setLlmControllerPromptTemplate()` 用于直接更新当前事件处理器上的提示模板。
- `convertTimestamp()` 适合处理模型或外部系统返回的 UTC 时间戳字符串。
