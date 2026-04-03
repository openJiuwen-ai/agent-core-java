# com.openjiuwen.core.runner.mq.QueueMessage

## 类 QueueMessage

```java
public class QueueMessage
```

`QueueMessage` 是消息队列通用消息载体，封装 `messageId`、`payload`、`errorCode` 与 `errorMsg`。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `messageId` | `String` | `""` | - |
| `payload` | `Object` | `-` | - |
| `errorCode` | `int` | `0` | - |
| `errorMsg` | `String` | `""` | - |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public QueueMessage()` | - |
| `public QueueMessage(String messageId, Object payload)` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getMessageId()` | - |
| `public void setMessageId(String messageId)` | - |
| `public Object getPayload()` | - |
| `public void setPayload(Object payload)` | - |
| `public int getErrorCode()` | - |
| `public void setErrorCode(int errorCode)` | - |
| `public String getErrorMsg()` | - |
| `public void setErrorMsg(String errorMsg)` | - |

## 相关测试

- `MessageQueueInMemoryTest`
