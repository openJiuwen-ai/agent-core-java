# com.openjiuwen.core.retrieval.common.RetrievalExceptions

## 类 RetrievalExceptions

```java
public final class RetrievalExceptions
```

retrieval 模块异常构造工具，统一创建带 `StatusCode` 的错误对象。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static BaseError error(StatusCode statusCode, String message)` | 创建带状态码与消息的错误对象。 |
| `public static ValidationError validation(String reason)` | 创建检索模块参数校验异常。 |
