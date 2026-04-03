# com.openjiuwen.core.foundation.llm.schema.ToolMessage

## 类 ToolMessage

```java
public class ToolMessage extends BaseMessage
```

表示 tool 角色返回的完整消息。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private String toolCallId` | 保存 `toolCallId` 标识。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ToolMessage(String content, String toolCallId) {` | 构造 `ToolMessage` 实例。 |
| `public ToolMessage(String content, String toolCallId, String name) {` | 构造 `ToolMessage` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getRole() {` | 返回 `role` 属性。 |

## 说明

- 所有签名均以当前 Java 源码为准。
