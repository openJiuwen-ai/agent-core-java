# com.openjiuwen.core.foundation.store.query.QueryDialectRegistration

## class QueryDialectRegistration

```java
public final class QueryDialectRegistration
```

内建查询方言注册入口。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static void ensureRegistered()` | 把内建的 Milvus 与 Chroma 方言注册到 `QueryLanguageRegistry`。 |

## 使用说明

- 该方法是幂等的，重复调用不会重复注册。
