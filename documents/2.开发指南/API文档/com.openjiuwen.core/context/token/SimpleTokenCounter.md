# com.openjiuwen.core.context.token.SimpleTokenCounter

## class SimpleTokenCounter

```java
public class SimpleTokenCounter extends TokenCounter
```

`SimpleTokenCounter` 是 Java 侧的默认启发式 token 计数器，使用“约 4 个字符折算 1 个 token”的近似规则，再叠加消息和工具包装开销。

## 构造方法

### `public SimpleTokenCounter()`

使用默认模型标签 `gpt-4` 创建计数器。

### `public SimpleTokenCounter(String model)`

使用自定义模型标签创建计数器。

## 主要方法

### `public int count(String text, String model)`

按字符长度估算单段文本 token 数；空文本返回 `0`，非空文本至少返回 `1`。

### `public int countMessages(List<BaseMessage> messages, String model)`

统计消息列表 token 数。

**说明**

- 每条消息都会按 `<|start|>role\ncontent<|end|>` 形式估算。
- `AssistantMessage` 存在 `toolCalls` 时，会额外序列化工具调用 JSON 并计入 token；序列化失败时按每个工具调用约 `20` 个 token 回退估算。
- 最终总数会追加固定的 `REPLY_OVERHEAD`。

### `public int countTools(List<ToolInfo> tools, String model)`

把每个工具的名称、描述和参数定义组装成 JSON 后估算 token 数。

**说明**

- 工具序列化失败时按每个工具约 `50` 个 token 回退估算。

## 说明

- 该实现主要用于没有原生 `tiktoken` 绑定时的回退场景，不承诺与真实模型编码完全一致。
- `SimpleTokenCounterTest` 覆盖了文本、消息和工具定义的基本计数行为。
