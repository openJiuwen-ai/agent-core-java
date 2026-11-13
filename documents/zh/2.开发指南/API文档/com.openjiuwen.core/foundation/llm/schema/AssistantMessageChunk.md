# com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk

## 类 AssistantMessageChunk

```java
public class AssistantMessageChunk extends AssistantMessage
```

表示 assistant 流式输出的消息片段。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public AssistantMessageChunk merge(AssistantMessageChunk other) {` | 合并流式片段；保留最新非空 responseModel 和 usageMetadata。 |

## 说明

- 所有签名均以当前 Java 源码为准。
