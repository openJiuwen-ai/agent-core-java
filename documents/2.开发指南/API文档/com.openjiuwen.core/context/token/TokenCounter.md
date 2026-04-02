# com.openjiuwen.core.context.token.TokenCounter

## abstract class TokenCounter

```java
public abstract class TokenCounter
```

`TokenCounter` 定义文本、消息列表和工具定义 token 统计的统一抽象，供 `SessionModelContext` 和 `ContextWindow` 统计模块复用。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `count(String text, String model)` | `int` | 统计纯文本 token 数。 |
| `count(String text)` | `int` | 使用默认模型参数的便捷重载。 |
| `countMessages(List<BaseMessage> messages, String model)` | `int` | 统计消息列表 token 数。 |
| `countMessages(List<BaseMessage> messages)` | `int` | 使用默认模型参数统计消息列表。 |
| `countTools(List<ToolInfo> tools, String model)` | `int` | 统计工具定义 token 数。 |
| `countTools(List<ToolInfo> tools)` | `int` | 使用默认模型参数统计工具定义。 |

## 说明

- 具体实现可以按模型编码策略实现精确或近似计数。
- 当前任务范围内的默认实现为 `SimpleTokenCounter`。
