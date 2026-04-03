# com.openjiuwen.core.foundation.llm.schema.BaseMessage

## 类 BaseMessage

```java
public class BaseMessage
```

定义对话消息的公共字段与基础能力。

## 字段

| 声明 | 说明 |
| --- | --- |
| `private String role` | 保存 `role` 相关状态或配置。 |
| `private Object content` | 保存文本或结构化内容。 |
| `private String name` | 保存 `name` 相关状态或配置。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BaseMessage(String role, String content) {` | 构造 `BaseMessage` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getContentAsString() {` | 返回 `contentAsString` 属性。 |
| `public List<Object> getContentAsList() {` | 返回 `contentAsList` 属性。 |

## 说明

- 所有签名均以当前 Java 源码为准。
