# com.openjiuwen.core.foundation.store.query.MilvusQueryDialect

## class MilvusQueryDialect

```java
public final class MilvusQueryDialect
```

Milvus 查询方言定义提供器。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static QueryLanguageDefinition definition()` | 返回 Milvus 查询方言定义。 |

## 使用说明

- 当前实现支持 comparison、range、arithmetic、null check、jsonFilter、array、logical 与 textMatch 等表达式类别。
- 非法操作会通过 `RETRIEVAL_VECTOR_STORE_QUERY_INVALID` 抛出错误。
