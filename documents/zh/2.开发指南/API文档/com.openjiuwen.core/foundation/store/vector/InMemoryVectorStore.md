# com.openjiuwen.core.foundation.store.vector.InMemoryVectorStore

## class InMemoryVectorStore

```java
public class InMemoryVectorStore extends AbstractRetrievalVectorStoreAdapter
```

foundation 层的内存向量存储适配器。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public InMemoryVectorStore()` | 使用默认选项构造内存向量存储。 |
| `public InMemoryVectorStore(Map<String, Object> options)` | 使用自定义选项构造内存向量存储。 |

## 使用说明

- 默认选项会把 `database_name` 设为 `default`、`collection_name` 设为 `default_collection`、`distance_metric` 设为 `cosine`、`index_type` 设为 `hybrid`。
- 标准 collection 生命周期、写入、搜索和删除能力由父类适配器统一提供。
