# com.openjiuwen.core.foundation.store.vector.PGVectorStore

## class PGVectorStore

```java
public class PGVectorStore extends AbstractRetrievalVectorStoreAdapter
```

foundation 层的 PGVector 向量存储适配器。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PGVectorStore(Map<String, Object> options)` | 使用选项构造 PGVector 适配器。 |

## 使用说明

- 常用选项包括 `database_name`、`collection_name`、`distance_metric`，并会补齐默认 `vector_field = embedding`。
- 标准 collection 生命周期、写入、搜索和删除能力由父类适配器统一提供。
