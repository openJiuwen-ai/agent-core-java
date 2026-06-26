# com.openjiuwen.core.controller.legacy.utils.ReasonerUtils

## final class ReasonerUtils

```java
public final class ReasonerUtils
```

`ReasonerUtils` 提供旧版 reasoner 读取聊天历史的静态辅助方法。

## 主要方法

### `public static List<BaseMessage> getChatHistory(ContextEngine contextEngine, Session session, int chatHistoryMaxTurn)`

从 `ContextEngine` 中读取当前会话的 `ModelContext`，并返回最近 `2 * chatHistoryMaxTurn` 条消息；当上下文引擎、会话或上下文不存在时返回空列表。

## 说明

- 该类是纯静态工具类，私有构造函数禁止实例化。
- `DefaultIntentDetector` 中的历史读取逻辑与这里保持同样的截断规则。
